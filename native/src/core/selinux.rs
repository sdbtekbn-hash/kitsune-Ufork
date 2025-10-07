use crate::consts::{DATABIN, LOG_PIPE, MAGISK_LOG_CON, MODULEROOT, SECURE_DIR};
use crate::ffi::get_magisk_tmp;
use base::libc::{O_CLOEXEC, O_WRONLY};
use base::{Directory, FsPathBuilder, LoggedResult, ResultExt, Utf8CStr, Utf8CStrBuf, cstr, libc};
use std::io::Write;

const UNLABEL_CON: &Utf8CStr = cstr!("u:object_r:unlabeled:s0");
const SYSTEM_CON: &Utf8CStr = cstr!("u:object_r:system_file:s0");
const ADB_CON: &Utf8CStr = cstr!("u:object_r:adb_data_file:s0");
const ROOT_CON: &Utf8CStr = cstr!("u:object_r:rootfs:s0");

fn restore_syscon_from_unlabeled(
    path: &mut dyn Utf8CStrBuf,
    con: &mut dyn Utf8CStrBuf,
) -> LoggedResult<()> {
    let dir_path_len = path.len();
    if path.get_secontext(con).log().is_ok() && con.as_str() == UNLABEL_CON {
        path.set_secontext(SYSTEM_CON)?;
    }
    let mut dir = Directory::open(path)?;
    while let Some(ref e) = dir.read()? {
        path.truncate(dir_path_len);
        path.append_path(e.name());
        if e.is_dir() {
            restore_syscon_from_unlabeled(path, con)?;
        } else if (e.is_file() || e.is_symlink())
            && path.get_secontext(con).log().is_ok()
            && con.as_str() == UNLABEL_CON
        {
            path.set_secontext(SYSTEM_CON)?;
        }
    }
    Ok(())
}

fn restore_syscon(path: &mut dyn Utf8CStrBuf) -> LoggedResult<()> {
    let dir_path_len = path.len();
    path.set_secontext(SYSTEM_CON)?;
    unsafe { libc::lchown(path.as_ptr(), 0, 0) };
    let mut dir = Directory::open(path)?;
    while let Some(ref e) = dir.read()? {
        path.truncate(dir_path_len);
        path.append_path(e.name());
        if e.is_dir() {
            restore_syscon(path)?;
        } else if e.is_file() || e.is_symlink() {
            path.set_secontext(SYSTEM_CON)?;
            unsafe { libc::lchown(path.as_ptr(), 0, 0) };
        }
    }
    Ok(())
}

pub(crate) fn restorecon() {
    if let Ok(mut file) = cstr!("/sys/fs/selinux/context")
        .open(O_WRONLY | O_CLOEXEC)
        .log()
    {
        if file.write_all(ADB_CON.as_bytes_with_nul()).is_ok() {
            cstr!(SECURE_DIR).set_secontext(ADB_CON).log_ok();
        }
    }

    let mut path = cstr::buf::default();
    let mut con = cstr::buf::new::<1024>();
    path.push_str(MODULEROOT);
    path.set_secontext(SYSTEM_CON).log_ok();
    restore_syscon_from_unlabeled(&mut path, &mut con).log_ok();

    path.clear();
    path.push_str(DATABIN);
    restore_syscon(&mut path).log_ok();
}

pub(crate) fn restore_tmpcon() -> LoggedResult<()> {
    let tmp = get_magisk_tmp();
    if tmp == "/sbin" {
        tmp.set_secontext(ROOT_CON)?;
    } else {
        unsafe { libc::chmod(tmp.as_ptr(), 0o711) };
    }

    let mut path = cstr::buf::default();
    let mut dir = Directory::open(tmp)?;
    while let Some(ref e) = dir.read()? {
        e.resolve_path(&mut path)?;
        path.set_secontext(SYSTEM_CON)?;
    }

    path.clear();
    path.append_path(tmp).append_path(LOG_PIPE);
    path.set_secontext(cstr!(MAGISK_LOG_CON))?;

    Ok(())
}

pub(crate) fn lgetfilecon(path: &Utf8CStr, con: &mut [u8]) -> bool {
    let mut con = cstr::buf::wrap(con);
    path.get_secontext(&mut con).is_ok()
}

pub(crate) fn setfilecon(path: &Utf8CStr, con: &Utf8CStr) -> bool {
    path.follow_link().set_secontext(con).is_ok()
}

pub(crate) fn lsetfilecon(path: &Utf8CStr, con: &Utf8CStr) -> bool {
    path.set_secontext(con).is_ok()
}

// Export C-compatible wrappers for deny system
#[unsafe(no_mangle)]
pub extern "C" fn setfilecon_impl(path: *const libc::c_char, con: *const libc::c_char) -> bool {
    if path.is_null() || con.is_null() {
        return false;
    }
    unsafe {
        let path = Utf8CStr::from_ptr(path);
        let con = Utf8CStr::from_ptr(con);
        setfilecon(path, con)
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn lsetfilecon_impl(path: *const libc::c_char, con: *const libc::c_char) -> bool {
    if path.is_null() || con.is_null() {
        return false;
    }
    unsafe {
        let path = Utf8CStr::from_ptr(path);
        let con = Utf8CStr::from_ptr(con);
        lsetfilecon(path, con)
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn fsetfilecon_impl(fd: libc::c_int, con: *const libc::c_char) -> bool {
    if con.is_null() {
        return false;
    }
    unsafe {
        use base::cstr;
        let con = Utf8CStr::from_ptr(con);
        // Use procfs to set context on file descriptor
        let mut path = cstr::buf::default();
        path.push_str("/proc/self/fd/");
        path.push_str(&fd.to_string());
        path.set_secontext(con).is_ok()
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn selinux_enabled_impl() -> bool {
    use base::libc::access;
    use base::libc::F_OK;
    unsafe {
        access(cstr!("/sys/fs/selinux").as_ptr(), F_OK) == 0
    }
}
