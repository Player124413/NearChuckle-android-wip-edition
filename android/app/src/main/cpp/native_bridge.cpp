#include <jni.h>
#include <android/log.h>
#include <string>
#include <mutex>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "NearChuckleBridge", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "NearChuckleBridge", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "NearChuckleBridge", __VA_ARGS__)

extern "C" {

// Forward decls for CryInput injection (weak symbols so desktop build not required)
typedef struct IInput IInput;
typedef struct ISystem ISystem;
// We will use SDL_PushEvent for injection to avoid hard dependency on CryInput headers
#include <SDL3/SDL.h>

// Global state
static JavaVM* gJvm = nullptr;
static bool gTouchEnabled = true;
static float gSensitivity = 1.0f;
static std::mutex gMutex;

// Cache for JNI method IDs if needed
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    gJvm = vm;
    LOGI("JNI_OnLoad: NearChuckle bridge loaded, touch=%d sens=%.2f", gTouchEnabled, gSensitivity);
    return JNI_VERSION_1_6;
}

// Helper to emit SDL key event that CryInput's SDLKeyboard will consume
static void pushSDLKey(SDL_Keycode sym, bool down) {
    SDL_Event ev{};
    ev.type = down ? SDL_EVENT_KEY_DOWN : SDL_EVENT_KEY_UP;
    ev.key.key = sym;
    ev.key.scancode = SDL_SCANCODE_UNKNOWN;
    ev.key.mod = 0;
    ev.key.repeat = 0;
    SDL_PushEvent(&ev);
}

static void pushSDLMouseButton(int btn, bool down) {
    SDL_Event ev{};
    ev.type = down ? SDL_EVENT_MOUSE_BUTTON_DOWN : SDL_EVENT_MOUSE_BUTTON_UP;
    ev.button.button = (btn==0? SDL_BUTTON_LEFT : (btn==1? SDL_BUTTON_RIGHT : SDL_BUTTON_MIDDLE));
    ev.button.x = 0; ev.button.y = 0;
    SDL_PushEvent(&ev);
}

static void pushSDLMouseMotion(float dx, float dy) {
    SDL_Event ev{};
    ev.type = SDL_EVENT_MOUSE_MOTION;
    ev.motion.xrel = dx;
    ev.motion.yrel = dy;
    ev.motion.x = 0; ev.motion.y = 0;
    SDL_PushEvent(&ev);
}

// XKEY -> SDL_Keycode mapping (partial, enough for touch)
static SDL_Keycode xkeyToSDL(int xkey) {
    switch (xkey) {
        case 0x11: return SDLK_W;
        case 0x1E: return SDLK_A;
        case 0x1F: return SDLK_S;
        case 0x20: return SDLK_D;
        case 0x39: return SDLK_SPACE;
        case 0x1D: return SDLK_LCTRL;
        case 0x2A: return SDLK_LSHIFT;
        case 0x13: return SDLK_R;
        case 0x21: return SDLK_F;
        case 0x10: return SDLK_Q;
        case 0x22: return SDLK_G;
        case 0x0F: return SDLK_TAB;
        case 0x01: return SDLK_ESCAPE;
        case 0x02: return SDLK_1;
        case 0x03: return SDLK_2;
        case 0x04: return SDLK_3;
        default: return SDLK_UNKNOWN;
    }
}

JNIEXPORT void JNICALL Java_com_nearchuckle_farcry_NativeBridge_nativeSendKey(JNIEnv* env, jclass clazz, jint xkey, jboolean down) {
    std::lock_guard<std::mutex> lk(gMutex);
    if (!gTouchEnabled && xkey != 0x01) { // allow ESC even when disabled? but respect flag
        // Still allow if needed; comment out to fully block
    }
    // Map XKEY special mouse buttons
    if (xkey == 0x00010000) { pushSDLMouseButton(0, down); return; }
    if (xkey == 0x00020000) { pushSDLMouseButton(1, down); return; }
    SDL_Keycode sym = xkeyToSDL(xkey);
    if (sym != SDLK_UNKNOWN) {
        pushSDLKey(sym, down);
        LOGI("nativeSendKey xkey=0x%x sdl=%d down=%d", xkey, (int)sym, (int)down);
    } else {
        LOGW("nativeSendKey unknown xkey 0x%x", xkey);
    }
}

JNIEXPORT void JNICALL Java_com_nearchuckle_farcry_NativeBridge_nativeSendMouseButton(JNIEnv* env, jclass clazz, jint button, jboolean down) {
    std::lock_guard<std::mutex> lk(gMutex);
    if (!gTouchEnabled) return;
    pushSDLMouseButton(button, down);
    LOGI("nativeSendMouseButton btn=%d down=%d", button, (int)down);
}

JNIEXPORT void JNICALL Java_com_nearchuckle_farcry_NativeBridge_nativeSendMouseMotion(JNIEnv* env, jclass clazz, jfloat dx, jfloat dy) {
    std::lock_guard<std::mutex> lk(gMutex);
    if (!gTouchEnabled) return;
    float sx = dx * gSensitivity;
    float sy = dy * gSensitivity;
    pushSDLMouseMotion(sx, sy);
}

JNIEXPORT void JNICALL Java_com_nearchuckle_farcry_NativeBridge_nativeSendAnalog(JNIEnv* env, jclass clazz, jint stickId, jfloat x, jfloat y) {
    // Analog sticks: we already handle via keys/mouse, but also could push gamepad events
    // For now push as mouse motion for look stick, or WASD for move already done
    if (!gTouchEnabled) return;
    if (stickId == 1) {
        // look: already handled via mouse motion per move, but send small motion for continuous
        // No-op if already sent via sendMouseMotion
    }
    // Could also emit SDL_EVENT_GAMEPAD_AXIS_MOTION if engine supports gamepad
    // SDL_Event ev{}; ev.type = SDL_EVENT_GAMEPAD_AXIS_MOTION; ...
}

JNIEXPORT void JNICALL Java_com_nearchuckle_farcry_NativeBridge_nativeSetTouchEnabled(JNIEnv* env, jclass clazz, jboolean enabled) {
    std::lock_guard<std::mutex> lk(gMutex);
    gTouchEnabled = enabled;
    LOGI("nativeSetTouchEnabled %d", (int)enabled);
}

JNIEXPORT void JNICALL Java_com_nearchuckle_farcry_NativeBridge_nativeSetSensitivity(JNIEnv* env, jclass clazz, jfloat sens) {
    std::lock_guard<std::mutex> lk(gMutex);
    gSensitivity = sens;
    LOGI("nativeSetSensitivity %.2f", sens);
}

} // extern "C"
