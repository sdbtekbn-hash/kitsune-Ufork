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

#include <base.hpp>

#include "../core-rs.hpp"

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
rust::Vec<rust::String> parse_mount_info_rs(const rust::String &pid);

// Modules hiding
void load_modules_hiding_config();
void hide_modules_from_app(int pid, int uid);
void init_modules_hiding();
bool should_hide_module(int uid, const char *module_name);

// Environment cleaning
void clean_environment_variables();
void spoof_selinux_context(int pid);
void hide_abnormal_environment(int pid);

// Mount cleaning
void unmount_magisk_paths(int pid);
void clean_mount_namespace(int pid);
void enhance_magic_mount_hiding(int pid);

// Zygisk hiding
void clean_zygisk_memory_traces();
void unload_zygisk_libraries();
void hide_zygisk_injection();
void zygisk_cleanup_post_specialize();
void zygisk_cleanup_with_jni(JNIEnv *env);
void register_plt_hook(void *symbol, void **backup);
void register_jni_hook(const string &clz, const JNINativeMethod &method);
void restore_plt_hooks();
void restore_jni_hooks(JNIEnv *env);
void reset_module_counters();

// Seccomp hiding
void send_seccomp_event();
void init_seccomp_hiding();

// SOList hiding
bool solist_init();
bool solist_drop_so_path(void *lib_memory, bool unload);
void solist_reset_counters(size_t load, size_t unload);
void init_solist_hiding();

// Ptrace hiding
bool trace_zygote(int pid);
void cleanup_ptrace();
bool is_ptrace_active();
void init_ptrace_hiding();
void scan_deny_apps();
bool is_deny_target(int uid, std::string_view process, int max_len = 0);
void crawl_procfs(const std::function<bool(int)> &fn);
void bind_mount_(const char *from, const char *to);
int tmpfs_mount(const char *from, const char *to);
std::vector<mount_info> parse_mount_info(const char *pid);
bool proc_context_match(int pid, std::string_view context);
void revert_unmount(int pid = -1) noexcept;
void update_deny_flags(int uid, rust::Str process, uint32_t &flags);
void update_sulist_config(bool enable);
void mount_magisk_to_pid(int pid);
void revert_daemon(int pid, int client = -1);
bool is_uid_on_list(int uid);
void proc_monitor();
void umount_all_zygote();

// MagiskSU
void exec_root_shell(int client, int pid, SuRequest &req, MntNsMode mode);
void app_log(const SuAppRequest &info, SuPolicy policy, bool notify);
void app_notify(const SuAppRequest &info, SuPolicy policy);
int app_request(const SuAppRequest &info);

// Rust bindings
static inline rust::Utf8CStr get_magisk_tmp_rs() { return get_magisk_tmp(); }
static inline rust::String resolve_preinit_dir_rs(rust::Utf8CStr base_dir) {
    return resolve_preinit_dir(base_dir.c_str());
}
