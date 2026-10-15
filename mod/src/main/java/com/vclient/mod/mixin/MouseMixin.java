package com.vclient.mod.mixin;

import com.vclient.mod.VClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While zooming, the scroll wheel changes the zoom level instead of the hotbar slot. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void vclient$scrollZoom(long window, double horizontal, double vertical, CallbackInfo info) {
        if (MinecraftClient.getInstance().currentScreen == null && VClientMod.onScroll(vertical)) info.cancel();
    }
}
