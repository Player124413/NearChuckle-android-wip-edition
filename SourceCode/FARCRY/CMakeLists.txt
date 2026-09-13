project(FarCry)

set(SRC_FILES
    "Main.cpp"
)

if(MSVC)
    list(APPEND SRC_FILES "FarCry.rc")
endif()

if(ANDROID)
    # Android: the engine entry lives in a shared library (libFarCry.so).
    # Its entry symbol FarCry_SDL_main (see Main.cpp) is called by the
    # SDL_main wrapper inside libnearchuckle_android.so, which is the
    # "main" library SDLActivity loads (last entry of getLibraries()).
    add_library(${PROJECT_NAME} SHARED ${SRC_FILES})

    target_include_directories(${PROJECT_NAME} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/../CryCommon
    )

    # Real SDL3 built from the official release source by the android
    # gradle build (android/app/src/main/cpp/CMakeLists.txt) - no stubs.
    # Main.cpp dlopen()s libCrySystem.so by bare name at runtime, the
    # Android linker resolves it from the app's nativeLibraryDir.
    target_link_libraries(${PROJECT_NAME} PRIVATE SDL3::SDL3 dl log)

    target_compile_definitions(${PROJECT_NAME} PRIVATE ANDROID SDL_MAIN_HANDLED)

    target_compile_options(${PROJECT_NAME} PRIVATE -O3 -ffast-math -fomit-frame-pointer -ffunction-sections -fdata-sections)
    target_link_options(${PROJECT_NAME} PRIVATE -Wl,--gc-sections)

    set_target_properties(${PROJECT_NAME} PROPERTIES OUTPUT_NAME "FarCry")
else()
    add_executable(${PROJECT_NAME} WIN32 ${SRC_FILES})

    target_include_directories(${PROJECT_NAME} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/../CryCommon
        ${CMAKE_SOURCE_DIR}/Externals/SDL/include
    )

    target_link_libraries(${PROJECT_NAME} SDL3 dl)
endif()
