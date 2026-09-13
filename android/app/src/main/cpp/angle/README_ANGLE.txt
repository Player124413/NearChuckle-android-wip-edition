ANGLE libs in android/app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libEGL_angle.so etc
are REAL builds from https://github.com/google/angle, built ONLY in CI.

CI WORKFLOW (.github/workflows/android.yml) builds REAL ANGLE and NO stubs are vendored:
  - clones depot_tools (chromium/tools/depot_tools)
  - fetch angle (--no-history) from https://chromium.googlesource.com/angle/angle (mirror of https://github.com/google/angle)
  - gclient sync with target_os=["android"] WITH hooks (clang toolchain)
  - gn gen out/Android_arm64 + out/Android_arm  with target_os="android" target_cpu="arm64"/"arm" is_debug=false is_component_build=false angle_enable_vulkan=true arm_control_flow_integrity="none" android_ndk_root=".../ndk/26.3.11579264"
  - autoninja -C out/Android_* libEGL libGLESv2
  - STRICT check: file+readelf must be aarch64 (arm64) / ARM (armeabi), else ::error
  - cp out/Android_arm64/libEGL.so -> android/app/src/main/jniLibs/arm64-v8a/libEGL_angle.so (and GLESv2)
  - cp out/Android_arm/libEGL.so   -> android/app/src/main/jniLibs/armeabi-v7a/libEGL_angle.so
  - cached in ~/depot_tools + ~/angle (cache key angle-depot-v8) to avoid re-fetch
  - APK verification: unzip -lv *angle* + readelf -h inside APK

LOCAL BUILD (no stubs):
  Repository no longer contains placeholder .so (deleted per user request).
  jniLibs/*/*.so are created ONLY by CI step above. For local build without network:
    - either run same depot_tools steps manually, OR
    - set ANGLE_FETCH=ON in android/app/src/main/cpp/CMakeLists.txt to FetchContent from https://github.com/google/angle (will download ~500MB at configure time)
    - OR rely on system ANGLE on Android 12+ via com.google.android.angle / GraphicsEnvironment (AngleManager dlopen fallback to native GLES if no ANGLE lib in APK)

Verification:
  CI step "Verify ANGLE libs before Gradle (strict)" prints file+readelf and fails if not aarch64/ARM.
  CI step "Verify APK" prints unzip -lv + readelf.
  ANGLE build logs always uploaded as artifact angle-logs-* (gn/ninja/gclient/fetch).
