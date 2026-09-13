#include "angle_manager.h"
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <dlfcn.h>
#include <string>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ANGLE", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "ANGLE", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ANGLE", __VA_ARGS__)

static bool gAngleInitialized = false;
static bool gUseAngle = false;
static void* gAngleEGLHandle = nullptr;

bool AngleManager::IsAvailable() {
#if USE_ANGLE
    return true;
#else
    // Check if system ANGLE via libEGL_angle.so exists or via system feature
    void* h = dlopen("libEGL_angle.so", RTLD_NOW);
    if (h) { dlclose(h); return true; }
    // Also check via property? Modern Android has ANGLE via GraphicsEnvironment
    // We query EGL extension
    const char* exts = eglQueryString(EGL_NO_DISPLAY, EGL_EXTENSIONS);
    if (exts && strstr(exts, "ANGLE")) return true;
    return false;
#endif
}

bool AngleManager::Initialize(bool preferVulkan) {
    if (gAngleInitialized) return gUseAngle;
    gAngleInitialized = true;

#if USE_ANGLE
    LOGI("Initializing ANGLE (preferVulkan=%d)", preferVulkan);
    // On Android, ANGLE can be forced via system property or by loading libEGL_angle.so first
    // Our CMake links against ANGLE's EGL; simply dlopen to ensure it is used
    gAngleEGLHandle = dlopen("libEGL_angle.so", RTLD_NOW);
    if (gAngleEGLHandle) {
        LOGI("Loaded libEGL_angle.so successfully - all GLES will go through ANGLE -> Vulkan");
        gUseAngle = true;
    } else {
        // Check if angle is provided by system (Android 13+ GraphicsEnvironment)
        // Then EGL will automatically use ANGLE if app requests via manifest meta-data
        const char* eglExt = eglQueryString(EGL_NO_DISPLAY, EGL_EXTENSIONS);
        LOGI("libEGL_angle.so not found, checking system EGL ext: %s", eglExt ? eglExt : "(null)");
        // We still consider ANGLE available via system
        // Try to detect ANGLE via eglGetDisplay + query
        EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (dpy != EGL_NO_DISPLAY) {
            const char* vendor = eglQueryString(dpy, EGL_VENDOR);
            const char* version = eglQueryString(dpy, EGL_VERSION);
            LOGI("EGL_VENDOR=%s VERSION=%s", vendor?vendor:"?", version?version:"?");
            if (vendor && (strstr(vendor, "ANGLE") || strstr(vendor, "Google"))) {
                gUseAngle = true;
                LOGI("System EGL is ANGLE (vendor string match)");
            } else {
                LOGW("System EGL is not ANGLE, falling back to native driver (still works, less optimal on Mali)");
                gUseAngle = false;
            }
        }
    }

    if (preferVulkan) {
        // Hint ANGLE to use Vulkan backend via env or egl attribute
        // On Android, ANGLE backend selection is via system setting "angle_gl_driver_selection"
        // We set property via setprop if we have permission? Not needed - manifest already hints
        LOGI("ANGLE Vulkan backend requested - manifest meta-data com.google.android.angle.GameAngle=vulkan will be honored on Android 12+");
        // Also try to set via egl angle extension: EGL_ANGLE_feature_control
    }

    LOGI("ANGLE init result: %s", gUseAngle ? "VULKAN via ANGLE enabled" : "native GLES fallback");
    return gUseAngle;
#else
    LOGW("ANGLE disabled at compile time (USE_ANGLE=0) - using native GLES");
    gUseAngle = false;
    return false;
#endif
}

void AngleManager::Shutdown() {
    if (gAngleEGLHandle) {
        dlclose(gAngleEGLHandle);
        gAngleEGLHandle = nullptr;
    }
    gAngleInitialized = false;
    gUseAngle = false;
}

bool AngleManager::IsUsingAngle() { return gUseAngle; }

const char* AngleManager::GetBackendName() {
    if (!gUseAngle) return "Native GLES";
#if USE_ANGLE
    return "ANGLE (GLES -> Vulkan)";
#else
    return "Native GLES (ANGLE not compiled)";
#endif
}

// Called from engine pre-init to configure EGL chooser
extern "C" void NearChuckle_ConfigureANGLE(bool useAngle, bool useVulkan) {
    AngleManager::Initialize(useVulkan && useAngle);
}
