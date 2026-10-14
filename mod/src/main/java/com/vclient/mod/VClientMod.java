package com.vclient.mod;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VClientMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("V-Client");
    private static final float ZOOM_FACTOR = 4.0f;

    private static boolean menuKeyWasDown;
    private static boolean zooming;
    private static boolean smoothCameraBeforeZoom;
    private static float zoomProgress;
    private static boolean togglesApplied;

    @Override
    public void onInitializeClient() {
        VConfig.load();
        LOGGER.info("V-Client mod loaded. Press Right Shift in game to open the mod menu.");
    }

    /** Called at the end of every client tick. */
    public static void tick(MinecraftClient client) {
        if (!togglesApplied && client.options != null) {
            if (VConfig.enabled(Module.TOGGLE_SPRINT)) client.options.getSprintToggled().setValue(true);
            if (VConfig.enabled(Module.TOGGLE_SNEAK)) client.options.getSneakToggled().setValue(true);
            togglesApplied = true;
        }
        long window = client.getWindow().getHandle();
        boolean inGame = client.player != null && client.currentScreen == null;

        boolean menuKeyDown = InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (menuKeyDown && !menuKeyWasDown && inGame) client.setScreen(new ModMenuScreen());
        menuKeyWasDown = menuKeyDown;

        boolean zoomKeyDown = inGame && VConfig.enabled(Module.ZOOM)
                && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
        if (zoomKeyDown != zooming) {
            if (zoomKeyDown) {
                smoothCameraBeforeZoom = client.options.smoothCameraEnabled;
                client.options.smoothCameraEnabled = true;
            } else {
                client.options.smoothCameraEnabled = smoothCameraBeforeZoom;
            }
            zooming = zoomKeyDown;
        }
    }

    /** Field of view after the zoom module, eased in and out over a few frames. */
    public static float modifyFov(float fov) {
        float target = zooming ? 1.0f : 0.0f;
        zoomProgress += (target - zoomProgress) * 0.35f;
        if (Math.abs(target - zoomProgress) < 0.002f) zoomProgress = target;
        if (zoomProgress <= 0) return fov;
        float divisor = 1.0f + (ZOOM_FACTOR - 1.0f) * zoomProgress;
        return fov / divisor;
    }

    public static boolean fullbright() {
        return VConfig.enabled(Module.FULLBRIGHT);
    }

    /** Toggle Sprint/Sneak use Minecraft's own toggle options, so they behave exactly like vanilla. */
    public static void applyMovementToggles(MinecraftClient client) {
        client.options.getSprintToggled().setValue(VConfig.enabled(Module.TOGGLE_SPRINT));
        client.options.getSneakToggled().setValue(VConfig.enabled(Module.TOGGLE_SNEAK));
    }
}
