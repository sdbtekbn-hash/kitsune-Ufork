#include <sys/mount.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <vector>
#include <string>
#include <map>

#include <consts.hpp>
#include <base.hpp>
#include <db.hpp>
#include <core.hpp>

using namespace std;

static map<int, vector<string>> hidden_modules;
static pthread_mutex_t modules_lock = PTHREAD_MUTEX_INITIALIZER;
static bool modules_hiding_enabled = false;

void load_modules_hiding_config() {
    mutex_guard lock(modules_lock);
    hidden_modules.clear();
    
    auto db = get_magisk_db();
    modules_hiding_enabled = db->get_db_setting(DbEntryKey::MODULES_HIDING_CONFIG) != 0;
    
    if (!modules_hiding_enabled)
        return;
    
    LOGI("modules-hide: Loading configuration\n");
}

bool should_hide_module(int uid, const char *module_name) {
    mutex_guard lock(modules_lock);
    
    int app_id = to_app_id(uid);
    if (auto it = hidden_modules.find(app_id); it != hidden_modules.end()) {
        for (const auto &mod : it->second) {
            if (mod == module_name) {
                return true;
            }
        }
    }
    return false;
}

void hide_modules_from_app(int pid, int uid) {
    if (!modules_hiding_enabled)
        return;
        
    if (switch_mnt_ns(pid))
        return;
    
    LOGD("modules-hide: hiding modules from PID=[%d] UID=[%d]\n", pid, uid);
    
    xmount(nullptr, "/", nullptr, MS_SLAVE | MS_REC, nullptr);
    
    const char *modules_dir = "/data/adb/modules";
    
    if (access(modules_dir, F_OK) != 0)
        return;
    
    mutex_guard lock(modules_lock);
    int app_id = to_app_id(uid);
    
    bool hide_all = hidden_modules.find(app_id) == hidden_modules.end() || 
                    hidden_modules[app_id].empty();
    
    if (hide_all) {
        umount2(modules_dir, MNT_DETACH);
        xmkdir(modules_dir, 0700);
        if (!xmount("tmpfs", modules_dir, "tmpfs", 0, "mode=000")) {
            LOGD("modules-hide: hid entire modules directory\n");
        }
        return;
    }
    
    auto dir = xopen_dir(modules_dir);
    if (!dir)
        return;
    
    dirent *entry;
    while ((entry = xreaddir(dir.get()))) {
        if (entry->d_name[0] == '.')
            continue;
        
        if (string(entry->d_name).ends_with(".disable"))
            continue;
        
        if (should_hide_module(uid, entry->d_name)) {
            char path[PATH_MAX];
            snprintf(path, sizeof(path), "%s/%s", modules_dir, entry->d_name);
            
            umount2(path, MNT_DETACH);
            rmdir(path);
            mkdir(path, 0000);
            
            LOGD("modules-hide: hid module: %s\n", entry->d_name);
        }
    }
}

void init_modules_hiding() {
    load_modules_hiding_config();
    
    if (modules_hiding_enabled) {
        LOGI("modules-hide: Module hiding is enabled\n");
    }
}

