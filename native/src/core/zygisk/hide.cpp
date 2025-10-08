#include <dlfcn.h>
#include <sys/mman.h>
#include <unistd.h>
#include <vector>
#include <string>
#include <regex.h>
#include <link.h>
#include <jni.h>

#include <base.hpp>
#include <core.hpp>

using namespace std;

struct MemoryRegion {
    void *start;
    size_t size;
    string name;
};

static vector<MemoryRegion> zygisk_regions;
static vector<pair<void*, void**>> plt_hooks;
static map<string, vector<JNINativeMethod>> jni_hooks;

void track_zygisk_memory(void *addr, size_t size, const char *name) {
    zygisk_regions.push_back({addr, size, name ? name : "unknown"});
}

void register_plt_hook(void *symbol, void **backup) {
    plt_hooks.emplace_back(symbol, backup);
}

void register_jni_hook(const string &clz, const JNINativeMethod &method) {
    jni_hooks[clz].push_back(method);
}

void clean_zygisk_memory_traces() {
    LOGD("zygisk-hide: Cleaning %zu memory regions\n", zygisk_regions.size());
    
    auto fp = fopen("/proc/self/maps", "r");
    if (!fp) {
        LOGW("zygisk-hide: Failed to open /proc/self/maps\n");
        return;
    }
    
    vector<pair<void*, size_t>> suspicious_regions;
    char line[1024];
    
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, "zygisk") || 
            strstr(line, "/jit-cache") ||
            strstr(line, "magisk")) {
            
            unsigned long start, end;
            char perms[5];
            
            if (sscanf(line, "%lx-%lx %4s", &start, &end, perms) == 3) {
                if (strchr(perms, 'r')) {
                    suspicious_regions.emplace_back((void*)start, end - start);
                }
            }
        }
    }
    fclose(fp);
    
    for (const auto &[addr, size] : suspicious_regions) {
        if (munmap(addr, size) == 0) {
            LOGD("zygisk-hide: Unmapped region at %p (size: %zu)\n", addr, size);
        }
    }
    
    zygisk_regions.clear();
    LOGD("zygisk-hide: Memory cleaning completed\n");
}

// These functions are kept for potential future use but not called to avoid conflicts
// with the original Magisk cleanup sequence

void unload_zygisk_libraries() {
    // Unused - original pthread_attr_destroy handles dlclose(self_handle)
    LOGD("zygisk-hide: unload_zygisk_libraries() - unused to avoid conflicts\n");
}

void restore_plt_hooks() {
    // Unused - original pthread_attr_destroy handles restore_plt_hook()
    LOGD("zygisk-hide: restore_plt_hooks() - unused to avoid conflicts\n");
}

void restore_jni_hooks(JNIEnv *env) {
    LOGD("zygisk-hide: Restoring %zu JNI hook classes\n", jni_hooks.size());
    
    for (const auto &[clz, methods] : jni_hooks) {
        jclass jc = env->FindClass(clz.c_str());
        if (jc) {
            if (!methods.empty() && env->RegisterNatives(jc, methods.data(),
                    static_cast<jint>(methods.size())) != 0) {
                LOGW("zygisk-hide: Failed to restore JNI hooks for %s\n", clz.c_str());
            }
            env->DeleteLocalRef(jc);
        }
    }
    
    jni_hooks.clear();
}

void reset_module_counters() {
    // Unused - original run_modules_post() handles solist_reset_counters()
    LOGD("zygisk-hide: reset_module_counters() - unused to avoid conflicts\n");
}

void hide_zygisk_injection() {
    LOGD("zygisk-hide: Starting comprehensive injection hiding\n");
    
    // Skip unload_zygisk_libraries() to avoid double unmapping
    // Skip reset_module_counters() to avoid double reset
    // The original pthread_attr_destroy will handle dlclose(self_handle)
    // The original run_modules_post() will handle solist_reset_counters()
    
    clean_zygisk_memory_traces();
    dlerror();
    
    LOGD("zygisk-hide: Comprehensive injection hiding completed\n");
}

void zygisk_cleanup_post_specialize() {
    // This will be called from pthread_attr_destroy in hook.cpp
    // after the original restore_plt_hook() is called
    hide_zygisk_injection();
}

void zygisk_cleanup_with_jni(JNIEnv *env) {
    restore_jni_hooks(env);
    // Skip restore_plt_hooks() to avoid double restoration
    // The original pthread_attr_destroy will handle restore_plt_hook()
    hide_zygisk_injection();
}

