#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <string>
#include <vector>

#include <base.hpp>
#include <core.hpp>
#include <db.hpp>
#include <selinux.hpp>

using namespace std;

static const char *NETHUNTER_PACKAGES[] = {
    "com.offsec.nhterm",
    "com.offsec.nethunter",
    "ru.meefik.linuxdeploy",
    nullptr
};

static const char *NETHUNTER_PATHS[] = {
    "/data/data/com.offsec.nhterm",
    "/data/data/com.offsec.nethunter",
    "/data/local/nhsystem",
    "/data/local/kali-armhf",
    nullptr
};

static void fix_directory_permissions(const char *path, mode_t mode) {
    struct stat st;
    if (stat(path, &st) != 0)
        return;
    
    chmod(path, mode);
    chown(path, 0, 0);
    
    auto dir = open_dir(path);
    if (!dir) return;
    
    for (dirent *entry; (entry = xreaddir(dir.get()));) {
        if (entry->d_name == "."sv || entry->d_name == ".."sv)
            continue;
        
        string subpath = string(path) + "/" + entry->d_name;
        
        if (entry->d_type == DT_DIR) {
            fix_directory_permissions(subpath.data(), 0755);
        } else if (entry->d_type == DT_REG) {
            chmod(subpath.data(), 0755);
            chown(subpath.data(), 0, 0);
        }
    }
}

static void fix_selinux_contexts(const char *path) {
    if (access(path, F_OK) != 0)
        return;
    
    setfilecon(path, "u:object_r:app_data_file:s0");
    
    string bin_path = string(path) + "/files/usr/bin";
    if (access(bin_path.data(), F_OK) == 0) {
        auto dir = open_dir(bin_path.data());
        if (dir) {
            for (dirent *entry; (entry = xreaddir(dir.get()));) {
                if (entry->d_name == "."sv || entry->d_name == ".."sv)
                    continue;
                
                string file_path = bin_path + "/" + entry->d_name;
                setfilecon(file_path.data(), "u:object_r:system_file:s0");
            }
        }
    }
}

static void fix_shell_scripts() {
    const char *kali_script = "/data/data/com.offsec.nhterm/files/usr/bin/kali";
    
    if (access(kali_script, F_OK) == 0) {
        string content;
        {
            auto fp = open_file(kali_script, "re");
            if (fp) {
                char buf[4096];
                while (fgets(buf, sizeof(buf), fp.get())) {
                    content += buf;
                }
            }
        }
        
        if (!content.empty() && !str_starts(content, "#!/system/bin/sh")) {
            size_t first_newline = content.find('\n');
            if (first_newline != string::npos) {
                content = "#!/system/bin/sh\n" + content.substr(first_newline + 1);
                
                auto fp = open_file(kali_script, "we");
                if (fp) {
                    fputs(content.data(), fp.get());
                }
            }
        }
        
        chmod(kali_script, 0755);
    }
}

void enable_nethunter_mode() {
    LOGI("* Enabling NetHunter Mode\n");
    
    for (int i = 0; NETHUNTER_PATHS[i]; i++) {
        const char *path = NETHUNTER_PATHS[i];
        LOGD("nethunter: fixing permissions for %s\n", path);
        fix_directory_permissions(path, 0755);
        fix_selinux_contexts(path);
    }
    
    fix_shell_scripts();
    
    for (int i = 0; NETHUNTER_PACKAGES[i]; i++) {
        const char *pkg = NETHUNTER_PACKAGES[i];
        LOGD("nethunter: adding %s to denylist\n", pkg);
        
        char sql[512];
        sprintf(sql, 
            "INSERT OR IGNORE INTO hidelist (package_name, process) VALUES('%s', '%s')",
            pkg, pkg);
        db_exec(sql);
    }
    
    LOGI("* NetHunter Mode enabled successfully\n");
}

void disable_nethunter_mode() {
    LOGI("* Disabling NetHunter Mode\n");
    
    for (int i = 0; NETHUNTER_PACKAGES[i]; i++) {
        const char *pkg = NETHUNTER_PACKAGES[i];
        
        char sql[256];
        sprintf(sql, "DELETE FROM hidelist WHERE package_name='%s'", pkg);
        db_exec(sql);
    }
    
    LOGI("* NetHunter Mode disabled\n");
}

void init_nethunter_mode() {
    db_settings dbs;
    get_db_settings(dbs, NETHUNTER_MODE_CONFIG);
    
    if (dbs[NETHUNTER_MODE_CONFIG]) {
        enable_nethunter_mode();
    }
}

