#pragma once
// Minimal SDL3 stub for CI when SDL3 not fetched
// Real build will use real SDL3 headers via FetchContent
#ifndef SDL_H
#define SDL_H
#include <stdint.h>
typedef uint32_t SDL_ThreadID;
static inline SDL_ThreadID SDL_ThreadID() { return 0; }
static inline void SDL_Delay(uint32_t ms) {}
static inline uint32_t SDL_GetTicks() { return 0; }
static inline uint64_t SDL_GetTicks64() { return 0; }
static inline int SDL_GetSystemRAM() { return 4096; }
#define SDL_INIT_VIDEO 0x20
#define SDL_WINDOW_OPENGL 0x02
#define SDL_EVENT_QUIT 0x100
#define SDL_EVENT_KEY_DOWN 0x300
#define SDL_EVENT_KEY_UP 0x301
#define SDL_EVENT_MOUSE_BUTTON_DOWN 0x400
#define SDL_EVENT_MOUSE_BUTTON_UP 0x401
#define SDL_EVENT_MOUSE_MOTION 0x402
#define SDL_EVENT_MOUSE_WHEEL 0x403
#define SDL_EVENT_TEXT_INPUT 0x303
#define SDL_EVENT_WINDOW_SHOWN 0x200
#define SDL_EVENT_WINDOW_HIDDEN 0x201
#define SDL_EVENT_WINDOW_RESTORED 0x202
#define SDL_EVENT_WINDOW_MAXIMIZED 0x203
#define SDL_EVENT_WINDOW_MOUSE_ENTER 0x204
#define SDL_EVENT_WINDOW_FOCUS_GAINED 0x205
#define SDL_EVENT_WINDOW_MOUSE_LEAVE 0x206
#define SDL_EVENT_WINDOW_FOCUS_LOST 0x207
#define SDL_EVENT_WINDOW_MINIMIZED 0x208
#define SDL_EVENT_GAMEPAD_AXIS_MOTION 0x650
#define SDL_BUTTON_LEFT 1
#define SDL_BUTTON_RIGHT 3
#define SDL_BUTTON_MIDDLE 2
#define SDLK_UNKNOWN 0
#define SDLK_W 119
#define SDLK_A 97
#define SDLK_S 115
#define SDLK_D 100
#define SDLK_SPACE 32
#define SDLK_LCTRL 1073742048
#define SDLK_LSHIFT 1073742049
#define SDLK_R 114
#define SDLK_F 102
#define SDLK_Q 113
#define SDLK_G 103
#define SDLK_TAB 9
#define SDLK_ESCAPE 27
#define SDLK_1 49
#define SDLK_2 50
#define SDLK_3 51
#define SDLK_GRAVE 96
#define SDLK_MINUS 45
#define SDLK_EQUALS 61
#define SDLK_BACKSPACE 8
#define SDLK_RETURN 13
#define SDLK_CAPSLOCK 0
#define SDLK_F1 1073741882
// ... add as needed
typedef int SDL_Keycode;
typedef int SDL_Scancode;
#define SDL_SCANCODE_UNKNOWN 0
typedef struct SDL_Event { int type; struct { int key; int scancode; int mod; int repeat; } key; struct { int button; int x; int y; } button; struct { float xrel,yrel; float x,y; } motion; struct { float y; } wheel; } SDL_Event;
static inline int SDL_PollEvent(SDL_Event* e){ return 0; }
static inline int SDL_PushEvent(SDL_Event* e){ return 0; }
typedef struct SDL_Window {} SDL_Window;
static inline int SDL_SetWindowRelativeMouseMode(SDL_Window*w, int b){ return 0; }
#endif
