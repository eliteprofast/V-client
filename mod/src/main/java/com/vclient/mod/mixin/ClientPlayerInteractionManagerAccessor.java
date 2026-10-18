package com.vclient.mod.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Fast Break clear the delay between breaking blocks. */
@Mixin(ClientPlayerInteractionManager.class)
public interface ClientPlayerInteractionManagerAccessor {
    @Accessor("blockBreakingCooldown")
    void vclient$setBlockBreakingCooldown(int cooldown);
}
