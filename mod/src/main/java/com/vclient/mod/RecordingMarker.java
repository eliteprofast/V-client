package com.vclient.mod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shows a small REC marker while the launcher's replay buffer is recording this window, and a short "clip saved"
 * note after a clip is written. The launcher refreshes {@code <game>/vclient/recording.state} twice a second as
 * {@code recording|savedAtMillis|timeMillis}; a stale file means the launcher is not recording.
 */
final class RecordingMarker {
    private static final long FRESH_MILLIS = 3000;
    private static final long SAVED_MILLIS = 4000;
    private static Path file;
    private static long lastRead;
    private static boolean recording;
    private static long savedAt;

    private RecordingMarker() {}

    static void render(DrawContext context, MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (now - lastRead >= 500) {
            lastRead = now;
            read(now);
        }
        boolean saved = now - savedAt < SAVED_MILLIS;
        if (!recording && !saved) return;
        TextRenderer font = client.textRenderer;
        String text = saved ? "Clip saved" : "REC";
        int width = font.getWidth(text) + 16;
        int x = context.getScaledWindowWidth() - width - 4;
        int y = 4;
        context.fill(x, y, x + width, y + 12, 0x90000000);
        boolean blink = saved || (now / 600) % 2 == 0;
        if (blink) context.fill(x + 3, y + 4, x + 7, y + 8, saved ? 0xFF55FF55 : 0xFFFF3B3B);
        context.drawText(font, text, x + 11, y + 2, 0xFFFFFFFF, true);
    }

    private static void read(long now) {
        recording = false;
        try {
            if (file == null) file = FabricLoader.getInstance().getGameDir().resolve("vclient").resolve("recording.state");
            if (!Files.isRegularFile(file)) return;
            String[] parts = Files.readString(file).trim().split("[|]");
            if (parts.length < 3 || now - Long.parseLong(parts[2]) > FRESH_MILLIS) return;
            recording = parts[0].equals("1");
            savedAt = Long.parseLong(parts[1]);
        } catch (Exception e) {
            // Marker is optional; ignore a half-written file.
        }
    }
}
