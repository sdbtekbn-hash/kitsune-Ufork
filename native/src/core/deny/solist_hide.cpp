#include <dlfcn.h>
#include <link.h>
#include <sys/mman.h>
#include <unistd.h>
#include <vector>
#include <string>
#include <map>

#include <base.hpp>
#include <core.hpp>

using namespace std;

typedef void SoInfo;

struct SolistInfo {
    SoInfo *somain;
    size_t solist_size_offset;
    size_t solist_realpath_offset;
    size_t solist_fini_array_size_offset;
    size_t solist_fini_offset;
    void *get_realpath_sym;
    void *soinfo_unload;
    void *find_containing_library;
    struct link_map *r_debug_tail;
    size_t *g_module_load_counter;
    size_t *g_module_unload_counter;
};

static SolistInfo solist_info = {0};

static inline const char *get_path(SoInfo *self) {
    if (solist_info.get_realpath_sym) {
        return ((const char *(*)(SoInfo *))solist_info.get_realpath_sym)(self);
    }
    return ((const char *)((uintptr_t)self + solist_info.solist_realpath_offset));
}

static inline void set_size(SoInfo *self, size_t size) {
    *(size_t *) ((uintptr_t)self + solist_info.solist_size_offset) = size;
}

bool solist_init() {
    const char *linker_path = lp_select("/system/bin/linker", "/system/bin/linker64");
    
    void *linker_handle = dlopen(linker_path, RTLD_NOW);
    if (!linker_handle) {
        LOGW("solist-hide: Failed to dlopen linker: %s", dlerror());
        return false;
    }

    solist_info.somain = (SoInfo *)dlsym(linker_handle, "__dl__ZL6somain");
    if (!solist_info.somain) {
        LOGW("solist-hide: Failed to find somain symbol");
        dlclose(linker_handle);
        return false;
    }

    solist_info.get_realpath_sym = dlsym(linker_handle, "__dl__ZNK6soinfo12get_realpathEv");
    solist_info.soinfo_unload = dlsym(linker_handle, "__dl__ZL13soinfo_unloadP6soinfo");
    solist_info.find_containing_library = dlsym(linker_handle, "__dl__Z23find_containing_libraryPKv");
    solist_info.r_debug_tail = (struct link_map *)dlsym(linker_handle, "__dl__ZL12r_debug_tail");
    solist_info.g_module_load_counter = (size_t *)dlsym(linker_handle, "__dl__ZL21g_module_load_counter");
    solist_info.g_module_unload_counter = (size_t *)dlsym(linker_handle, "__dl__ZL23g_module_unload_counter");

    dlclose(linker_handle);

    if (!solist_info.find_containing_library) {
        LOGW("solist-hide: Failed to find find_containing_library");
        return false;
    }

    solist_info.solist_size_offset = lp_select(0x90, 0x18);
    solist_info.solist_realpath_offset = lp_select(0x174, 0x1a8);
    solist_info.solist_fini_array_size_offset = lp_select(0x0, 0x0);
    solist_info.solist_fini_offset = lp_select(0x0, 0x0);

    LOGD("solist-hide: Initialized solist hiding");
    return true;
}

bool solist_drop_so_path(void *lib_memory, bool unload) {
    if (!solist_info.find_containing_library && !solist_init()) {
        LOGW("solist-hide: Failed to initialize solist");
        return false;
    }

    SoInfo *found = ((SoInfo *(*)(const void *))solist_info.find_containing_library)(lib_memory);
    if (!found) {
        LOGD("solist-hide: Could not find containing library for %p", lib_memory);
        return false;
    }

    const char *path = get_path(found);
    if (!path) {
        LOGW("solist-hide: Failed to get path for %p", found);
        return false;
    }

    LOGD("solist-hide: Found so path for %p: %s", lib_memory, path);

    set_size(found, 0);
    LOGD("solist-hide: Set size of %p to 0", (void *)found);

    if (unload) {
        if (dlclose((void *)found) == -1) {
            LOGW("solist-hide: Failed to dlclose so path for %s: %s", path, dlerror());
            return false;
        }
    } else {
        if (solist_info.soinfo_unload) {
            ((void (*)(SoInfo *))solist_info.soinfo_unload)(found);
        }
    }

    LOGD("solist-hide: Successfully hidden soinfo traces for %s", path);
    return true;
}

void solist_reset_counters(size_t load, size_t unload) {
    if (!solist_info.g_module_load_counter || !solist_info.g_module_unload_counter) {
        LOGD("solist-hide: Module counters not available, skipping reset");
        return;
    }

    size_t loaded_modules = *solist_info.g_module_load_counter;
    size_t unloaded_modules = *solist_info.g_module_unload_counter;

    if (loaded_modules >= load) {
        *solist_info.g_module_load_counter -= load;
        LOGD("solist-hide: Reset g_module_load_counter to %zu", *solist_info.g_module_load_counter);
    }

    if (unloaded_modules >= unload) {
        *solist_info.g_module_unload_counter -= unload;
        LOGD("solist-hide: Reset g_module_unload_counter to %zu", *solist_info.g_module_unload_counter);
    }
}

void init_solist_hiding() {
    if (solist_init()) {
        LOGD("solist-hide: SOList hiding initialized successfully");
    } else {
        LOGW("solist-hide: Failed to initialize SOList hiding");
    }
}
