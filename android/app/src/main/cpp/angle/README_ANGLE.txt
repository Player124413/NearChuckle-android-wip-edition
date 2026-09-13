ANGLE libs in android/app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libEGL_angle.so + libGLESv2_angle.so
are REAL builds from https://github.com/google/angle, built ONLY in CI (no stubs, no placeholders).

WHY PREVIOUS BUILDS ALWAYS FAILED (root cause, fixed 2026-09-13):
  1. `gn`/`autoninja` are depot_tools shell wrappers that `exec python3`, and that
     python3 wrapper hard-requires `python3_bin_reldir.txt` + `.cipd_bin` — files that
     are created ONLY by `ensure_bootstrap` / `update_depot_tools`. The CI set
     DEPOT_TOOLS_UPDATE=0 (and ANGLE's own DEPS hook disables auto-update as well),
     so depot_tools was never bootstrapped and EVERY `gn` invocation died with
     "python3_bin_reldir.txt not found. need to initialize depot_tools by running
     gclient, update_depot_tools or ensure_bootstrap." Meanwhile `gclient`/`fetch`
     kept working (they go through `vpython3`, which self-bootstraps .cipd_bin) —
     that is why sync succeeded while gn/ninja never ran even once.
     FIX: the workflow now runs `~/depot_tools/ensure_bootstrap` explicitly after
     cloning and hard-checks `python3 --version` / `ninja --version` before syncing.
  2. Output names: on Android ANGLE builds SUFFIXED libs by default
     (gni/angle.gni: angle_libs_suffix defaults to "_angle"), i.e. the build produces
     out/<dir>/libEGL_angle.so and libGLESv2_angle.so WITH matching SONAMEs. The old
     script looked for libEGL.so / libGLESv2.so (wrong names — they never exist for
     Android builds) and would have failed even after fixing (1).
  3. GN args: the old script passed unnecessary/obsolete args (android_ndk_root /
     android_sdk_root pointing at the system NDK instead of the DEPS-managed
     toolchain, arm_control_flow_integrity). Official ANGLE CI android builders use
     just target_os="android" + target_cpu (see infra/config/gn_args.star in the
     ANGLE repo). The NDK (r30, third_party/android_toolchain/ndk), the Android SDK
     and clang are all downloaded by `gclient sync` from DEPS when
     target_os = ["android"] is set in .gclient.

CI WORKFLOW (.github/workflows/android.yml), step "Build ANGLE from source":
  - clones depot_tools (chromium.googlesource.com) and runs ./ensure_bootstrap (THE FIX)
  - writes .gclient with a PINNED revision (env ANGLE_REVISION, currently main@2026-09-12)
    and target_os = ["android"]; wipes the cached checkout automatically when the pin changes
  - gclient sync -D --no-history (DEPS + hooks: chromium clang, Android NDK/SDK via CIPD)
  - gn gen out/Android_arm64 / out/Android_arm with:
      target_os="android" target_cpu="arm64"|"arm" [arm_version=7]
      is_debug=false is_component_build=false symbol_level=0
      use_remoteexec=false angle_enable_vulkan=true
  - autoninja -C out/Android_* libEGL libGLESv2
  - STRICT check via readelf: Machine must be AArch64 (arm64-v8a) / ARM (armeabi-v7a),
    size >= 1MB — otherwise the job FAILS (no more placeholder fallbacks)
  - cp out/Android_arm64/libEGL_angle.so  -> jniLibs/arm64-v8a/libEGL_angle.so (same for GLESv2 + armeabi-v7a)
  - ~/depot_tools + ~/angle cached (key angle-depot-*-v9); compiler intermediates
    (out/*/obj) are deleted before save to stay under the 10 GB cache limit; valid
    cached .so files are verified (readelf) and reused without rebuilding
  - APK verification: unzip -lv *angle* + readelf inside the APK (step "Verify APK")
  - build logs (gn/ninja/gclient/final jniLibs) are pushed to the `ci-logs` branch
    and uploaded as artifact angle-logs-* on every run

LOCAL BUILD (no stubs):
  The repository does not contain any prebuilt .so. jniLibs/*/*.so are produced ONLY
  by the CI step above. For a local build either run the same depot_tools steps
  manually, or grab libEGL_angle.so/libGLESv2_angle.so from a CI artifact
  (NearChuckle-Android-* APK or angle-logs-* job artifacts) and drop them into
  android/app/src/main/jniLibs/<abi>/.

RUNTIME:
  AngleManager (android/app/src/main/cpp/angle_manager.cpp) dlopen()s
  libEGL_angle.so before any GL use; on Android the ANGLE EGL library loads its GLES
  counterpart (libGLESv2_angle.so) via its ANGLE_DISPATCH_LIBRARY soname. If loading
  fails, the app gracefully falls back to the system GLES driver. USE_ANGLE=1 is now
  defined in both source and prebuilt modes (previously the prebuilt path compiled
  USE_ANGLE=0, so the bundled libs were never even attempted).
