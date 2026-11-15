package com.droprig.gui;

import com.droprig.gui.render.Gfx;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.jetbrains.annotations.Nullable;

/**
 * All of the panel's vector geometry for one frame, submitted to Minecraft's GUI renderer as a
 * single element (one batch). {@link Gfx} produces triangles; the GUI pipeline draws quads, so each
 * triangle is sent as a quad whose last corner repeats the third (the extra half is degenerate).
 */
final class ShapeElement implements SimpleGuiElementRenderState {
    private final float[] xy;
    private final int[] colors;
    private final int count;
    private final Matrix3x2f pose;
    private final ScreenRect bounds;

    ShapeElement(Collector c, Matrix3x2f pose, ScreenRect bounds) {
        this.xy = c.xy;
        this.colors = c.colors;
        this.count = c.count - c.count % 3;
        this.pose = pose;
        this.bounds = bounds;
    }

    @Override
    public void setupVertices(VertexConsumer vertices) {
        for (int i = 0; i < count; i += 3) {
            put(vertices, i);
            put(vertices, i + 1);
            put(vertices, i + 2);
            put(vertices, i + 2);
        }
    }

    private void put(VertexConsumer v, int i) {
        v.vertex(pose, xy[i * 2], xy[i * 2 + 1]).color(colors[i]);
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.empty();
    }

    @Override
    public @Nullable ScreenRect scissorArea() {
        return null;
    }

    @Override
    public @Nullable ScreenRect bounds() {
        return bounds;
    }

    /**
     * Receives {@link Gfx} triangles. Two of these are alternated frame to frame so the arrays a
     * submitted element points at are never overwritten before the GUI renderer has read them.
     */
    static final class Collector implements Gfx.Sink {
        float[] xy = new float[2 * 16384];
        int[] colors = new int[16384];
        int count;

        void reset() {
            count = 0;
        }

        @Override
        public void vertex(float x, float y, int argb) {
            if (count == colors.length) {
                xy = java.util.Arrays.copyOf(xy, xy.length * 2);
                colors = java.util.Arrays.copyOf(colors, colors.length * 2);
            }
            xy[count * 2] = x;
            xy[count * 2 + 1] = y;
            colors[count] = argb;
            count++;
        }
    }
}
