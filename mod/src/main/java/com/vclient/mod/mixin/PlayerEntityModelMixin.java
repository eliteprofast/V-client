package com.vclient.mod.mixin;

import com.vclient.mod.cosmetics.Emotes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets an emote offset the local player's pose after the normal animation has been applied. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Inject(method = "method_17087(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
            at = @At("TAIL"))
    private void vclient$emote(BipedEntityRenderState state, CallbackInfo info) {
        if (!Emotes.active()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && state instanceof PlayerEntityRenderState player && player.id == client.player.getId()) {
            Emotes.pose((PlayerEntityModel) (Object) this);
        }
    }
}
