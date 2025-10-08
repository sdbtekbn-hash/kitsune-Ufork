#pragma once

#include <sys/socket.h>
#include <sys/un.h>
#include <pthread.h>
#include <poll.h>
#include <string>
#include <string_view>
#include <limits>
#include <atomic>
#include <functional>
#include <vector>
#include <memory>
#include <jni.h>  // For JNIEnv and JNINativeMethod

#include <base.hpp>

#include "../core-rs.hpp"
#include <resetprop.hpp>

#define AID_ROOT   0
#define AID_SHELL  2000
#define AID_USER_OFFSET 100000

#define to_app_id(uid)  (uid % AID_USER_OFFSET)
#define to_user_id(uid) (uid / AID_USER_OFFSET)

// Return codes for daemon
enum class RespondCode : int {
    ERROR = -1,
    OK = 0,
    ROOT_REQUIRED,
    ACCESS_DENIED,
    END
};

struct ModuleInfo;

// Forward declarations for types defined in Rust
struct SuRequest;
struct SuAppRequest;
enum class SuPolicy : int;
enum class MntNsMode : int;

extern std::string native_bridge;

// Daemon
int connect_daemon(int req, bool create = false);
const char *get_magisk_tmp();
void unlock_blocks();
bool setup_magisk_env();
bool check_key_combo();
void restore_zygisk_prop();

// Sockets
struct sock_cred : public ucred {
    std::string context;
};

template<typename T>
T read_any(int fd) {
    T val;
    if (xxread(fd, &val, sizeof(val)) != sizeof(val))
        return -1;
    return val;
}

template<typename T>
void write_any(int fd, T val) {
    if (fd < 0) return;
    xwrite(fd, &val, sizeof(val));
}

bool get_client_cred(int fd, sock_cred *cred);
static inline int read_int(int fd) { return read_any<int>(fd); }
static inline void write_int(int fd, int val) { write_any(fd, val); }
std::string read_string(int fd);
bool read_string(int fd, std::string &str);
void write_string(int fd, std::string_view str);

template<typename T>
void write_vector(int fd, const std::vector<T> &vec) {
    write_int(fd, vec.size());
    xwrite(fd, vec.data(), vec.size() * sizeof(T));
}

template<typename T>
bool read_vector(int fd, std::vector<T> &vec) {
    int size = read_int(fd);
    vec.resize(size);
    return xread(fd, vec.data(), size * sizeof(T)) == size * sizeof(T);
}

// Poll control
using poll_callback = void(*)(pollfd*);
void register_poll(const pollfd *pfd, poll_callback callback);
void unregister_poll(int fd, bool auto_close);
void clear_poll();

// Thread pool
void init_thread_pool();
void exec_task(std::function<void()> &&task);

// Daemon handlers
void denylist_handler(int client, const sock_cred *cred);

// Module stuffs
void disable_modules();
void remove_modules();

// Scripting
void install_apk(rust::Utf8CStr apk);
void uninstall_pkg(rust::Utf8CStr pkg);
void exec_common_scripts(rust::Utf8CStr stage);
void exec_module_scripts(rust::Utf8CStr stage, const rust::Vec<ModuleInfo> &module_list);
void exec_script(const char *script);
void clear_pkg(const char *pkg, int user_id);
[[noreturn]] void install_module(const char *file);

// Denylist
extern std::atomic<bool> denylist_enforced;
extern bool sulist_enabled;
extern pthread_t monitor_thread;
extern int su_bin_fd;
extern int magisktmpfs_fd;
extern bool logging_muted;
extern bool HAVE_32;
int denylist_cli(int argc, char **argv);
void initialize_denylist();

// NetHunter Mode
void enable_nethunter_mode();
void disable_nethunter_mode();
void init_nethunter_mode();

// Module handling
void prepare_modules();
rust::Vec<ModuleInfo> collect_modules(bool zygisk_enabled, bool open_zygisk);
void load_modules(bool zygisk_enabled, const rust::Vec<ModuleInfo> &module_list);
void load_modules_su();
int get_manager_for_cxx(int user_id, rust::String &pkg, bool install);
// Rust FFI wrapper functions
bool is_deny_target_rs(int uid, rust::Str process, int max_len);
void zygisk_cleanup_with_jni_rs(uint8_t* env);
void register_plt_hook_rs(uint8_t* symbol, uint8_t** backup);
void register_jni_hook_rs(rust::Str clz, uint8_t* method);
void restore_jni_hooks_rs(uint8_t* env);
rust::Vec<rust::String> parse_mount_info_rs_wrapper(rust::Str pid);
bool setfilecon_rs(rust::Str path, rust::Str con);

// Original functions
rust::Vec<rust::String> parse_mount_info_rs(const rust::String &pid);
int setfilecon(const char *path, const char *con);
void zygisk_cleanup_with_jni(JNIEnv *env);
void register_plt_hook(void *symbol, void **backup);
void register_jni_hook(const std::string &clz, const JNINativeMethod &method);
void restore_jni_hooks(JNIEnv *env);
bool is_deny_target(int uid, std::string_view process, int max_len = 0);

// Additional missing functions
void scan_deny_apps();
void restorecon();
rust::String find_preinit_device();
void exec_root_shell(int client, int pid, SuRequest &req, MntNsMode mode);
void app_log(const SuAppRequest &info, SuPolicy policy, bool notify);
void app_notify(const SuAppRequest &info, SuPolicy policy);
int app_request(const SuAppRequest &info);

// Rust bindings
static inline rust::Utf8CStr get_magisk_tmp_rs() { return get_magisk_tmp(); }
static inline rust::String resolve_preinit_dir_rs(rust::Utf8CStr base_dir) {
    return resolve_preinit_dir(base_dir.c_str());
}

// Additional missing function declarations
bool zygisk_enabled();
void update_deny_flags(int uid, rust::Str process, uint32_t &flags);
void init_solist_hiding();
void init_seccomp_hiding();
void init_ptrace_hiding();
void solist_reset_counters(size_t load, size_t unload);
void restore_plt_hooks();
void reset_module_counters();
void send_seccomp_event();
bool trace_zygote(int pid);
void cleanup_ptrace();
bool is_ptrace_active();
void setup_logfile();
void android_logging();

// Persist functions
bool persist_set_prop(const char *name, const char *value);
void persist_get_props(prop_collector &collector);
bool persist_delete_prop(const char *name);
void persist_get_prop(const char *name, prop_cb &cb);

// Socket functions
void restore_stdin();
bool send_fd(int socket, int fd);
int recv_fd(int socket);
void pump_tty(int infd, int outfd);
int get_pty_num(int fd);

// Additional zygisk functions
rust::Vec<int> recv_fds(int socket);
void zygisk_logging();
bool zygisk_should_load_module(uint32_t flags);
void zygisk_close_logd();
int zygisk_get_logd();

// Additional missing function declarations for deny module
void revert_unmount(int pid = -1) noexcept;
void update_sulist_config(bool enable);
void hide_abnormal_environment(int pid);
void hide_modules_from_app(int pid, int uid);
void* proc_monitor(void *arg);

// Additional missing functions for ptrace and revert modules
void PTRACE_LOG(const char *fmt, ...);
void crawl_procfs(std::function<bool(int)> callback);
void revert_daemon(int pid, int signal);
void revert_daemon(int pid);
void mount_magisk_to_pid(int pid);
void bind_mount_(const char *source, const char *target);
int tmpfs_mount(const char *name, const char *target);
void enhance_magic_mount_hiding(int pid);
bool proc_context_match(int pid, const char *context);

// SuRequest helper functions
SuRequest create_su_request();
void su_request_write_to_fd(const SuRequest &req, int fd);
