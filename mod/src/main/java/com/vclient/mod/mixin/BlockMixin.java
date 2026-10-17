package com.vclient.mod.mixin;

import com.vclient.mod.Module;
import com.vclient.mod.VConfig;
import com.vclient.mod.Xray;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** X-Ray draws every face of ores and valuables and hides the faces of all other blocks. */
@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "shouldDrawSide(Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Direction;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void vclient$xray(BlockState state, BlockState neighbor, Direction side,
                                     CallbackInfoReturnable<Boolean> info) {
        if (VConfig.enabled(Module.XRAY)) info.setReturnValue(Xray.isVisible(state.getBlock()));
    }
}
