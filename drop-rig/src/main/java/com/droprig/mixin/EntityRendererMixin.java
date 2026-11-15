package com.droprig.mixin;

import com.droprig.DropRigClient;
import com.droprig.rig.DisplayRigger;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skips rendering (model, nameplate and shadow) of a freshly dropped item for a few ticks while
 * Drop Rig waits for the other dropper, so a number is never shown and then swapped.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void droprig$holdFreshDrop(Entity entity, Frustum frustum, double x, double y, double z,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof ItemEntity item)) return;
        DisplayRigger rigger = DropRigClient.rigger();
        if (rigger != null && rigger.shouldHide(item)) cir.setReturnValue(false);
    }
}
