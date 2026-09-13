#pragma once
#ifdef ANDROID
#include <IInput.h>
#include <ISystem.h>

// Lightweight injector that Java overlay uses via JNI native_bridge.cpp
// Also provides CVar hooks for touch enable/sensitivity so user can toggle via console

class AndroidTouchInjector {
public:
    static AndroidTouchInjector& Get();

    void Init(ISystem* pSystem);
    void Shutdown();

    // Called from JNI (native_bridge.cpp)
    void InjectKey(int xkey, bool down);
    void InjectMouseButton(int btn, bool down);
    void InjectMouseMotion(float dx, float dy);
    void InjectAnalog(int stick, float x, float y);

    void SetTouchEnabled(bool v);
    bool IsTouchEnabled() const { return m_touchEnabled; }
    void SetSensitivity(float s) { m_sensitivity = s; }
    float GetSensitivity() const { return m_sensitivity; }

    // CVar callbacks
    static void Cmd_TouchEnable(IConsoleCmdArgs* args);
    static void Cmd_TouchSensitivity(IConsoleCmdArgs* args);
    static void Cmd_OpenTouchEditor(IConsoleCmdArgs* args);

private:
    ISystem* m_pSystem = nullptr;
    IInput* m_pInput = nullptr;
    bool m_touchEnabled = true;
    float m_sensitivity = 1.0f;
};

#endif
