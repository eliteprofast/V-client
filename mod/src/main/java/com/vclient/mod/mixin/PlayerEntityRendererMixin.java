package com.vclient.mod.mixin;

import com.vclient.mod.cosmetics.CosmeticsFeatureRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the cosmetics layer (hats, wings, animated pieces) to every player renderer. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRendererFactory$Context;Z)V",
            at = @At("RETURN"))
    private void vclient$addCosmetics(EntityRendererFactory.Context context, boolean slim, CallbackInfo info) {
        ((LivingEntityRendererAccess) (Object) this).vclient$addFeature(new CosmeticsFeatureRenderer(
                (FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) (Object) this));
    }
}
