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

# ---------------------------------------------------------------------------
# Real SDL3 - built from the official release source (no prebuilt blobs, no
# stubs). The gradle task 'downloadSdl3' fetches/unpacks the release tarball;
# CI caches it. SDL3_SOURCE_DIR can also be passed explicitly.
# ---------------------------------------------------------------------------
if(NOT DEFINED SDL3_SOURCE_DIR)
    set(SDL3_SOURCE_DIR "${CMAKE_SOURCE_DIR}/../../../../.sdl3/SDL3-3.4.16")
endif()
if(NOT EXISTS "${SDL3_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "SDL3 source not found at '${SDL3_SOURCE_DIR}'.\n"
        "Run './gradlew downloadSdl3' in android/ (CI runs it automatically).\n"
        "The Android build requires a real SDL3 - there is no stub path.")
endif()
message(STATUS "[NearChuckle] Using SDL3 source: ${SDL3_SOURCE_DIR}")
set(SDL_SHARED ON  CACHE BOOL "" FORCE)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
add_subdirectory("${SDL3_SOURCE_DIR}" "${CMAKE_BINARY_DIR}/SDL3-build" EXCLUDE_FROM_ALL)

# ---------------------------------------------------------------------------
# Sound stack: openal-soft (shared libopenal.so, dlopen'ed through
# libCrySoundSystem.so) + libogg/libvorbis (static, linked into
# libCrySoundSystem.so). All built from official release sources - no
# prebuilt blobs. Sources come from the gradle task 'downloadAudioSources'.
# ---------------------------------------------------------------------------
# NOTE: GitHub auto-archive tarballs strip the leading "v" from the tag in
# the extracted directory name (v1.3.5 -> ogg-1.3.5/).
set(AUDIO_DEPS_DIR "${CMAKE_SOURCE_DIR}/../../../../.audio-deps")
set(OPENAL_DIR  "${AUDIO_DEPS_DIR}/openal-soft-1.25.1")
set(OGG_DIR     "${AUDIO_DEPS_DIR}/ogg-1.3.5")
set(VORBIS_DIR  "${AUDIO_DEPS_DIR}/vorbis-1.3.7")
foreach(_d "${OPENAL_DIR}" "${OGG_DIR}" "${VORBIS_DIR}")
    if(NOT EXISTS "${_d}/CMakeLists.txt")
        message(FATAL_ERROR
            "Audio dependency source missing: ${_d}\n"
            "Run './gradlew downloadAudioSources' in android/ (CI runs it automatically).")
    endif()
endforeach()

# libogg (static)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory("${OGG_DIR}" "${CMAKE_BINARY_DIR}/ogg-build" EXCLUDE_FROM_ALL)

# libvorbis + vorbisfile (static). vorbis 1.3.7's own CMake ships a
# MODULE-mode FindOgg.cmake (find_library-based) that cannot see a
# not-yet-built sibling ogg in the same tree when cross-compiling with the
# NDK (CMAKE_FIND_ROOT_PATH restricts the search to the sysroot), so instead
# of fighting find_package() we compile the same source lists directly and
# link them against the ogg target above.
add_library(vorbis STATIC
    ${VORBIS_DIR}/lib/mdct.c
    ${VORBIS_DIR}/lib/smallft.c
    ${VORBIS_DIR}/lib/block.c
    ${VORBIS_DIR}/lib/envelope.c
    ${VORBIS_DIR}/lib/window.c
    ${VORBIS_DIR}/lib/lsp.c
    ${VORBIS_DIR}/lib/lpc.c
    ${VORBIS_DIR}/lib/analysis.c
    ${VORBIS_DIR}/lib/synthesis.c
    ${VORBIS_DIR}/lib/psy.c
    ${VORBIS_DIR}/lib/info.c
    ${VORBIS_DIR}/lib/floor1.c
    ${VORBIS_DIR}/lib/floor0.c
    ${VORBIS_DIR}/lib/res0.c
    ${VORBIS_DIR}/lib/mapping0.c
    ${VORBIS_DIR}/lib/registry.c
    ${VORBIS_DIR}/lib/codebook.c
    ${VORBIS_DIR}/lib/sharedbook.c
    ${VORBIS_DIR}/lib/lookup.c
    ${VORBIS_DIR}/lib/bitrate.c
)
add_library(vorbisfile STATIC ${VORBIS_DIR}/lib/vorbisfile.c)
target_include_directories(vorbis PRIVATE
    ${VORBIS_DIR}/include
    ${VORBIS_DIR}/lib
)
target_include_directories(vorbisfile PUBLIC
    ${VORBIS_DIR}/include
)
target_link_libraries(vorbis PUBLIC ogg m)
target_link_libraries(vorbisfile PUBLIC vorbis)

# openal-soft (shared; LIBTYPE is its own option name)
set(LIBTYPE SHARED CACHE STRING "" FORCE)
add_subdirectory("${OPENAL_DIR}" "${CMAKE_BINARY_DIR}/openal-build" EXCLUDE_FROM_ALL)

# ---------------------------------------------------------------------------
# CryEngine: build the whole engine tree (SourceCode) for Android.
# The root CMakeLists detects the NDK toolchain (IS_ANDROID) and compiles the
# engine modules as shared libraries; AGP packages every produced .so into
# the APK. libFarCry.so exports FarCry_SDL_main, which our SDL_main wrapper
# below calls.
# NOTE: this CMakeLists sits at android/app/src/main/cpp - FIVE levels below
# the repo root.
# ---------------------------------------------------------------------------
set(ENGINE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../..")
add_subdirectory("${ENGINE_ROOT}" "${CMAKE_BINARY_DIR}/engine" EXCLUDE_FROM_ALL)

# Our JNI bridge + touch injector + SDL_main wrapper
add_library(nearchuckle_android SHARED
    native_bridge.cpp
    touch_injector.cpp
    android_main.cpp
)

target_include_directories(nearchuckle_android PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ENGINE_ROOT}/SourceCode/CryCommon
    ${ENGINE_ROOT}/SourceCode/CrySystem
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

# SDL3 (real target built above) and the engine entry library: this makes
# libnearchuckle_android.so depend on libSDL3.so + libFarCry.so, so loading
# the wrapper pulls in the whole chain and the weak FarCry_SDL_main symbol
# resolves at link time.
list(APPEND _NEAR_LINK_LIBS SDL3::SDL3 FarCry)
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
