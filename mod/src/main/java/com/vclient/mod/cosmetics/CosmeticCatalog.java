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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The list of cosmetics the menu shows. It starts from the catalog bundled in the mod (so the menu works
 * offline), overlays the last catalog downloaded from the backend, then refreshes that in the background.
 * Schema 1 of the server document is documented in docs/COSMETICS_API.md; unknown fields and unknown
 * categories are ignored so a newer server never breaks an older client.
 *
 * Loading is a small state machine, {@link State#LOADING} then {@link State#SUCCESS} or {@link State#FAILED}.
 * It can never stay in LOADING: every refresh has a 10-second deadline and every failure, including a
 * malformed response, ends in FAILED with a message the menu shows.
 */
public final class CosmeticCatalog {
    public enum State { LOADING, SUCCESS, FAILED }

    private static final Gson GSON = new Gson();
    private static final String BUILTIN_RESOURCE = "/vclient/builtin-cosmetics.json";
    private static final int SCHEMA = 1;
    /** Set by the launcher for admin accounts: every cosmetic is usable. Visual only, so this is not a security boundary. */
    private static final boolean UNLOCK_ALL = Boolean.getBoolean("vclient.cosmetics.unlockAll");

    private static volatile List<Cosmetic> items = List.of();
    private static volatile Map<String, Cosmetic> byId = Map.of();
    private static volatile Set<String> owned = Set.of();
    private static volatile State state = State.LOADING;
    private static volatile String status = "Loading cosmetics...";
    private static volatile String error = "";
    private static volatile int revision;
    private static final AtomicBoolean REFRESHING = new AtomicBoolean();

    private CosmeticCatalog() {}

    /** Loads the bundled and cached catalogs synchronously (both small, local) and starts an online refresh. */
    public static void init() {
        List<JsonObject> builtin = new ArrayList<>();
        try {
            builtin = readBuiltin();
        } catch (IOException | RuntimeException e) {
            CosmeticNet.logError("Could not read the bundled catalog", e);
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
            CosmeticNet.logError("Ignoring the unreadable cached catalog", e);
        }
        boolean hasBackend = !CosmeticNet.backendUrl().isEmpty();
        publish(builtin, remote, hasBackend ? "Cached catalog" : "Built-in cosmetics (no backend configured)",
                hasBackend ? State.LOADING : State.SUCCESS, "");
        VClientMod.LOGGER.info("[Cosmetics] Backend: {}", hasBackend ? CosmeticNet.backendUrl() : "none configured");
        refreshAsync();
    }

    /**
     * Fetches the catalog and the player's owned items from the backend without blocking the game. Always
     * finishes within about 10 seconds, in {@link State#SUCCESS} or {@link State#FAILED}.
     */
    public static void refreshAsync() {
        String base = CosmeticNet.backendUrl();
        if (base.isEmpty()) {
            state = State.SUCCESS;
            return;
        }
        if (!REFRESHING.compareAndSet(false, true)) return;
        state = State.LOADING;
        error = "";
        status = "Updating cosmetics...";
        VClientMod.LOGGER.info("[Cosmetics] Refreshing the catalog from {}", base);
        CosmeticNet.supply(() -> {
            try {
                fetch(base);
                return Boolean.TRUE;
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException(e);
            }
        }).whenComplete((ignored, failure) -> {
            if (failure != null) fail(base, failure);
            REFRESHING.set(false);
        });
    }

    private static void fail(String base, Throwable failure) {
        CosmeticNet.logError("Catalog refresh from " + base + " failed", failure);
        error = CosmeticNet.describe(failure);
        status = "Could not load the online catalog: " + error;
        state = State.FAILED;
        revision++;
    }

    private static void fetch(String base) throws IOException, InterruptedException {
        Path directory = CosmeticNet.directory();
        Path etagFile = directory.resolve("catalog.etag");
        String etag = Files.isRegularFile(etagFile) ? Files.readString(etagFile, StandardCharsets.UTF_8).strip() : "";
        HttpResponse<byte[]> response = CosmeticNet.get(URI.create(base + "/api/cosmetics/catalog"),
                Files.isRegularFile(directory.resolve("catalog.json")) ? etag : "");
        JsonObject remote = null;
        if (response.statusCode() == 200) {
            String text = new String(response.body(), StandardCharsets.UTF_8);
            try {
                remote = GSON.fromJson(text, JsonObject.class);
            } catch (com.google.gson.JsonParseException e) {
                String type = response.headers().firstValue("Content-Type").orElse("unknown content type");
                throw new IOException("The backend at " + base + " did not return a catalog (" + type + "). "
                        + "Check the backend address; if it is hosted on a free plan it may still be waking up.", e);
            }
            if (remote == null || !(remote.get("items") instanceof JsonArray)) {
                throw new IOException("The backend answered 200 but the document has no \"items\" list.");
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
            throw new IOException("The catalog request returned HTTP " + response.statusCode()
                    + (response.statusCode() == 404 ? " - this backend does not have the cosmetics API (is it redeployed?)" : ""));
        }

        UUID player = playerId();
        if (player != null) {
            try {
                HttpResponse<byte[]> ownedResponse = CosmeticNet.get(URI.create(
                        base + "/api/cosmetics/owned/" + player.toString().replace("-", "")), null);
                if (ownedResponse.statusCode() == 200) {
                    owned = readOwned(new String(ownedResponse.body(), StandardCharsets.UTF_8));
                    CosmeticNet.write(directory.resolve("owned.json"), ownedResponse.body());
                } else {
                    VClientMod.LOGGER.warn("[Cosmetics] Owned-items request returned HTTP {}", ownedResponse.statusCode());
                }
            } catch (IOException | RuntimeException e) {
                CosmeticNet.logError("Could not load owned cosmetics (continuing without them)", e);
            }
        } else {
            VClientMod.LOGGER.warn("[Cosmetics] No player UUID is available; skipping the owned-items request.");
        }

        List<JsonObject> builtin = readBuiltin();
        if (remote == null && Files.isRegularFile(directory.resolve("catalog.json"))) {
            remote = GSON.fromJson(Files.readString(directory.resolve("catalog.json"), StandardCharsets.UTF_8),
                    JsonObject.class);
        }
        publish(builtin, remote, "Online - updated " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                State.SUCCESS, "");
    }

    private static List<JsonObject> readBuiltin() throws IOException {
        List<JsonObject> builtin = new ArrayList<>();
        try (Reader reader = new InputStreamReader(CosmeticCatalog.class.getResourceAsStream(BUILTIN_RESOURCE),
                StandardCharsets.UTF_8)) {
            collect(GSON.fromJson(reader, JsonObject.class), builtin);
        }
        return builtin;
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

    private static void publish(List<JsonObject> builtin, JsonObject remote, String newStatus, State newState,
                                String newError) {
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
        error = newError;
        status = newStatus + " - " + sorted.size() + " items";
        state = newState;
        revision++;
        VClientMod.LOGGER.info("[Cosmetics] Catalog {}: {} items ({}).", newState, sorted.size(), newStatus);
    }

    public static List<Cosmetic> items() {
        return items;
    }

    public static Cosmetic byId(String id) {
        return id == null ? null : byId.get(id);
    }

    public static State state() {
        return state;
    }

    public static String status() {
        return status;
    }

    /** Why the last online refresh failed, or empty. */
    public static String error() {
        return error;
    }

    /** Increases whenever the item list or the load state changes, so screens know to rebuild. */
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
        if (cosmetic.startsAt() > now) {
            return "Starts " + java.time.LocalDate.ofEpochDay(cosmetic.startsAt() / 86400).format(
                    java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH));
        }
        if (!cosmetic.windowOpen(now) && cosmetic.tier() != CosmeticTier.PREMIUM) return "Event ended";
        return cosmetic.tier() == CosmeticTier.PREMIUM ? "Premium" : "Locked";
    }
}
