#include <android/log.h>
#if __has_include(<SDL3/SDL.h>)
#include <SDL3/SDL.h>
#elif __has_include(<SDL.h>)
#include <SDL.h>
#else
#include "SDL_stub.h"
#endif
#include <jni.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "NearChuckleAndroid", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "NearChuckleAndroid", __VA_ARGS__)

// This is the SDL_main entry that the engine's Main.cpp will provide.
// For Android we wrap it to chdir to the game folder before calling real main.

// Weak symbol — if CryEngine not linked (CI stub build), fallback to 0 instead of linker error
extern "C" int FarCry_SDL_main(int argc, char** argv) __attribute__((weak));

// SDL defines SDL_main as macro; we provide our own that SDLActivity will call via JNI
extern "C" int SDL_main(int argc, char* argv[]) {
    LOGI("SDL_main entry: argc=%d", argc);
    for (int i=0;i<argc;i++) LOGI(" argv[%d]=%s", i, argv[i]);

    // Renderer: native GLES driver of the device (ANGLE removed).
    // Chdir to game folder is done via Java NativeBridge nativeGetGameFolder + chdir in SystemInit

    LOGI("Forwarding to FarCry main...");
    int ret = 0;
    if (FarCry_SDL_main) {
        ret = FarCry_SDL_main(argc, argv);
        LOGI("FarCry exited with %d", ret);
    } else {
        LOGW("FarCry_SDL_main not linked (CI stub) — skipping engine main, returning 0");
        ret = 0;
    }
    return ret;
}

// Helper called from System.cpp to get game folder from Java
extern "C" const char* Android_GetGameFolder(JNIEnv* env, jobject ctx) {
    // This will be implemented via JNI callback to SettingsManager.getGameFolder
    return nullptr; // stub
}
