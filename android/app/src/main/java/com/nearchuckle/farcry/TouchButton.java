package com.nearchuckle.farcry;

import org.json.JSONObject;

/**
 * Модель одной сенсорной кнопки.
 * x,y — нормализованные координаты центра (0..1, относительно ширины/высоты экрана)
 * radius — радиус в долях min(screenW, screenH) (0.03 .. 0.12)
 * keyCode — XKEY_* (например XKEY_W, XKEY_MOUSE1)
 * visible — показывать ли
 * alpha — прозрачность 0..1 (умножается на глобальную)
 * label — текст на кнопке, icon — имя drawable
 */
public class TouchButton {
    public String id;
    public String label;
    public String iconName;
    public float x, y;
    public float radius;
    public int keyCode;
    public boolean visible;
    public float alpha;
    // для стиков: type
    public String type; // "button" | "stick_move" | "stick_look" | "trigger"

    public TouchButton(String id, String label, String iconName, float x, float y, float radius, int keyCode, String type) {
        this.id = id;
        this.label = label;
        this.iconName = iconName;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.keyCode = keyCode;
        this.type = type;
        this.visible = true;
        this.alpha = 1.0f;
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("label", label);
            o.put("icon", iconName);
            o.put("x", x);
            o.put("y", y);
            o.put("r", radius);
            o.put("key", keyCode);
            o.put("vis", visible);
            o.put("alpha", alpha);
            o.put("type", type);
            return o;
        } catch (Exception e) { return new JSONObject(); }
    }

    public static TouchButton fromJson(JSONObject o) {
        try {
            TouchButton b = new TouchButton(
                    o.optString("id"),
                    o.optString("label"),
                    o.optString("icon","circle"),
                    (float)o.optDouble("x",0.5),
                    (float)o.optDouble("y",0.5),
                    (float)o.optDouble("r",0.06),
                    o.optInt("key", 0),
                    o.optString("type","button")
            );
            b.visible = o.optBoolean("vis", true);
            b.alpha = (float)o.optDouble("alpha",1.0);
            return b;
        } catch (Exception e) { return null; }
    }

    public float distance(float nx, float ny) {
        float dx = nx - x;
        float dy = ny - y;
        return (float)Math.sqrt(dx*dx + dy*dy);
    }

    public boolean hitTest(float nx, float ny) {
        return distance(nx, ny) <= radius * 1.15f;
    }
}
