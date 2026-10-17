package com.vclient.mod.mixin;

import com.vclient.mod.VClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While Freecam is on, mouse movement turns the free camera instead of the player. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "changeLookDirection(DD)V", at = @At("HEAD"), cancellable = true)
    private void vclient$freecamLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo info) {
        if (VClientMod.freecamActive() && (Object) this == MinecraftClient.getInstance().player) {
            VClientMod.freecamLook(cursorDeltaX, cursorDeltaY);
            info.cancel();
        }
    }
}
