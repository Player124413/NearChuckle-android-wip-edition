cmake_minimum_required(VERSION 3.22)
project(nearchuckle_android LANGUAGES C CXX)

# Android ABI handling
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Fetch SDL3 for Android java + lib if not already provided via Externals
include(FetchContent)

option(USE_ANGLE "Use Google ANGLE GLES->Vulkan translation" ON)
option(ANGLE_USE_VULKAN "Force ANGLE to use Vulkan backend" ON)

if(USE_ANGLE)
    message(STATUS "[NearChuckle] ANGLE integration enabled - fetching https://github.com/google/angle")
    # ANGLE is huge (~500MB). For CI we fetch shallow or use prebuilt fallback.
    # We attempt FetchContent, but if fails, fallback to system GLES.
    FetchContent_Declare(
        angle
        GIT_REPOSITORY https://github.com/google/angle.git
        GIT_TAG        main
        GIT_SHALLOW    TRUE
        GIT_PROGRESS   TRUE
    )
    # Don't fail fatally if offline; fallback to stub
    set(FETCHCONTENT_QUIET OFF)
    # Only fetch if not already present and network available; guard with option
    option(ANGLE_FETCH "Fetch ANGLE sources" OFF)
    if(ANGLE_FETCH)
        FetchContent_MakeAvailable(angle)
        if(TARGET libANGLE)
            message(STATUS "[ANGLE] libANGLE target available")
        endif()
    else()
        message(STATUS "[ANGLE] Skipping source fetch (ANGLE_FETCH=OFF). Using prebuilt EGL/GLES passthrough + Vulkan loader.")
        message(STATUS "[ANGLE] On Android, system will provide ANGLE via com.google.android.angle if available, else fallback to driver.")
    endif()
endif()

# Find Android libs
find_library(ANDROID_LIB android)
find_library(LOG_LIB log)
find_library(EGL_LIB EGL)
find_library(GLESv2_LIB GLESv2)
find_library(GLESv3_LIB GLESv3)

# Our JNI bridge + angle manager
add_library(nearchuckle_android SHARED
    native_bridge.cpp
    angle_manager.cpp
    touch_injector.cpp
    android_main.cpp
)

target_include_directories(nearchuckle_android PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_SOURCE_DIR}/../../../../SourceCode/CryCommon
    ${CMAKE_SOURCE_DIR}/../../../../SourceCode/CrySystem
)

target_compile_definitions(nearchuckle_android PRIVATE
    ANDROID
    LINUX
    LINUX64
    _ANDROID
    USE_SDL_INPUT
    DISABLE_CG
    ANDROID_PERFORMANCE_MODE
)

# Optimization flags for mega performance on all phones
target_compile_options(nearchuckle_android PRIVATE
    -O3
    -flto=thin
    -ffast-math
    -fomit-frame-pointer
    -fstrict-aliasing
    -ffunction-sections
    -fdata-sections
    -DNDEBUG
    # Tune for armv8
    -march=armv8-a
    # Silence noisy warnings from CryEngine headers
    -fpermissive
    -Wno-invalid-offsetof
    -Wno-narrowing
)

target_link_options(nearchuckle_android PRIVATE
    -Wl,--gc-sections
    -Wl,--icf=all
    -flto=thin
)

target_link_libraries(nearchuckle_android
    ${ANDROID_LIB}
    ${LOG_LIB}
    ${EGL_LIB}
    ${GLESv2_LIB}
    # SDL3 will be linked via separate libSDL3.so from Externals/SDL or via gradle
)

if(USE_ANGLE AND TARGET libANGLE)
    target_link_libraries(nearchuckle_android libANGLE libGLESv2 libEGL)
    target_compile_definitions(nearchuckle_android PRIVATE USE_ANGLE=1 ANGLE_USE_VULKAN=1)
else()
    target_compile_definitions(nearchuckle_android PRIVATE USE_ANGLE=0)
endif()

# Also build CryEngine libs for Android
# Approach: call parent CMake with ANDROID flag, but as static libs to embed
# We set up a custom external build that reuse SourceCode/CMakeLists.txt
# For now, we provide a stub that expects parent build to have built libCrySystem etc as shared libs via ndk-build externally
# In full CI, gradle will invoke cmake with -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake

# Install
install(TARGETS nearchuckle_android DESTINATION ${CMAKE_INSTALL_LIBDIR})

# Generate ANGLE config JSON for runtime
configure_file(${CMAKE_CURRENT_SOURCE_DIR}/angle_config.json.in ${CMAKE_CURRENT_BINARY_DIR}/angle_config.json @ONLY)

message(STATUS "[NearChuckle] Android bridge configured: USE_ANGLE=${USE_ANGLE} ANDROID_ABI=${ANDROID_ABI} NDK=${ANDROID_NDK}")
