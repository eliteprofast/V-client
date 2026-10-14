package com.vclient.mod.mixin;

import com.vclient.mod.VClientMod;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)F", at = @At("RETURN"), cancellable = true)
    private void vclient$zoom(Camera camera, float tickProgress, boolean changingFov,
                              CallbackInfoReturnable<Float> info) {
        info.setReturnValue(VClientMod.modifyFov(info.getReturnValue()));
    }
}
