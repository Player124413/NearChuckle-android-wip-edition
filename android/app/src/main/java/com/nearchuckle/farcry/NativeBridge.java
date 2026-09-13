package com.nearchuckle.farcry;

import android.util.Log;

/**
 * JNI мост к нативному движку.
 * Java оверлей вызывает эти методы, а нативная часть форвардит в CryInput (CInput::PostInputEvent / FeedVirtualKey)
 * Если нативная либа ещё не загружена — вызовы тихо игнорируются (чтоб лаунчер не падал).
 */
public class NativeBridge {
    private static final String TAG = "NearChuckleBridge";
    private static boolean nativeLoaded = false;

    static {
        try {
            System.loadLibrary("FarCry"); // main executable as lib? Actually libCrySystem etc; we load wrapper
            // Try load native bridge lib
            System.loadLibrary("nearchuckle_android");
            nativeLoaded = true;
        } catch (Throwable t) {
            Log.w(TAG, "Native libs not yet available (launcher mode): " + t.getMessage());
            nativeLoaded = false;
        }
    }

    // Called from GameActivity to init JNI
    public static void ensureLoaded() {
        if (!nativeLoaded) {
            try {
                System.loadLibrary("nearchuckle_android");
                nativeLoaded = true;
            } catch (Throwable t) { Log.e(TAG, "ensureLoaded failed", t); }
        }
    }

    // Key events: XKEY_* codes (see IInput.h)
    public static void sendKey(int xkey, boolean down) {
        if (!nativeLoaded) { Log.d(TAG, "sendKey mock: " + xkey + " down=" + down); return; }
        try { nativeSendKey(xkey, down); } catch (Throwable t) { Log.e(TAG, "nativeSendKey fail", t); }
    }

    public static void sendMouseButton(int button, boolean down) {
        // button: 0=left 1=right 2=middle ...
        // maps to XKEY_MOUSE1 etc
        if (!nativeLoaded) return;
        try { nativeSendMouseButton(button, down); } catch (Throwable t) { Log.e(TAG, "nativeSendMouseButton fail", t); }
    }

    public static void sendMouseMotion(float dx, float dy) {
        if (!nativeLoaded) return;
        try { nativeSendMouseMotion(dx, dy); } catch (Throwable t) { Log.e(TAG, "nativeSendMouseMotion fail", t); }
    }

    public static void sendAnalogStick(int stickId, float x, float y) {
        // stickId 0=move, 1=look
        if (!nativeLoaded) return;
        try { nativeSendAnalog(stickId, x, y); } catch (Throwable t) { Log.e(TAG, "nativeSendAnalog fail", t); }
    }

    public static void setTouchEnabled(boolean enabled) {
        if (!nativeLoaded) return;
        try { nativeSetTouchEnabled(enabled); } catch (Throwable t) { Log.e(TAG, "nativeSetTouchEnabled fail", t); }
    }

    public static void setSensitivity(float sens) {
        if (!nativeLoaded) return;
        try { nativeSetSensitivity(sens); } catch (Throwable t) { Log.e(TAG, "nativeSetSensitivity fail", t); }
    }

    // JNI
    private static native void nativeSendKey(int xkey, boolean down);
    private static native void nativeSendMouseButton(int button, boolean down);
    private static native void nativeSendMouseMotion(float dx, float dy);
    private static native void nativeSendAnalog(int stickId, float x, float y);
    private static native void nativeSetTouchEnabled(boolean enabled);
    private static native void nativeSetSensitivity(float sens);

    // Called from native to query settings
    public static String nativeGetGameFolder(android.content.Context ctx) {
        return SettingsManager.get(ctx).getGameFolder();
    }
}
