#ifndef _CRY_COMMON_ANDROID_SPECIFIC_HDR_
#define _CRY_COMMON_ANDROID_SPECIFIC_HDR_

// Android inherits from LinuxSpecific but adds Android-specific quirks
#include "LinuxSpecific.h"
#include <android/log.h>
#include <jni.h>
#include <unistd.h>
#include <sys/system_properties.h>

// Fixups for Android Bionic vs glibc differences
#ifndef ANDROID
#define ANDROID
#endif

// Android log helper (maps CryLog to logcat as well)
#define ANDROID_LOG_TAG "NearChuckle"
#define ANDROID_LOGI(...) __android_log_print(ANDROID_LOG_INFO, ANDROID_LOG_TAG, __VA_ARGS__)
#define ANDROID_LOGW(...) __android_log_print(ANDROID_LOG_WARN, ANDROID_LOG_TAG, __VA_ARGS__)
#define ANDROID_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, ANDROID_LOG_TAG, __VA_ARGS__)

// Storage paths: Android scoped storage + legacy
// Game folder is provided via Java SettingsManager.getGameFolder() -> /storage/emulated/0/FarCry or content://
// Native code will chdir to that folder on startup (see SystemInit.cpp Android branch)

// Workaround: bionic doesn't have some legacy functions
#ifndef MAX_PATH
#define MAX_PATH 512
#endif

// Thread priority helpers for big.LITTLE
#include <sys/resource.h>
inline void Android_SetThreadPriority(int nice) { setpriority(PRIO_PROCESS, 0, nice); }

// ANGLE detection helper
inline bool Android_IsAngleAvailable() {
    // Check via EGL vendor string or libEGL_angle.so existence
    void* h = dlopen("libEGL_angle.so", RTLD_NOW);
    if (h) { dlclose(h); return true; }
    return false;
}

#endif // _CRY_COMMON_ANDROID_SPECIFIC_HDR_
