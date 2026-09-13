#include "StdAfx.h"
#ifdef ANDROID
#include "AndroidTouchInjector.h"
#include <android/log.h>
#include <SDL3/SDL.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AndroidTouch", __VA_ARGS__)

AndroidTouchInjector& AndroidTouchInjector::Get() {
    static AndroidTouchInjector s;
    return s;
}

void AndroidTouchInjector::Init(ISystem* pSystem) {
    m_pSystem = pSystem;
    m_pInput = pSystem ? pSystem->GetIInput() : nullptr;
    LOGI("AndroidTouchInjector Init touchEnabled=%d sens=%.2f", m_touchEnabled, m_sensitivity);
    if (pSystem && pSystem->GetIConsole()) {
        pSystem->GetIConsole()->AddCommand("touch_enable", Cmd_TouchEnable, VF_DUMPTODISK, "Enable/disable touch overlay 0/1");
        pSystem->GetIConsole()->AddCommand("touch_sensitivity", Cmd_TouchSensitivity, VF_DUMPTODISK, "Touch camera sensitivity");
        pSystem->GetIConsole()->AddCommand("touch_editor", Cmd_OpenTouchEditor, 0, "Open touch editor");
        pSystem->GetIConsole()->CreateVariable("touch_enable", "1", VF_DUMPTODISK);
        pSystem->GetIConsole()->CreateVariable("touch_sensitivity", "1.0", VF_DUMPTODISK);
    }
}

void AndroidTouchInjector::Shutdown() {
    m_pSystem = nullptr;
    m_pInput = nullptr;
}

void AndroidTouchInjector::InjectKey(int xkey, bool down) {
    if (!m_touchEnabled && xkey != 0x01) return; // allow ESC?
    SDL_Keycode sym = SDLK_UNKNOWN;
    // mirror mapping from native_bridge.cpp
    switch(xkey) {
        case 0x11: sym = SDLK_W; break;
        case 0x1E: sym = SDLK_A; break;
        case 0x1F: sym = SDLK_S; break;
        case 0x20: sym = SDLK_D; break;
        case 0x39: sym = SDLK_SPACE; break;
        case 0x1D: sym = SDLK_LCTRL; break;
        case 0x2A: sym = SDLK_LSHIFT; break;
        case 0x13: sym = SDLK_R; break;
        case 0x21: sym = SDLK_F; break;
        case 0x10: sym = SDLK_Q; break;
        default: break;
    }
    if (sym != SDLK_UNKNOWN) {
        SDL_Event ev{}; ev.type = down ? SDL_EVENT_KEY_DOWN : SDL_EVENT_KEY_UP; ev.key.key = sym; SDL_PushEvent(&ev);
    }
    // also post via IInput for direct binding
    if (m_pInput) {
        SInputEvent e; e.key = xkey; e.type = down? SInputEvent::KEY_PRESS : SInputEvent::KEY_RELEASE; e.value = down?1:0;
        m_pInput->PostInputEvent(e);
    }
}

void AndroidTouchInjector::InjectMouseButton(int btn, bool down) {
    if (!m_touchEnabled) return;
    SDL_Event ev{}; ev.type = down? SDL_EVENT_MOUSE_BUTTON_DOWN : SDL_EVENT_MOUSE_BUTTON_UP;
    ev.button.button = (btn==0? SDL_BUTTON_LEFT: SDL_BUTTON_RIGHT);
    SDL_PushEvent(&ev);
    if (m_pInput) {
        int xkey = (btn==0? 0x00010000:0x00020000);
        SInputEvent e; e.key=xkey; e.type= down? SInputEvent::KEY_PRESS: SInputEvent::KEY_RELEASE; m_pInput->PostInputEvent(e);
    }
}
void AndroidTouchInjector::InjectMouseMotion(float dx, float dy) {
    if (!m_touchEnabled) return;
    dx*=m_sensitivity; dy*=m_sensitivity;
    SDL_Event ev{}; ev.type=SDL_EVENT_MOUSE_MOTION; ev.motion.xrel=dx; ev.motion.yrel=dy; SDL_PushEvent(&ev);
    if (m_pInput) { SInputEvent e; e.key=0x000B0000; e.type=SInputEvent::MOUSE_MOVE; e.value=dx; m_pInput->PostInputEvent(e); e.key=0x000C0000; e.value=dy; m_pInput->PostInputEvent(e);}
}
void AndroidTouchInjector::InjectAnalog(int stick, float x, float y) {}
void AndroidTouchInjector::SetTouchEnabled(bool v){ m_touchEnabled=v; LOGI("SetTouchEnabled %d", v); if(m_pSystem&&m_pSystem->GetIConsole()) m_pSystem->GetIConsole()->GetCVar("touch_enable")->Set(v?1:0); }
void AndroidTouchInjector::Cmd_TouchEnable(IConsoleCmdArgs* args){ if(args->GetArgCount()>1) Get().SetTouchEnabled(atoi(args->GetArg(1))!=0); }
void AndroidTouchInjector::Cmd_TouchSensitivity(IConsoleCmdArgs* args){ if(args->GetArgCount()>1) Get().SetSensitivity((float)atof(args->GetArg(1))); }
void AndroidTouchInjector::Cmd_OpenTouchEditor(IConsoleCmdArgs* args){ LOGI("touch_editor cmd: Java overlay should open editor activity"); }

#endif
