package com.droprig.gui.render;

/** Text backend. Positions are layout units; {@code cy} is the vertical centre of capital letters. */
public interface TextSink {
    float width(FontSpec font, String text);

    void draw(FontSpec font, String text, float x, float cy, int argb);

    /** Width including extra letter spacing. */
    default float width(FontSpec font, String text, float tracking) {
        if (tracking == 0 || text.length() < 2) return width(font, text);
        float w = 0;
        int n = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int len = Character.charCount(cp);
            w += width(font, text.substring(i, i + len));
            n++;
            i += len;
        }
        return w + tracking * (n - 1);
    }

    /** Draws with extra letter spacing. {@code align}: 0 = left, 0.5 = centre, 1 = right of {@code x}. */
    default void draw(FontSpec font, String text, float x, float cy, int argb, float tracking, float align) {
        if ((argb >>> 24) < 4 || text.isEmpty()) return;
        float start = x - width(font, text, tracking) * align;
        if (tracking == 0) {
            draw(font, text, start, cy, argb);
            return;
        }
        float cx = start;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int len = Character.charCount(cp);
            String ch = text.substring(i, i + len);
            draw(font, ch, cx, cy, argb);
            cx += width(font, ch) + tracking;
            i += len;
        }
    }
}
