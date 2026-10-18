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
            long cheatModules = java.util.Arrays.stream(Module.values()).filter(Module::cheat).count();
            LOGGER.info("Hacker mode unlocked: {} hacker modules are available in the Right Shift menu.", cheatModules);
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
        trackDeath(client);

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
        } else if (VConfig.enabled(Module.FLY) && !client.player.hasVehicle()) {
            tickFly(client);
        } else if (VConfig.enabled(Module.SPEED_HACK) && !client.player.hasVehicle()
                && !client.player.isSneaking()) {
            tickSpeedHack(client);
        }
        if (!freecamOn) tickMovementExtras(client);
        if (VConfig.enabled(Module.FAST_PLACE)) {
            ((com.vclient.mod.mixin.MinecraftClientAccessor) client).vclient$setItemUseCooldown(0);
        }
        if (VConfig.enabled(Module.FAST_BREAK) && client.interactionManager != null) {
            ((com.vclient.mod.mixin.ClientPlayerInteractionManagerAccessor) client.interactionManager)
                    .vclient$setBlockBreakingCooldown(0);
        }
        tickWorldExtras(client);
    }

    private static int airJumpCooldown;

    /** No Fall, Air Jump, Glide, Time Changer and Auto Walk. */
    private static void tickWorldExtras(MinecraftClient client) {
        var player = client.player;
        if (VConfig.enabled(Module.NO_FALL) && player.fallDistance > 2.5 && !player.isOnGround()
                && client.getNetworkHandler() != null) {
            // Tell the server we are on the ground so it resets the fall distance before we land.
            client.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
                    .OnGroundOnly(true, player.horizontalCollision));
            player.fallDistance = 0;
        }
        boolean jumpHeld = client.options.jumpKey.isPressed() && client.currentScreen == null;
        if (airJumpCooldown > 0) airJumpCooldown--;
        if (VConfig.enabled(Module.AIR_JUMP) && jumpHeld && !player.isOnGround() && airJumpCooldown == 0
                && !player.isTouchingWater() && !player.isClimbing()) {
            player.jump();
            airJumpCooldown = 8;
        }
        if (VConfig.enabled(Module.GLIDE) && jumpHeld && !player.isOnGround() && !player.isTouchingWater()) {
            var velocity = player.getVelocity();
            double limit = -VConfig.get(Module.GLIDE, "speed");
            if (velocity.y < limit) player.setVelocity(velocity.x, limit, velocity.z);
            player.fallDistance = 0;
        }
        if (VConfig.enabled(Module.TIME_CHANGER) && client.world != null) {
            long[] times = {1000, 6000, 12000, 13000, 18000, 23000};
            int choice = Math.clamp(VConfig.integer(Module.TIME_CHANGER, "time"), 0, times.length - 1);
            client.world.setTime(client.world.getTime(), times[choice], false);
        }
        if (VConfig.enabled(Module.AUTO_WALK) && client.currentScreen == null) {
            client.options.forwardKey.setPressed(true);
        }
        tickMoreCheats(client);
        tickUtilities(client);
    }

    private static int utilityCooldown;
    private static boolean eating;
    private static int eatTicks;
    private static int eatReturnSlot = -1;
    private static final java.util.Set<String> NOT_FOR_EATING = java.util.Set.of("golden_apple", "enchanted_golden_apple",
            "rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish", "chorus_fruit", "suspicious_stew");

    /** UseAction is NONE, EAT, DRINK, ...: its constants have no readable names at runtime, so EAT is its position. */
    private static final int EAT_ORDINAL = 1;

    static String itemId(net.minecraft.item.ItemStack stack) {
        return net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
    }

    /** Auto Totem, Auto Tool and Auto Eat. */
    private static void tickUtilities(MinecraftClient client) {
        var player = client.player;
        var inventory = player.getInventory();
        boolean free = client.currentScreen == null;
        if (utilityCooldown > 0) utilityCooldown--;

        if (VConfig.enabled(Module.AUTO_TOTEM) && utilityCooldown == 0 && client.interactionManager != null
                && !"totem_of_undying".equals(itemId(player.getOffHandStack()))) {
            for (int i = 0; i < 36; i++) {
                if (!"totem_of_undying".equals(itemId(inventory.getStack(i)))) continue;
                // Player screen handler slots: 9-35 main inventory, 36-44 hotbar; button 40 swaps with the off hand.
                int slot = i < 9 ? 36 + i : i;
                client.interactionManager.clickSlot(player.playerScreenHandler.syncId, slot, 40,
                        net.minecraft.screen.slot.SlotActionType.SWAP, player);
                utilityCooldown = 6;
                break;
            }
        }

        if (VConfig.enabled(Module.AUTO_TOOL) && free && client.options.attackKey.isPressed() && client.world != null
                && client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit) {
            var state = client.world.getBlockState(hit.getBlockPos());
            int best = inventory.getSelectedSlot();
            float bestSpeed = inventory.getStack(best).getMiningSpeedMultiplier(state);
            for (int i = 0; i < 9; i++) {
                float speed = inventory.getStack(i).getMiningSpeedMultiplier(state);
                if (speed > bestSpeed + 0.01f) {
                    best = i;
                    bestSpeed = speed;
                }
            }
            if (best != inventory.getSelectedSlot()) inventory.setSelectedSlot(best);
        }

        if (VConfig.enabled(Module.AUTO_EAT) && free) {
            boolean hungry = player.getHungerManager().getFoodLevel() <= VConfig.integer(Module.AUTO_EAT, "hunger")
                    && !player.isCreative() && !player.isSpectator();
            if (eating) {
                eatTicks++;
                if (eatTicks > 3 && (!player.isUsingItem() || !hungry)) {
                    client.options.useKey.setPressed(false);
                    eating = false;
                    if (eatReturnSlot >= 0) inventory.setSelectedSlot(eatReturnSlot);
                    eatReturnSlot = -1;
                    utilityCooldown = 10;
                }
            } else if (hungry && !player.isUsingItem() && utilityCooldown == 0) {
                for (int i = 0; i < 9; i++) {
                    var stack = inventory.getStack(i);
                    if (stack.isEmpty() || stack.getUseAction().ordinal() != EAT_ORDINAL
                            || NOT_FOR_EATING.contains(itemId(stack))) continue;
                    eatReturnSlot = inventory.getSelectedSlot();
                    inventory.setSelectedSlot(i);
                    client.options.useKey.setPressed(true);
                    eating = true;
                    eatTicks = 0;
                    break;
                }
            }
        } else if (eating) {
            client.options.useKey.setPressed(false);
            eating = false;
        }
    }

    // ---- HUD data for the Item Counter and Last Death

    private static String lastDeath = "no deaths yet";
    private static boolean deathRecorded;

    public static String lastDeath() {
        return lastDeath;
    }

    /** How many of an item (by registry path, e.g. "arrow") the player carries, off hand included. */
    public static int countItem(MinecraftClient client, String path) {
        if (client.player == null) return 0;
        int total = 0;
        var inventory = client.player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            var stack = inventory.getStack(i);
            if (path.equals(itemId(stack))) total += stack.getCount();
        }
        var offhand = client.player.getOffHandStack();
        if (path.equals(itemId(offhand)) && !contains(inventory, offhand)) total += offhand.getCount();
        return total;
    }

    private static boolean contains(net.minecraft.entity.player.PlayerInventory inventory, net.minecraft.item.ItemStack stack) {
        for (int i = 0; i < inventory.size(); i++) if (inventory.getStack(i) == stack) return true;
        return false;
    }

    private static void trackDeath(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.player.isDead()) {
            if (!deathRecorded) {
                deathRecorded = true;
                lastDeath = String.format(java.util.Locale.ROOT, "%d %d %d (%s)", (int) client.player.getX(),
                        (int) client.player.getY(), (int) client.player.getZ(),
                        client.world.getRegistryKey().getValue().getPath());
            }
        } else {
            deathRecorded = false;
        }
    }

    private static int afkTicks;
    private static boolean longJumpWasOnGround;

    /** Bunny Hop, Long Jump, Fast Climb, Water Float, Auto Respawn, Anti AFK, Auto Sneak and Fast Swim. */
    private static void tickMoreCheats(MinecraftClient client) {
        var player = client.player;
        boolean free = client.currentScreen == null;
        boolean forward = free && client.options.forwardKey.isPressed();
        boolean onGround = player.isOnGround();
        boolean inWater = player.isTouchingWater();

        if (VConfig.enabled(Module.AUTO_RESPAWN) && player.isDead()) player.requestRespawn();

        if (VConfig.enabled(Module.BUNNY_HOP) && forward && onGround && !player.isSneaking() && !inWater) player.jump();

        var velocity = player.getVelocity();
        if (VConfig.enabled(Module.LONG_JUMP) && longJumpWasOnGround && !onGround && forward && velocity.y > 0.25) {
            double boost = VConfig.get(Module.LONG_JUMP, "boost");
            player.setVelocity(velocity.x * boost, velocity.y, velocity.z * boost);
        }
        longJumpWasOnGround = onGround;

        if (VConfig.enabled(Module.FAST_CLIMB) && player.isClimbing() && (forward || (free && client.options.jumpKey.isPressed()))) {
            double speed = VConfig.get(Module.FAST_CLIMB, "speed");
            player.setVelocity(velocity.x, Math.max(velocity.y, speed), velocity.z);
        }

        if (VConfig.enabled(Module.WATER_FLOAT) && inWater && !player.isSneaking()) {
            double lift = free && client.options.jumpKey.isPressed() ? 0.3 : 0.11;
            player.setVelocity(velocity.x, Math.max(velocity.y, lift), velocity.z);
            player.fallDistance = 0;
        }

        if (VConfig.enabled(Module.FAST_SWIM) && forward && inWater && !onGround) {
            double yaw = Math.toRadians(player.getYaw());
            double speed = 0.2 * VConfig.get(Module.FAST_SWIM, "speed");
            player.setVelocity(-Math.sin(yaw) * speed, velocity.y, Math.cos(yaw) * speed);
        }

        if (VConfig.enabled(Module.AUTO_SNEAK) && free) client.options.sneakKey.setPressed(true);

        if (VConfig.enabled(Module.ANTI_AFK)) {
            boolean moving = free && (client.options.forwardKey.isPressed() || client.options.backKey.isPressed()
                    || client.options.leftKey.isPressed() || client.options.rightKey.isPressed()
                    || client.options.jumpKey.isPressed());
            afkTicks = moving ? 0 : afkTicks + 1;
            if (afkTicks >= VConfig.get(Module.ANTI_AFK, "interval") * 20) {
                afkTicks = 0;
                if (onGround) player.jump();
                player.setYaw(player.getYaw() + 1.5f);
            }
        } else {
            afkTicks = 0;
        }
    }

    /** Anti Knockback: whether a velocity packet for this entity should be ignored. */
    public static boolean ignoreKnockback(int entityId) {
        MinecraftClient client = MinecraftClient.getInstance();
        return VConfig.enabled(Module.ANTI_KNOCKBACK) && client.player != null && client.player.getId() == entityId;
    }

    /** Safe Walk: the local player behaves as if sneaking at ledges. */
    public static boolean safeWalk(net.minecraft.entity.Entity entity) {
        return VConfig.enabled(Module.SAFE_WALK) && entity == MinecraftClient.getInstance().player;
    }

    private static boolean wasOnGround;
    private static int flyTicks;

    /** Creative-style flight: hold jump to rise, sneak to sink, otherwise hover in place. */
    private static void tickFly(MinecraftClient client) {
        var player = client.player;
        if (client.currentScreen != null) return;
        double speed = 0.4 * VConfig.get(Module.FLY, "speed");
        double forward = axis(client.options.forwardKey.isPressed(), client.options.backKey.isPressed());
        double strafe = axis(client.options.rightKey.isPressed(), client.options.leftKey.isPressed());
        double yaw = Math.toRadians(player.getYaw());
        double dx = forward * -Math.sin(yaw) + strafe * -Math.cos(yaw);
        double dz = forward * Math.cos(yaw) + strafe * -Math.sin(yaw);
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > 0) {
            dx = dx / length * speed;
            dz = dz / length * speed;
        }
        double dy = axis(client.options.jumpKey.isPressed(), client.options.sneakKey.isPressed()) * speed;
        // Servers kick players who hover without moving for about four seconds; a tiny alternating drop avoids that.
        flyTicks++;
        if (dy == 0 && VConfig.bool(Module.FLY, "hover")) dy = flyTicks % 40 == 0 ? -0.04 : flyTicks % 40 == 20 ? 0.04 : 0;
        player.setVelocity(dx, dy, dz);
        player.fallDistance = 0;
    }

    /** Auto Sprint, Step, Spider and High Jump. */
    private static void tickMovementExtras(MinecraftClient client) {
        var player = client.player;
        boolean forward = client.options.forwardKey.isPressed() && client.currentScreen == null;
        if (VConfig.enabled(Module.AUTO_SPRINT) && forward && !player.isSneaking() && !player.isSprinting()
                && player.getHungerManager().getFoodLevel() > 6) {
            player.setSprinting(true);
        }
        if (VConfig.enabled(Module.STEP) && forward && player.horizontalCollision && player.isOnGround()) {
            player.jump();
        }
        if (VConfig.enabled(Module.SPIDER) && forward && player.horizontalCollision && !player.isSneaking()) {
            var velocity = player.getVelocity();
            player.setVelocity(velocity.x, 0.2 * VConfig.get(Module.SPIDER, "speed"), velocity.z);
            player.fallDistance = 0;
        }
        boolean onGround = player.isOnGround();
        if (VConfig.enabled(Module.HIGH_JUMP) && wasOnGround && !onGround && client.options.jumpKey.isPressed()
                && player.getVelocity().y > 0.25) {
            var velocity = player.getVelocity();
            player.setVelocity(velocity.x, 0.42 * VConfig.get(Module.HIGH_JUMP, "power"), velocity.z);
        }
        wasOnGround = onGround;
    }

    /** Glow ESP: whether this entity should get the glowing outline for the local player. */
    public static boolean glowEsp(net.minecraft.entity.Entity entity) {
        if (!VConfig.enabled(Module.GLOW_ESP)) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || entity == client.player) return false;
        if (entity instanceof net.minecraft.entity.player.PlayerEntity) return true;
        return VConfig.bool(Module.GLOW_ESP, "mobs") && entity instanceof net.minecraft.entity.LivingEntity;
    }

    /** Hitboxes: extra size added to other living entities' targeting box. */
    public static float hitboxMargin(net.minecraft.entity.Entity entity) {
        if (!VConfig.enabled(Module.HITBOXES) || !(entity instanceof net.minecraft.entity.LivingEntity)) return 0;
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null || entity == client.player ? 0 : (float) VConfig.get(Module.HITBOXES, "size");
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
