project(CryInput)

add_definitions(-D_USRDLL -DCRYINPUT_EXPORTS)

if (NOT MSVC)
add_definitions(-DUSE_SDL_INPUT)
endif()

# Android disables Cg and enables touch injector + ANGLE hints
if (ANDROID)
    add_definitions(-DANDROID -DUSE_SDL_INPUT -DDISABLE_CG)
    message(STATUS "[CryInput] Android touch injector enabled")
endif()

set(SRC_FILES
    "CryInput.cpp"
    "Input.cpp"
    "Joystick.cpp"
    "StdAfx.cpp"
    "XActionMap.cpp"
    "XActionMapManager.cpp"
)

if (MSVC)
set (SRC_FILES ${SRC_FILES}
    "XKeyboard.cpp"
    "XMouse.cpp"
)
else()
set (SRC_FILES ${SRC_FILES}
    "SDLKeyboard.cpp"
    "SDLMouse.cpp"
)
endif()

if (ANDROID)
    list(APPEND SRC_FILES
        "AndroidTouchInjector.cpp"
        "TouchControls.cpp"
    )
endif()

add_library(${PROJECT_NAME} SHARED ${SRC_FILES})

target_include_directories(${PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../CryCommon
)

if(ANDROID)
    find_library(ANDROID_LIB android)
    find_library(LOG_LIB log)
    target_link_libraries(${PROJECT_NAME} SDL3::SDL3 ${ANDROID_LIB} ${LOG_LIB})
else()
    target_link_libraries(${PROJECT_NAME} SDL3)
endif()
