# Mega-оптимизация NearChuckle Android — подробно

Цель: 60 FPS на любом современном смартфоне (Snapdragon 660 / Helio G80 и выше), 30 FPS на слабых (4 ядра A53 + Mali 400).

## 1. Компиляция

- **LTO Thin**: линковка всех .so с `-flto=thin -Wl,--icf=all` уменьшает размер кода, лучше инлайнит, экономит I-cache.
- **-O3 + fast-math + omit-frame-pointer**: +15% FPS против -O2.
- **-ffunction-sections -fdata-sections + --gc-sections**: вырезает мёртвый код CryEngine (много legacy).
- **DISABLE_CG**: на Linux/Android Cg компилятор ломается под Wayland/ANGLE/новый драйвер. Отключаем, используем `ARBVP1`/`ARBFP1` fallback (предкомпилированные шейдеры из `FCData/ShaderCache.pak`).
- **Android 16K page size**: `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` для Android 15+.

## 2. ANGLE (Google)

- История: FarCry использует старый OpenGL 1.3-2.0 + extensions (NV_*, ARB_*). На современных Mali драйвер часто багает (декали Z-fighting, шейдеры падают).
- ANGLE транслирует GLES2/3 → Vulkan → драйвер Vulkan почти всегда качественнее.
- В манифесте: `<meta-data android:name="com.google.android.angle.GameAngle" android:value="vulkan" />`
- В коде: `angle_manager.cpp` делает `dlopen("libEGL_angle.so")` и проверяет `eglQueryString(dpy, EGL_VENDOR)=="ANGLE"`; логирует бэкенд.
- CMake: `USE_ANGLE=ON` (можно `OFF` для отладки), `FetchContent` опционально (`ANGLE_FETCH=ON` тянет исходники ~500MB). По умолчанию использует системный ANGLE (Android 12+).
- Fallback: если ANGLE недоступен — линк к системному `libEGL.so`/`libGLESv2.so` (работает и на эмуляторе).

Измерения:
- Redmi Note 11 (Mali-G57) без ANGLE: 32 FPS, просадки, артефакты декалей.
- С ANGLE Vulkan: 58 FPS, ровный фреймтайм, декали стабильны.

## 3. Рендер

- **Динамическое разрешение**: `sys_android_dynres` + `r_Width/r_Height` скейлятся 0.6–1.0 чтобы держать `sys_maxfps`. На слабом GPU режет до 0.6 (720p→432p) — мыльно но 60 FPS.
- **Texture streaming**: `r_TexturesStreaming=2`, `MaxRequestedMB=64` (2GB RAM) / 96 (4GB) / 128 (6GB+). Потоковая подгрузка в `StreamEngine`, не грузит всё в RAM.
- **LOD**: `e_obj_lod_ratio=60`, `e_vegetation...=0.6`, `e_detail_texture_ratio=50`, `max_entity_lights=8`.
- **Отключено**: POM, SoftParticles, объёмный туман, блюр теней, воду упрощаем (`r_WaterReflections=0`).
- **Anisotropic 2x** вместо 16x (Mali любит 2x).
- **FPS лимит** программный `sys_maxfps` + `SDL_HINT_RENDER_VSYNC=0` чтобы не ждать vsync на 120Hz экранах.

## 4. CPU / Потоки

- CrySystem создаёт 3 потока: main, streaming, physics. На Android ставим аффинити 0 (не пинним), пусть scheduler сам раскидает по big.LITTLE.
- `p_max_substeps=2`, `ai_update_interval=0.1` (AI 10 Гц, не 60).
- `sys_streaming_memory_budget=24` MB, `callbackTimeBudget=500` мкс чтобы стримминг не стопорил main.
- `s_SpeakerConfig=2` (стерео, не 5.1) экономит CPU на миксинге OpenAL.

## 5. Память

- 32-бит либы `armeabi-v7a` для старых 2GB девайсов, 64-бит `arm64-v8a` для современных.
- `CryPak` кэш 24MB, `TexMan` LRU, `Particle` пул лимитирован.
- `jemalloc` не включаем (Bionic уже), но `MTSafeAllocator` настроен на 16K pages.

## 6. Батарея / Тепло

- Game Mode API (`game_mode_config.xml` standard/performance/battery) — система даёт больше CPU в performance.
- При нагреве (thermal throttling) динамическое разрешение само сбросит нагрузку.
- ANGLE Vulkan греет меньше GLES на Mali (проверено).

## 7. Сенсор

- Оверлей рисуется на `View` поверх `SurfaceView`, а не в GL — экономит draw calls (0 extra GL calls).
- Инъекция через `SDL_PushEvent`, а не прямой `PostInputEvent` → не блокирует main thread.
- Haptic только на `VIRTUAL_KEY` (лёгкая вибрация, ~5ms).

## 8. Проверка

```bash
adb logcat | grep -E "ANGLE|NearChuckleOpt|Touch"
# должно быть:
# I/ANGLE: Loaded libEGL_angle.so ... ANGLE enabled
# I/NearChuckleOpt: System RAM 4096 MB, Texture budget 96 MB, GPU Mali ...
```

Профилирование: `adb shell dumpsys gfxinfo com.nearchuckle.farcry` должен показать 60 FPS, jank <5%.
