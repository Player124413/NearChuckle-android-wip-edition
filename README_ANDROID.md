# NearChuckle Android Port — Полный гайд

> **Этот файл — краткий гайд по Android-порту. Подробнее: `android/README_ANDROID.md` и `docs/OPTIMIZATION_ANDROID.md`.**

## Быстрый старт (для пользователя)

1. Установи APK из `Releases` или собери сам: `cd android && ./gradlew :app:assembleRelease`
2. Скопируй **полную** установку Far Cry PC (папка с `FCData`, `Levels`, `Shaders`) на телефон: `/storage/emulated/0/FarCry/`
   - Через USB: `adb push "C:/FarCry" /storage/emulated/0/FarCry`
3. Запусти **NearChuckle**, выбери папку, нажми **ИГРАТЬ**.

## Лаунчер

- **Папка**: SAF picker (Android 11+) или ручной путь. Кнопка *Проверить файлы* ищет `FCData`/`Levels`.
- **Рендер**: `ANGLE (Vulkan)` — рекомендуется (GLES→Vulkan via https://github.com/google/angle), `GLES` — fallback, `Авто` — выбирает сам.
- **FPS / Разрешение**: слайдеры, динамическое разрешение `on/off`.
- **Сенсор**: вкл/выкл, чувствительность, прозрачность, кнопка *Редактор управления*.

## Сенсорное управление + EDIT

В игре всегда есть круглая кнопка **EDIT** (синяя, левый верхний угол):

- **Тап EDIT → режим редактирования**:
  - Перетаскивай любую кнопку пальцем
  - Щипок двумя пальцами на выбранной кнопке — меняет размер (0.025–0.14 от экрана)
  - Тап по 👁 над кнопкой — скрывает/показывает её (🚫 = скрыта)
  - Повторный тап по самой кнопке **EDIT** — полностью **отключает/включает сенсорный оверлей** (панель внизу тоже имеет переключатель *Отключить сенсор*).
- **Вне режима**: кнопки шлют `XKEY_*`/`mouse` через `NativeBridge` → `SDL_PushEvent` → `CryInput`. Два стика: левый — `WASD`, правый — `mouse look` с учётом `sensitivity`.
- **Когда сенсор отключён**: на экране полупрозрачная плашка «Сенсор отключён — нажми EDIT», खेल продолжает работать с геймпадом/клавиатурой. Нажатие EDIT возвращает оверлей.
- Настройки сохраняются в `SharedPreferences` (JSON) и выживают перезапуск.

## ANGLE

Используется либа **https://github.com/google/angle** — трансляция GLES→Vulkan для стабильности на Mali/Adreno/PowerVR.

- В `android/app/src/main/cpp/CMakeLists.txt`: `option(USE_ANGLE ON)`, `FetchContent` опционально.
- В `AndroidManifest.xml`: `<meta-data android:name="com.google.android.angle.GameAngle" android:value="vulkan" />`
- `angle_manager.cpp` детектит `libEGL_angle.so` и `EGL_VENDOR==ANGLE`, логирует бэкенд.
- Реальные `libEGL_angle.so`/`libGLESv2_angle.so` собираются в CI из исходников ANGLE
  (depot_tools + `ensure_bootstrap` + `gclient sync` c `target_os=["android"]` + `gn gen`/`autoninja`,
  GN-арги как у официального ANGLE CI). Никаких заглушек: если сборка ANGLE падает — job красный.
  Подробности: `android/app/src/main/cpp/angle/README_ANGLE.txt`.

## Мега-оптимизация

- `-O3 -flto=thin --gc-sections --icf=all -ffast-math -march=armv8-a`
- `DISABLE_CG=ON` (нет `libCg.so` на Android), потоковый LOD, лимит FPS, текстуры `ETC2/ASTC`, `system_android.cfg` с пресетами для 2–6 GB RAM.
- Подробнее: `docs/OPTIMIZATION_ANDROID.md`.

## Сборка APK

### GitHub Actions (проверено, без ошибок)

Workflow `.github/workflows/android.yml`:
- `setup-java 17`, `setup-android`, `ndk 26.3.11579264`, `cmake 3.22.1`
- Кэш `gradle` и `ANGLE`
- Автоскачивание `gradle-wrapper.jar` если отсутствует
- Сборка `assembleDebug` + `assembleRelease`, проверка `aapt2 dump`, загрузка артефактов.

Статус: [![CI Android](https://github.com/Player124413/NearChuckle-android-wip-edition/actions/workflows/android.yml/badge.svg)](../../actions/workflows/android.yml)

### Локально

```bash
cd android
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
# Установить:
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat | grep -E "ANGLE|NearChuckle"
```

Требования: JDK 17, Android SDK 34, NDK 26.3.11579264, CMake 3.22.1.

## Устранение ошибок

- `CG_LIB_PATH is not set` → на Android не должен спрашивать: `DISABLE_CG=ON` форсится в `CMakeLists.txt`.
- `FCData not found` → проверь путь в лаунчере; на Android 11+ дай разрешение *Все файлы*.
- Чёрный экран → `adb logcat | grep -i egl` — смотри выбрался ли ANGLE; попробуй переключить на `GLES`.
- Лагает → включи *Динамическое разрешение*, снизь масштаб до 0.7, лимит 30 FPS.

