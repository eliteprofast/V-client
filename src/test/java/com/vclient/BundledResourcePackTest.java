package com.vclient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledResourcePackTest {
    @TempDir
    Path minecraft;

    @Test
    void installsAndEnablesOnFirstRunOnly() throws Exception {
        List<String> output = new ArrayList<>();
        assertTrue(BundledResourcePack.ensureInstalled(minecraft, output::add));
        assertTrue(Files.size(minecraft.resolve("resourcepacks").resolve(BundledResourcePack.FILE_NAME)) > 1000);
        assertEquals("resourcePacks:[\"vanilla\",\"file/Ultra Smooth PvP.zip\"]",
                Files.readAllLines(minecraft.resolve("options.txt")).getFirst());

        // the player turns it off in Options: the launcher must not turn it back on
        Files.writeString(minecraft.resolve("options.txt"), "resourcePacks:[\"vanilla\"]\n");
        output.clear();
        assertFalse(BundledResourcePack.ensureInstalled(minecraft, output::add));
        assertEquals("resourcePacks:[\"vanilla\"]", Files.readAllLines(minecraft.resolve("options.txt")).getFirst());
    }

    @Test
    void keepsTheirOtherPacksAndSettings() throws Exception {
        Files.writeString(minecraft.resolve("options.txt"),
                "renderDistance:12\nresourcePacks:[\"vanilla\",\"file/Mine.zip\"]\nfov:0.5\n");
        BundledResourcePack.ensureInstalled(minecraft, line -> {});
        assertEquals(List.of("renderDistance:12",
                "resourcePacks:[\"vanilla\",\"file/Mine.zip\",\"file/Ultra Smooth PvP.zip\"]", "fov:0.5"),
                Files.readAllLines(minecraft.resolve("options.txt")));
    }
}
