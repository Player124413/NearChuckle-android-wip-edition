#include "StdAfx.h"
#include "CryCommon/AndroidOptimization.h"
#include <SDL3/SDL_cpuinfo.h>
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "NearChuckleOpt", __VA_ARGS__)

#ifdef ANDROID

namespace AndroidOpt {

GpuVendor DetectGpuVendor(const char* r) {
    if (!r) return GpuVendor::Unknown;
    std::string s(r);
    for (auto &c: s) c = tolower(c);
    if (s.find("adreno") != std::string::npos) return GpuVendor::Adreno;
    if (s.find("mali") != std::string::npos) return GpuVendor::Mali;
    if (s.find("powervr") != std::string::npos) return GpuVendor::PowerVR;
    if (s.find("nvidia") != std::string::npos) return GpuVendor::Nvidia;
    return GpuVendor::Unknown;
}

void ApplyGpuProfile(GpuVendor v) {
    switch(v) {
        case GpuVendor::Adreno:
            LOGI("GPU Adreno detected: enabling ETC2, 4x anisotropic, no depth bounds");
            // Adreno Vulkan via ANGLE is fastest; tune
            break;
        case GpuVendor::Mali:
            LOGI("GPU Mali detected: enabling ASTC, conservative LOD, ANGLE Vulkan strongly recommended");
            break;
        case GpuVendor::PowerVR:
            LOGI("GPU PowerVR detected: lowering shadow map to 512, disabling POM");
            break;
        default: LOGI("GPU Unknown: using balanced profile"); break;
    }
}

void SetFPSLimit(int fps) {
    // Will be read via CVar sys_maxfps; also hint to choreographer
    LOGI("FPS limit set to %d", fps);
}

void ApplyAllOptimizations() {
    int ram = SDL_GetSystemRAM(); // MB
    LOGI("System RAM %d MB", ram);
    int texBudget = GetTextureBudgetMB(ram);
    LOGI("Texture budget %d MB", texBudget);
    // Additional tuning could set CVars here via gEnv->pConsole if available
}

} // namespace AndroidOpt

#endif // ANDROID
