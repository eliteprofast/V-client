package com.vclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

/**
 * The launcher's side of the cosmetics system. It lists the same catalog the in-game menu shows (the
 * catalog bundled in the mod plus the backend's) and reads/writes the same equipped-cosmetics file
 * ({@code <game>/config/vclient-cosmetics.json}) the mod loads at start-up, so choices made here show up
 * in the game. Previews with artwork stay in the game; the launcher only manages what is equipped.
 */
final class CosmeticsStore {
    /** The six categories, in menu order. */
    static final List<String> CATEGORIES = List.of("cape", "wings", "hat", "emote", "animated", "trail");
    private static final Map<String, String> FIXED_SLOT = Map.of("cape", "CAPE", "wings", "WINGS", "hat", "HAT",
            "emote", "EMOTE", "trail", "TRAIL");
    private static final List<String> SLOTS = List.of("CAPE", "WINGS", "HAT", "TRAIL", "EMOTE");
    private static final String MOD_JAR = "/vclient-fabric/libraries/com/vclient/vclient-mod/1.0.0/vclient-mod-1.0.0.jar";
    private static final String BUILTIN_ENTRY = "vclient/builtin-cosmetics.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    record Item(String id, String name, String category, String slot, String tier, String description,
                long startsAt, long endsAt, int sort) {
        boolean windowOpen(long nowSeconds) {
            return (startsAt == 0 || nowSeconds >= startsAt) && (endsAt == 0 || nowSeconds < endsAt);
        }
    }

    private CosmeticsStore() {}

    // ---- catalog ---------------------------------------------------------------------------------

    /** The cosmetics bundled inside the mod; empty if the bundled mod cannot be read. */
    static List<Item> builtIn() {
        try (InputStream raw = CosmeticsStore.class.getResourceAsStream(MOD_JAR)) {
            if (raw == null) return List.of();
            try (JarInputStream jar = new JarInputStream(raw)) {
                for (ZipEntry entry; (entry = jar.getNextEntry()) != null; ) {
                    if (entry.getName().equals(BUILTIN_ENTRY)) return parse(JSON.readTree(jar.readAllBytes()));
                }
            }
        } catch (IOException e) {
            // The remote catalog and the in-game menu still work.
        }
        return List.of();
    }

    /** Parses a catalog document; entries this launcher cannot understand are skipped. */
    static List<Item> parse(JsonNode document) {
        List<Item> items = new ArrayList<>();
        for (JsonNode node : document.path("items")) {
            String id = node.path("id").asText("").toLowerCase(Locale.ROOT);
            String category = node.path("category").asText("").toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_.-]{1,64}") || !CATEGORIES.contains(category)) continue;
            String slot = FIXED_SLOT.get(category);
            if (slot == null) slot = node.path("definition").path("slot").asText("").toUpperCase(Locale.ROOT);
            if (!SLOTS.contains(slot)) continue;
            String tier = node.path("tier").asText("free").toLowerCase(Locale.ROOT);
            if (!List.of("free", "premium", "event").contains(tier)) tier = "premium";
            items.add(new Item(id, node.path("name").asText(id), category, slot, tier,
                    node.path("description").asText(""), node.path("startsAt").asLong(0),
                    node.path("endsAt").asLong(0), node.path("sort").asInt(1000)));
        }
        return items;
    }

    /** Bundled items overlaid with the backend's (same id wins), minus anything the backend retired. */
    static List<Item> merge(List<Item> builtIn, JsonNode remoteDocument) {
        Map<String, Item> merged = new LinkedHashMap<>();
        for (Item item : builtIn) merged.put(item.id(), item);
        if (remoteDocument != null) {
            for (Item item : parse(remoteDocument)) merged.put(item.id(), item);
            for (JsonNode removed : remoteDocument.path("removed")) merged.remove(removed.asText());
        }
        List<Item> sorted = new ArrayList<>(merged.values());
        sorted.sort(Comparator.comparingInt(Item::sort).thenComparing(Item::name, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    /** Same rule as the in-game menu: free always, event while open or owned, premium only when owned. */
    static boolean unlocked(Item item, Set<String> owned, long nowSeconds) {
        return switch (item.tier()) {
            case "free" -> item.windowOpen(nowSeconds);
            case "event" -> item.windowOpen(nowSeconds) || owned.contains(item.id());
            default -> owned.contains(item.id()) && (item.windowOpen(nowSeconds) || item.endsAt() == 0);
        };
    }

    // ---- equipped cosmetics file shared with the mod -----------------------------------------------

    static Path configFile(Path minecraftDirectory) {
        return minecraftDirectory.resolve("config").resolve("vclient-cosmetics.json");
    }

    /** Equipped item id per slot name ({@code CAPE}, {@code WINGS}, ...). */
    static Map<String, String> equipped(Path file) {
        Map<String, String> result = new LinkedHashMap<>();
        JsonNode root = read(file);
        root.path("equipped").fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
        return result;
    }

    static void equip(Path file, String slot, String id) throws IOException {
        ObjectNode root = writable(file);
        ((ObjectNode) root.get("equipped")).put(slot, id);
        ArrayNode hidden = (ArrayNode) root.get("hidden");
        for (int i = hidden.size() - 1; i >= 0; i--) if (slot.equals(hidden.get(i).asText())) hidden.remove(i);
        write(file, root);
    }

    static void unequip(Path file, String slot) throws IOException {
        ObjectNode root = writable(file);
        ((ObjectNode) root.get("equipped")).remove(slot);
        write(file, root);
    }

    private static JsonNode read(Path file) {
        try {
            if (Files.isRegularFile(file)) return JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            // Treat an unreadable file as empty; the mod does the same.
        }
        return JSON.createObjectNode();
    }

    private static ObjectNode writable(Path file) {
        JsonNode existing = read(file);
        ObjectNode root = existing instanceof ObjectNode object ? object : JSON.createObjectNode();
        if (!(root.get("equipped") instanceof ObjectNode)) root.putObject("equipped");
        if (!(root.get("hidden") instanceof ArrayNode)) root.putArray("hidden");
        return root;
    }

    private static void write(Path file, ObjectNode root) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".vclient-", ".tmp");
        try {
            Files.writeString(temporary, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                    StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
