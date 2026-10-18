package com.vclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Writes a Boost profile's video settings into the game's options.txt and can undo it. Only the keys a profile
 * names are touched; every other setting (keys, sensitivity, resource packs, servers...) stays as it is. The
 * values the player had are saved the first time, so "Restore my settings" brings them back exactly.
 */
final class BoostOptions {
    private static final ObjectMapper JSON = new ObjectMapper();

    private BoostOptions() {}

    static Path optionsFile(Path minecraftDirectory) {
        return minecraftDirectory.resolve("options.txt");
    }

    static Path backupFile(Path minecraftDirectory) {
        return minecraftDirectory.resolve("vclient").resolve("boost-backup.json");
    }

    /** True while a backup exists, i.e. Boost settings are currently applied on top of the player's own. */
    static boolean applied(Path minecraftDirectory) {
        return Files.isRegularFile(backupFile(minecraftDirectory));
    }

    /**
     * Sets {@code values} in options.txt. Returns how many keys actually changed. The player's original values
     * are backed up once, on the first apply, and are not overwritten by later applies.
     */
    static int apply(Path minecraftDirectory, Map<String, String> values, Consumer<String> log) throws IOException {
        Path file = optionsFile(minecraftDirectory);
        List<String> lines = Files.isRegularFile(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : new ArrayList<>();
        Map<String, Integer> positions = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            int colon = lines.get(i).indexOf(':');
            if (colon > 0) positions.putIfAbsent(lines.get(i).substring(0, colon), i);
        }

        Path backup = backupFile(minecraftDirectory);
        ObjectNode saved = Files.isRegularFile(backup) && JSON.readTree(backup.toFile()) instanceof ObjectNode existing
                ? existing : JSON.createObjectNode();
        ObjectNode originals = saved.get("originals") instanceof ObjectNode node ? node : saved.putObject("originals");

        int changed = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            Integer position = positions.get(key);
            String current = position == null ? null : lines.get(position).substring(key.length() + 1);
            if (!originals.has(key)) {
                if (current == null) originals.putNull(key);
                else originals.put(key, current);
            }
            if (entry.getValue().equals(current)) continue;
            String line = key + ":" + entry.getValue();
            if (position == null) {
                lines.add(line);
            } else {
                lines.set(position, line);
            }
            log.accept("Boost: " + key + " " + (current == null ? "(not set)" : current) + " -> " + entry.getValue());
            changed++;
        }
        Files.createDirectories(backup.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(backup.toFile(), saved);
        writeAtomically(file, lines);
        return changed;
    }

    /** Puts the player's own values back and removes the backup. Returns how many keys were restored. */
    static int restore(Path minecraftDirectory, Consumer<String> log) throws IOException {
        Path backup = backupFile(minecraftDirectory);
        if (!Files.isRegularFile(backup)) return 0;
        JsonNode saved = JSON.readTree(backup.toFile());
        Path file = optionsFile(minecraftDirectory);
        List<String> lines = Files.isRegularFile(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : new ArrayList<>();
        int restored = 0;
        var fields = saved.path("originals").fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String key = entry.getKey();
            int position = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(key + ":")) {
                    position = i;
                    break;
                }
            }
            if (entry.getValue().isNull()) {
                if (position >= 0) lines.remove(position);
            } else if (position >= 0) {
                lines.set(position, key + ":" + entry.getValue().asText());
            } else {
                lines.add(key + ":" + entry.getValue().asText());
            }
            log.accept("Boost: restored " + key + (entry.getValue().isNull() ? " (removed)" : " = " + entry.getValue().asText()));
            restored++;
        }
        writeAtomically(file, lines);
        Files.deleteIfExists(backup);
        return restored;
    }

    private static void writeAtomically(Path file, List<String> lines) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".options-", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
