package com.vclient.mod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

/** Draws the movable HUD elements and tracks clicks per second. */
public final class Hud {
    static final int PANEL = 0x90000000;
    static final int TEXT = 0xFFFFFFFF;
    static final int ACCENT = 0xFFE6BD59;
    static final int MUTED = 0xFFB3B3CF;
    private static final int KEY = 22;
    private static final int GAP = 2;
    // Looked up by serialized name: enum constant names are obfuscated in the running game.
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.byName("head"),
            EquipmentSlot.byName("chest"), EquipmentSlot.byName("legs"), EquipmentSlot.byName("feet")};

    private static final Module[] LEFT_COLUMN = {Module.FPS, Module.CPS, Module.PING, Module.COORDINATES,
            Module.KEYSTROKES};

    private static final ArrayDeque<Long> LEFT_CLICKS = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT_CLICKS = new ArrayDeque<>();
    private static boolean leftWasDown;
    private static boolean rightWasDown;

    private Hud() {}

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;
        trackClicks(client);
        if (client.currentScreen instanceof HudEditorScreen) return;
        for (Module module : Module.values()) {
            if (!module.hudElement() || !VConfig.enabled(module)) continue;
            if (!visible(client, module)) continue;
            int[] bounds = bounds(client, module);
            draw(context, client, module, bounds[0], bounds[1], false);
        }
    }

    /** Elements that only appear while relevant (the editor shows them all). */
    private static boolean visible(MinecraftClient client, Module module) {
        if (module == Module.TOGGLE_SPRINT) return client.options.sprintKey.isPressed();
        return true;
    }

    static int width(MinecraftClient client, Module module) {
        TextRenderer font = client.textRenderer;
        return switch (module) {
            case FPS -> font.getWidth("FPS: 9999") + 8;
            case CPS -> font.getWidth("CPS: 99 | 99") + 8;
            case COORDINATES -> Math.max(font.getWidth("XYZ: " + coordinateText(client.player)),
                    font.getWidth("Facing: southeast")) + 8;
            case PING -> font.getWidth("Ping: 9999 ms") + 8;
            case KEYSTROKES -> KEY * 3 + GAP * 2;
            case ARMOR_STATUS -> 16 + 4 + font.getWidth("9999") + 4;
            case TOGGLE_SPRINT -> font.getWidth("[Sprinting (Toggled)]") + 8;
            default -> 0;
        };
    }

    static int height(MinecraftClient client, Module module) {
        return switch (module) {
            case COORDINATES -> 26;
            case KEYSTROKES -> KEY * 3 + GAP * 3 + 12;
            case ARMOR_STATUS -> 5 * 17;
            default -> 13;
        };
    }

    /** Current top-left corner, width, and height, kept inside the screen. */
    static int[] bounds(MinecraftClient client, Module module) {
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int width = width(client, module);
        int height = height(client, module);
        int[] saved = VConfig.position(module);
        int x;
        int y;
        if (saved != null) {
            x = saved[0];
            y = saved[1];
        } else {
            int[] fallback = defaultPosition(client, module, screenWidth, screenHeight, width);
            x = fallback[0];
            y = fallback[1];
        }
        x = Math.clamp(x, 0, Math.max(0, screenWidth - width));
        y = Math.clamp(y, 0, Math.max(0, screenHeight - height));
        return new int[]{x, y, width, height};
    }

    private static int[] defaultPosition(MinecraftClient client, Module module, int screenWidth,
                                         int screenHeight, int width) {
        return switch (module) {
            case ARMOR_STATUS -> new int[]{screenWidth - width - 4, screenHeight / 2 - 42};
            case TOGGLE_SPRINT -> new int[]{4, screenHeight - 17};
            default -> new int[]{4, leftColumnY(client, module)};
        };
    }

    /** Stacks the left-column elements that still use default positions, with no gaps. */
    private static int leftColumnY(MinecraftClient client, Module target) {
        int y = 4;
        for (Module module : LEFT_COLUMN) {
            if (module == target) return y;
            if (VConfig.enabled(module) && VConfig.position(module) == null) y += height(client, module) + 3;
        }
        return y;
    }

    static void draw(DrawContext context, MinecraftClient client, Module module, int x, int y, boolean editing) {
        TextRenderer font = client.textRenderer;
        int width = width(client, module);
        switch (module) {
            case FPS -> label(context, font, x, y, width, "FPS: ", Integer.toString(client.getCurrentFps()));
            case CPS -> label(context, font, x, y, width, "CPS: ", count(LEFT_CLICKS) + " | " + count(RIGHT_CLICKS));
            case PING -> label(context, font, x, y, width, "Ping: ", ping(client));
            case COORDINATES -> coordinates(context, font, client.player, x, y, width);
            case KEYSTROKES -> keystrokes(context, client, x, y);
            case ARMOR_STATUS -> armor(context, client, x, y, editing);
            case TOGGLE_SPRINT -> {
                context.fill(x, y, x + width, y + 13, PANEL);
                context.drawText(font, "[Sprinting (Toggled)]", x + 4, y + 3, ACCENT, true);
            }
            default -> {
            }
        }
    }

    private static void label(DrawContext context, TextRenderer font, int x, int y, int width,
                              String name, String value) {
        context.fill(x, y, x + width, y + 13, PANEL);
        context.drawText(font, name, x + 4, y + 3, ACCENT, true);
        context.drawText(font, value, x + 4 + font.getWidth(name), y + 3, TEXT, true);
    }

    private static void coordinates(DrawContext context, TextRenderer font, ClientPlayerEntity player,
                                    int x, int y, int width) {
        context.fill(x, y, x + width, y + 26, PANEL);
        String xyz = coordinateText(player);
        String facing = player == null ? "north"
                : player.getHorizontalFacing().asString();
        context.drawText(font, "XYZ: ", x + 4, y + 3, ACCENT, true);
        context.drawText(font, xyz, x + 4 + font.getWidth("XYZ: "), y + 3, TEXT, true);
        context.drawText(font, "Facing: ", x + 4, y + 15, ACCENT, true);
        context.drawText(font, facing, x + 4 + font.getWidth("Facing: "), y + 15, TEXT, true);
    }

    private static String coordinateText(ClientPlayerEntity player) {
        if (player == null) return "0 / 0 / 0";
        return player.getBlockPos().getX() + " / " + player.getBlockPos().getY() + " / " + player.getBlockPos().getZ();
    }

    private static String ping(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return "0 ms";
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return (entry == null ? 0 : entry.getLatency()) + " ms";
    }

    private static void keystrokes(DrawContext context, MinecraftClient client, int x, int y) {
        TextRenderer font = client.textRenderer;
        key(context, font, x + KEY + GAP, y, KEY, KEY, "W", client.options.forwardKey);
        int row = y + KEY + GAP;
        key(context, font, x, row, KEY, KEY, "A", client.options.leftKey);
        key(context, font, x + KEY + GAP, row, KEY, KEY, "S", client.options.backKey);
        key(context, font, x + (KEY + GAP) * 2, row, KEY, KEY, "D", client.options.rightKey);
        int mouseRow = row + KEY + GAP;
        int mouseWidth = (KEY * 3 + GAP) / 2;
        key(context, font, x, mouseRow, mouseWidth, KEY, "LMB", client.options.attackKey);
        key(context, font, x + mouseWidth + GAP, mouseRow, KEY * 3 + GAP * 2 - mouseWidth - GAP, KEY,
                "RMB", client.options.useKey);
        key(context, font, x, mouseRow + KEY + GAP, KEY * 3 + GAP * 2, 12, "", client.options.jumpKey);
    }

    private static void key(DrawContext context, TextRenderer font, int x, int y, int width, int height,
                            String text, KeyBinding binding) {
        boolean pressed = binding.isPressed();
        context.fill(x, y, x + width, y + height, pressed ? 0xD0FFFFFF : PANEL);
        if (text.isEmpty()) {
            int barY = y + height / 2;
            context.fill(x + width / 4, barY, x + width * 3 / 4, barY + 1, pressed ? 0xFF000000 : TEXT);
            return;
        }
        context.drawText(font, text, x + (width - font.getWidth(text)) / 2, y + (height - 8) / 2,
                pressed ? 0xFF000000 : TEXT, !pressed);
    }

    private static void armor(DrawContext context, MinecraftClient client, int x, int y, boolean editing) {
        ClientPlayerEntity player = client.player;
        int row = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player == null ? ItemStack.EMPTY : player.getEquippedStack(slot);
            armorRow(context, client.textRenderer, stack, x, y + row * 17, editing);
            row++;
        }
        armorRow(context, client.textRenderer, player == null ? ItemStack.EMPTY : player.getMainHandStack(),
                x, y + row * 17, editing);
    }

    private static void armorRow(DrawContext context, TextRenderer font, ItemStack stack, int x, int y,
                                 boolean editing) {
        if (stack.isEmpty()) {
            if (editing) context.fill(x, y, x + 16, y + 16, 0x40FFFFFF);
            return;
        }
        context.drawItem(stack, x, y);
        context.drawStackOverlay(font, stack, x, y);
        if (stack.isDamageable()) {
            int remaining = stack.getMaxDamage() - stack.getDamage();
            float fraction = remaining / (float) Math.max(1, stack.getMaxDamage());
            int color = fraction > 0.5f ? 0xFF7CF29A : fraction > 0.2f ? ACCENT : 0xFFFF6B6B;
            context.drawText(font, Integer.toString(remaining), x + 20, y + 4, color, true);
        } else if (stack.getCount() > 1) {
            context.drawText(font, Integer.toString(stack.getCount()), x + 20, y + 4, TEXT, true);
        }
    }

    private static void trackClicks(MinecraftClient client) {
        long now = System.currentTimeMillis();
        boolean playing = client.currentScreen == null && client.isWindowFocused();
        long window = client.getWindow().getHandle();
        boolean leftDown = playing && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rightDown = playing && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (leftDown && !leftWasDown) LEFT_CLICKS.addLast(now);
        if (rightDown && !rightWasDown) RIGHT_CLICKS.addLast(now);
        leftWasDown = leftDown;
        rightWasDown = rightDown;
        while (!LEFT_CLICKS.isEmpty() && now - LEFT_CLICKS.peekFirst() > 1000) LEFT_CLICKS.removeFirst();
        while (!RIGHT_CLICKS.isEmpty() && now - RIGHT_CLICKS.peekFirst() > 1000) RIGHT_CLICKS.removeFirst();
    }

    private static int count(ArrayDeque<Long> clicks) {
        long now = System.currentTimeMillis();
        int total = 0;
        for (long time : clicks) if (now - time <= 1000) total++;
        return total;
    }
}
