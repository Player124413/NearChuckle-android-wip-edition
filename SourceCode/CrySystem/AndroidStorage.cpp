#include "StdAfx.h"
#ifdef ANDROID
#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <sys/stat.h>
#include <string>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AndroidStorage", __VA_ARGS__)

extern JavaVM* gJvm; // from native_bridge

// Helper to get game folder from Java SettingsManager
std::string Android_GetGameFolderFromJava() {
    if (!gJvm) return "/storage/emulated/0/FarCry";
    JNIEnv* env = nullptr;
    gJvm->AttachCurrentThread(&env, nullptr);
    if (!env) return "/storage/emulated/0/FarCry";
    jclass cls = env->FindClass("com/nearchuckle/farcry/SettingsManager");
    if (!cls) { LOGI("SettingsManager class not found"); return "/storage/emulated/0/FarCry"; }
    // We need context; try to get via SDL_GetAndroidJNIEnv? Alternative: read env var
    const char* envFolder = getenv("NEARCHUCKLE_GAME_FOLDER");
    if (envFolder) return std::string(envFolder);
    // fallback to default
    return "/storage/emulated/0/FarCry";
}

bool Android_EnsureGameFolder() {
    std::string folder = Android_GetGameFolderFromJava();
    LOGI("Android game folder: %s", folder.c_str());
    if (folder.rfind("content://",0)==0) {
        LOGI("SAF folder (content://) - need to use DocumentFile, not chdir. Will use legacy path via /storage");
        // For SAF, we can't chdir; instead we rely on SDL's storage mapping
        // Try to map to /storage/emulated/0/...
        return false;
    }
    if (chdir(folder.c_str())==0) {
        LOGI("chdir to game folder ok");
        char cwd[512]; getcwd(cwd,sizeof(cwd)); LOGI("cwd now %s", cwd);
        // Also ensure FCData exists
        struct stat st; if (stat("FCData",&st)!=0) LOGI("WARNING: FCData not found in %s", folder.c_str());
        return true;
    } else {
        LOGI("chdir failed to %s: %s", folder.c_str(), strerror(errno));
        return false;
    }
}

// Called early from SystemInit::Init
extern "C" void Android_OnEngineInit() {
    Android_EnsureGameFolder();
    // Copy system_android.cfg to system.cfg if not exists
    struct stat st;
    if (stat("system.cfg",&st)!=0) {
        LOGI("system.cfg not found, will use system_android.cfg from assets if present");
        // Assets are unpacked via SDL? On Android assets are in APK; we need to extract via AAssetManager
        // Simplification: launcher already copies assets/system_android.cfg to app files dir and we chdir there? 
        // We'll try to copy from assets via SDL_RWops if available
    }
}

#endif
