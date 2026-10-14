package com.vclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class LauncherSettings {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path SETTINGS_FILE = Path.of(
            System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
            "V-Client", "settings.json");

    private Path minecraftDirectory;
    private int memoryGb;
    private int javaMajorOverride;
    private String lastAccountId;
    private String discordInvite;

    private LauncherSettings(Path minecraftDirectory, int memoryGb, int javaMajorOverride,
                             String lastAccountId, String discordInvite) {
        this.minecraftDirectory = minecraftDirectory;
        this.memoryGb = memoryGb;
        this.javaMajorOverride = javaMajorOverride;
        this.lastAccountId = lastAccountId;
        this.discordInvite = discordInvite;
    }

    static LauncherSettings load() throws IOException {
        Path appData = Path.of(System.getenv().getOrDefault("APPDATA",
                Path.of(System.getProperty("user.home"), "AppData", "Roaming").toString()));
        Path defaultDirectory = privateMinecraftDirectory(appData);
        if (!Files.exists(SETTINGS_FILE)) {
            return new LauncherSettings(defaultDirectory, defaultMemory(), 0, "", "");
        }
        ObjectNode saved = (ObjectNode) JSON.readTree(SETTINGS_FILE.toFile());
        String directory = saved.path("minecraftDirectory").asText(defaultDirectory.toString());
        int memory = saved.path("memoryGb").asInt(defaultMemory());
        Path selectedDirectory = Path.of(directory);
        Path minecraftDirectory = migrateLegacyDirectory(
                normalizeMinecraftDirectory(selectedDirectory), appData);
        LauncherSettings settings = new LauncherSettings(minecraftDirectory,
                Math.clamp(memory, 1, maxMemory()), saved.path("javaMajorOverride").asInt(0) == 25 ? 25 : 0,
                saved.path("lastAccountId").asText(""),
                saved.path("discordInvite").asText(""));
        if (!minecraftDirectory.equals(selectedDirectory)) settings.save();
        return settings;
    }

    static Path privateMinecraftDirectory(Path appData) {
        return appData.resolve("V-Client").resolve("minecraft");
    }

    static Path migrateLegacyDirectory(Path configured, Path appData) {
        Path oldLauncherRoot = appData.resolve("V-Client").toAbsolutePath().normalize();
        Path oldDefaultDirectory = appData.resolve(".minecraft").toAbsolutePath().normalize();
        Path normalizedConfigured = configured.toAbsolutePath().normalize();
        if (normalizedConfigured.equals(oldLauncherRoot) || normalizedConfigured.equals(oldDefaultDirectory)) {
            return privateMinecraftDirectory(appData);
        }
        return configured;
    }

    static Path normalizeMinecraftDirectory(Path directory) {
        Path versionsDirectory = directory.getParent();
        if (versionsDirectory == null || versionsDirectory.getFileName() == null
                || !versionsDirectory.getFileName().toString().equalsIgnoreCase("versions")
                || directory.getFileName() == null) {
            return directory;
        }
        Path profileMetadata = directory.resolve(directory.getFileName() + ".json");
        if (!Files.isRegularFile(profileMetadata)) return directory;
        Path gameDirectory = versionsDirectory.getParent();
        return gameDirectory == null ? directory : gameDirectory;
    }

    void save() throws IOException {
        Files.createDirectories(SETTINGS_FILE.getParent());
        ObjectNode saved = JSON.createObjectNode();
        saved.put("minecraftDirectory", minecraftDirectory.toString());
        saved.put("memoryGb", memoryGb);
        saved.put("javaMajorOverride", javaMajorOverride);
        saved.put("lastAccountId", lastAccountId);
        saved.put("discordInvite", discordInvite);
        JSON.writerWithDefaultPrettyPrinter().writeValue(SETTINGS_FILE.toFile(), saved);
    }

    Path minecraftDirectory() {
        return minecraftDirectory;
    }

    void setMinecraftDirectory(Path directory) {
        minecraftDirectory = directory;
    }

    int memoryGb() {
        return memoryGb;
    }

    int javaMajorOverride() {
        return javaMajorOverride;
    }

    void setJavaMajorOverride(int javaMajorOverride) {
        if (javaMajorOverride != 0 && javaMajorOverride != 25) {
            throw new IllegalArgumentException("Only Java 25 can be selected as a runtime override.");
        }
        this.javaMajorOverride = javaMajorOverride;
    }

    String lastAccountId() {
        return lastAccountId;
    }

    void setLastAccountId(String lastAccountId) {
        this.lastAccountId = lastAccountId;
    }

    void setMemoryGb(int memoryGb) {
        this.memoryGb = memoryGb;
    }

    String discordInvite() {
        return discordInvite;
    }

    void setDiscordInvite(String discordInvite) {
        this.discordInvite = discordInvite;
    }

    static int maxMemory() {
        return Math.max(4, (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024 * 1024)) + 2);
    }

    private static int defaultMemory() {
        return Math.clamp((int) (Runtime.getRuntime().maxMemory() / (1024 * 1024 * 1024)) / 2, 2, maxMemory());
    }
}
