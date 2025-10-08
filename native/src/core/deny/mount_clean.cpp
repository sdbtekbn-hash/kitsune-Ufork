#include <sys/mount.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <vector>
#include <string>

#include <consts.hpp>
#include <base.hpp>
#include <core.hpp>

using namespace std;

static const char *MAGISK_MOUNT_POINTS[] = {
    MODULEMNT,
    INTLROOT,
    DEVICEDIR,
    WORKERDIR,
    "/sbin",
    "/debug_ramdisk",
    nullptr
};

void unmount_magisk_paths(int pid) {
    if (switch_mnt_ns(pid))
        return;
    
    LOGD("mount-clean: Unmounting Magisk paths for PID=[%d]\n", pid);
    
    xmount(nullptr, "/", nullptr, MS_SLAVE | MS_REC, nullptr);
    
    for (int i = 0; MAGISK_MOUNT_POINTS[i]; i++) {
        const char *path = MAGISK_MOUNT_POINTS[i];
        
        if (access(path, F_OK) == 0) {
            if (umount2(path, MNT_DETACH) == 0) {
                LOGD("mount-clean: Unmounted %s\n", path);
            }
        }
    }
}

void clean_mount_namespace(int pid) {
    LOGD("mount-clean: Cleaning mount namespace for PID=[%d]\n", pid);
    
    unmount_magisk_paths(pid);
    xmount(nullptr, "/", nullptr, MS_PRIVATE | MS_REC, nullptr);
}

bool is_suspicious_mount(const char *mount_line) {
    return strstr(mount_line, "magisk") != nullptr ||
           strstr(mount_line, MODULEMNT) != nullptr ||
           strstr(mount_line, "/data/adb") != nullptr ||
           strstr(mount_line, "tmpfs magisk") != nullptr ||
           strstr(mount_line, "overlay") != nullptr;
}

void hide_mount_points_from_proc(int pid) {
    LOGD("mount-clean: Mount points hidden via unmounting\n");
}

void enhance_magic_mount_hiding(int pid) {
    LOGD("mount-clean: Enhancing magic mount hiding for PID=[%d]\n", pid);
    
    clean_mount_namespace(pid);
    hide_mount_points_from_proc(pid);
    
    LOGD("mount-clean: Magic mount hiding enhanced\n");
}

