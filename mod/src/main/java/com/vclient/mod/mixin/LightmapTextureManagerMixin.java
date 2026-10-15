package com.vclient.mod.mixin;

import com.vclient.mod.VClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Fullbright raises the brightness only where the lightmap reads it, so options.txt is never changed. */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
    @Redirect(method = "update(F)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"))
    private Object vclient$gamma(SimpleOption<?> option) {
        Object value = option.getValue();
        if (VClientMod.fullbright() && option == MinecraftClient.getInstance().options.getGamma()) {
            return VClientMod.fullbrightGamma();
        }
        return value;
    }
}
