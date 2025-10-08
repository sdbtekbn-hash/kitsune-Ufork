#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>

#include <base.hpp>
#include <core.hpp>

using namespace std;

static const char *SUSPICIOUS_ENV_VARS[] = {
    "MAGISK_VER",
    "MAGISK_VER_CODE",
    "MAGISKTMP",
    "ASH_STANDALONE",
    nullptr
};

void clean_environment_variables() {
    LOGD("env-clean: Cleaning suspicious environment variables\n");
    
    for (int i = 0; SUSPICIOUS_ENV_VARS[i]; i++) {
        if (getenv(SUSPICIOUS_ENV_VARS[i])) {
            unsetenv(SUSPICIOUS_ENV_VARS[i]);
            LOGD("env-clean: Removed %s\n", SUSPICIOUS_ENV_VARS[i]);
        }
    }
    
    // Clean LD_PRELOAD if it contains magisk
    const char *ld_preload = getenv("LD_PRELOAD");
    if (ld_preload && strstr(ld_preload, "magisk")) {
        unsetenv("LD_PRELOAD");
        LOGD("env-clean: Removed LD_PRELOAD (contained magisk)\n");
    }
    
    const char *ld_lib_path = getenv("LD_LIBRARY_PATH");
    if (ld_lib_path && (strstr(ld_lib_path, "magisk") || strstr(ld_lib_path, "/sbin"))) {
        unsetenv("LD_LIBRARY_PATH");
        LOGD("env-clean: Removed LD_LIBRARY_PATH (suspicious path)\n");
    }
}

void spoof_selinux_context(int pid) {
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "/proc/%d/attr/current", pid);
    
    int fd = open(path, O_WRONLY);
    if (fd < 0) {
        LOGD("env-clean: Failed to open SELinux context file\n");
        return;
    }
    
    const char *normal_context = "u:r:untrusted_app:s0:c512,c768";
    ssize_t written = write(fd, normal_context, strlen(normal_context));
    close(fd);
    
    if (written > 0) {
        LOGD("env-clean: Spoofed SELinux context to: %s\n", normal_context);
    }
}

void clean_proc_cmdline(int pid) {
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
    
    int fd = open(path, O_RDONLY);
    if (fd < 0)
        return;
    
    char cmdline[1024];
    ssize_t len = read(fd, cmdline, sizeof(cmdline) - 1);
    close(fd);
    
    if (len <= 0)
        return;
    
    cmdline[len] = '\0';
    
    if (strstr(cmdline, "magisk") || strstr(cmdline, "zygisk")) {
        LOGD("env-clean: Suspicious cmdline detected, but cannot modify (read-only)\n");
    }
}

void hide_abnormal_environment(int pid) {
    LOGD("env-clean: Hiding abnormal environment for PID=[%d]\n", pid);
    
    clean_environment_variables();
    spoof_selinux_context(pid);
    clean_proc_cmdline(pid);
    
    LOGD("env-clean: Environment cleaning completed\n");
}

