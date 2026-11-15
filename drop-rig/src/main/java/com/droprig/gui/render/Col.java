package com.droprig.gui.render;

/** ARGB colour helpers. */
public final class Col {
    private Col() {
    }

    public static int a(int c) {
        return c >>> 24;
    }

    public static int r(int c) {
        return (c >> 16) & 0xFF;
    }

    public static int g(int c) {
        return (c >> 8) & 0xFF;
    }

    public static int b(int c) {
        return c & 0xFF;
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    /** Same colour, alpha multiplied by {@code mul}. */
    public static int mulA(int c, float mul) {
        if (mul >= 1f) return c;
        if (mul <= 0f) return c & 0x00FFFFFF;
        return (Math.round(a(c) * mul) << 24) | (c & 0x00FFFFFF);
    }

    /** Same RGB with an absolute alpha in 0..1. */
    public static int alpha(int c, float alpha) {
        return (clamp(Math.round(alpha * 255f)) << 24) | (c & 0x00FFFFFF);
    }

    public static int clear(int c) {
        return c & 0x00FFFFFF;
    }

    public static int lerp(int c1, int c2, float t) {
        if (t <= 0f) return c1;
        if (t >= 1f) return c2;
        return argb(
                Math.round(a(c1) + (a(c2) - a(c1)) * t),
                Math.round(r(c1) + (r(c2) - r(c1)) * t),
                Math.round(g(c1) + (g(c2) - g(c1)) * t),
                Math.round(b(c1) + (b(c2) - b(c1)) * t));
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }
}
