package com.vclient.mod.mixin;

import com.vclient.mod.Module;
import com.vclient.mod.NightSky;
import com.vclient.mod.VConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the rotating panorama behind the title screen and other menus with the night sky. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderPanoramaBackground(Lnet/minecraft/client/gui/DrawContext;F)V",
            at = @At("HEAD"), cancellable = true)
    private void vclient$renderNightSky(DrawContext context, float delta, CallbackInfo info) {
        if (!VConfig.enabled(Module.MENU_BACKGROUND)) return;
        NightSky.render(context);
        info.cancel();
    }
}
