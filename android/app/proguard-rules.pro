# Keep SDL and NativeBridge
-keep class org.libsdl.app.SDLActivity { *; }
-keep class org.libsdl.app.SDLAudioManager { *; }
-keep class org.libsdl.app.SDLControllerManager { *; }
-keep class com.nearchuckle.farcry.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn org.libsdl.app.**
# Optimization keep
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
