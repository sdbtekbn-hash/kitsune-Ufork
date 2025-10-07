#include <sys/mount.h>
#include <core.hpp>
#include <base.hpp>
#include <consts.hpp>

using namespace std;

// Wrapper functions for deny system

void bind_mount_(const char *from, const char *to) {
    xmount(from, to, nullptr, MS_BIND | MS_REC, nullptr);
}

int tmpfs_mount(const char *from, const char *to) {
    return xmount(from, to, "tmpfs", 0, nullptr);
}

void su_mount() {
    // Placeholder - calls Rust implementation through FFI
}

// Forward declaration from Rust
extern "C" int get_manager_uid_impl(int user_id);

int get_manager(int user_id, string *pkg, bool install) {
    // Simple implementation - just return -1 for now
    // This will be properly implemented when we integrate with Rust
    return -1;
}

