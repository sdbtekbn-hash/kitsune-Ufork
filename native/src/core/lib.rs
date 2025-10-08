#![feature(format_args_nl)]
#![feature(try_blocks)]
#![feature(let_chains)]
#![feature(fn_traits)]
#![feature(unix_socket_ancillary_data)]
#![feature(unix_socket_peek)]
#![feature(maybe_uninit_uninit_array)]
#![allow(clippy::missing_safety_doc)]

use crate::ffi::SuRequest;
use crate::socket::Encodable;
use base::{Utf8CStr, libc};
use cxx::{ExternType, type_id};
use daemon::{MagiskD, daemon_entry};
use derive::Decodable;
use logging::{android_logging, setup_logfile, zygisk_close_logd, zygisk_get_logd, zygisk_logging};
use mount::find_preinit_device;
use resetprop::{persist_delete_prop, persist_get_prop, persist_get_props, persist_set_prop};
use selinux::{lgetfilecon, lsetfilecon, restorecon, setfilecon};
use socket::{recv_fd, recv_fds, send_fd, send_fds};
use std::fs::File;
use std::mem::ManuallyDrop;
use std::ops::DerefMut;
use std::os::fd::FromRawFd;
use su::{get_pty_num, pump_tty, restore_stdin};
use zygisk::zygisk_should_load_module;

#[path = "../include/consts.rs"]
mod consts;
mod daemon;
mod db;
mod logging;
mod mount;
mod package;
mod resetprop;
mod selinux;
mod socket;
mod su;
mod zygisk;

#[allow(clippy::needless_lifetimes)]
#[cxx::bridge]
mod ffi {
    #[repr(i32)]
    enum RequestCode {
        START_DAEMON,
        CHECK_VERSION,
        CHECK_VERSION_CODE,
        STOP_DAEMON,

        _SYNC_BARRIER_,

        SUPERUSER,
        ZYGOTE_RESTART,
        DENYLIST,
        SQLITE_CMD,
        REMOVE_MODULES,
        ZYGISK,

        _STAGE_BARRIER_,

        POST_FS_DATA,
        LATE_START,
        BOOT_COMPLETE,

        END,
    }

    enum DbEntryKey {
        RootAccess,
        SuMultiuserMode,
        SuMntNs,
        DenylistConfig,
        ZygiskConfig,
        BootloopCount,
        SuManager,
        SulistConfig,
    }

    #[repr(i32)]
    enum MntNsMode {
        Global,
        Requester,
        Isolate,
    }

    #[repr(i32)]
    enum SuPolicy {
        Query,
        Deny,
        Allow,
    }

    struct ModuleInfo {
        name: String,
        z32: i32,
        z64: i32,
    }

    #[repr(i32)]
    enum ZygiskRequest {
        GetInfo,
        ConnectCompanion,
        GetModDir,
    }

    #[repr(u32)]
    enum ZygiskStateFlags {
        ProcessGrantedRoot = 0x00000001,
        ProcessOnDenyList = 0x00000002,
        DenyListEnforced = 0x40000000,
        ProcessIsMagiskApp = 0x80000000,
    }

    #[derive(Decodable)]
    struct SuRequest {
        target_uid: i32,
        target_pid: i32,
        login: bool,
        keep_env: bool,
        shell: String,
        command: String,
        context: String,
        gids: Vec<u32>,
    }

    struct SuAppRequest<'a> {
        uid: i32,
        pid: i32,
        eval_uid: i32,
        mgr_pkg: &'a str,
        mgr_uid: i32,
        request: &'a SuRequest,
    }

    unsafe extern "C++" {
        include!("include/resetprop.hpp");

        #[cxx_name = "prop_cb"]
        type PropCb;
        unsafe fn get_prop_rs(name: *const c_char, persist: bool) -> String;
        #[cxx_name = "set_prop"]
        unsafe fn set_prop_rs(name: *const c_char, value: *const c_char, skip_svc: bool) -> i32;
        unsafe fn prop_cb_exec(
            cb: Pin<&mut PropCb>,
            name: *const c_char,
            value: *const c_char,
            serial: u32,
        );
    }

    unsafe extern "C++" {
        #[namespace = "rust"]
        #[cxx_name = "Utf8CStr"]
        type Utf8CStrRef<'a> = base::ffi::Utf8CStrRef<'a>;
        #[cxx_name = "ucred"]
        type UCred = crate::UCred;

        include!("include/core.hpp");

        #[cxx_name = "get_magisk_tmp_rs"]
        fn get_magisk_tmp() -> Utf8CStrRef<'static>;
        #[cxx_name = "resolve_preinit_dir_rs"]
        fn resolve_preinit_dir(base_dir: Utf8CStrRef) -> String;
        fn setup_magisk_env() -> bool;
        fn check_key_combo() -> bool;
        fn disable_modules();
        fn exec_common_scripts(stage: Utf8CStrRef);
        fn exec_module_scripts(state: Utf8CStrRef, modules: &Vec<ModuleInfo>);
        fn prepare_modules();
        fn collect_modules(zygisk_enabled: bool, open_zygisk: bool) -> Vec<ModuleInfo>;
        fn load_modules(zygisk_enabled: bool, modules: &Vec<ModuleInfo>);
        fn install_apk(apk: Utf8CStrRef);
        fn uninstall_pkg(apk: Utf8CStrRef);
        fn update_deny_flags(uid: i32, process: &str, flags: &mut u32);
        fn is_deny_target(uid: i32, process: &str, max_len: i32) -> bool;
        fn initialize_denylist();
        fn init_nethunter_mode();
        fn enable_nethunter_mode();
        fn disable_nethunter_mode();
        
        // ReZygisk Integration
        fn init_solist_hiding();
        fn init_seccomp_hiding();
        fn init_ptrace_hiding();
        fn solist_reset_counters(load: usize, unload: usize);
        unsafe fn zygisk_cleanup_with_jni(env: *mut u8);
        unsafe fn register_plt_hook(symbol: *mut u8, backup: *mut *mut u8);
        unsafe fn register_jni_hook(clz: &str, method: *mut u8);
        fn restore_plt_hooks();
        unsafe fn restore_jni_hooks(env: *mut u8);
        fn reset_module_counters();
        fn send_seccomp_event();
        fn trace_zygote(pid: i32) -> bool;
        fn cleanup_ptrace();
        fn is_ptrace_active() -> bool;
        fn restore_zygisk_prop();
        fn switch_mnt_ns(pid: i32) -> i32;
        fn app_request(req: &SuAppRequest) -> i32;
        fn app_notify(req: &SuAppRequest, policy: SuPolicy);
        fn app_log(req: &SuAppRequest, policy: SuPolicy, notify: bool);
        fn exec_root_shell(client: i32, pid: i32, req: &mut SuRequest, mode: MntNsMode);
    }
}

#[cxx::bridge]
mod sqlite {
    unsafe extern "C++" {
        include!("include/sqlite.hpp");

        type sqlite3;
        type DbValues;
        type DbStatement;

        fn sqlite3_errstr(code: i32) -> *const c_char;
        fn open_and_init_db() -> *mut sqlite3;
        fn get_int(self: &DbValues, index: i32) -> i32;
        #[cxx_name = "get_str"]
        fn get_text(self: &DbValues, index: i32) -> &str;
        fn bind_text(self: Pin<&mut DbStatement>, index: i32, val: &str) -> i32;
        fn bind_int64(self: Pin<&mut DbStatement>, index: i32, val: i64) -> i32;
    }
}

#[repr(transparent)]
pub struct UCred(pub libc::ucred);

unsafe impl ExternType for UCred {
    type Id = type_id!("ucred");
    type Kind = cxx::kind::Trivial;
}

impl SuRequest {
    unsafe fn write_to_fd(&self, fd: i32) {
        unsafe {
            let mut w = ManuallyDrop::new(File::from_raw_fd(fd));
            self.encode(w.deref_mut()).ok();
        }
    }
}

pub fn get_prop(name: &Utf8CStr, persist: bool) -> String {
    unsafe { ffi::get_prop_rs(name.as_ptr(), persist) }
}

pub fn set_prop(name: &Utf8CStr, value: &Utf8CStr, skip_svc: bool) -> bool {
    unsafe { ffi::set_prop_rs(name.as_ptr(), value.as_ptr(), skip_svc) == 0 }
}
