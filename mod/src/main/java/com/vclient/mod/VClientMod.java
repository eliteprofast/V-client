package com.vclient.mod;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

public final class VClientMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("V-Client");

    private static final Map<Module, Boolean> TOGGLE_KEYS_DOWN = new EnumMap<>(Module.class);
    private static boolean menuKeyWasDown;
    private static boolean zooming;
    private static boolean smoothCameraBeforeZoom;
    private static boolean zoomChangedSmoothCamera;
    private static float zoomProgress;
    private static double zoomLevel;
    private static boolean togglesApplied;
    private static double lastX = Double.NaN;
    private static double lastZ;
    private static double speed;

    @Override
    public void onInitializeClient() {
        VConfig.load();
        LOGGER.info("V-Client mod loaded. Press {} in game to open the mod menu.",
                KeyNames.name(VConfig.integer(Module.CLIENT, "menuKey")));
    }

    /** Called at the end of every client tick. */
    public static void tick(MinecraftClient client) {
        if (!togglesApplied && client.options != null) {
            if (VConfig.enabled(Module.TOGGLE_SPRINT)) client.options.getSprintToggled().setValue(true);
            if (VConfig.enabled(Module.TOGGLE_SNEAK)) client.options.getSneakToggled().setValue(true);
            togglesApplied = true;
        }
        boolean inGame = client.player != null && client.currentScreen == null;

        boolean menuKeyDown = keyDown(client, VConfig.integer(Module.CLIENT, "menuKey"));
        if (menuKeyDown && !menuKeyWasDown && inGame) client.setScreen(new ModMenuScreen());
        menuKeyWasDown = menuKeyDown;

        for (Module module : Module.values()) {
            if (!module.toggleable()) continue;
            boolean down = inGame && keyDown(client, VConfig.integer(module, CommonSettings.TOGGLE_KEY.id()));
            if (down && !TOGGLE_KEYS_DOWN.getOrDefault(module, false)) {
                boolean enabled = !VConfig.enabled(module);
                VConfig.setEnabled(module, enabled);
                if (module == Module.TOGGLE_SPRINT || module == Module.TOGGLE_SNEAK) applyMovementToggles(client);
                client.inGameHud.setOverlayMessage(Text.literal(module.title() + (enabled ? " enabled" : " disabled")),
                        false);
            }
            TOGGLE_KEYS_DOWN.put(module, down);
        }

        boolean zoomKeyDown = inGame && VConfig.enabled(Module.ZOOM)
                && keyDown(client, VConfig.integer(Module.ZOOM, "key"));
        if (zoomKeyDown != zooming) {
            if (zoomKeyDown) {
                zoomLevel = VConfig.get(Module.ZOOM, "level");
                zoomChangedSmoothCamera = VConfig.bool(Module.ZOOM, "cinematic");
                if (zoomChangedSmoothCamera) {
                    smoothCameraBeforeZoom = client.options.smoothCameraEnabled;
                    client.options.smoothCameraEnabled = true;
                }
            } else if (zoomChangedSmoothCamera) {
                client.options.smoothCameraEnabled = smoothCameraBeforeZoom;
            }
            zooming = zoomKeyDown;
        }

        if (client.player != null) {
            double x = client.player.getX();
            double z = client.player.getZ();
            if (!Double.isNaN(lastX)) {
                double perSecond = Math.hypot(x - lastX, z - lastZ) * 20;
                speed += (perSecond - speed) * 0.5;
            }
            lastX = x;
            lastZ = z;
        } else {
            lastX = Double.NaN;
            speed = 0;
        }
    }

    private static boolean keyDown(MinecraftClient client, int key) {
        return key != Setting.NO_KEY && GLFW.glfwGetKey(client.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    /** Field of view after the zoom module. */
    public static float modifyFov(float fov) {
        float target = zooming ? 1.0f : 0.0f;
        if (VConfig.bool(Module.ZOOM, "smooth")) {
            zoomProgress += (target - zoomProgress) * 0.35f;
            if (Math.abs(target - zoomProgress) < 0.002f) zoomProgress = target;
        } else {
            zoomProgress = target;
        }
        if (zoomProgress <= 0) return fov;
        float divisor = 1.0f + (float) (zoomLevel - 1.0) * zoomProgress;
        return fov / divisor;
    }

    /** Scroll while zooming changes the zoom level; returns true when the scroll was used. */
    public static boolean onScroll(double amount) {
        if (!zooming || amount == 0 || !VConfig.bool(Module.ZOOM, "scroll")) return false;
        zoomLevel = Math.clamp(zoomLevel + Math.signum(amount), 1.5, 50);
        return true;
    }

    public static boolean fullbright() {
        return VConfig.enabled(Module.FULLBRIGHT);
    }

    public static double fullbrightGamma() {
        return VConfig.get(Module.FULLBRIGHT, "brightness");
    }

    public static boolean noHurtCam() {
        return VConfig.enabled(Module.NO_HURT_CAM);
    }

    public static double speed() {
        return speed;
    }

    /** Toggle Sprint/Sneak use Minecraft's own toggle options, so they behave exactly like vanilla. */
    public static void applyMovementToggles(MinecraftClient client) {
        client.options.getSprintToggled().setValue(VConfig.enabled(Module.TOGGLE_SPRINT));
        client.options.getSneakToggled().setValue(VConfig.enabled(Module.TOGGLE_SNEAK));
    }
}
