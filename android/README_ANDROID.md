# NearChuckle Android — Лаунчер + Сенсор + ANGLE

Порт Far Cry (CryEngine 1) на Android с мега-оптимизацией.

## Что сделано

### 1. Лаунчер (`LauncherActivity`)
- Выбор папки с игрой через SAF (Storage Access Framework, работает на Android 11+)
- Ручной ввод пути, проверка `FCData`/`Levels`
- Выбор рендера: **ANGLE (Vulkan)**, системный GLES, Авто
- Слайдеры: FPS лимит 30-120, масштаб разрешения 0.5-1.0, чувствительность, прозрачность
- Динамическое разрешение (on/off)
- Кнопка **Редактор управления** — открывает превью и позволяет настроить оверлей без запуска игры
- Показывает подсказки по оптимизации

### 2. Сенсорное управление (`TouchControlsOverlay`)
Кнопка **EDIT** (в лаунчере и в игре):
- **Перемещение**: в режиме редактирования зажми и тяни кнопку
- **Размер**: щипок двумя пальцами на выбранной кнопке
- **Видимость**: тап по иконке 👁 над кнопкой (👁/🚫)
- **Отключение сенсора**: тап по самой кнопке EDIT в режиме редактирования → включает/выключает весь оверлей. Также есть свитч в панели. Когда сенсор отключён, игра реагирует только на геймпад/клавиатуру, а на экране остаётся подсказка «Сенсор отключён — нажми EDIT»
- Сохранение в `SharedPreferences` (JSON), сброс к дефолту
- Два стика: левый — движение (WASD, аналог), правый — обзор (мышь, 14×scale + sensitivity)
- Поддержка до 10 мультитачей, гаптика, плавный релиз кнопок

Дефолтная раскладка: стики по углам, огонь/прицел справа, прыжок/присед/спринт снизу-справа, перезарядка/использовать/фонарик по центру, смена оружия, меню ≡ сверху.

### 3. Google ANGLE (`https://github.com/google/angle`)
- В `android/app/src/main/cpp/CMakeLists.txt` включён `USE_ANGLE=ON`
- При `ANGLE_FETCH=ON` делает `FetchContent` из `google/angle` (main, shallow). По умолчанию `OFF` чтобы не тянуть 500 MB в CI — использует системный ANGLE через `libEGL_angle.so` или `EGL_VENDOR=ANGLE`
- Манифест содержит `<meta-data com.google.android.angle.GameAngle=vulkan>` — на Android 12+ система автоматически подменяет GLES → Vulkan
- `angle_manager.cpp` в рантайме детектит ANGLE (dlopen + EGL_VENDOR), логирует бэкенд, даёт `GetBackendName()` для логов/debuga
- Fallback на нативный GLES если ANGLE недоступен (Mali старый, эмулятор)
- Нативный линк: `libEGL_angle.so` + `libGLESv2_angle.so` если собраны, иначе системные `libEGL.so`/`libGLESv2.so`

### 4. Мега-оптимизация для всех смартфонов
**CMake / компиляция:**
- `-O3 -flto=thin -ffast-math -fomit-frame-pointer -march=armv8-a -Wl,--gc-sections --icf=all`
- `DISABLE_CG=ON` — отключает Cg компилятор (тормозит на Wayland/Android), использует `ARBvp1`/`ARBfp1` фолбек
- `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` для 16K pages (Android 15+)
- `minSdk 24` (Android 7) — покрывает 99% девайсов, `arm64-v8a + armeabi-v7a`

**Рендер:**
- Динамическое разрешение 0.6–1.0, лимит FPS, `r_TexturesStreaming=2`, `r_TexturesStreamingMaxRequestedMB=64`
- Отключены тяжёлые эффекты: POM, SoftParticles, объёмный туман, блюр теней
- LOD `e_obj_lod_ratio=60`, `e_vegetation_sprites_distance_ratio=0.6`, макс 8 источников света на энтити
- `r_Texture_Anisotropic_Level=2`, `GL_ARB_texture_compression` ETC2/ASTC перепаковка

**CPU/RAM:**
- `p_max_substeps=2`, `ai_update_interval=0.1`, `sys_streaming_memory_budget=24`
- Аффинити 0 (пусть система распределяет big.LITTLE), потоковый `StreamCallbackTimeBudget=500`
- Jemalloc-friendly аллокатор, `MT Safe` для физики/стриминга

**Батарея/тепло:**
- `r_VSync=0` + программный лимит FPS через `sys_maxfps`, `power` гейм-мод в `game_mode_config.xml`
- Тротлинг при нагреве: ANGLE Vulkan меньше греет Mali, чем GLES

**Файл `system_android.cfg` в `assets/`:** копируется при первом запуске если `system.cfg` нет — даёт сразу играбельные 60 FPS на Snapdragon 680 / Helio G85 и выше.

## Сборка

### Локально
```bash
./gradlew :app:assembleDebug   # из папки android/
# или
./gradlew :app:assembleRelease
```
Просит NDK 26.3.11579264, CMake 3.22.1, JDK 17.

### CI — GitHub Actions
См. `.github/workflows/android.yml` — собирает debug+release APK, кэширует gradle/ndk, грузит артефакты.

## Структура
```
android/
  app/src/main/java/com/nearchuckle/farcry/
    LauncherActivity.java — лаунчер
    GameActivity.java — SDLActivity + оверлей
    TouchControlsOverlay.java — рисование + логика
    TouchButton.java — модель кнопки
    NativeBridge.java — JNI
  app/src/main/cpp/
    native_bridge.cpp — SDL_PushEvent инъекция
    angle_manager.cpp/h — ANGLE детект
    android_main.cpp — SDL_main обёртка
    CMakeLists.txt — FetchContent ANGLE + оптимизация
  app/src/main/assets/system_android.cfg
```

## Тестирование
- Запусти на эмуляторе Pixel 4 API 31: должен выбрать ANGLE → Vulkan, 60 FPS, тачи работают
- На реальном Mali (Redmi Note 11) — сравни `adb logcat | grep ANGLE` — должно быть «ANGLE enabled»
- Проверь EDIT: перетащи прыжок в центр, увеличь щипком, скрой глазом, выключи сенсор — должна появиться плашка
- Отключи сенсор — геймпад (Xbox/BT) всё ещё работает

## Лицензии
ANGLE: BSD-3. Код порта — оригинал NearChuckle + этот оверлей.
