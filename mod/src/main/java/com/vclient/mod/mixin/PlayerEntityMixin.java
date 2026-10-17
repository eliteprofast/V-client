package com.vclient.mod.mixin;

import com.vclient.mod.VClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reach-Hits lengthens how far the local player can target and hit entities. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "getEntityInteractionRange()D", at = @At("RETURN"), cancellable = true)
    private void vclient$reach(CallbackInfoReturnable<Double> info) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            info.setReturnValue(Math.max(info.getReturnValue(), VClientMod.reachDistance()));
        }
    }
}
