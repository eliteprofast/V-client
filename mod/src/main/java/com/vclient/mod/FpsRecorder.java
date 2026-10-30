package com.vclient.mod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes one {@code epochSeconds,fps} line per second while the player is in a world, to
 * {@code <game>/vclient/fps-history.csv}. The launcher's Performance tab reads it to show average, lowest and
 * highest FPS per session. The file is trimmed to its newest lines at start-up so it never grows without bound.
 */
final class FpsRecorder {
    private static final int MAX_LINES = 200_000;
    private static final int FLUSH_EVERY = 10;
    private static Path file;
    private static boolean failed;
    private static long lastSecond;
    private static final List<String> PENDING = new ArrayList<>();

    private FpsRecorder() {}

    static void tick(MinecraftClient client) {
        if (failed || client.world == null || client.player == null) return;
        long now = System.currentTimeMillis() / 1000;
        if (now == lastSecond) return;
        lastSecond = now;
        PENDING.add(now + "," + client.getCurrentFps() + "\n");
        if (PENDING.size() >= FLUSH_EVERY) flush();
    }

    private static void flush() {
        try {
            if (file == null) {
                file = FabricLoader.getInstance().getGameDir().resolve("vclient").resolve("fps-history.csv");
                Files.createDirectories(file.getParent());
                trim();
            }
            Files.writeString(file, String.join("", PENDING), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            failed = true;
            VClientMod.LOGGER.warn("FPS history could not be written: {}", e.toString());
        } finally {
            PENDING.clear();
        }
    }

    private static void trim() throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) < 2_000_000) return;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.size() > MAX_LINES) {
            Files.write(file, lines.subList(lines.size() - MAX_LINES / 2, lines.size()), StandardCharsets.UTF_8);
        }
    }
}
