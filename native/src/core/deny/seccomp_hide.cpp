#include <sys/ptrace.h>
#include <sys/wait.h>
#include <sys/syscall.h>
#include <linux/seccomp.h>
#include <linux/filter.h>
#include <linux/audit.h>
#include <sys/prctl.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdbool.h>
#include <string.h>
#include <stdlib.h>

#include <base.hpp>
#include <core.hpp>

using namespace std;

static bool seccomp_filters_visible() {
    FILE *status_file = fopen("/proc/self/status", "r");
    if (!status_file) {
        return true;
    }

    const char *needle = "Seccomp_filters:";
    char line[256];

    while (fgets(line, sizeof(line), status_file)) {
        if (strncmp(line, needle, strlen(needle)) == 0) {
            fclose(status_file);
            return true;
        }
    }

    fclose(status_file);
    return false;
}

void send_seccomp_event() {
    if (seccomp_filters_visible()) {
        LOGD("seccomp-hide: Seccomp filters are visible, skipping hiding via seccomp event");
        return;
    }

    __u32 args[4] = {0};

    int rnd_fd = open("/dev/urandom", O_RDONLY);
    if (rnd_fd == -1) {
        PLOGE("send_seccomp_event: open(/dev/urandom)");
        return;
    }

    if (read(rnd_fd, &args, sizeof(args)) != sizeof(args)) {
        PLOGE("send_seccomp_event: read(rnd_fd)");
        close(rnd_fd);
        return;
    }

    close(rnd_fd);

    args[0] |= 0x10000;

    struct sock_filter filter[] = {
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_exit_group, 0, 9),

            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[0])),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, args[0], 0, 7),

            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[1])),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, args[1], 0, 5),

            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[2])),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, args[2], 0, 3),

            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[3])),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, args[3], 0, 1),

            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRACE),

            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
    };

    struct sock_fprog prog = {
            .len = (unsigned short)(sizeof(filter)/sizeof(filter[0])),
            .filter = filter,
    };

    if (prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &prog)) {
        PLOGE("send_seccomp_event: prctl(SECCOMP)");
        return;
    }

    syscall(__NR_exit_group, args[0], args[1], args[2], args[3]);
}

void init_seccomp_hiding() {
    struct kernel_version version = parse_kversion();
    if (version.major > 3 || (version.major == 3 && version.minor >= 8)) {
        LOGD("seccomp-hide: Supported kernel version %d.%d.%d, enabling seccomp hiding", 
             version.major, version.minor, version.patch);
        send_seccomp_event();
    } else {
        LOGD("seccomp-hide: Kernel version %d.%d.%d not supported for seccomp hiding", 
             version.major, version.minor, version.patch);
    }
}
