package com.vclient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Installs the bundled "Ultra Smooth PvP" resource pack into the game's resourcepacks folder and switches it on
 * the first time only, so a player who turns it off in Options stays off. Nothing is downloaded. The pack only
 * changes how things look (textures, a first-person shield position), never anything that affects gameplay.
 */
final class BundledResourcePack {
    static final String FILE_NAME = "Ultra Smooth PvP.zip";
    private static final String RESOURCE = "/vclient-pack/" + FILE_NAME;
    private static final String ENTRY = "file/" + FILE_NAME;

    private BundledResourcePack() {}

    static Path markerFile(Path minecraftDirectory) {
        return minecraftDirectory.resolve("vclient").resolve("pack-enabled");
    }

    /** True for the Minecraft releases the pack was built for (resource pack formats 46..75 = 1.21.4 to 1.21.11). */
    static boolean supports(String clientVersionId) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("1\\.21\\.(\\d+)").matcher(clientVersionId == null ? "" : clientVersionId);
        if (!m.matches()) return false;
        int patch = Integer.parseInt(m.group(1));
        return patch >= 4 && patch <= 11;
    }

    /**
     * Copies the pack into resourcepacks if it is missing or outdated, so it always shows up in the in-game
     * Resource Packs list. Does not switch it on. Returns true if the file was written.
     */
    static boolean ensureInstalled(Path minecraftDirectory, Consumer<String> onOutput) throws IOException {
        Path root = minecraftDirectory.toAbsolutePath().normalize();
        byte[] bundled;
        try (InputStream input = BundledResourcePack.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("The launcher is missing its bundled resource pack.");
            bundled = input.readAllBytes();
        }
        boolean changed = false;
        Path target = root.resolve("resourcepacks").resolve(FILE_NAME);
        if (!Files.isRegularFile(target) || !Arrays.equals(Files.readAllBytes(target), bundled)) {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".vclient-", ".tmp");
            try {
                Files.write(temporary, bundled);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            onOutput.accept("Installed the Ultra Smooth PvP resource pack into " + target.getParent() + ".");
            changed = true;
        }
        return changed;
    }

    /** Installs the pack and, the first time only, switches it on in options.txt. Returns true if anything changed. */
    static boolean ensureInstalledAndEnabled(Path minecraftDirectory, Consumer<String> onOutput) throws IOException {
        boolean changed = ensureInstalled(minecraftDirectory, onOutput);
        Path root = minecraftDirectory.toAbsolutePath().normalize();
        Path marker = markerFile(root);
        if (!Files.exists(marker)) {
            if (enable(root.resolve("options.txt"))) {
                onOutput.accept("Turned on the Ultra Smooth PvP resource pack (you can switch it off in Options > Resource Packs).");
            }
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "enabled once; delete this file to have the launcher enable the pack again\n");
            changed = true;
        }
        return changed;
    }

    /** Adds the pack to the resourcePacks line of options.txt (last = highest priority). False if already listed. */
    static boolean enable(Path optionsFile) throws IOException {
        List<String> lines = Files.isRegularFile(optionsFile)
                ? new ArrayList<>(Files.readAllLines(optionsFile, StandardCharsets.UTF_8)) : new ArrayList<>();
        boolean written = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith("resourcePacks:")) continue;
            if (line.contains("\"" + ENTRY + "\"")) return false;
            int close = line.lastIndexOf(']');
            if (close < 0) return false; // unrecognised format: leave the player's file alone
            String head = line.substring(0, close);
            lines.set(i, head + (head.endsWith("[") ? "" : ",") + "\"" + ENTRY + "\"]");
            written = true;
            break;
        }
        if (!written) lines.add("resourcePacks:[\"vanilla\",\"" + ENTRY + "\"]");
        Path temporary = Files.createTempFile(optionsFile.toAbsolutePath().getParent(), ".vclient-", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            Files.move(temporary, optionsFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return true;
    }
}
