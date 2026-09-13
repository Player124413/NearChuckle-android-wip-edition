package com.nearchuckle.farcry;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Central settings storage for launcher + game.
 * All values are persisted via SharedPreferences and forwarded to native via NativeBridge.
 */
public class SettingsManager {
    private static final String PREF = "nearchuckle_prefs";
    private static SettingsManager instance;
    private final SharedPreferences sp;

    // Keys
    public static final String KEY_GAME_FOLDER = "game_folder";
    public static final String KEY_RENDERER = "renderer"; // gles/auto
    public static final String KEY_FPS_LIMIT = "fps_limit";
    public static final String KEY_RES_SCALE = "res_scale";
    public static final String KEY_DYN_RES = "dyn_res";
    public static final String KEY_TOUCH_ENABLED = "touch_enabled";
    public static final String KEY_TOUCH_SENS = "touch_sens";
    public static final String KEY_TOUCH_OPACITY = "touch_opacity";
    public static final String KEY_TOUCH_SCALE = "touch_scale";

    private SettingsManager(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized SettingsManager get(Context c) {
        if (instance == null) instance = new SettingsManager(c);
        return instance;
    }

    public String getGameFolder() { return sp.getString(KEY_GAME_FOLDER, "/storage/emulated/0/FarCry"); }
    public void setGameFolder(String v) { sp.edit().putString(KEY_GAME_FOLDER, v).apply(); }

    public String getRenderer() {
        String v = sp.getString(KEY_RENDERER, "auto");
        // ANGLE removed — migrate legacy preference to native GLES
        if ("angle".equals(v)) { setRenderer("gles"); return "gles"; }
        return v;
    }
    public void setRenderer(String v) { sp.edit().putString(KEY_RENDERER, v).apply(); }

    public int getFpsLimit() { return sp.getInt(KEY_FPS_LIMIT, 60); }
    public void setFpsLimit(int v) { sp.edit().putInt(KEY_FPS_LIMIT, v).apply(); }

    public float getResScale() { return sp.getFloat(KEY_RES_SCALE, 1.0f); }
    public void setResScale(float v) { sp.edit().putFloat(KEY_RES_SCALE, v).apply(); }

    public boolean isDynRes() { return sp.getBoolean(KEY_DYN_RES, true); }
    public void setDynRes(boolean v) { sp.edit().putBoolean(KEY_DYN_RES, v).apply(); }

    public boolean isTouchEnabled() { return sp.getBoolean(KEY_TOUCH_ENABLED, true); }
    public void setTouchEnabled(boolean v) { sp.edit().putBoolean(KEY_TOUCH_ENABLED, v).apply(); }

    public float getTouchSens() { return sp.getFloat(KEY_TOUCH_SENS, 1.0f); }
    public void setTouchSens(float v) { sp.edit().putFloat(KEY_TOUCH_SENS, v).apply(); }

    public float getTouchOpacity() { return sp.getFloat(KEY_TOUCH_OPACITY, 0.6f); }
    public void setTouchOpacity(float v) { sp.edit().putFloat(KEY_TOUCH_OPACITY, v).apply(); }

    public float getTouchScale() { return sp.getFloat(KEY_TOUCH_SCALE, 1.0f); }
    public void setTouchScale(float v) { sp.edit().putFloat(KEY_TOUCH_SCALE, v).apply(); }
}
