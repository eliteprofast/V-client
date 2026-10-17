package com.vclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CosmeticsStoreTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path directory;

    @Test
    void bundledCatalogIsReadFromTheModJar() {
        List<CosmeticsStore.Item> items = CosmeticsStore.builtIn();

        assertTrue(items.stream().anyMatch(item -> item.id().equals("night_sky") && item.slot().equals("CAPE")));
        assertTrue(items.stream().anyMatch(item -> item.tier().equals("premium")));
    }

    @Test
    void backendItemsOverrideAndRetireBundledOnes() throws Exception {
        JsonNode remote = JSON.readTree("""
                {"items":[{"id":"x1","category":"animated","tier":"premium","name":"X","definition":{"slot":"wings"}},
                          {"id":"nope","category":"animated","definition":{}},
                          {"id":"alien","category":"somethingnew"}],
                 "removed":["wave"]}""");

        List<CosmeticsStore.Item> merged = CosmeticsStore.merge(CosmeticsStore.builtIn(), remote);

        assertTrue(merged.stream().anyMatch(item -> item.id().equals("x1") && item.slot().equals("WINGS")));
        assertFalse(merged.stream().anyMatch(item -> item.id().equals("wave")));
        assertFalse(merged.stream().anyMatch(item -> item.id().equals("nope") || item.id().equals("alien")));
    }

    @Test
    void premiumNeedsOwnershipAndEventsNeedAnOpenWindow() {
        CosmeticsStore.Item premium = new CosmeticsStore.Item("p", "P", "cape", "CAPE", "premium", "", 0, 0, 1);
        CosmeticsStore.Item event = new CosmeticsStore.Item("e", "E", "trail", "TRAIL", "event", "", 100, 200, 1);
        CosmeticsStore.Item free = new CosmeticsStore.Item("f", "F", "hat", "HAT", "free", "", 0, 0, 1);

        assertFalse(CosmeticsStore.unlocked(premium, Set.of(), 150));
        assertTrue(CosmeticsStore.unlocked(premium, Set.of("p"), 150));
        assertTrue(CosmeticsStore.unlocked(event, Set.of(), 150));
        assertFalse(CosmeticsStore.unlocked(event, Set.of(), 250));
        assertTrue(CosmeticsStore.unlocked(event, Set.of("e"), 250));
        assertTrue(CosmeticsStore.unlocked(free, Set.of(), 0));
    }

    @Test
    void equippedFileRoundTripsInTheFormatTheModReads() throws Exception {
        Path file = CosmeticsStore.configFile(directory);

        CosmeticsStore.equip(file, "CAPE", "night_sky");
        CosmeticsStore.equip(file, "HAT", "crown");
        CosmeticsStore.unequip(file, "HAT");

        assertEquals(Map.of("CAPE", "night_sky"), CosmeticsStore.equipped(file));
        assertTrue(JSON.readTree(file.toFile()).path("hidden").isArray());
    }
}
