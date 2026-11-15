package com.droprig.gui;

import com.droprig.DropRigClient;
import com.droprig.gui.render.FontSpec;
import com.droprig.gui.render.TextSink;
import net.minecraft.client.font.TextDrawable;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Text backend for the in-game screen. Text is queued while the shapes are built and submitted
 * after them, so it always sits on top, using the mod's TrueType fonts at sub-pixel positions.
 *
 * <p>Vertical centring uses the real glyph bounds of "H" in each font, measured with
 * {@link TextRenderer#prepare} whenever the screen initialises.
 */
final class McText implements TextSink {
    private record Op(FontSpec font, String text, float x, float cy, int color) {
    }

    private final List<Op> ops = new ArrayList<>();
    private final Map<FontSpec, Style> styles = new EnumMap<>(FontSpec.class);
    private final Map<FontSpec, float[]> capBounds = new EnumMap<>(FontSpec.class);
    private final Map<FontSpec, Map<String, Float>> widths = new EnumMap<>(FontSpec.class);
    private TextRenderer renderer;

    McText() {
        for (FontSpec f : FontSpec.values()) {
            styles.put(f, Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of(DropRigClient.MOD_ID, f.id))));
            widths.put(f, new HashMap<>());
        }
    }

    /** Measures glyph bounds; call when the screen (re)initialises. */
    void calibrate(TextRenderer tr) {
        this.renderer = tr;
        for (Map<String, Float> m : widths.values()) m.clear();
        for (FontSpec f : FontSpec.values()) {
            float[] bounds = measureCapBounds(tr, f);
            if (bounds == null) {
                // Fallback: baseline one ascender below the draw origin.
                float baseline = f.size * f.ascRatio;
                bounds = new float[]{baseline - f.size * f.capRatio, baseline};
            }
            capBounds.put(f, bounds);
        }
    }

    private float[] measureCapBounds(TextRenderer tr, FontSpec f) {
        try {
            float[] mm = {Float.MAX_VALUE, -Float.MAX_VALUE};
            TextRenderer.GlyphDrawable drawable = tr.prepare(styled(f, "H").asOrderedText(), 0f, 0f, 0xFFFFFFFF, false, false, 0);
            drawable.draw(new TextRenderer.GlyphDrawer() {
                @Override
                public void drawGlyph(TextDrawable.DrawnGlyphRect glyph) {
                    mm[0] = Math.min(mm[0], glyph.getEffectiveMinY());
                    mm[1] = Math.max(mm[1], glyph.getEffectiveMaxY());
                }
            });
            float h = mm[1] - mm[0];
            if (h > f.size * 0.4f && h < f.size * 1.3f) return mm;
        } catch (Throwable t) {
            DropRigClient.LOG.debug("Glyph measurement failed for {}", f.id, t);
        }
        return null;
    }

    private MutableText styled(FontSpec f, String s) {
        return Text.literal(s).setStyle(styles.get(f));
    }

    @Override
    public float width(FontSpec font, String text) {
        if (renderer == null) return text.length() * font.size * 0.55f;
        Map<String, Float> cache = widths.get(font);
        Float w = cache.get(text);
        if (w == null) {
            w = renderer.getTextHandler().getWidth(styled(font, text));
            if (cache.size() > 512) cache.clear();
            cache.put(text, w);
        }
        return w;
    }

    @Override
    public void draw(FontSpec font, String text, float x, float cy, int argb) {
        if ((argb >>> 24) < 4 || text.isEmpty()) return;
        ops.add(new Op(font, text, x, cy, argb));
    }

    void begin() {
        ops.clear();
    }

    /** Submits queued text. Call inside the same matrix scope the layout was drawn in. */
    void flush(DrawContext ctx) {
        if (renderer == null || ops.isEmpty()) return;
        Matrix3x2fStack m = ctx.getMatrices();
        for (Op op : ops) {
            float[] b = capBounds.get(op.font());
            float y = op.cy() - (b[0] + b[1]) / 2f;
            m.pushMatrix();
            m.translate(op.x(), y);
            ctx.drawText(renderer, styled(op.font(), op.text()), 0, 0, op.color(), false);
            m.popMatrix();
        }
        ops.clear();
    }
}
