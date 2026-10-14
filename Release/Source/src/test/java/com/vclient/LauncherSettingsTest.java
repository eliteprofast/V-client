package com.vclient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LauncherSettingsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void correctsSelectedVersionSubfolderToMinecraftGameRoot() throws IOException {
        Path gameDirectory = temporaryDirectory.resolve("Minecraft").resolve("game");
        Path profileDirectory = gameDirectory.resolve("versions").resolve("Fabric 1.21.11");
        Files.createDirectories(profileDirectory);
        Files.createFile(profileDirectory.resolve("Fabric 1.21.11.json"));

        assertEquals(gameDirectory, LauncherSettings.normalizeMinecraftDirectory(profileDirectory));
    }

    @Test
    void leavesGameRootAndUnrecognizedFoldersUnchanged() throws IOException {
        Path gameDirectory = temporaryDirectory.resolve("Minecraft").resolve("game");
        Path versionDirectory = gameDirectory.resolve("versions");
        Files.createDirectories(versionDirectory);
        Path nonProfile = versionDirectory.resolve("Fabric 1.21.11");
        Files.createDirectories(nonProfile);

        assertEquals(gameDirectory, LauncherSettings.normalizeMinecraftDirectory(gameDirectory));
        assertEquals(nonProfile, LauncherSettings.normalizeMinecraftDirectory(nonProfile));
    }

    @Test
    void privateMinecraftDirectoryLivesUnderAppDataVClient() {
        Path appData = temporaryDirectory.resolve("AppData").resolve("Roaming");
        assertEquals(appData.resolve("V-Client").resolve("minecraft"),
                LauncherSettings.privateMinecraftDirectory(appData));
    }

    @Test
    void migratesOldLauncherAndDefaultMinecraftDirectoriesToPrivateFolder() {
        Path appData = temporaryDirectory.resolve("AppData").resolve("Roaming");
        Path privateDirectory = LauncherSettings.privateMinecraftDirectory(appData);

        assertEquals(privateDirectory,
                LauncherSettings.migrateLegacyDirectory(appData.resolve("V-Client"), appData));
        assertEquals(privateDirectory, LauncherSettings.migrateLegacyDirectory(appData.resolve(".minecraft"), appData));
    }

    @Test
    void keepsAnExplicitCustomMinecraftDirectory() throws IOException {
        Path appData = temporaryDirectory.resolve("AppData").resolve("Roaming");
        Path customDirectory = temporaryDirectory.resolve("CustomMinecraft");
        Path profileDirectory = customDirectory.resolve("versions").resolve("1.21.11");
        Files.createDirectories(profileDirectory);
        Files.createFile(profileDirectory.resolve("1.21.11.json"));

        assertEquals(customDirectory, LauncherSettings.migrateLegacyDirectory(customDirectory, appData));
    }

    @Test
    void selectingVersionSubfolderNormalizesBeforeApplyingFolderMigration() throws IOException {
        Path appData = temporaryDirectory.resolve("AppData").resolve("Roaming");
        Path versionDirectory = appData.resolve("V-Client").resolve("versions").resolve("1.21.11");
        Files.createDirectories(versionDirectory);
        Files.createFile(versionDirectory.resolve("1.21.11.json"));

        Path normalized = LauncherSettings.normalizeMinecraftDirectory(versionDirectory);
        assertEquals(LauncherSettings.privateMinecraftDirectory(appData),
                LauncherSettings.migrateLegacyDirectory(normalized, appData));
    }
}
