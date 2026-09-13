#include <android/log.h>
#if __has_include(<SDL3/SDL.h>)
#include <SDL3/SDL.h>
#elif __has_include(<SDL.h>)
#include <SDL.h>
#else
#include "SDL_stub.h"
#endif
#include <jni.h>
#include "angle_manager.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "NearChuckleAndroid", __VA_ARGS__)

// This is the SDL_main entry that the engine's Main.cpp will provide.
// For Android we wrap it to setup ANGLE + chdir to game folder before calling real main.

extern int FarCry_SDL_main(int argc, char** argv);

// SDL defines SDL_main as macro; we provide our own that SDLActivity will call via JNI
extern "C" int SDL_main(int argc, char* argv[]) {
    LOGI("SDL_main entry: argc=%d", argc);
    for (int i=0;i<argc;i++) LOGI(" argv[%d]=%s", i, argv[i]);

    // Init ANGLE before any GL context creation
    // Check intent extras: renderer pref via env or settings
    // We read a property file written by launcher? For now default to Vulkan if available
    bool useAngle = true;
    bool useVulkan = true;
    // Could read from SDL hint or system property
    const char* envAngle = getenv("NEARCHUCKLE_USE_ANGLE");
    if (envAngle && strcmp(envAngle, "0")==0) useAngle = false;

    AngleManager::Initialize(useVulkan && useAngle);
    LOGI("ANGLE backend: %s", AngleManager::GetBackendName());

    // Performance: set thread priority, GC hints
    // Chdir to game folder is done via Java NativeBridge nativeGetGameFolder + chdir in SystemInit

    LOGI("Forwarding to FarCry main...");
    int ret = FarCry_SDL_main(argc, argv);
    LOGI("FarCry exited with %d", ret);
    AngleManager::Shutdown();
    return ret;
}

// Helper called from System.cpp to get game folder from Java
extern "C" const char* Android_GetGameFolder(JNIEnv* env, jobject ctx) {
    // This will be implemented via JNI callback to SettingsManager.getGameFolder
    return nullptr; // stub
}
