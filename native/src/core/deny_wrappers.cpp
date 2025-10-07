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

int get_manager(int user_id, string *pkg, bool install) {
    // Call Rust implementation through the daemon
    return MagiskD::get()->get_manager(user_id, install).first;
}

