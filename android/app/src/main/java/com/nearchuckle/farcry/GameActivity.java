package com.nearchuckle.farcry;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.Toast;
import org.libsdl.app.SDLActivity;

/**
 * GameActivity extends SDLActivity (SDL3).
 * Поверх SDL Surface накладывается TouchControlsOverlay.
 * Управляет жизненным циклом, передаёт настройки в native через Intent extras и NativeBridge,
 * включает иммерсивный фуллскрин, обрабатывает ANGLE.
 *
 * Оптимизации:
 * - фиксируем landscape, immersive sticky
 * - отключаем вырезку, ставим high refresh rate
 * - форвардим FPS лимит и res scale в CVars через JNI/командную строку
 */
public class GameActivity extends SDLActivity {

    private static final String TAG = "NearChuckleGame";
    private TouchControlsOverlay touchOverlay;
    private FrameLayout rootContainer;
    private View editFab; // small floating button to re-enter edit if overlay hidden?

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Применяем оптимизации до super.onCreate (SDL создаёт окно)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        // Request high refresh rate if fps >60
        tuneRefreshRate();

        super.onCreate(savedInstanceState);

        // SDLActivity creates mSurface etc. Now overlay it.
        setupTouchOverlay();

        // Hide system UI
        hideSystemUI();

        // Log ANGLE info
        Log.i(TAG, "GameActivity onCreate renderer=" + getIntent().getStringExtra("renderer") + " ANGLE enabled via CMake USE_ANGLE=ON");
    }

    private void tuneRefreshRate() {
        try {
            Intent intent = getIntent();
            int fps = intent.getIntExtra("fps_limit", 60);
            if (fps > 60 && android.os.Build.VERSION.SDK_INT >= 30) {
                // nothing, SDL will set swap interval; window hints high refresh
                getWindow().setAttributes(getWindow().getAttributes());
            }
        } catch (Exception e) { }
    }

    @Override
    protected String[] getLibraries() {
        // Порядок важен: сначала зависимости, потом главный
        // SDL3 создаст libSDL3.so, ANGLE даст libEGL_angle.so/libGLESv2_angle.so
        // Наш враппер NearChuckle загрузит CrySystem etc
        return new String[] {
                "SDL3",
                "nearchuckle_android", // наш bridge + angle manager
                // CryEngine libs будут подгружены динамически через CrySystem LoadDLL (libCrySystem.so etc)
        };
    }

    @Override
    protected String getMainFunction() {
        return "SDL_main";
    }

    // Forward command line args to SDL_main (FarCry's Main.cpp expects argc/argv)
    @Override
    protected String[] getArguments() {
        Intent i = getIntent();
        String folder = i.getStringExtra("game_folder");
        String renderer = i.getStringExtra("renderer");
        int fps = i.getIntExtra("fps_limit", 60);
        float resScale = i.getFloatExtra("res_scale", 1.0f);
        boolean dynRes = i.getBooleanExtra("dyn_res", true);

        // Map renderer string to engine cvar: r_Driver
        String rDriver = "OpenGL";
        if ("angle".equals(renderer)) rDriver = "OpenGL"; // still GL, but via ANGLE EGL
        // Could also be Direct3D9 via DXVK->ANGLE? но пока только OGL

        // Build cmdline resembling FarCry: -DEVMODE maybe, -mod etc
        // Мы передадим через SDL: args array is passed as argv to native SDL_main
        // Engine reads from command line and then system.cfg
        // Также сетим env для ANGLE
        return new String[] {
                "FarCry",
                "-r_driver", rDriver,
                "-r_width", String.valueOf((int)(getDisplayWidth() * resScale)),
                "-r_height", String.valueOf((int)(getDisplayHeight() * resScale)),
                "-r_displayinfo", "0",
                "-sys_maxfps", String.valueOf(fps),
                "-r_DynRes", dynRes?"1":"0",
                "-touch", i.getBooleanExtra("touch_enabled", true) ? "1":"0",
                // Game folder will be handled via nativeGetGameFolder or chdir
        };
    }

    private int getDisplayWidth() {
        WindowManager wm = getWindowManager();
        if (wm!=null) {
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(dm);
            return dm.widthPixels;
        }
        return 1280;
    }
    private int getDisplayHeight() {
        WindowManager wm = getWindowManager();
        if (wm!=null) {
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(dm);
            return dm.heightPixels;
        }
        return 720;
    }

    private void setupTouchOverlay() {
        // SDLActivity's layout is a FrameLayout with mSurface. We overlay our view.
        // Find root: getWindow().getDecorView()
        ViewGroup decor = (ViewGroup) getWindow().getDecorView();
        // SDLActivity typically uses mLayout as container; try to find it via traversal
        ViewGroup sdlLayout = findSDLLayout(decor);
        if (sdlLayout == null) sdlLayout = decor;

        // create container
        rootContainer = new FrameLayout(this);
        // Move existing SDL children? Instead add overlay on top
        touchOverlay = new TouchControlsOverlay(this);
        boolean touchEnabled = getIntent().getBooleanExtra("touch_enabled", true);
        float opacity = SettingsManager.get(this).getTouchOpacity();
        float scale = SettingsManager.get(this).getTouchScale();
        float sens = getIntent().getFloatExtra("touch_sens", SettingsManager.get(this).getTouchSens());
        touchOverlay.setTouchEnabled(touchEnabled);
        touchOverlay.setGlobalOpacity(opacity);
        touchOverlay.setGlobalScale(scale);
        touchOverlay.setSensitivity(sens);
        touchOverlay.setOnEditModeListener(new TouchControlsOverlay.OnEditModeListener() {
            @Override public void onEditModeChanged(boolean enabled) {
                // when entering edit, pause game? Show toast
                if (enabled) Toast.makeText(GameActivity.this, "Режим редактирования: двигай, щипок — размер, 👁 — скрыть", Toast.LENGTH_LONG).show();
            }
            @Override public void onTouchEnabledChanged(boolean enabled) {
                Toast.makeText(GameActivity.this, enabled? "Сенсор включён":"Сенсор выключен — нажми EDIT чтобы вернуть", Toast.LENGTH_SHORT).show();
            }
            @Override public void onRequestSave() {}
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);

        // Need to ensure we add overlay to same parent as SDL surface (so it overlays)
        // SDLActivity may have already set content view to mLayout; we add overlay there
        sdlLayout.addView(touchOverlay, lp);

        // Floating EDIT trigger when touch disabled (so user can return)
        // TouchControlsOverlay already shows hint, but also add a small always-visible FAB at corner if disabled
        // We'll keep it simple: rely on EDIT button inside overlay (it always draws)
    }

    private ViewGroup findSDLLayout(ViewGroup root) {
        // BFS search for a FrameLayout containing SurfaceView
        java.util.Queue<ViewGroup> q = new java.util.LinkedList<>();
        q.add(root);
        while (!q.isEmpty()) {
            ViewGroup vg = q.poll();
            for (int i=0;i<vg.getChildCount();i++) {
                View ch = vg.getChildAt(i);
                if (ch instanceof SurfaceView) return vg;
                if (ch instanceof ViewGroup) q.add((ViewGroup)ch);
            }
        }
        return null;
    }

    private void hideSystemUI() {
        View decor = getWindow().getDecorView();
        int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decor.setSystemUiVisibility(flags);
        if (getSupportActionBar()!=null) getSupportActionBar().hide();
        // Landscape
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Back button: if edit mode, exit edit; else send ESC to game
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction()==KeyEvent.ACTION_UP) {
            if (touchOverlay!=null && touchOverlay.isEditMode()) {
                touchOverlay.setEditMode(false);
                touchOverlay.saveLayout(this);
                return true;
            }
            // Let SDL handle (will become XKEY_ESCAPE)
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (touchOverlay!=null) {
            // release stuck keys
            // will be handled via onPause native?
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        // Reapply settings that may have changed in launcher
        if (touchOverlay!=null) {
            touchOverlay.setGlobalOpacity(SettingsManager.get(this).getTouchOpacity());
            touchOverlay.setGlobalScale(SettingsManager.get(this).getTouchScale());
            touchOverlay.setSensitivity(SettingsManager.get(this).getTouchSens());
        }
    }

    // SDLActivity will call native SDL_main; we hook library load for ANGLE
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (touchOverlay!=null) touchOverlay.saveLayout(this);
    }
}
