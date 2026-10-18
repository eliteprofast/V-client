package com.vclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

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
    private boolean offlineMode;
    private String backendUrl = "";
    private String backendToken = "";
    private String installId = "";
    private boolean autoUpdate = true;
    /** Logs every backend request with its URL, status and stack traces of failures. */
    private boolean debugMode;
    /** Ely.by account names allowed to use hacker mode. Izyrn is the first admin. */
    private final Set<String> adminUsers = new LinkedHashSet<>(Set.of("Izyrn"));
    /** Ids of accounts whose owner picked "Hacker / Cheater" instead of "Normal player". */
    private final Set<String> hackerAccountIds = new LinkedHashSet<>();

    private LauncherSettings(Path minecraftDirectory, int memoryGb, int javaMajorOverride,
                             String lastAccountId, String discordInvite, boolean offlineMode) {
        this.minecraftDirectory = minecraftDirectory;
        this.memoryGb = memoryGb;
        this.javaMajorOverride = javaMajorOverride;
        this.lastAccountId = lastAccountId;
        this.discordInvite = discordInvite;
        this.offlineMode = offlineMode;
    }

    static LauncherSettings load() throws IOException {
        Path appData = Path.of(System.getenv().getOrDefault("APPDATA",
                Path.of(System.getProperty("user.home"), "AppData", "Roaming").toString()));
        Path defaultDirectory = privateMinecraftDirectory(appData);
        if (!Files.exists(SETTINGS_FILE)) {
            LauncherSettings fresh = new LauncherSettings(defaultDirectory, defaultMemory(), 0, "", "", true);
            fresh.installId = java.util.UUID.randomUUID().toString();
            return fresh;
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
                saved.path("discordInvite").asText(""),
                saved.path("offlineMode").asBoolean(true));
        settings.backendUrl = saved.path("backendUrl").asText("");
        settings.backendToken = saved.path("backendToken").asText("");
        settings.autoUpdate = saved.path("autoUpdate").asBoolean(true);
        settings.debugMode = saved.path("debugMode").asBoolean(false);
        settings.installId = saved.path("installId").asText("");
        saved.path("adminUsers").forEach(name -> settings.adminUsers.add(name.asText("")));
        saved.path("hackerAccountIds").forEach(id -> settings.hackerAccountIds.add(id.asText("")));
        boolean newInstallId = settings.installId.isBlank();
        if (newInstallId) settings.installId = java.util.UUID.randomUUID().toString();
        if (!minecraftDirectory.equals(selectedDirectory) || newInstallId) settings.save();
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
        saved.put("offlineMode", offlineMode);
        saved.put("backendUrl", backendUrl);
        saved.put("backendToken", backendToken);
        saved.put("autoUpdate", autoUpdate);
        saved.put("debugMode", debugMode);
        saved.put("installId", installId);
        var admins = saved.putArray("adminUsers");
        adminUsers.forEach(admins::add);
        var hackers = saved.putArray("hackerAccountIds");
        hackerAccountIds.forEach(hackers::add);
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

    boolean offlineMode() {
        return offlineMode;
    }

    void setOfflineMode(boolean offlineMode) {
        this.offlineMode = offlineMode;
    }

    boolean debugMode() {
        return debugMode;
    }

    void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    String backendUrl() {
        return backendUrl;
    }

    void setBackendUrl(String backendUrl) {
        this.backendUrl = backendUrl == null ? "" : backendUrl;
    }

    /** Session token for the player's V-Client account; empty when signed out. */
    String backendToken() {
        return backendToken;
    }

    void setBackendToken(String backendToken) {
        this.backendToken = backendToken == null ? "" : backendToken;
    }

    /** Random id for anonymous usage counts. It is not derived from the PC or the player. */
    String installId() {
        return installId;
    }

    boolean autoUpdate() {
        return autoUpdate;
    }

    void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    boolean isAdmin(PlayerAccount account) {
        return account != null && account.type() == PlayerAccount.Type.ELY_BY
                && adminUsers.stream().anyMatch(name -> name.equalsIgnoreCase(account.name()));
    }

    boolean hackerSelected(PlayerAccount account) {
        return account != null && hackerAccountIds.contains(account.id());
    }

    void setHackerSelected(PlayerAccount account, boolean hacker) {
        if (account == null) return;
        if (hacker) hackerAccountIds.add(account.id());
        else hackerAccountIds.remove(account.id());
    }

    /** Cheat modules are enabled only for an admin account that chose Hacker / Cheater. */
    boolean cheatsEnabled(PlayerAccount account) {
        return isAdmin(account) && hackerSelected(account);
    }

    static int maxMemory() {
        return Math.max(4, (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024 * 1024)) + 2);
    }

    private static int defaultMemory() {
        return Math.clamp((int) (Runtime.getRuntime().maxMemory() / (1024 * 1024 * 1024)) / 2, 2, maxMemory());
    }
}
