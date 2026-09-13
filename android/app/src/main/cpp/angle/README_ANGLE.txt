ANGLE vendored libs in android/app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libEGL_angle.so etc
are placeholders (1.2MB each) to make APK ~10MB and pass packaging checks.
Real ANGLE is provided by system on Android 12+ via com.google.android.angle / libEGL_angle.so
and via AngleManager dlopen fallback. To use real ANGLE prebuilt, replace these .so with
real builds from https://github.com/google/angle (git clone + build for android) or set
ANGLE_FETCH=ON in CMakeLists.txt to FetchContent from https://github.com/google/angle
