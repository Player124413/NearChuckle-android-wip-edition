package org.libsdl.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Minimal stub of SDL3 SDLActivity for lint / CI without real SDL3.
 * Real build will replace this via CMake FetchContent copying the real SDLActivity.java from
 * https://github.com/libsdl-org/SDL/blob/main/android-project/app/src/main/java/org/libsdl/app/SDLActivity.java
 * This stub allows gradle lint to pass and Java compilation to succeed when SDL3 not fetched.
 * In real device build, the real SDLActivity (with native load, lifecycle, etc) is used.
 */
public class SDLActivity extends Activity {
    protected ViewGroup mLayout;
    protected SurfaceView mSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLayout = new FrameLayout(this);
        mSurface = new SurfaceView(this);
        mLayout.addView(mSurface);
        setContentView(mLayout);
    }
    protected String[] getLibraries() { return new String[]{}; }
    protected String getMainFunction() { return "SDL_main"; }
    protected String[] getArguments() { return new String[]{}; }
    public static void initialize() {}
}
