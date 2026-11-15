package com.droprig.gui.render;

/** Colour as a function of position (used for gradients). Colours are interpolated between vertices. */
@FunctionalInterface
public interface Paint {
    int at(float x, float y);

    static Paint solid(int c) {
        return (x, y) -> c;
    }

    static Paint vertical(float y0, float h, int top, int bottom) {
        return (x, y) -> Col.lerp(top, bottom, h <= 0 ? 0 : (y - y0) / h);
    }

    static Paint horizontal(float x0, float w, int left, int right) {
        return (x, y) -> Col.lerp(left, right, w <= 0 ? 0 : (x - x0) / w);
    }

    /** Top-left to bottom-right. */
    static Paint diagonal(float x0, float y0, float w, float h, int from, int to) {
        return (x, y) -> Col.lerp(from, to, ((x - x0) / Math.max(w, 1e-3f) + (y - y0) / Math.max(h, 1e-3f)) * 0.5f);
    }

    /** A highlight that sweeps around a centre point; {@code phase} in radians. */
    static Paint sweep(float cx, float cy, float phase, int dim, int bright) {
        return (x, y) -> {
            double ang = Math.atan2(y - cy, x - cx);
            float k = (float) (0.5 + 0.5 * Math.cos(ang - phase));
            return Col.lerp(dim, bright, k * k * k);
        };
    }
}
