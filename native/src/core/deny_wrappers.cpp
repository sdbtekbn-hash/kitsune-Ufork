#include <sys/mount.h>
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
    // Placeholder - calls Rust implementation through FFI
}

int get_manager(int user_id, string *pkg, bool install) {
    // Simple implementation - just return -1 for now
    return -1;
}

// Wrapper for parse_mount_info - calls Rust implementation
vector<mount_info> parse_mount_info(const char *pid) {
    // Return empty vector for now
    // The Rust implementation exists but we need FFI bridge
    return vector<mount_info>();
}

// SELinux wrappers
int setfilecon(const char *path, const char *con) {
    return lsetfilecon(path, con);
}

bool selinux_enabled() {
    return getfilecon != nullptr;
}

