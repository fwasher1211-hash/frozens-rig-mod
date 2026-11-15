package com.droprig.gui.render;

/** Frame-rate independent smoothing plus easing curves. */
public final class Anim {
    private float value;

    public Anim(float initial) {
        this.value = initial;
    }

    public float get() {
        return value;
    }

    public void snap(float v) {
        value = v;
    }

    /** Moves toward {@code target}; {@code speed} ≈ how many "time constants" per second. */
    public float step(float target, float speed, float dt) {
        value += (target - value) * (1f - (float) Math.exp(-speed * dt));
        if (Math.abs(target - value) < 1e-4f) value = target;
        return value;
    }

    public static float clamp01(float t) {
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }

    public static float outCubic(float t) {
        t = clamp01(t);
        float u = 1 - t;
        return 1 - u * u * u;
    }

    public static float inCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float inOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    /** Ease-out with a gentle overshoot. */
    public static float outBack(float t) {
        t = clamp01(t);
        float c1 = 1.35f, c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
