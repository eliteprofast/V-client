package com.vclient.mod.mixin;

import com.vclient.mod.VClientMod;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Freecam detaches the camera from the player. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V", at = @At("TAIL"))
    private void vclient$freecam(World world, Entity focused, boolean thirdPerson, boolean inverted,
                                 float tickProgress, CallbackInfo info) {
        if (!VClientMod.freecamActive()) return;
        Vec3d position = VClientMod.freecamPosition(tickProgress);
        setPos(position.x, position.y, position.z);
        setRotation(VClientMod.freecamYaw(), VClientMod.freecamPitch());
    }

    /** Draws the player's own model while the camera is away from it. */
    @Inject(method = "isThirdPerson()Z", at = @At("RETURN"), cancellable = true)
    private void vclient$showPlayer(CallbackInfoReturnable<Boolean> info) {
        if (VClientMod.freecamActive()) info.setReturnValue(true);
    }
}
