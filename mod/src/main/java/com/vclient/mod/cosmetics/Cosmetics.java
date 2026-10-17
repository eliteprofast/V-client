package com.vclient.mod.cosmetics;

import com.vclient.mod.Module;
import com.vclient.mod.Setting;
import com.vclient.mod.VConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * Entry point of the cosmetics system: start-up, the per-tick work (trails, emote and menu keys) and the
 * hooks the mixins call. Cosmetics only change how the local player looks on this client.
 */
public final class Cosmetics {
    private static boolean menuKeyWasDown;
    private static boolean emoteKeyWasDown;

    private Cosmetics() {}

    public static void init() {
        CosmeticState.load();
        CosmeticCatalog.init();
        SkinFix.init();
    }

    /** Called at the end of every client tick. */
    public static void tick(MinecraftClient client) {
        if (client.player == null) {
            Emotes.stop();
            return;
        }
        boolean inGame = client.currentScreen == null;
        boolean menuDown = inGame && keyDown(client, VConfig.integer(Module.COSMETICS, "menuKey"));
        if (menuDown && !menuKeyWasDown) openMenu(client, null);
        menuKeyWasDown = menuDown;

        boolean emoteDown = inGame && keyDown(client, VConfig.integer(Module.COSMETICS, "emoteKey"));
        if (emoteDown && !emoteKeyWasDown && CosmeticState.enabled()) {
            if (Emotes.active()) Emotes.stop();
            else Emotes.play(CosmeticState.effective(CosmeticSlot.EMOTE));
        }
        emoteKeyWasDown = emoteDown;

        Emotes.tick(client);
        Trails.tick(client);
    }

    public static void openMenu(MinecraftClient client, Screen parent) {
        client.setScreen(new CosmeticsScreen(parent));
    }

    private static boolean keyDown(MinecraftClient client, int key) {
        return key != Setting.NO_KEY && GLFW.glfwGetKey(client.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }
}
