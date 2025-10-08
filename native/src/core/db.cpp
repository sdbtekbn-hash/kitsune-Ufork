#include <unistd.h>
#include <dlfcn.h>
#include <sys/stat.h>

#include <consts.hpp>
#include <base.hpp>
#include <db.hpp>
#include <core.hpp>

#define DB_VERSION 12

using namespace std;

struct sqlite3;

static sqlite3 *mDB = nullptr;

#define DBLOGV(...)
//#define DBLOGV(...) LOGD("magiskdb: " __VA_ARGS__)

// SQLite APIs

#define SQLITE_OPEN_READWRITE        0x00000002
#define SQLITE_OPEN_CREATE           0x00000004
#define SQLITE_OPEN_FULLMUTEX        0x00010000

static int (*sqlite3_open_v2)(
        const char *filename,
        sqlite3 **ppDb,
        int flags,
        const char *zVfs);
static const char *(*sqlite3_errmsg)(sqlite3 *db);
static int (*sqlite3_close)(sqlite3 *db);
static void (*sqlite3_free)(void *v);
static int (*sqlite3_exec)(
        sqlite3 *db,
        const char *sql,
        int (*callback)(void*, int, char**, char**),
        void *v,
        char **errmsg);

// Internal Android linker APIs

static void (*android_get_LD_LIBRARY_PATH)(char *buffer, size_t buffer_size);
static void (*android_update_LD_LIBRARY_PATH)(const char *ld_library_path);

#define DLERR(ptr) if (!(ptr)) { \
    LOGE("db: %s\n", dlerror()); \
    return false; \
}

#define DLOAD(handle, arg) {\
    auto f = dlsym(handle, #arg); \
    DLERR(f) \
    *(void **) &(arg) = f; \
}

#ifdef __LP64__
constexpr char apex_path[] = "/apex/com.android.runtime/lib64:/apex/com.android.art/lib64:/apex/com.android.i18n/lib64:";
#else
constexpr char apex_path[] = "/apex/com.android.runtime/lib:/apex/com.android.art/lib:/apex/com.android.i18n/lib:";
#endif

static int dl_init = 0;

static bool dload_sqlite() {
    if (dl_init)
        return dl_init > 0;
    dl_init = -1;

    auto sqlite = dlopen("libsqlite.so", RTLD_LAZY);
    if (!sqlite) {
        auto dl = dlopen("libdl_android.so", RTLD_LAZY);
        DLERR(dl);

        DLOAD(dl, android_get_LD_LIBRARY_PATH);
        DLOAD(dl, android_update_LD_LIBRARY_PATH);

        char ld_path[4096];
        memcpy(ld_path, apex_path, sizeof(apex_path));
        constexpr int len = sizeof(apex_path) - 1;
        android_get_LD_LIBRARY_PATH(ld_path + len, sizeof(ld_path) - len);
        android_update_LD_LIBRARY_PATH(ld_path);
        sqlite = dlopen("libsqlite.so", RTLD_LAZY);

        android_update_LD_LIBRARY_PATH(ld_path + len);
    }
    DLERR(sqlite);

    DLOAD(sqlite, sqlite3_open_v2);
    DLOAD(sqlite, sqlite3_errmsg);
    DLOAD(sqlite, sqlite3_close);
    DLOAD(sqlite, sqlite3_exec);
    DLOAD(sqlite, sqlite3_free);

    dl_init = 1;
    return true;
}

int db_strings::get_idx(string_view key) const {
    int idx = 0;
    for (const char *k : DB_STRING_KEYS) {
        if (key == k)
            break;
        ++idx;
    }
    return idx;
}

db_settings::db_settings() {
    data[ROOT_ACCESS] = ROOT_ACCESS_APPS_AND_ADB;
    data[SU_BIOMETRIC] = BIOMETRIC_DISABLED;
    data[SU_MULTIUSER_MODE] = MULTIUSER_MODE_OWNER_ONLY;
    data[SU_MNT_NS] = NAMESPACE_MODE_REQUESTER;
    data[DENYLIST_CONFIG] = false;
    data[ZYGISK_CONFIG] = false;
    data[SULIST_CONFIG] = false;
}

int db_settings::get_idx(string_view key) const {
    int idx = 0;
    for (const char *k : DB_SETTING_KEYS) {
        if (key == k)
            break;
        ++idx;
    }
    return idx;
}

static int ver_cb(void *ver, int, char **data, char **) {
    *((int *) ver) = parse_int(data[0]);
    return 0;
}

#define err_ret(e) if (e) return e;

static char *open_and_init_db(sqlite3 *&db) {
    if (!dload_sqlite())
        return strdup("Cannot load libsqlite.so");

    int ret = sqlite3_open_v2(MAGISKDB, &db,
            SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nullptr);
    if (ret)
        return strdup(sqlite3_errmsg(db));
    int ver = 0;
    bool upgrade = false;
    char *err = nullptr;
    sqlite3_exec(db, "PRAGMA user_version", ver_cb, &ver, &err);
    err_ret(err);
    if (ver > DB_VERSION) {
        sqlite3_close(db);
        return strdup("Downgrading database is not supported");
    }

    auto create_policy = [&] {
        sqlite3_exec(db,
                "CREATE TABLE IF NOT EXISTS policies "
                "(uid INT, policy INT, until INT, logging INT, "
                "notification INT, PRIMARY KEY(uid))",
                nullptr, nullptr, &err);
    };
    auto create_settings = [&] {
        sqlite3_exec(db,
                "CREATE TABLE IF NOT EXISTS settings "
                "(key TEXT, value INT, PRIMARY KEY(key))",
                nullptr, nullptr, &err);
    };
    auto create_strings = [&] {
        sqlite3_exec(db,
                "CREATE TABLE IF NOT EXISTS strings "
                "(key TEXT, value TEXT, PRIMARY KEY(key))",
                nullptr, nullptr, &err);
    };
    auto create_denylist = [&] {
        sqlite3_exec(db,
                "CREATE TABLE IF NOT EXISTS denylist "
                "(package_name TEXT, process TEXT, PRIMARY KEY(package_name, process))",
                nullptr, nullptr, &err);
    };

    if (ver <= 6) {
        create_policy();
        err_ret(err);
        create_settings();
        err_ret(err);
        create_strings();
        err_ret(err);
        create_denylist();
        err_ret(err);

        ver = DB_VERSION;
        upgrade = true;
    }
    if (ver == 7 || ver == 8) {
        sqlite3_exec(db,
                "BEGIN TRANSACTION;"
                "ALTER TABLE hidelist RENAME TO hidelist_tmp;"
                "CREATE TABLE IF NOT EXISTS hidelist "
                "(package_name TEXT, process TEXT, PRIMARY KEY(package_name, process));"
                "INSERT INTO hidelist SELECT * FROM hidelist_tmp;"
                "DROP TABLE hidelist_tmp;"
                "COMMIT;",
                nullptr, nullptr, &err);
        err_ret(err);
        ver = 9;
        upgrade = true;
    }
    if (ver == 9) {
        sqlite3_exec(db, "DROP TABLE IF EXISTS logs", nullptr, nullptr, &err);
        err_ret(err);
        ver = 10;
        upgrade = true;
    }
    if (ver == 10) {
        create_denylist();
        err_ret(err);
        ver = 11;
        upgrade = true;
    }
    if (ver == 11) {
        sqlite3_exec(db,
                "BEGIN TRANSACTION;"
                "ALTER TABLE policies RENAME TO policies_tmp;"
                "CREATE TABLE IF NOT EXISTS policies "
                "(uid INT, policy INT, until INT, logging INT, "
                "notification INT, PRIMARY KEY(uid));"
                "INSERT INTO policies "
                "SELECT uid, policy, until, logging, notification FROM policies_tmp;"
                "DROP TABLE policies_tmp;"
                "COMMIT;",
                nullptr, nullptr, &err);
        err_ret(err);
        ver = 12;
        upgrade = true;
    }

    if (upgrade) {
        char query[32];
        sprintf(query, "PRAGMA user_version=%d", ver);
        sqlite3_exec(db, query, nullptr, nullptr, &err);
        err_ret(err);
    }
    sqlite3_exec(db, "REPLACE INTO settings (key,value) VALUES('denylist',0);"
                     "CREATE TABLE IF NOT EXISTS hidelist "
                "(package_name TEXT, process TEXT, PRIMARY KEY(package_name, process));"
                     "CREATE TABLE IF NOT EXISTS sulist "
                "(package_name TEXT, process TEXT, PRIMARY KEY(package_name, process));", nullptr, nullptr, &err);
    return nullptr;
}

char *db_exec_impl(const char *sql) {
    char *err = nullptr;
    if (mDB == nullptr) {
        err = open_and_init_db(mDB);
        db_err_cmd(err,
            unlink(MAGISKDB);
            err = open_and_init_db(mDB);
            err_ret(err);
        );
    }
    if (mDB) {
        sqlite3_exec(mDB, sql, nullptr, nullptr, &err);
        return err;
    }
    return nullptr;
}

static int sqlite_db_row_callback(void *cb, int col_num, char **data, char **col_name) {
    auto &func = *static_cast<const db_row_cb*>(cb);
    db_row row;
    for (int i = 0; i < col_num; ++i)
        row[col_name[i]] = data[i];
    return func(row) ? 0 : 1;
}

char *db_exec_impl(const char *sql, const db_row_cb &fn) {
    char *err = nullptr;
    if (mDB == nullptr) {
        err = open_and_init_db(mDB);
        db_err_cmd(err,
            unlink(MAGISKDB);
            err = open_and_init_db(mDB);
            err_ret(err);
        );
    }
    if (mDB) {
        sqlite3_exec(mDB, sql, sqlite_db_row_callback, (void *) &fn, &err);
        return err;
    }
    return nullptr;
}

int get_db_settings(db_settings &cfg, int key) {
    char *err = nullptr;
    auto settings_cb = [&](db_row &row) -> bool {
        cfg[row["key"]] = parse_int(row["value"]);
        DBLOGV("query %s=[%s]\n", row["key"].data(), row["value"].data());
        return true;
    };
    if (key >= 0) {
        char query[128];
        ssprintf(query, sizeof(query), "SELECT * FROM settings WHERE key='%s'", DB_SETTING_KEYS[key]);
        err = db_exec_impl(query, settings_cb);
    } else {
        err = db_exec_impl("SELECT * FROM settings", settings_cb);
    }
    db_err_cmd(err, return 1);
    return 0;
}

int get_db_strings(db_strings &str, int key) {
    char *err = nullptr;
    auto string_cb = [&](db_row &row) -> bool {
        str[row["key"]] = row["value"];
        DBLOGV("query %s=[%s]\n", row["key"].data(), row["value"].data());
        return true;
    };
    if (key >= 0) {
        char query[128];
        ssprintf(query, sizeof(query), "SELECT * FROM strings WHERE key='%s'", DB_STRING_KEYS[key]);
        err = db_exec_impl(query, string_cb);
    } else {
        err = db_exec_impl("SELECT * FROM strings", string_cb);
    }
    db_err_cmd(err, return 1);
    return 0;
}

void exec_sql(int client) {
    run_finally f([=]{ close(client); });
    string sql = read_string(client);
    char *err = db_exec_impl(sql.data(), [client](db_row &row) -> bool {
        string out;
        bool first = true;
        for (auto it : row) {
            if (first) first = false;
            else out += '|';
            out += it.first;
            out += '=';
            out += it.second;
        }
        write_string(client, out);
        return true;
    });
    write_int(client, 0);
    db_err_cmd(err, return; );
}

bool db_err(char *e) {
    if (e) {
        LOGE("sqlite3_exec: %s\n", e);
        sqlite3_free(e);
        return true;
    }
    return false;
}

