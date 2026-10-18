package com.vclient.mod.mixin;

import com.vclient.mod.VClientMod;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anti Knockback: drops the server's velocity update when it is about the local player. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onEntityVelocityUpdate(Lnet/minecraft/network/packet/s2c/play/EntityVelocityUpdateS2CPacket;)V",
            at = @At("HEAD"), cancellable = true)
    private void vclient$noKnockback(EntityVelocityUpdateS2CPacket packet, CallbackInfo info) {
        if (VClientMod.ignoreKnockback(packet.getEntityId())) info.cancel();
    }
}
