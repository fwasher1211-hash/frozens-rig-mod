package com.droprig.gui.render;

/**
 * Anti-aliased vector shapes built from coloured triangles: rounded rectangles, borders, soft
 * shadows, glows, lines and arcs. Every edge gets a one-pixel feather so nothing looks jagged at
 * any GUI scale.
 *
 * <p>This class has no Minecraft dependencies; it emits triangles to a {@link Sink}. In game the
 * sink collects them into one GUI render element for the whole panel.
 */
public final class Gfx {

    /** Receives triangles, three vertices at a time. */
    public interface Sink {
        void vertex(float x, float y, int argb);
    }

    private Sink sink;
    /** Size of one physical pixel in layout units (controls feather width and curve detail). */
    public float px = 0.5f;
    /** Opacity multiplier applied to everything drawn (used for fades). */
    public float alpha = 1f;
    /** Vertical offset applied to everything drawn (used for slide-in). */
    public float dy = 0f;

    private final float[] ca = new float[256];
    private final float[] cb = new float[256];
    private final float[] cc = new float[256];
    private final float[] cd = new float[256];

    public void begin(Sink sink, float px) {
        this.sink = sink;
        this.px = Math.max(0.05f, px);
        this.alpha = 1f;
        this.dy = 0f;
    }

    public void end() {
        this.sink = null;
    }

    // ------------------------------------------------------------------ primitives

    private void v(float x, float y, int c) {
        sink.vertex(x, y + dy, Col.mulA(c, alpha));
    }

    private void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        if (((c1 | c2 | c3) >>> 24) == 0) return; // fully transparent
        v(x1, y1, c1);
        v(x2, y2, c2);
        v(x3, y3, c3);
    }

    private void quad(float x1, float y1, int c1, float x2, float y2, int c2,
                      float x3, float y3, int c3, float x4, float y4, int c4) {
        tri(x1, y1, c1, x2, y2, c2, x3, y3, c3);
        tri(x1, y1, c1, x3, y3, c3, x4, y4, c4);
    }

    private int segments(float r) {
        if (r <= 0.01f) return 1;
        int s = (int) Math.ceil(r / px / 3.2f);
        return Math.max(3, Math.min(12, s));
    }

    /** Fills {@code out} with the outline of a rounded rectangle; returns the point count. */
    private static int contour(float x, float y, float w, float h, float r, int segs, float[] out) {
        w = Math.max(w, 0);
        h = Math.max(h, 0);
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
        float[][] corners = {
                {x + w - r, y + r, -90},
                {x + w - r, y + h - r, 0},
                {x + r, y + h - r, 90},
                {x + r, y + r, 180},
        };
        int n = 0;
        for (float[] c : corners) {
            for (int i = 0; i <= segs; i++) {
                double ang = Math.toRadians(c[2] + 90.0 * i / segs);
                out[n++] = c[0] + (float) Math.cos(ang) * r;
                out[n++] = c[1] + (float) Math.sin(ang) * r;
            }
        }
        return n / 2;
    }

    private void ring(float[] in, Paint pin, boolean inClear, float[] out, Paint pout, boolean outClear, int n) {
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ax = in[i * 2], ay = in[i * 2 + 1], bx = in[j * 2], by = in[j * 2 + 1];
            float cx = out[j * 2], cy = out[j * 2 + 1], dx = out[i * 2], dy2 = out[i * 2 + 1];
            int c1 = pin.at(ax, ay), c2 = pin.at(bx, by), c3 = pout.at(cx, cy), c4 = pout.at(dx, dy2);
            if (inClear) {
                c1 = Col.clear(c1);
                c2 = Col.clear(c2);
            }
            if (outClear) {
                c3 = Col.clear(c3);
                c4 = Col.clear(c4);
            }
            quad(ax, ay, c1, bx, by, c2, cx, cy, c3, dx, dy2, c4);
        }
    }

    // ------------------------------------------------------------------ shapes

    /** Filled rounded rectangle with an anti-aliased edge. */
    public void rect(float x, float y, float w, float h, float r, Paint p) {
        if (w <= 0 || h <= 0) return;
        int s = segments(r);
        int n = contour(x, y, w, h, r, s, ca);
        float f = px;
        contour(x - f * 0.5f, y - f * 0.5f, w + f, h + f, r + f * 0.5f, s, cb);
        float mx = x + w / 2, my = y + h / 2;
        int cm = p.at(mx, my);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            tri(mx, my, cm, ca[i * 2], ca[i * 2 + 1], p.at(ca[i * 2], ca[i * 2 + 1]),
                    ca[j * 2], ca[j * 2 + 1], p.at(ca[j * 2], ca[j * 2 + 1]));
        }
        ring(ca, p, false, cb, p, true, n);
    }

    public void rect(float x, float y, float w, float h, float r, int color) {
        rect(x, y, w, h, r, Paint.solid(color));
    }

    /** Border of thickness {@code t} drawn inside the rectangle's edge. */
    public void border(float x, float y, float w, float h, float r, float t, Paint p) {
        if (w <= 0 || h <= 0 || t <= 0) return;
        t = Math.min(t, Math.min(w, h) / 2f);
        int s = segments(r);
        float f = px * 0.5f;
        int n = contour(x, y, w, h, r, s, ca);
        contour(x + t, y + t, w - 2 * t, h - 2 * t, Math.max(0, r - t), s, cb);
        contour(x - f, y - f, w + 2 * f, h + 2 * f, r + f, s, cc);
        contour(x + t + f, y + t + f, w - 2 * (t + f), h - 2 * (t + f), Math.max(0, r - t - f), s, cd);
        ring(ca, p, false, cb, p, false, n);
        ring(cc, p, true, ca, p, false, n);
        ring(cb, p, false, cd, p, true, n);
    }

    public void border(float x, float y, float w, float h, float r, float t, int color) {
        border(x, y, w, h, r, t, Paint.solid(color));
    }

    /**
     * Soft shadow / glow around a rounded rectangle. {@code color} is the strength at the edge; it
     * fades to nothing over {@code spread}. Only the outside is painted, so it works under glass.
     */
    public void shadow(float x, float y, float w, float h, float r, float spread, int color, float offsetY) {
        if (spread <= 0 || (color >>> 24) == 0) return;
        // The inner edge hugs the shape itself (never paints inside it); only the outer falloff is
        // shifted by offsetY, so the shadow reaches further below than above.
        offsetY = Math.max(-spread * 0.8f, Math.min(spread * 0.8f, offsetY));
        int s = segments(r + spread);
        int n = contour(x, y, w, h, r, s, ca);
        float m = spread * 0.38f;
        contour(x - m, y - m + offsetY * 0.4f, w + 2 * m, h + 2 * m, r + m, s, cb);
        contour(x - spread, y - spread + offsetY, w + 2 * spread, h + 2 * spread, r + spread, s, cc);
        int mid = Col.mulA(color, 0.34f);
        ring(ca, Paint.solid(color), false, cb, Paint.solid(mid), false, n);
        ring(cb, Paint.solid(mid), false, cc, Paint.solid(color), true, n);
    }

    public void circle(float cx, float cy, float radius, int color) {
        rect(cx - radius, cy - radius, radius * 2, radius * 2, radius, Paint.solid(color));
    }

    public void circle(float cx, float cy, float radius, Paint p) {
        rect(cx - radius, cy - radius, radius * 2, radius * 2, radius, p);
    }

    /** Soft radial glow (elliptical), strongest in the middle. */
    public void glow(float cx, float cy, float rx, float ry, int color) {
        if ((color >>> 24) == 0) return;
        int segs = 36;
        float[] rings = {0f, 0.35f, 0.7f, 1f};
        float[] alphas = {1f, 0.62f, 0.22f, 0f};
        for (int k = 0; k < rings.length - 1; k++) {
            int ci = Col.mulA(color, alphas[k]), co = Col.mulA(color, alphas[k + 1]);
            if (k == rings.length - 2) co = Col.clear(color);
            for (int i = 0; i < segs; i++) {
                double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                float ir = rings[k], or = rings[k + 1];
                if (ir == 0) {
                    tri(cx, cy, ci, cx + c0 * rx * or, cy + s0 * ry * or, co, cx + c1 * rx * or, cy + s1 * ry * or, co);
                } else {
                    quad(cx + c0 * rx * ir, cy + s0 * ry * ir, ci, cx + c1 * rx * ir, cy + s1 * ry * ir, ci,
                            cx + c1 * rx * or, cy + s1 * ry * or, co, cx + c0 * rx * or, cy + s0 * ry * or, co);
                }
            }
        }
    }

    /** Anti-aliased straight line with flat ends. */
    public void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1, dy2 = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy2 * dy2);
        if (len < 1e-4f) return;
        float nx = -dy2 / len, ny = dx / len;
        float h = Math.max(thickness, px) / 2f, f = h + px * 0.75f;
        int clear = Col.clear(color);
        quad(x1 + nx * h, y1 + ny * h, color, x2 + nx * h, y2 + ny * h, color,
                x2 - nx * h, y2 - ny * h, color, x1 - nx * h, y1 - ny * h, color);
        quad(x1 + nx * f, y1 + ny * f, clear, x2 + nx * f, y2 + ny * f, clear,
                x2 + nx * h, y2 + ny * h, color, x1 + nx * h, y1 + ny * h, color);
        quad(x1 - nx * h, y1 - ny * h, color, x2 - nx * h, y2 - ny * h, color,
                x2 - nx * f, y2 - ny * f, clear, x1 - nx * f, y1 - ny * f, clear);
    }

    /** Chevron made of two lines meeting at the tip; {@code dir} −1 points left, +1 right, 0 is a bar. */
    public void chevronH(float cx, float cy, float halfW, float halfH, float dir, float thickness, int color) {
        float tipX = cx + halfW * dir, backX = cx - halfW * dir;
        line(backX, cy - halfH, tipX, cy, thickness, color);
        line(tipX, cy, backX, cy + halfH, thickness, color);
    }

    /** Chevron pointing up ({@code dir}=+1) or down (−1). */
    public void chevronV(float cx, float cy, float halfW, float halfH, float dir, float thickness, int color) {
        float tipY = cy - halfH * dir, backY = cy + halfH * dir;
        line(cx - halfW, backY, cx, tipY, thickness, color);
        line(cx, tipY, cx + halfW, backY, thickness, color);
    }

    /** Anti-aliased arc between two angles (radians, clockwise on screen). */
    public void arc(float cx, float cy, float r, float a0, float a1, float thickness, int color) {
        int segs = Math.max(8, (int) Math.ceil(Math.abs(a1 - a0) * r / px / 2.5f));
        segs = Math.min(segs, 64);
        float h = Math.max(thickness, px) / 2f, f = h + px * 0.75f;
        int clear = Col.clear(color);
        for (int i = 0; i < segs; i++) {
            float t0 = a0 + (a1 - a0) * i / segs, t1 = a0 + (a1 - a0) * (i + 1) / segs;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            quad(cx + c0 * (r + h), cy + s0 * (r + h), color, cx + c1 * (r + h), cy + s1 * (r + h), color,
                    cx + c1 * (r - h), cy + s1 * (r - h), color, cx + c0 * (r - h), cy + s0 * (r - h), color);
            quad(cx + c0 * (r + f), cy + s0 * (r + f), clear, cx + c1 * (r + f), cy + s1 * (r + f), clear,
                    cx + c1 * (r + h), cy + s1 * (r + h), color, cx + c0 * (r + h), cy + s0 * (r + h), color);
            quad(cx + c0 * (r - h), cy + s0 * (r - h), color, cx + c1 * (r - h), cy + s1 * (r - h), color,
                    cx + c1 * (r - f), cy + s1 * (r - f), clear, cx + c0 * (r - f), cy + s0 * (r - f), clear);
        }
    }

    public void triangle(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        tri(x1, y1, color, x2, y2, color, x3, y3, color);
    }

    /** Hairline that fades out at both ends. */
    public void fadeLineH(float x, float y, float w, int color) {
        float t = px;
        int clear = Col.clear(color);
        float mid = x + w / 2;
        quad(x, y, clear, mid, y, color, mid, y + t, color, x, y + t, clear);
        quad(mid, y, color, x + w, y, clear, x + w, y + t, clear, mid, y + t, color);
    }

    public void fadeLineV(float x, float y, float h, int color) {
        float t = px;
        int clear = Col.clear(color);
        float mid = y + h / 2;
        quad(x, y, clear, x + t, y, clear, x + t, mid, color, x, mid, color);
        quad(x, mid, color, x + t, mid, color, x + t, y + h, clear, x, y + h, clear);
    }
}
