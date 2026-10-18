package com.vclient.mod.mixin;

import com.vclient.mod.VClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Freecam's mouse look, plus the Glow ESP outline and Hitboxes margin of the hacker-mode modules. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isGlowing()Z", at = @At("RETURN"), cancellable = true)
    private void vclient$glowEsp(CallbackInfoReturnable<Boolean> info) {
        if (!info.getReturnValue() && VClientMod.glowEsp((Entity) (Object) this)) info.setReturnValue(true);
    }

    @Inject(method = "getTargetingMargin()F", at = @At("RETURN"), cancellable = true)
    private void vclient$hitboxes(CallbackInfoReturnable<Float> info) {
        float extra = VClientMod.hitboxMargin((Entity) (Object) this);
        if (extra > 0) info.setReturnValue(info.getReturnValue() + extra);
    }

    @Inject(method = "changeLookDirection(DD)V", at = @At("HEAD"), cancellable = true)
    private void vclient$freecamLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo info) {
        if (VClientMod.freecamActive() && (Object) this == MinecraftClient.getInstance().player) {
            VClientMod.freecamLook(cursorDeltaX, cursorDeltaY);
            info.cancel();
        }
    }
}
