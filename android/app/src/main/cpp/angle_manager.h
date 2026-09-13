#pragma once

class AngleManager {
public:
    static bool IsAvailable();
    static bool Initialize(bool preferVulkan = true);
    static void Shutdown();
    static bool IsUsingAngle();
    static const char* GetBackendName();
};

extern "C" void NearChuckle_ConfigureANGLE(bool useAngle, bool useVulkan);
