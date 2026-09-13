#pragma once
// Central Android mega-optimization tuning
// Included from SystemInit, Renderer, Cry3DEngine

namespace AndroidOpt {

// Dynamic resolution scaler: adjusts render resolution to keep FPS
struct DynResConfig {
    bool enabled = true;
    float minScale = 0.6f;
    float maxScale = 1.0f;
    float targetFPS = 60.0f;
    float adjustSpeed = 0.05f;
};

// Per-GPU profile
enum class GpuVendor { Unknown, Adreno, Mali, PowerVR, Nvidia };
GpuVendor DetectGpuVendor(const char* rendererString);
void ApplyGpuProfile(GpuVendor v);

// Texture budget
inline int GetTextureBudgetMB(int totalRamMB) {
    if (totalRamMB <= 2048) return 48;
    if (totalRamMB <= 3072) return 64;
    if (totalRamMB <= 4096) return 96;
    return 128;
}

// LOD bias
inline float GetLODBias(int gpuClass) { // 0=low,1=mid,2=high
    if (gpuClass==0) return 0.6f;
    if (gpuClass==1) return 0.8f;
    return 1.0f;
}

// FPS limiter
void SetFPSLimit(int fps);

// Called once on startup
void ApplyAllOptimizations();

} // namespace AndroidOpt
