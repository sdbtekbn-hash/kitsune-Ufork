#include <sys/ptrace.h>
#include <sys/wait.h>
#include <signal.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#include <base.hpp>
#include <core.hpp>

using namespace std;

static bool ptrace_attached = false;
static int target_pid = -1;

bool trace_zygote(int pid) {
    LOGI("ptrace-hide: Starting trace for zygote PID=%d (tracer PID=%d)", pid, getpid());
    
    target_pid = pid;
    
    if (ptrace(PTRACE_SEIZE, pid, 0, PTRACE_O_EXITKILL | PTRACE_O_TRACESECCOMP) == -1) {
        PLOGE("ptrace-hide: Failed to seize process");
        return false;
    }
    
    int status;
    if (waitpid(pid, &status, __WALL) == -1) {
        PLOGE("ptrace-hide: Failed to wait for initial stop");
        return false;
    }
    
    if (WIFSTOPPED(status) && WSTOPSIG(status) == SIGSTOP) {
        LOGD("ptrace-hide: Process stopped with SIGSTOP, continuing");
        ptrace_attached = true;
        
        if (kill(pid, SIGCONT) == -1) {
            PLOGE("ptrace-hide: Failed to send SIGCONT");
            return false;
        }
        
        if (waitpid(pid, &status, __WALL) == -1) {
            PLOGE("ptrace-hide: Failed to wait after SIGCONT");
            return false;
        }
        
        if (WIFSTOPPED(status) && WSTOPSIG(status) == SIGTRAP) {
            LOGD("ptrace-hide: Received SIGTRAP, detaching");
            
            if (ptrace(PTRACE_DETACH, pid, 0, SIGCONT) == -1) {
                PLOGE("ptrace-hide: Failed to detach");
                return false;
            }
            
            ptrace_attached = false;
            return true;
        }
    }
    
    LOGW("ptrace-hide: Unexpected status: %d", status);
    ptrace(PTRACE_DETACH, pid, 0, 0);
    return false;
}

void cleanup_ptrace() {
    if (ptrace_attached && target_pid > 0) {
        LOGD("ptrace-hide: Cleaning up ptrace for PID=%d", target_pid);
        ptrace(PTRACE_DETACH, target_pid, 0, 0);
        ptrace_attached = false;
        target_pid = -1;
    }
}

bool is_ptrace_active() {
    return ptrace_attached;
}

void init_ptrace_hiding() {
    LOGD("ptrace-hide: Ptrace hiding initialized");
}
