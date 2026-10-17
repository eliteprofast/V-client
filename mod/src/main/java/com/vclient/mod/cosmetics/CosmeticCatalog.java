package com.vclient.mod.cosmetics;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vclient.mod.VClientMod;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The list of cosmetics the menu shows. It starts from the catalog bundled in the mod (so the menu works
 * offline), overlays the last catalog downloaded from the backend, then refreshes that in the background.
 * Schema 1 of the server document is documented in docs/COSMETICS_API.md; unknown fields and unknown
 * categories are ignored so a newer server never breaks an older client.
 */
public final class CosmeticCatalog {
    private static final Gson GSON = new Gson();
    private static final String BUILTIN_RESOURCE = "/vclient/builtin-cosmetics.json";
    private static final int SCHEMA = 1;
    /** Set by the launcher for admin accounts: every cosmetic is usable. Visual only, so this is not a security boundary. */
    private static final boolean UNLOCK_ALL = Boolean.getBoolean("vclient.cosmetics.unlockAll");

    private static volatile List<Cosmetic> items = List.of();
    private static volatile Map<String, Cosmetic> byId = Map.of();
    private static volatile Set<String> owned = Set.of();
    private static volatile String status = "Loading cosmetics...";
    private static volatile int revision;
    private static final AtomicBoolean REFRESHING = new AtomicBoolean();

    private CosmeticCatalog() {}

    /** Loads the bundled and cached catalogs synchronously (both small, local) and starts an online refresh. */
    public static void init() {
        List<JsonObject> builtin = new ArrayList<>();
        try (Reader reader = new InputStreamReader(CosmeticCatalog.class.getResourceAsStream(BUILTIN_RESOURCE),
                StandardCharsets.UTF_8)) {
            collect(GSON.fromJson(reader, JsonObject.class), builtin);
        } catch (IOException | RuntimeException e) {
            VClientMod.LOGGER.warn("[Cosmetics] Could not read the bundled catalog.", e);
        }
        Path cached = CosmeticNet.directory().resolve("catalog.json");
        JsonObject remote = null;
        try {
            if (Files.isRegularFile(cached)) {
                remote = GSON.fromJson(Files.readString(cached, StandardCharsets.UTF_8), JsonObject.class);
            }
            Path ownedFile = CosmeticNet.directory().resolve("owned.json");
            if (Files.isRegularFile(ownedFile)) owned = readOwned(Files.readString(ownedFile, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            VClientMod.LOGGER.warn("[Cosmetics] Ignoring unreadable cached catalog: {}", e.getMessage());
        }
        publish(builtin, remote, CosmeticNet.backendUrl().isEmpty() ? "Built-in cosmetics (no backend configured)"
                : "Cached catalog");
        refreshAsync();
    }

    /** Fetches the catalog and the player's owned items from the backend without blocking the game. */
    public static void refreshAsync() {
        String base = CosmeticNet.backendUrl();
        if (base.isEmpty() || !REFRESHING.compareAndSet(false, true)) return;
        status = "Updating cosmetics...";
        CosmeticNet.run(() -> {
            try {
                fetch(base);
            } catch (IOException e) {
                status = "Offline - showing saved cosmetics";
                VClientMod.LOGGER.warn("[Cosmetics] Catalog refresh failed: {}", e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                REFRESHING.set(false);
            }
        });
    }

    private static void fetch(String base) throws IOException, InterruptedException {
        Path directory = CosmeticNet.directory();
        Path etagFile = directory.resolve("catalog.etag");
        String etag = Files.isRegularFile(etagFile) ? Files.readString(etagFile, StandardCharsets.UTF_8).strip() : "";
        VClientMod.LOGGER.info("[Cosmetics] Requesting catalog from {}", base);
        HttpResponse<byte[]> response = CosmeticNet.get(URI.create(base + "/api/cosmetics/catalog"),
                Files.isRegularFile(directory.resolve("catalog.json")) ? etag : "");
        JsonObject remote = null;
        if (response.statusCode() == 200) {
            String text = new String(response.body(), StandardCharsets.UTF_8);
            remote = GSON.fromJson(text, JsonObject.class);
            if (remote == null || (remote.has("schema") && remote.get("schema").getAsInt() > SCHEMA + 5)) {
                throw new IOException("Unsupported catalog document.");
            }
            CosmeticNet.write(directory.resolve("catalog.json"), response.body());
            response.headers().firstValue("ETag").ifPresent(value -> {
                try {
                    CosmeticNet.write(etagFile, value.getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // The ETag only saves bandwidth.
                }
            });
        } else if (response.statusCode() == 304) {
            VClientMod.LOGGER.info("[Cosmetics] Catalog unchanged (304).");
        } else {
            throw new IOException("Catalog request returned HTTP " + response.statusCode());
        }

        UUID player = playerId();
        if (player != null) {
            try {
                HttpResponse<byte[]> ownedResponse = CosmeticNet.get(URI.create(
                        base + "/api/cosmetics/owned/" + player.toString().replace("-", "")), null);
                if (ownedResponse.statusCode() == 200) {
                    String text = new String(ownedResponse.body(), StandardCharsets.UTF_8);
                    owned = readOwned(text);
                    CosmeticNet.write(directory.resolve("owned.json"), ownedResponse.body());
                }
            } catch (IOException e) {
                VClientMod.LOGGER.warn("[Cosmetics] Could not load owned cosmetics: {}", e.getMessage());
            }
        }

        List<JsonObject> builtin = new ArrayList<>();
        try (Reader reader = new InputStreamReader(CosmeticCatalog.class.getResourceAsStream(BUILTIN_RESOURCE),
                StandardCharsets.UTF_8)) {
            collect(GSON.fromJson(reader, JsonObject.class), builtin);
        }
        if (remote == null && Files.isRegularFile(directory.resolve("catalog.json"))) {
            remote = GSON.fromJson(Files.readString(directory.resolve("catalog.json"), StandardCharsets.UTF_8),
                    JsonObject.class);
        }
        publish(builtin, remote, "Online - updated " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
    }

    private static UUID playerId() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null || client.getSession() == null ? null : client.getSession().getUuidOrNull();
    }

    private static Set<String> readOwned(String text) {
        Set<String> result = new HashSet<>();
        JsonObject json = GSON.fromJson(text, JsonObject.class);
        if (json != null && json.get("owned") instanceof JsonArray array) {
            for (JsonElement element : array) if (element.isJsonPrimitive()) result.add(element.getAsString());
        }
        return Set.copyOf(result);
    }

    private static void collect(JsonObject document, List<JsonObject> into) {
        if (document == null || !(document.get("items") instanceof JsonArray array)) return;
        for (JsonElement element : array) if (element.isJsonObject()) into.add(element.getAsJsonObject());
    }

    private static void publish(List<JsonObject> builtin, JsonObject remote, String newStatus) {
        Map<String, Cosmetic> merged = new LinkedHashMap<>();
        for (JsonObject json : builtin) {
            Cosmetic cosmetic = Cosmetic.parse(json, true);
            if (cosmetic != null) merged.put(cosmetic.id(), cosmetic);
        }
        if (remote != null) {
            List<JsonObject> remoteItems = new ArrayList<>();
            collect(remote, remoteItems);
            for (JsonObject json : remoteItems) {
                Cosmetic cosmetic = Cosmetic.parse(json, false);
                if (cosmetic != null) merged.put(cosmetic.id(), cosmetic);
            }
            if (remote.get("removed") instanceof JsonArray removed) {
                for (JsonElement element : removed) if (element.isJsonPrimitive()) merged.remove(element.getAsString());
            }
        }
        List<Cosmetic> sorted = new ArrayList<>(merged.values());
        sorted.sort(Comparator.comparingInt(Cosmetic::sort).thenComparing(Cosmetic::name, String.CASE_INSENSITIVE_ORDER));
        byId = Map.copyOf(merged);
        items = List.copyOf(sorted);
        status = newStatus + " - " + sorted.size() + " items";
        revision++;
        VClientMod.LOGGER.info("[Cosmetics] Catalog ready: {} items ({}).", sorted.size(), newStatus);
    }

    public static List<Cosmetic> items() {
        return items;
    }

    public static Cosmetic byId(String id) {
        return id == null ? null : byId.get(id);
    }

    public static String status() {
        return status;
    }

    /** Increases whenever the item list changes, so screens know to rebuild. */
    public static int revision() {
        return revision;
    }

    public static boolean owns(Cosmetic cosmetic) {
        return owned.contains(cosmetic.id());
    }

    /** Whether the player may equip the cosmetic right now. Purely a menu rule: nothing here affects gameplay. */
    public static boolean unlocked(Cosmetic cosmetic) {
        if (UNLOCK_ALL) return true;
        long now = System.currentTimeMillis() / 1000;
        return switch (cosmetic.tier()) {
            case FREE -> cosmetic.windowOpen(now);
            case EVENT -> cosmetic.windowOpen(now) || owns(cosmetic);
            case PREMIUM -> owns(cosmetic) && (cosmetic.windowOpen(now) || cosmetic.endsAt() == 0);
        };
    }

    /** Short reason shown on locked cards. */
    public static String lockReason(Cosmetic cosmetic) {
        long now = System.currentTimeMillis() / 1000;
        if (!cosmetic.windowOpen(now) && cosmetic.tier() != CosmeticTier.PREMIUM) return "Event ended";
        return cosmetic.tier() == CosmeticTier.PREMIUM ? "Premium" : "Locked";
    }
}
