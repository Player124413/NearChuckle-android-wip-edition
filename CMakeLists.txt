cmake_minimum_required(VERSION 3.12 FATAL_ERROR)
cmake_policy(VERSION 3.12)

project(farcry LANGUAGES C CXX)

# --- Android detection (NDK sets ANDROID) ---
if(ANDROID OR DEFINED ANDROID_PLATFORM OR CMAKE_SYSTEM_NAME STREQUAL "Android")
    set(IS_ANDROID TRUE)
    message(STATUS "[NearChuckle] Android build detected: ANDROID_PLATFORM=${ANDROID_PLATFORM} ANDROID_ABI=${ANDROID_ABI} ANDROID_NDK=${ANDROID_NDK}")
else()
    set(IS_ANDROID FALSE)
endif()

if (CMAKE_SIZEOF_VOID_P EQUAL 8)
    set(ARCH_TYPE "x64")
else (CMAKE_SIZEOF_VOID_P EQUAL 4)
    set(ARCH_TYPE "x86")
endif()
message("CMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}")
message("ARCH_TYPE=${ARCH_TYPE}")
message("IS_ANDROID=${IS_ANDROID}")

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${PROJECT_SOURCE_DIR}/bin")
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE ${CMAKE_SOURCE_DIR}/bin/${ARCH_TYPE}-${CMAKE_BUILD_TYPE})
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY_DEBUG ${CMAKE_SOURCE_DIR}/bin/${ARCH_TYPE}-${CMAKE_BUILD_TYPE})
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_RUNTIME_OUTPUT_DIRECTORY})
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE ${CMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE})
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY_DEBUG ${CMAKE_RUNTIME_OUTPUT_DIRECTORY_DEBUG})

if(MSVC)
    if (${ARCH_TYPE} STREQUAL "x64")
        add_definitions(-DWIN64 -D_AMD64_)
    endif()
    # Always generate PDBs
    add_compile_options(/Zi)
    add_link_options(/DEBUG)

    add_compile_options(/W3) # Warning level
else()
    # Common Linux/Android defines
    add_definitions(-DLINUX -fpermissive)
    if (${ARCH_TYPE} STREQUAL "x64")
        add_definitions(-DLINUX64 -D_AMD64_)
    endif()

    if(IS_ANDROID)
        # Android specific defines + mega optimization flags
        add_definitions(-DANDROID -D_ANDROID -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=1)
        # Disable Cg on Android (no libCg.so), force DISABLE_CG path
        add_definitions(-DDISABLE_CG)
        # Performance
        set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -flto=thin -ffast-math -fomit-frame-pointer -ffunction-sections -fdata-sections")
        set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3 -flto=thin -ffast-math -fomit-frame-pointer -ffunction-sections -fdata-sections")
        set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -Wl,--gc-sections -Wl,--icf=all -flto=thin")
        set(CMAKE_EXE_LINKER_FLAGS_RELEASE "${CMAKE_EXE_LINKER_FLAGS_RELEASE} -Wl,--gc-sections -Wl,--icf=all -flto=thin")
        # Renderer links the system libEGL.so/libGLESv2.so (native GLES driver)
        message(STATUS "[NearChuckle] Android optimization flags enabled (LTO thin, gc-sections, fast-math)")
    else()
        set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS} -O3")
        set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS} -D_DEBUG -g")
    endif()
endif()

# --- SDL3 fallback for CI without Externals/SDL ---
# If SDL3 target still not defined after Externals, create a stub interface so configure doesn't fail
# Real SDL3 will be provided via system package (libsdl3-dev) or FetchContent when available
find_package(SDL3 QUIET)
if(NOT TARGET SDL3 AND NOT TARGET SDL3::SDL3)
    include(FindPkgConfig QUIET)
    if(PKG_CONFIG_FOUND)
        pkg_check_modules(SDL3_PKG QUIET sdl3)
        if(SDL3_PKG_FOUND)
            add_library(SDL3 INTERFACE)
            target_include_directories(SDL3 INTERFACE ${SDL3_PKG_INCLUDE_DIRS})
            target_link_directories(SDL3 INTERFACE ${SDL3_PKG_LIBRARY_DIRS})
            target_link_libraries(SDL3 INTERFACE ${SDL3_PKG_LIBRARIES})
            message(STATUS "[SDL3] Found via pkg-config")
        endif()
    endif()
endif()
if(NOT TARGET SDL3 AND NOT TARGET SDL3::SDL3)
    # Final fallback: stub headers from android/app/src/main/cpp/SDL_stub.h
    if(NOT TARGET SDL3)
        add_library(SDL3 INTERFACE)
        target_include_directories(SDL3 INTERFACE ${CMAKE_SOURCE_DIR}/android/app/src/main/cpp)
        message(WARNING "[SDL3] SDL3 not found - using stub headers (CI builds will configure but native features disabled). Install libsdl3-dev or provide Externals/SDL for full build.")
    endif()
endif()
# Alias SDL3::SDL3 to SDL3 if needed for uniform linking
if(TARGET SDL3 AND NOT TARGET SDL3::SDL3)
    add_library(SDL3::SDL3 ALIAS SDL3)
endif()

add_subdirectory(SourceCode)
