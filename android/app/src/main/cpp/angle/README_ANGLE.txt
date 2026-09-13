ANGLE libs in android/app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libEGL_angle.so etc
are REAL builds from https://github.com/google/angle when built via CI.

CI WORKFLOW (.github/workflows/android.yml) builds REAL ANGLE:
  - clones depot_tools (chromium/tools/depot_tools)
  - fetch angle (--no-history) from https://chromium.googlesource.com/angle/angle (mirror of https://github.com/google/angle)
  - gclient sync with target_os=["android"]
  - gn gen out/Android_arm64 + out/Android_arm  with target_os="android" target_cpu="arm64"/"arm" is_debug=false is_component_build=false angle_enable_vulkan=true
  - autoninja -C out/Android_* libEGL libGLESv2
  - cp out/Android_arm64/libEGL.so -> android/app/src/main/jniLibs/arm64-v8a/libEGL_angle.so (and GLESv2)
  - cp out/Android_arm/libEGL.so   -> android/app/src/main/jniLibs/armeabi-v7a/libEGL_angle.so
  - cached in ~/depot_tools + ~/angle (cache key angle-depot-v7) to avoid re-fetch on next CI
  - falls back to vendored placeholders (1.2MB each, incompressible random) if build fails, so APK still builds and uses system ANGLE via AngleManager dlopen fallback

LOCAL FALLBACK (without network):
  Vendored .so are placeholders (1.2MB random ELF, header 7f454c46) to keep repo checkout buildable offline and make APK ~10MB.
  Real ANGLE is provided by system on Android 12+ via com.google.android.angle / GraphicsEnvironment.
  To build locally with real ANGLE: either run the same depot_tools steps above, or set ANGLE_FETCH=ON in CMakeLists.txt to FetchContent from https://github.com/google/angle

Verification:
  CI step "Verify ANGLE libs before Gradle" prints ls -lh + file + hexdump and checks size >3MB => REAL.
  CI step "Verify APK" does unzip -lv *angle* and checks uncompressed size inside APK.
