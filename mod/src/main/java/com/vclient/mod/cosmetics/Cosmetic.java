package com.vclient.mod.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One catalog entry. Everything that describes how it looks lives in {@link #definition()}, a JSON
 * object the client interprets (textures, model cuboids, particle settings, emote keyframes), so new
 * cosmetics are added on the server without a client update.
 */
public final class Cosmetic {
    /** A downloadable file the definition refers to by {@link #file()}. */
    public record Asset(String file, String url, String sha256, long size) {}

    private final String id;
    private final int version;
    private final CosmeticCategory category;
    private final CosmeticSlot slot;
    private final CosmeticTier tier;
    private final String name;
    private final String description;
    private final long startsAt;
    private final long endsAt;
    private final int sort;
    private final boolean builtIn;
    private final JsonObject definition;
    private final List<Asset> assets;

    private Cosmetic(String id, int version, CosmeticCategory category, CosmeticSlot slot, CosmeticTier tier,
                     String name, String description, long startsAt, long endsAt, int sort, boolean builtIn,
                     JsonObject definition, List<Asset> assets) {
        this.id = id;
        this.version = version;
        this.category = category;
        this.slot = slot;
        this.tier = tier;
        this.name = name;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.sort = sort;
        this.builtIn = builtIn;
        this.definition = definition;
        this.assets = assets;
    }

    /** Parses one catalog item; returns null for entries this client version cannot use. */
    static Cosmetic parse(JsonObject json, boolean builtIn) {
        String id = string(json, "id", "").toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_.-]{1,64}")) return null;
        CosmeticCategory category = CosmeticCategory.parse(string(json, "category", ""));
        if (category == null) return null;
        JsonObject definition = json.has("definition") && json.get("definition").isJsonObject()
                ? json.getAsJsonObject("definition") : new JsonObject();
        CosmeticSlot slot = category.fixedSlot() != null ? category.fixedSlot()
                : CosmeticSlot.parse(string(definition, "slot", ""));
        if (slot == null) return null;
        List<Asset> assets = new ArrayList<>();
        JsonElement assetList = json.get("assets");
        if (assetList instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject asset = element.getAsJsonObject();
                String file = string(asset, "file", "");
                if (file.isEmpty()) continue;
                assets.add(new Asset(file, string(asset, "url", ""),
                        string(asset, "sha256", "").toLowerCase(Locale.ROOT),
                        asset.has("size") ? asset.get("size").getAsLong() : -1));
            }
        }
        return new Cosmetic(id, json.has("version") ? json.get("version").getAsInt() : 1, category, slot,
                CosmeticTier.parse(string(json, "tier", "free")), string(json, "name", id),
                string(json, "description", ""),
                json.has("startsAt") && !json.get("startsAt").isJsonNull() ? json.get("startsAt").getAsLong() : 0,
                json.has("endsAt") && !json.get("endsAt").isJsonNull() ? json.get("endsAt").getAsLong() : 0,
                json.has("sort") ? json.get("sort").getAsInt() : 1000, builtIn, definition, List.copyOf(assets));
    }

    static String string(JsonObject json, String key, String fallback) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    public String id() {
        return id;
    }

    public int version() {
        return version;
    }

    public CosmeticCategory category() {
        return category;
    }

    public CosmeticSlot slot() {
        return slot;
    }

    public CosmeticTier tier() {
        return tier;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public int sort() {
        return sort;
    }

    public boolean builtIn() {
        return builtIn;
    }

    public JsonObject definition() {
        return definition;
    }

    public List<Asset> assets() {
        return assets;
    }

    public Asset asset(String file) {
        for (Asset asset : assets) if (asset.file().equals(file)) return asset;
        return null;
    }

    /** Whether the item's event window (if any) is open at the given Unix time in seconds. */
    public boolean windowOpen(long nowSeconds) {
        return (startsAt == 0 || nowSeconds >= startsAt) && (endsAt == 0 || nowSeconds < endsAt);
    }

    public long endsAt() {
        return endsAt;
    }

    /** Changes whenever the client has to rebuild textures or models for this item. */
    String cacheKey() {
        return id + "@" + version;
    }
}
