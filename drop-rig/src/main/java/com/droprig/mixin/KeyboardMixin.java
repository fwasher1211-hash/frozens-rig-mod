package com.droprig.mixin;

import com.droprig.DropRigClient;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Listens to raw key presses so Drop Rig's own (GUI-configurable) keybinds work. Never cancels input. */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method = "onKey(JILnet/minecraft/client/input/KeyInput;)V", at = @At("HEAD"))
    private void droprig$onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        DropRigClient.onRawKey(window, input.key(), action);
    }
}
