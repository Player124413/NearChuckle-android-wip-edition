project(FarCry)

set(SRC_FILES
    "Main.cpp"
)

if(MSVC)
    list(APPEND SRC_FILES "FarCry.rc")
endif()

if(ANDROID)
    # On Android, build as shared lib for SDLActivity (SDL_main entry)
    # SDL3 on Android expects libmain.so with SDL_main()
    add_library(${PROJECT_NAME} SHARED ${SRC_FILES})

    target_include_directories(${PROJECT_NAME} PRIVATE
        ${CMAKE_SOURCE_DIR}/SourceCode/CryCommon
    )

    # SDL3 headers via FetchContent or system
    find_package(SDL3 QUIET)
    if(NOT SDL3_FOUND)
        # fallback: try pkg-config
        find_library(SDL3_LIB SDL3)
    endif()

    target_link_libraries(${PROJECT_NAME} SDL3 android log EGL GLESv2)

    # Keep SDL_main symbol
    target_compile_definitions(${PROJECT_NAME} PRIVATE ANDROID SDL_MAIN_HANDLED)

    # Optimization
    target_compile_options(${PROJECT_NAME} PRIVATE -O3 -flto=thin -ffast-math -fomit-frame-pointer -ffunction-sections -fdata-sections)
    target_link_options(${PROJECT_NAME} PRIVATE -Wl,--gc-sections -Wl,--icf=all -flto=thin)

    # Output name must be libFarCry.so but SDLActivity expects libmain.so; we provide alias via soname?
    set_target_properties(${PROJECT_NAME} PROPERTIES OUTPUT_NAME "main")
else()
    add_executable(${PROJECT_NAME} WIN32 ${SRC_FILES})

    target_include_directories(${PROJECT_NAME} PRIVATE
        ${CMAKE_SOURCE_DIR}/SourceCode/CryCommon
        ${CMAKE_SOURCE_DIR}/Externals/SDL/include
    )

    target_link_libraries(${PROJECT_NAME} SDL3 dl)
endif()
