cmake_minimum_required(VERSION 3.22)
project(nearchuckle_android LANGUAGES C CXX)

# Android ABI handling
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find Android libs (optional for non-Android CI lint builds)
find_library(ANDROID_LIB android)
find_library(LOG_LIB log)
find_library(EGL_LIB EGL)
find_library(GLESv2_LIB GLESv2)
find_library(GLESv3_LIB GLESv3)
if(NOT ANDROID_LIB)
    message(WARNING "[nearchuckle_android] ANDROID lib not found (non-Android CI) - using stub")
    set(ANDROID_LIB "")
endif()
if(NOT LOG_LIB)
    set(LOG_LIB "")
endif()
if(NOT EGL_LIB)
    message(WARNING "[nearchuckle_android] EGL not found - using stub, APK will use system EGL at runtime")
    set(EGL_LIB "")
endif()
if(NOT GLESv2_LIB)
    set(GLESv2_LIB "")
endif()
if(NOT GLESv3_LIB)
    set(GLESv3_LIB "")
endif()

# Our JNI bridge + touch injector
add_library(nearchuckle_android SHARED
    native_bridge.cpp
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
# ABI-aware march: arm64-v8a uses armv8-a, armeabi-v7a uses armv7-a
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(NEARCHUCKLE_MARCH -march=armv8-a)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(NEARCHUCKLE_MARCH -march=armv7-a -mthumb)
else()
    set(NEARCHUCKLE_MARCH "")
endif()
target_compile_options(nearchuckle_android PRIVATE
    -O3
    -ffast-math
    -fomit-frame-pointer
    -fstrict-aliasing
    -ffunction-sections
    -fdata-sections
    -DNDEBUG
    ${NEARCHUCKLE_MARCH}
    # Silence noisy warnings from CryEngine headers
    -fpermissive
    -Wno-invalid-offsetof
    -Wno-narrowing
    -Wno-unused-parameter
    -Wno-sign-compare
    -Wno-error
)

target_link_options(nearchuckle_android PRIVATE
    -Wl,--gc-sections
    -Wl,--icf=all
)

set(_NEAR_LINK_LIBS "")
foreach(_lib IN ITEMS ANDROID_LIB LOG_LIB EGL_LIB GLESv2_LIB)
    if(${_lib} AND NOT ${_lib} MATCHES "NOTFOUND")
        list(APPEND _NEAR_LINK_LIBS ${${_lib}})
    else()
        message(STATUS "[nearchuckle_android] Skipping ${_lib} (NOTFOUND/empty)")
    endif()
endforeach()
if(_NEAR_LINK_LIBS)
    target_link_libraries(nearchuckle_android PRIVATE ${_NEAR_LINK_LIBS})
else()
    message(WARNING "[nearchuckle_android] No Android system libs found - linking with -landroid -lEGL -lGLESv2 as fallback")
    target_link_libraries(nearchuckle_android PRIVATE android EGL GLESv2 log)
endif()

# Also build CryEngine libs for Android
# Approach: call parent CMake with ANDROID flag, but as static libs to embed
# We set up a custom external build that reuse SourceCode/CMakeLists.txt
# For now, we provide a stub that expects parent build to have built libCrySystem etc as shared libs via ndk-build externally
# In full CI, gradle will invoke cmake with -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake

# Install (fallback to lib if GNUInstallDirs not included)
include(GNUInstallDirs OPTIONAL)
if(NOT CMAKE_INSTALL_LIBDIR)
    set(CMAKE_INSTALL_LIBDIR lib)
endif()
install(TARGETS nearchuckle_android DESTINATION ${CMAKE_INSTALL_LIBDIR})

message(STATUS "[NearChuckle] Android bridge configured: renderer=native GLES, ANDROID_ABI=${ANDROID_ABI} NDK=${ANDROID_NDK}")
