#include <android/log.h>
#if __has_include(<SDL3/SDL.h>)
#include <SDL3/SDL.h>
#elif __has_include(<SDL.h>)
#include <SDL.h>
#else
#include "SDL_stub.h"
#endif
#include <jni.h>
#include <cstdlib>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "NearChuckleAndroid", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "NearChuckleAndroid", __VA_ARGS__)

// This is the SDL_main entry that the engine's Main.cpp will provide.
// For Android we wrap it to chdir to the game folder before calling real main.

// Weak symbol — if CryEngine not linked (should not happen anymore: the build
// links libFarCry.so), fall back to 0 instead of a linker error.
extern "C" int FarCry_SDL_main(int argc, char** argv) __attribute__((weak));

// Ask Java (SettingsManager, filled by the launcher) for the game data folder
// and export it as NEARCHUCKLE_GAME_FOLDER. CrySystem's AndroidStorage reads
// this env var (Android_GetGameFolderFromJava) and chdir()s there before the
// engine opens any file.
static void SetGameFolderEnvFromJava() {
    JNIEnv* env = (JNIEnv*)SDL_GetAndroidJNIEnv();
    if (!env) { LOGW("SDL_GetAndroidJNIEnv failed"); return; }
    jobject activity = (jobject)SDL_GetAndroidActivity();
    if (!activity) { LOGW("SDL_GetAndroidActivity failed"); return; }

    // Use the activity's classloader to find app classes (FindClass from a
    // native-attached thread can miss the app classloader).
    jclass activityCls = env->GetObjectClass(activity);
    jmethodID mGetClassLoader = env->GetMethodID(activityCls, "getClassLoader", "()Ljava/lang/ClassLoader;");
    if (!mGetClassLoader) { env->ExceptionClear(); LOGW("getClassLoader not found"); return; }
    jobject classLoader = env->CallObjectMethod(activity, mGetClassLoader);
    if (env->ExceptionCheck() || !classLoader) { env->ExceptionClear(); LOGW("getClassLoader() failed"); return; }

    jclass loaderCls = env->GetObjectClass(classLoader);
    jmethodID mFindClass = env->GetMethodID(loaderCls, "findClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    if (!mFindClass) { env->ExceptionClear(); LOGW("findClass method not found"); return; }

    jstring className = env->NewStringUTF("com.nearchuckle.farcry.SettingsManager");
    jclass smCls = (jclass)env->CallObjectMethod(classLoader, mFindClass, className);
    if (env->ExceptionCheck() || !smCls) {
        env->ExceptionClear();
        LOGW("SettingsManager class not found - using default game folder");
        return;
    }

    jmethodID mGet = env->GetStaticMethodID(smCls, "get", "(Landroid/content/Context;)Lcom/nearchuckle/farcry/SettingsManager;");
    if (!mGet) { env->ExceptionClear(); LOGW("SettingsManager.get() not found"); return; }
    jobject sm = env->CallStaticObjectMethod(smCls, mGet, activity);
    if (env->ExceptionCheck() || !sm) { env->ExceptionClear(); LOGW("SettingsManager.get() failed"); return; }

    jmethodID mFolder = env->GetMethodID(smCls, "getGameFolder", "()Ljava/lang/String;");
    if (!mFolder) { env->ExceptionClear(); LOGW("getGameFolder() not found"); return; }
    jstring jFolder = (jstring)env->CallObjectMethod(sm, mFolder);
    if (env->ExceptionCheck() || !jFolder) { env->ExceptionClear(); LOGW("getGameFolder() call failed"); return; }

    const char* folder = env->GetStringUTFChars(jFolder, nullptr);
    if (folder && folder[0]) {
        setenv("NEARCHUCKLE_GAME_FOLDER", folder, 1 /*overwrite*/);
        LOGI("Game folder (from launcher): %s", folder);
    } else {
        LOGW("Empty game folder - engine will use the default /storage/emulated/0/FarCry");
    }
    if (folder) env->ReleaseStringUTFChars(jFolder, folder);
}

// SDL defines SDL_main as macro; we provide our own that SDLActivity will call via JNI
extern "C" int SDL_main(int argc, char* argv[]) {
    LOGI("SDL_main entry: argc=%d", argc);
    for (int i=0;i<argc;i++) LOGI(" argv[%d]=%s", i, argv[i]);

    // Renderer: native GLES driver of the device (ANGLE removed).
    SetGameFolderEnvFromJava();

    LOGI("Forwarding to FarCry main...");
    int ret = 0;
    if (FarCry_SDL_main) {
        ret = FarCry_SDL_main(argc, argv);
        LOGI("FarCry exited with %d", ret);
    } else {
        LOGW("FarCry_SDL_main not linked - libFarCry.so missing from the build/APK!");
        ret = 1;
    }
    return ret;
}
