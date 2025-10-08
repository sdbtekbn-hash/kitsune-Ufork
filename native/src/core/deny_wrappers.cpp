#include <sys/mount.h>
#include <string>
#include <vector>
#include <core.hpp>
#include <base.hpp>
#include <consts.hpp>
#include <selinux.hpp>

using namespace std;

// Wrapper functions for deny system

int su_bin_fd = -1;

void bind_mount_(const char *from, const char *to) {
    xmount(from, to, nullptr, MS_BIND | MS_REC, nullptr);
}

int tmpfs_mount(const char *from, const char *to) {
    return xmount(from, to, "tmpfs", 0, nullptr);
}

void su_mount() {
    // Call Rust implementation through FFI
    load_modules_su();
}

int get_manager(int user_id, string *pkg, bool install) {
    if (!pkg) return -1;
    
    // Call Rust implementation through FFI
    rust::String rust_pkg;
    int uid = rust::get_manager_for_cxx(user_id, rust_pkg, install);
    
    if (uid >= 0) {
        *pkg = std::string(rust_pkg);
    }
    
    return uid;
}

// Wrapper for parse_mount_info - calls Rust implementation
vector<string> parse_mount_info_wrapper(const char *pid) {
    if (!pid) return vector<string>();
    
    // Call Rust implementation through FFI
    string pid_str(pid);
    rust::Vec<rust::String> rust_result = rust::parse_mount_info_rs(pid_str);
    
    // Convert rust::Vec<rust::String> to vector<string>
    vector<string> result;
    for (const auto& item : rust_result) {
        result.push_back(string(item));
    }
    
    return result;
}

// SELinux wrappers - these are defined in Rust via FFI
// We need to declare them as extern "C" to avoid name mangling issues
extern "C" {
    bool setfilecon_impl(const char *path, const char *con);
    bool lsetfilecon_impl(const char *path, const char *con);
    bool fsetfilecon_impl(int fd, const char *con);
    bool selinux_enabled_impl();
}

// C++ wrappers that call Rust implementations
int setfilecon_wrapper(const char *path, const char *con) {
    // Rust returns bool, C++ expects int (0 = success, -1 = error)
    return setfilecon_impl(path, con) ? 0 : -1;
}

int lsetfilecon(const char *path, const char *con) {
    return lsetfilecon_impl(path, con) ? 0 : -1;
}

int fsetfilecon(int fd, const char *con) {
    return fsetfilecon_impl(fd, con) ? 0 : -1;
}

bool selinux_enabled() {
    return selinux_enabled_impl();
}

