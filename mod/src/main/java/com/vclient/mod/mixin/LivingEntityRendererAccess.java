package com.vclient.mod.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Gives the cosmetics layer a way to register itself on a player renderer. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccess {
    @Invoker("addFeature")
    boolean vclient$addFeature(FeatureRenderer<?, ?> feature);
}
