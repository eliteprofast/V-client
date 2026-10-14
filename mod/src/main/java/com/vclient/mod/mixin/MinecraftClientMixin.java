package com.vclient.mod.mixin;

import com.vclient.mod.VClientMod;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "tick()V", at = @At("TAIL"))
    private void vclient$tick(CallbackInfo info) {
        VClientMod.tick((MinecraftClient) (Object) this);
    }
}
