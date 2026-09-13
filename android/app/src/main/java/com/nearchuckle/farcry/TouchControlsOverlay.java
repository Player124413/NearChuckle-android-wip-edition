package com.nearchuckle.farcry;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.*;
import android.widget.Toast;
import org.json.*;

import java.util.*;

/**
 * Главный оверлей сенсорного управления.
 * Рисует кнопки, обрабатывает тачи, поддерживает режим редактирования (editMode):
 * - перетаскивание пальцем
 * - изменение размера щипком (pinch)
 * - видимость через чекбоксы в панели
 * - полное отключение через кнопку EDIT (глаз/переключатель)
 *
 * В нормальном режиме каждый тап мапится в XKEY_* через NativeBridge.
 */
public class TouchControlsOverlay extends View {

    // XKEY constants mirrored from IInput.h
    public static final int XKEY_W = 0x11;
    public static final int XKEY_A = 0x1E;
    public static final int XKEY_S = 0x1F;
    public static final int XKEY_D = 0x20;
    public static final int XKEY_SPACE = 0x39;
    public static final int XKEY_LCONTROL = 0x1D;
    public static final int XKEY_LSHIFT = 0x2A;
    public static final int XKEY_R = 0x13;
    public static final int XKEY_F = 0x21;
    public static final int XKEY_E = 0x12;
    public static final int XKEY_Q = 0x10;
    public static final int XKEY_G = 0x22;
    public static final int XKEY_TAB = 0x0F;
    public static final int XKEY_ESCAPE = 0x01;
    public static final int XKEY_1 = 0x02, XKEY_2=0x03, XKEY_3=0x04;
    public static final int XKEY_MOUSE1 = 0x00010000;
    public static final int XKEY_MOUSE2 = 0x00020000;
    public static final int XKEY_MAXIS_X = 0x000B0000;
    public static final int XKEY_MAXIS_Y = 0x000C0000;

    private static final String PREF_LAYOUT = "touch_layout_v2";
    private static final String PREF_TOUCH_ENABLED = "touch_enabled_overlay";

    private List<TouchButton> buttons = new ArrayList<>();
    private Map<Integer, TouchButton> pointerToButton = new HashMap<>();
    private Map<String, Boolean> buttonDownState = new HashMap<>();

    private boolean touchEnabled = true;
    private boolean editMode = false;
    private TouchButton editSelected = null;
    private float editStartX, editStartY;
    private float editOrigX, editOrigY;
    private float editOrigR;
    private int editActivePointer = -1;
    private float pinchStartDist = 0;

    // Paints
    private Paint buttonPaint, buttonStroke, textPaint, handlePaint, stickPaint, stickKnobPaint;
    private Paint editBgPaint;

    // Settings scaling
    private float globalOpacity = 0.6f;
    private float globalScale = 1.0f;
    private float sensitivity = 1.0f;

    // sticks state
    private Map<String, PointF> stickOffsets = new HashMap<>(); // id -> offset (-1..1)

    private OnEditModeListener editListener;

    public interface OnEditModeListener {
        void onEditModeChanged(boolean enabled);
        void onTouchEnabledChanged(boolean enabled);
        void onRequestSave();
    }

    public TouchControlsOverlay(Context ctx) { super(ctx); init(ctx); }
    public TouchControlsOverlay(Context ctx, AttributeSet a) { super(ctx, a); init(ctx); }

    private void init(Context ctx) {
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        buttonPaint.setColor(Color.argb(110, 255, 255, 255));
        buttonPaint.setStyle(Paint.Style.FILL);

        buttonStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        buttonStroke.setColor(Color.argb(180, 255, 193, 7));
        buttonStroke.setStyle(Paint.Style.STROKE);
        buttonStroke.setStrokeWidth(dp(2));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(dp(2), 0, 0, Color.BLACK);

        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setColor(Color.argb(220, 255, 64, 129));
        handlePaint.setStyle(Paint.Style.FILL);

        stickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stickPaint.setColor(Color.argb(80, 255, 255, 255));
        stickPaint.setStyle(Paint.Style.FILL);

        stickKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stickKnobPaint.setColor(Color.argb(180, 255, 193, 7));
        stickKnobPaint.setStyle(Paint.Style.FILL);

        editBgPaint = new Paint();
        editBgPaint.setColor(Color.argb(90, 0, 229, 255));

        loadLayout(ctx);
        // load global settings
        SharedPreferences sp = ctx.getSharedPreferences("nearchuckle_prefs", Context.MODE_PRIVATE);
        touchEnabled = sp.getBoolean(SettingsManager.KEY_TOUCH_ENABLED, true);
        globalOpacity = sp.getFloat(SettingsManager.KEY_TOUCH_OPACITY, 0.6f);
        globalScale = sp.getFloat(SettingsManager.KEY_TOUCH_SCALE, 1.0f);
        sensitivity = sp.getFloat(SettingsManager.KEY_TOUCH_SENS, 1.0f);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    // ===== Layout defaults & persistence =====

    public void loadLayout(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("nearchuckle_prefs", Context.MODE_PRIVATE);
        String json = sp.getString(PREF_LAYOUT, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                buttons.clear();
                for (int i=0;i<arr.length();i++) {
                    TouchButton b = TouchButton.fromJson(arr.getJSONObject(i));
                    if (b!=null) buttons.add(b);
                }
                if (!buttons.isEmpty()) { invalidate(); return; }
            } catch (Exception e) { e.printStackTrace(); }
        }
        // create default layout
        createDefaultLayout();
        saveLayout(ctx);
    }

    public void saveLayout(Context ctx) {
        try {
            JSONArray arr = new JSONArray();
            for (TouchButton b: buttons) arr.put(b.toJson());
            ctx.getSharedPreferences("nearchuckle_prefs", Context.MODE_PRIVATE)
                    .edit().putString(PREF_LAYOUT, arr.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void createDefaultLayout() {
        buttons.clear();
        // Левая зона: стик движения (аналог WASD)
        buttons.add(new TouchButton("stick_move","MOVE","stick", 0.13f, 0.72f, 0.11f, 0, "stick_move"));
        // Правая зона: стик обзора (аналог мыши)
        buttons.add(new TouchButton("stick_look","LOOK","eye", 0.86f, 0.68f, 0.11f, 0, "stick_look"));

        // Кнопки действий
        buttons.add(new TouchButton("btn_fire","🔫","fire", 0.86f, 0.50f, 0.07f, XKEY_MOUSE1, "button")); // огонь
        buttons.add(new TouchButton("btn_aim","🎯","aim", 0.74f, 0.62f, 0.055f, XKEY_MOUSE2, "button")); // прицел
        buttons.add(new TouchButton("btn_jump","⤒","jump", 0.90f, 0.88f, 0.06f, XKEY_SPACE, "button"));
        buttons.add(new TouchButton("btn_crouch","⬇","crouch", 0.78f, 0.88f, 0.055f, XKEY_LCONTROL, "button"));
        buttons.add(new TouchButton("btn_sprint","⚡","sprint", 0.68f, 0.88f, 0.05f, XKEY_LSHIFT, "button"));
        buttons.add(new TouchButton("btn_reload","↻","reload", 0.74f, 0.78f, 0.045f, XKEY_R, "button"));
        buttons.add(new TouchButton("btn_use","✋","use", 0.64f, 0.72f, 0.05f, XKEY_F, "button"));
        buttons.add(new TouchButton("btn_flash","🔦","flash", 0.64f, 0.58f, 0.04f, XKEY_Q, "button"));
        buttons.add(new TouchButton("btn_grenade","💣","gren", 0.60f, 0.45f, 0.04f, XKEY_G, "button"));
        buttons.add(new TouchButton("btn_prev","◀","prev", 0.50f, 0.90f, 0.04f, XKEY_1, "button"));
        buttons.add(new TouchButton("btn_next","▶","next", 0.56f, 0.90f, 0.04f, XKEY_2, "button"));
        // Центр-верх для меню
        buttons.add(new TouchButton("btn_menu","≡","menu", 0.50f, 0.08f, 0.045f, XKEY_ESCAPE, "button"));
        // Кнопка EDIT всегда видна (даже если touch отключен)
        TouchButton editBtn = new TouchButton("btn_edit","EDIT","edit", 0.08f, 0.08f, 0.042f, 0, "edit");
        editBtn.visible = true;
        buttons.add(editBtn);

        // init stick offsets
        stickOffsets.put("stick_move", new PointF(0,0));
        stickOffsets.put("stick_look", new PointF(0,0));
    }

    public List<TouchButton> getButtons() { return buttons; }

    public void setOnEditModeListener(OnEditModeListener l) { editListener = l; }

    public boolean isEditMode() { return editMode; }
    public void setEditMode(boolean v) {
        editMode = v;
        editSelected = null;
        invalidate();
        if (editListener!=null) editListener.onEditModeChanged(v);
        // в режиме редактирования не шлём игровые инпуты
        releaseAllButtons();
    }

    public boolean isTouchEnabled() { return touchEnabled; }
    public void setTouchEnabled(boolean v) {
        touchEnabled = v;
        getContext().getSharedPreferences("nearchuckle_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean(SettingsManager.KEY_TOUCH_ENABLED, v).apply();
        NativeBridge.setTouchEnabled(v);
        invalidate();
        if (editListener!=null) editListener.onTouchEnabledChanged(v);
    }

    public void toggleTouchEnabled() { setTouchEnabled(!touchEnabled); }

    public void setGlobalOpacity(float o) { globalOpacity=o; invalidate(); }
    public void setGlobalScale(float s) { globalScale=s; invalidate(); }
    public void setSensitivity(float s) { sensitivity=s; NativeBridge.setSensitivity(s); }

    public void resetLayout(Context ctx) {
        createDefaultLayout();
        saveLayout(ctx);
        invalidate();
        Toast.makeText(ctx, "Раскладка сброшена", Toast.LENGTH_SHORT).show();
    }

    // ===== Rendering =====

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (canvas==null) return;
        int w = getWidth(), h = getHeight();
        if (w==0||h==0) return;
        float minDim = Math.min(w,h);

        if (editMode) {
            canvas.drawColor(Color.argb(35, 0, 229, 255));
            // grid
            Paint grid = new Paint(); grid.setColor(Color.argb(30,255,255,255)); grid.setStrokeWidth(1);
            for (int i=1;i<10;i++) { canvas.drawLine(w*i/10f,0,w*i/10f,h,grid); canvas.drawLine(0,h*i/10f,w,h*i/10f,grid); }
        }

        for (TouchButton b: buttons) {
            if (!b.visible && !editMode) continue;
            // EDIT кнопка всегда рисуется даже если touch отключен
            boolean isEditBtn = "btn_edit".equals(b.id);
            if (!touchEnabled && !isEditBtn && !editMode) continue;

            float cx = b.x * w;
            float cy = b.y * h;
            float r = b.radius * minDim * globalScale;

            // alpha handling
            int alpha = (int)( (touchEnabled? 255: 80) * globalOpacity * b.alpha );
            if (editMode && b==editSelected) alpha = 255;

            if ("stick_move".equals(b.type) || "stick_look".equals(b.type)) {
                // outer
                stickPaint.setAlpha(editMode? 120 : (int)(80*globalOpacity));
                canvas.drawCircle(cx, cy, r, stickPaint);
                buttonStroke.setAlpha((int)(180*globalOpacity));
                canvas.drawCircle(cx, cy, r, buttonStroke);
                // knob
                PointF off = stickOffsets.get(b.id);
                if (off==null) off=new PointF(0,0);
                float kx = cx + off.x * r * 0.55f;
                float ky = cy + off.y * r * 0.55f;
                stickKnobPaint.setAlpha(alpha);
                canvas.drawCircle(kx, ky, r*0.35f, stickKnobPaint);
                // cross
                Paint lp = new Paint(); lp.setColor(Color.argb(60,255,255,255)); lp.setStrokeWidth(dp(1));
                canvas.drawLine(cx - r*0.5f, cy, cx + r*0.5f, cy, lp);
                canvas.drawLine(cx, cy - r*0.5f, cx, cy + r*0.5f, lp);
                // label
                textPaint.setTextSize(r*0.22f);
                textPaint.setAlpha(120);
                canvas.drawText(b.label, cx, cy + r + dp(10), textPaint);
                // edit handles
                if (editMode) drawEditHandles(canvas, cx, cy, r, b==editSelected);
            } else if ("edit".equals(b.type)) {
                // EDIT button distinct
                Paint ep = new Paint(Paint.ANTI_ALIAS_FLAG);
                ep.setColor(editMode? Color.argb(230, 255,64,129) : Color.argb((int)(200*globalOpacity), 0, 229, 255));
                ep.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, r, ep);
                Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
                sp.setColor(Color.WHITE); sp.setStyle(Paint.Style.STROKE); sp.setStrokeWidth(dp(2));
                canvas.drawCircle(cx, cy, r, sp);
                textPaint.setTextSize(r*0.45f);
                textPaint.setAlpha(255);
                canvas.drawText("EDIT", cx, cy + r*0.15f, textPaint);
                if (editMode) drawEditHandles(canvas, cx, cy, r, true);
            } else {
                // regular button
                int bg = isButtonDown(b.id) ? Color.argb(alpha, 255,193,7) : Color.argb((int)(alpha*0.5f), 255,255,255);
                if (editMode && !b.visible) bg = Color.argb(90, 255, 80, 80);
                buttonPaint.setColor(bg);
                canvas.drawCircle(cx, cy, r, buttonPaint);
                // stroke
                int strokeA = isButtonDown(b.id)? 255 : (int)(180*globalOpacity);
                if (editMode) strokeA=255;
                buttonStroke.setAlpha(strokeA);
                if (editMode && b==editSelected) buttonStroke.setColor(Color.argb(255,255,64,129));
                else buttonStroke.setColor(Color.argb(strokeA,255,193,7));
                canvas.drawCircle(cx, cy, r, buttonStroke);
                // text / icon
                textPaint.setAlpha(b.visible? 255 : 100);
                textPaint.setTextSize(r*0.48f);
                // эмодзи/текст
                String lab = b.label;
                // для компактности показываем только эмодзи или первую букву
                canvas.drawText(lab, cx, cy + r*0.16f, textPaint);
                // visibility indicator in edit mode
                if (editMode) {
                    // eye icon
                    Paint eye = new Paint(Paint.ANTI_ALIAS_FLAG);
                    eye.setColor(b.visible? Color.argb(220,255,255,255) : Color.argb(180,255,80,80));
                    eye.setTextSize(r*0.35f);
                    eye.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(b.visible? "👁" : "🚫", cx, cy - r - dp(6), eye);
                    drawEditHandles(canvas, cx, cy, r, b==editSelected);
                }
            }
        }

        // overlay label when disabled
        if (!touchEnabled && !editMode) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.argb(160,0,0,0));
            canvas.drawRoundRect(w/2f - dp(110), dp(10), w/2f + dp(110), dp(32), dp(12), dp(12), p);
            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG); tp.setColor(Color.WHITE); tp.setTextAlign(Paint.Align.CENTER); tp.setTextSize(dp(11));
            canvas.drawText("Сенсор отключён — нажми EDIT", w/2f, dp(24), tp);
        }

        if (editMode) {
            Paint inf = new Paint(Paint.ANTI_ALIAS_FLAG); inf.setColor(Color.argb(200,0,0,0));
            canvas.drawRoundRect(dp(6), h - dp(38), w - dp(6), h - dp(6), dp(10), dp(10), inf);
            Paint tp2 = new Paint(Paint.ANTI_ALIAS_FLAG); tp2.setColor(Color.WHITE); tp2.setTextSize(dp(10)); tp2.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("РЕДАКТИРОВАНИЕ: перетаскивай • щипок = размер • тап по 👁 = скрыть • EDIT = выкл. сенсор", w/2f, h - dp(16), tp2);
        }
    }

    private void drawEditHandles(Canvas c, float cx, float cy, float r, boolean selected) {
        // corner handles
        float hs = dp(5);
        Paint hp = selected? handlePaint : buttonStroke;
        c.drawCircle(cx + r, cy, hs, handlePaint);
        c.drawCircle(cx - r, cy, hs, handlePaint);
        c.drawCircle(cx, cy + r, hs, handlePaint);
        c.drawCircle(cx, cy - r, hs, handlePaint);
        if (selected) {
            Paint sel = new Paint(Paint.ANTI_ALIAS_FLAG); sel.setColor(Color.argb(40,255,64,129)); sel.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, r+ dp(6), sel);
        }
    }

    private boolean isButtonDown(String id) { return Boolean.TRUE.equals(buttonDownState.get(id)); }

    // ===== Input handling =====

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        int w = getWidth(), h = getHeight();
        if (w==0||h==0) return false;
        float minDim = Math.min(w,h);

        if (editMode) {
            return handleEditTouch(ev);
        }

        // Normal mode
        if (!touchEnabled) {
            // Only allow EDIT button
            if (action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
                int idx = ev.getActionIndex();
                float nx = ev.getX(idx)/w, ny = ev.getY(idx)/h;
                TouchButton edit = findButton("btn_edit");
                if (edit!=null && edit.hitTest(nx, ny)) {
                    // tap EDIT brings edit mode or re-enables
                    setEditMode(true);
                    // also need to consume?
                    return true;
                }
            }
            return false; // pass through to game (camera) maybe? But touch disabled => ignore
        }

        // handle multitouch
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = ev.getActionIndex();
                int pid = ev.getPointerId(idx);
                float nx = ev.getX(idx)/w, ny=ev.getY(idx)/h;
                TouchButton hit = hitTest(nx, ny, minDim);
                if (hit!=null) {
                    // edit button special
                    if ("btn_edit".equals(hit.id)) {
                        setEditMode(true);
                        return true;
                    }
                    pointerToButton.put(pid, hit);
                    if ("stick_move".equals(hit.type) || "stick_look".equals(hit.type)) {
                        updateStick(hit, nx, ny, w, h, minDim);
                    } else {
                        pressButton(hit, true);
                    }
                    invalidate();
                } else {
                    // No button hit => treat as look drag if in right half? Optionally pass raw motion to NativeBridge as mouse
                    // We'll map free screen drag to mouse look for immersive feel (when touching non-button area on right)
                    // Simple: if x > 0.5 and not hitting stick, generate mouse motion via stick_look fallback
                    // But to keep predictable, we ignore and let Game handle via SDL touch->mouse? We instead inject mouse motion via down move tracking
                    // Store as free look pointer
                    // We'll use a synthetic handling: if no button, we still track pointer for camera swipe
                    // Tag as null button but track motion delta in MOVE
                    pointerToButton.put(pid, null);
                    // store start pos for delta
                    // use tag in separate map?
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i=0;i<ev.getPointerCount();i++) {
                    int pid = ev.getPointerId(i);
                    TouchButton b = pointerToButton.get(pid);
                    float nx = ev.getX(i)/w, ny=ev.getY(i)/h;
                    if (b!=null) {
                        if ("stick_move".equals(b.type) || "stick_look".equals(b.type)) {
                            updateStick(b, nx, ny, w, h, minDim);
                        } else {
                            // check if still inside -> keep pressed, else release (slide off)
                            boolean inside = b.hitTest(nx, ny);
                            boolean wasDown = isButtonDown(b.id);
                            if (inside && !wasDown) pressButton(b, true);
                            else if (!inside && wasDown) pressButton(b, false);
                        }
                    } else {
                        // free look swipe: generate mouse delta from historical pos
                        // Need previous pos; use getHistorical? Simpler: compute delta via MotionEvent's history not reliable.
                        // We'll approximate: send small delta based on movement since last event.
                        // We need to track last pos per pointer; for brevity use raw X/Y diff stored in map.
                        // Implemented via lastFreePos map
                        PointF last = lastFreePos.get(pid);
                        if (last!=null) {
                            float dx = (ev.getX(i) - last.x) * sensitivity * 0.35f;
                            float dy = (ev.getY(i) - last.y) * sensitivity * 0.35f;
                            NativeBridge.sendMouseMotion(dx, dy);
                        }
                        lastFreePos.put(pid, new PointF(ev.getX(i), ev.getY(i)));
                    }
                }
                invalidate();
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                int idx = ev.getActionIndex();
                int pid = ev.getPointerId(idx);
                TouchButton b = pointerToButton.remove(pid);
                lastFreePos.remove(pid);
                if (b!=null) {
                    if ("stick_move".equals(b.type) || "stick_look".equals(b.type)) {
                        // reset stick
                        stickOffsets.put(b.id, new PointF(0,0));
                        if ("stick_move".equals(b.type)) {
                            // release WASD
                            releaseWASD();
                        } else {
                            // stop look
                        }
                        invalidate();
                        // send zero analog
                        NativeBridge.sendAnalogStick("stick_move".equals(b.type)?0:1, 0,0);
                    } else {
                        pressButton(b, false);
                    }
                }
                invalidate();
                break;
            }
        }
        return true;
    }

    private Map<Integer, PointF> lastFreePos = new HashMap<>();

    private void releaseWASD() {
        NativeBridge.sendKey(XKEY_W, false);
        NativeBridge.sendKey(XKEY_A, false);
        NativeBridge.sendKey(XKEY_S, false);
        NativeBridge.sendKey(XKEY_D, false);
        buttonDownState.put("W", false); buttonDownState.put("A",false); buttonDownState.put("S",false); buttonDownState.put("D",false);
    }

    private void updateStick(TouchButton b, float nx, float ny, int w, int h, float minDim) {
        float cx = b.x, cy = b.y;
        float dx = (nx - cx) * w / (b.radius * minDim * globalScale);
        float dy = (ny - cy) * h / (b.radius * minDim * globalScale);
        // clamp to unit circle
        float len = (float)Math.sqrt(dx*dx + dy*dy);
        if (len > 1.0f) { dx/=len; dy/=len; len=1.0f; }
        stickOffsets.put(b.id, new PointF(dx, dy));
        // map to input
        if ("stick_move".equals(b.type)) {
            // WASD analog: threshold 0.25
            boolean wDown = dy < -0.25f;
            boolean sDown = dy > 0.25f;
            boolean aDown = dx < -0.25f;
            boolean dDown = dx > 0.25f;
            // use pressButton logic via NativeBridge sendKey
            setKeyState(XKEY_W, wDown);
            setKeyState(XKEY_S, sDown);
            setKeyState(XKEY_A, aDown);
            setKeyState(XKEY_D, dDown);
            // also send analog for smoother speed (?) NativeBridge analog 0
            NativeBridge.sendAnalogStick(0, dx, -dy);
        } else {
            // look stick -> mouse motion delta scaled by distance from center * sensitivity
            // Instead of immediate delta, send continuous delta per frame? We send proportional mouse motion each move event.
            // Scale: dx * sens * 18, dy * sens * 18
            float mdx = dx * 14f * sensitivity;
            float mdy = dy * 14f * sensitivity;
            NativeBridge.sendMouseMotion(mdx, mdy);
            NativeBridge.sendAnalogStick(1, dx, dy);
        }
        invalidate();
    }

    private void setKeyState(int xkey, boolean down) {
        String k = String.valueOf(xkey);
        Boolean was = buttonDownState.get(k);
        if (was==null || was != down) {
            buttonDownState.put(k, down);
            NativeBridge.sendKey(xkey, down);
        }
    }

    private void pressButton(TouchButton b, boolean down) {
        buttonDownState.put(b.id, down);
        if (b.keyCode != 0) {
            if (b.keyCode == XKEY_MOUSE1 || b.keyCode == XKEY_MOUSE2) {
                int btn = (b.keyCode == XKEY_MOUSE1)?0:1;
                NativeBridge.sendMouseButton(btn, down);
                // also send key for input system that listens to mouse
                NativeBridge.sendKey(b.keyCode, down);
            } else {
                NativeBridge.sendKey(b.keyCode, down);
            }
        }
        // vibrate軽
        if (down) performHaptic();
        invalidate();
    }

    private void releaseAllButtons() {
        for (TouchButton b: buttons) {
            if (isButtonDown(b.id)) pressButton(b, false);
        }
        // also release WASD keys tracked via xkey string
        for (int k: new int[]{XKEY_W,XKEY_A,XKEY_S,XKEY_D,XKEY_SPACE,XKEY_LCONTROL,XKEY_LSHIFT,XKEY_R,XKEY_F,XKEY_Q,XKEY_G,XKEY_ESCAPE}) {
            String ks = String.valueOf(k);
            if (Boolean.TRUE.equals(buttonDownState.get(ks))) {
                buttonDownState.put(ks,false);
                NativeBridge.sendKey(k,false);
            }
        }
        // reset sticks
        stickOffsets.put("stick_move", new PointF(0,0));
        stickOffsets.put("stick_look", new PointF(0,0));
        NativeBridge.sendAnalogStick(0,0,0);
        NativeBridge.sendAnalogStick(1,0,0);
    }

    private void performHaptic() {
        try { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); } catch (Exception e){}
    }

    private TouchButton hitTest(float nx, float ny, float minDim) {
        // prioritize small buttons over sticks? Check sticks first with larger radius, but allow button on top
        // We'll iterate reverse (topmost last)
        for (int i=buttons.size()-1;i>=0;i--) {
            TouchButton b = buttons.get(i);
            if (!b.visible && !editMode) continue;
            // touch enabled check already done, but edit button always hittable
            if (!touchEnabled && !"btn_edit".equals(b.id) && !editMode) continue;
            if (b.hitTest(nx, ny)) return b;
        }
        return null;
    }

    private TouchButton findButton(String id) {
        for (TouchButton b: buttons) if (id.equals(b.id)) return b;
        return null;
    }

    // ===== Edit mode touch handling =====

    private boolean handleEditTouch(MotionEvent ev) {
        int action = ev.getActionMasked();
        int w=getWidth(), h=getHeight();
        float minDim = Math.min(w,h);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                float nx = ev.getX()/w, ny=ev.getY()/h;
                // check if tapping eye icon area to toggle visibility (top of button)
                for (TouchButton b: buttons) {
                    float cx = b.x*w, cy=b.y*h, r=b.radius*minDim*globalScale;
                    // eye hitbox: above button
                    if (Math.abs(ev.getX()-cx) < r && Math.abs(ev.getY()-(cy - r - dp(8))) < dp(14)) {
                        b.visible = !b.visible;
                        saveLayout(getContext());
                        invalidate();
                        Toast.makeText(getContext(), b.label + (b.visible? " показан":" скрыт"), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
                TouchButton hit = hitTest(nx, ny, minDim);
                if (hit!=null) {
                    editSelected = hit;
                    editStartX = ev.getX(); editStartY = ev.getY();
                    editOrigX = hit.x; editOrigY = hit.y; editOrigR = hit.radius;
                    editActivePointer = ev.getPointerId(0);
                    invalidate();
                    // if double-tap on selected -> toggle visibility as well
                    return true;
                } else {
                    // tap empty deselect
                    editSelected=null;
                    invalidate();
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (editSelected!=null && ev.getPointerCount()==2) {
                    pinchStartDist = dist(ev.getX(0), ev.getY(0), ev.getX(1), ev.getY(1));
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (editSelected!=null) {
                    if (ev.getPointerCount()==2) {
                        float curDist = dist(ev.getX(0), ev.getY(0), ev.getX(1), ev.getY(1));
                        if (pinchStartDist>10) {
                            float scale = curDist / pinchStartDist;
                            float newR = editOrigR * scale;
                            newR = Math.max(0.025f, Math.min(0.14f, newR));
                            editSelected.radius = newR;
                            invalidate();
                        }
                    } else if (ev.getPointerCount()==1) {
                        float dx = (ev.getX() - editStartX)/w;
                        float dy = (ev.getY() - editStartY)/h;
                        float nx = editOrigX + dx;
                        float ny = editOrigY + dy;
                        nx = Math.max(editSelected.radius*0.5f, Math.min(1 - editSelected.radius*0.5f, nx));
                        ny = Math.max(editSelected.radius*0.5f, Math.min(1 - editSelected.radius*0.5f, ny));
                        editSelected.x = nx;
                        editSelected.y = ny;
                        invalidate();
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (ev.getPointerCount()<=2) pinchStartDist=0;
                if (action==MotionEvent.ACTION_UP) {
                    // check if it was a tap on EDIT to toggle touch enabled
                    if (editSelected!=null && "btn_edit".equals(editSelected.id)) {
                        // single tap on EDIT while in edit mode -> toggle touch enabled
                        // need to detect tap vs drag: if move < 10dp it's tap
                        float dx = Math.abs(ev.getX() - editStartX);
                        float dy = Math.abs(ev.getY() - editStartY);
                        if (dx < dp(10) && dy < dp(10)) {
                            toggleTouchEnabled();
                            Toast.makeText(getContext(), touchEnabled? "Сенсор включён":"Сенсор отключён", Toast.LENGTH_SHORT).show();
                            // stay in edit mode but show feedback
                        }
                    }
                    saveLayout(getContext());
                    // keep selection
                }
                break;
            }
        }
        return true;
    }

    private float dist(float x1,float y1,float x2,float y2){ return (float)Math.hypot(x2-x1,y2-y1); }
}
