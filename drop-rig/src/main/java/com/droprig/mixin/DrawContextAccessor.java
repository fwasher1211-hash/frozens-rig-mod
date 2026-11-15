package com.droprig.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Gives the GUI access to the frame's render state so it can submit its own vector geometry. */
@Mixin(DrawContext.class)
public interface DrawContextAccessor {
    @Accessor("state")
    GuiRenderState droprig$getState();
}
