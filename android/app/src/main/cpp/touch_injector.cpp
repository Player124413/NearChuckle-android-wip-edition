#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "TouchInjector", __VA_ARGS__)

// This file provides direct CryInput injection alternative to SDL_PushEvent.
// If we link against libCryInput.so, we can call CInput::PostInputEvent directly.

#if 0
// Uncomment and link when building full engine as single binary
#include <IInput.h>
#include <ISystem.h>
extern ISystem* g_pISystem;

void InjectKey(int xkey, bool down) {
    if (!g_pISystem || !g_pISystem->GetIInput()) return;
    SInputEvent ev;
    ev.key = xkey;
    ev.type = down ? SInputEvent::KEY_PRESS : SInputEvent::KEY_RELEASE;
    ev.value = down ? 1.0f : 0.0f;
    g_pISystem->GetIInput()->PostInputEvent(ev);
}
#else
// Stub when engine not linked (SDL path used instead)
void InjectKey(int xkey, bool down) {
    // No-op, SDL path in native_bridge.cpp will handle
}
#endif
