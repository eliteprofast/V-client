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

    /** Set by the launcher only for the admin account when it chose Hacker / Cheater. */
    private static final boolean CHEATS_REQUESTED = Boolean.getBoolean("vclient.cheats");
    /** The only account allowed hacker mode: Izyrn's Ely.by UUID. The launcher flag alone is not enough. */
    private static final String ADMIN_UUID = "1331c81d33844eb6baf6a511c8298af3";
    private static Boolean cheatsGranted;
    private static boolean xrayWas;
    private static boolean freecamOn;
    private static double camX, camY, camZ, prevCamX, prevCamY, prevCamZ;
    private static float camYaw, camPitch;

    public static boolean cheatsAllowed() {
        if (!CHEATS_REQUESTED) return false;
        if (cheatsGranted != null) return cheatsGranted;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSession() == null || client.getSession().getUuidOrNull() == null) return false;
        cheatsGranted = ADMIN_UUID.equals(client.getSession().getUuidOrNull().toString().replace("-", ""));
        if (!cheatsGranted) LOGGER.warn("Hacker mode was requested, but this account is not the admin account; it stays off.");
        return cheatsGranted;
    }

    @Override
    public void onInitializeClient() {
        VConfig.load();
        com.vclient.mod.cosmetics.Cosmetics.init();
        if (CHEATS_REQUESTED && cheatsAllowed()) {
            VConfig.setEnabled(Module.FREECAM, false);
            LOGGER.info("Hacker mode unlocked: X-Ray, Reach-Hits, Speed Hack and Freecam are available.");
        }
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
            if (!module.toggleable() || (module.cheat() && !cheatsAllowed())) continue;
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

        com.vclient.mod.cosmetics.Cosmetics.tick(client);

        if (cheatsAllowed()) tickCheats(client);

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

    private static void tickCheats(MinecraftClient client) {
        boolean xray = VConfig.enabled(Module.XRAY);
        if (xray != xrayWas) {
            xrayWas = xray;
            if (client.worldRenderer != null && client.world != null) client.worldRenderer.reload();
        }

        boolean freecam = VConfig.enabled(Module.FREECAM) && client.player != null;
        if (freecam && !freecamOn) {
            camX = prevCamX = client.player.getX();
            camY = prevCamY = client.player.getEyeY();
            camZ = prevCamZ = client.player.getZ();
            camYaw = client.player.getYaw();
            camPitch = client.player.getPitch();
        }
        if (!freecam && VConfig.enabled(Module.FREECAM)) VConfig.setEnabled(Module.FREECAM, false);
        freecamOn = freecam;

        if (client.player == null) return;
        if (freecamOn) {
            tickFreecam(client);
        } else if (VConfig.enabled(Module.SPEED_HACK) && !client.player.hasVehicle()
                && !client.player.isSneaking()) {
            tickSpeedHack(client);
        }
    }

    private static void tickFreecam(MinecraftClient client) {
        prevCamX = camX;
        prevCamY = camY;
        prevCamZ = camZ;
        if (client.currentScreen != null) return;
        double yaw = Math.toRadians(camYaw);
        double pitch = Math.toRadians(camPitch);
        double forward = axis(client.options.forwardKey.isPressed(), client.options.backKey.isPressed());
        double strafe = axis(client.options.rightKey.isPressed(), client.options.leftKey.isPressed());
        double vertical = axis(client.options.jumpKey.isPressed(), client.options.sneakKey.isPressed());
        double dx = forward * -Math.sin(yaw) * Math.cos(pitch) + strafe * -Math.cos(yaw);
        double dy = forward * -Math.sin(pitch) + vertical;
        double dz = forward * Math.cos(yaw) * Math.cos(pitch) + strafe * -Math.sin(yaw);
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4) return;
        double step = 0.3 * VConfig.get(Module.FREECAM, "speed") / length;
        camX += dx * step;
        camY += dy * step;
        camZ += dz * step;
    }

    private static void tickSpeedHack(MinecraftClient client) {
        if (client.currentScreen != null) return;
        double forward = axis(client.options.forwardKey.isPressed(), client.options.backKey.isPressed());
        double strafe = axis(client.options.rightKey.isPressed(), client.options.leftKey.isPressed());
        if (forward == 0 && strafe == 0) return;
        double yaw = Math.toRadians(client.player.getYaw());
        double dx = forward * -Math.sin(yaw) + strafe * -Math.cos(yaw);
        double dz = forward * Math.cos(yaw) + strafe * -Math.sin(yaw);
        double length = Math.sqrt(dx * dx + dz * dz);
        // 0.2806 blocks per tick is vanilla sprinting speed on flat ground.
        double blocksPerTick = 0.2806 * VConfig.get(Module.SPEED_HACK, "multiplier");
        var velocity = client.player.getVelocity();
        client.player.setVelocity(dx / length * blocksPerTick, velocity.y, dz / length * blocksPerTick);
    }

    private static double axis(boolean positive, boolean negative) {
        return (positive ? 1 : 0) - (negative ? 1 : 0);
    }

    public static boolean freecamActive() {
        return freecamOn;
    }

    public static net.minecraft.util.math.Vec3d freecamPosition(float tickProgress) {
        return new net.minecraft.util.math.Vec3d(prevCamX + (camX - prevCamX) * tickProgress,
                prevCamY + (camY - prevCamY) * tickProgress, prevCamZ + (camZ - prevCamZ) * tickProgress);
    }

    public static float freecamYaw() {
        return camYaw;
    }

    public static float freecamPitch() {
        return camPitch;
    }

    public static void freecamLook(double cursorDeltaX, double cursorDeltaY) {
        camYaw += (float) cursorDeltaX * 0.15f;
        camPitch = Math.clamp(camPitch + (float) cursorDeltaY * 0.15f, -90f, 90f);
    }

    public static double reachDistance() {
        return VConfig.enabled(Module.REACH) ? VConfig.get(Module.REACH, "distance") : 0;
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
        return VConfig.enabled(Module.FULLBRIGHT) || VConfig.enabled(Module.XRAY);
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
