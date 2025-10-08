// IRootUtils.aidl
package io.github.huskydg.magisk.core.utils;

// Declare any non-default types here with import statements

interface IRootUtils {
    android.app.ActivityManager.RunningAppProcessInfo getAppProcess(int pid);
    IBinder getFileSystem();
}
