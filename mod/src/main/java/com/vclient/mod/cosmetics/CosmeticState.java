package com.vclient.mod.cosmetics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vclient.mod.Module;
import com.vclient.mod.VClientMod;
import com.vclient.mod.VConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * What the local player has equipped, saved to config/vclient-cosmetics.json, plus the temporary
 * preview chosen in the menu. {@link #effective} is the single place the renderers ask what to draw.
 */
public final class CosmeticState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("vclient-cosmetics.json");

    private static final Map<CosmeticSlot, String> EQUIPPED = new EnumMap<>(CosmeticSlot.class);
    private static final Set<CosmeticSlot> HIDDEN = EnumSet.noneOf(CosmeticSlot.class);
    private static volatile Cosmetic preview;
    private static volatile int version;

    private CosmeticState() {}

    /** Increases on every change that affects how the player looks; renderers use it to invalidate caches. */
    public static int version() {
        return version;
    }

    /** Master switch: the Cosmetics module in the mod menu. */
    public static boolean enabled() {
        return VConfig.enabled(Module.COSMETICS);
    }

    public static void load() {
        if (!Files.isRegularFile(FILE)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) return;
            if (root.get("equipped") instanceof JsonObject equipped) {
                for (CosmeticSlot slot : CosmeticSlot.values()) {
                    JsonElement id = equipped.get(slot.name());
                    if (id != null && id.isJsonPrimitive()) EQUIPPED.put(slot, id.getAsString());
                }
            }
            if (root.get("hidden") instanceof JsonArray hidden) {
                for (JsonElement element : hidden) {
                    CosmeticSlot slot = CosmeticSlot.parse(element.getAsString());
                    if (slot != null) HIDDEN.add(slot);
                }
            }
            version++;
        } catch (IOException | RuntimeException e) {
            VClientMod.LOGGER.warn("[Cosmetics] Could not read saved cosmetics; starting with none equipped.", e);
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        JsonObject equipped = new JsonObject();
        EQUIPPED.forEach((slot, id) -> equipped.addProperty(slot.name(), id));
        JsonArray hidden = new JsonArray();
        HIDDEN.forEach(slot -> hidden.add(slot.name()));
        root.add("equipped", equipped);
        root.add("hidden", hidden);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            VClientMod.LOGGER.warn("[Cosmetics] Could not save cosmetics.", e);
        }
    }

    public static String equippedId(CosmeticSlot slot) {
        return EQUIPPED.get(slot);
    }

    public static boolean isEquipped(Cosmetic cosmetic) {
        return cosmetic.id().equals(EQUIPPED.get(cosmetic.slot()));
    }

    /** Equips an unlocked cosmetic in its slot; returns false when it is still locked. */
    public static boolean equip(Cosmetic cosmetic) {
        if (!CosmeticCatalog.unlocked(cosmetic)) return false;
        EQUIPPED.put(cosmetic.slot(), cosmetic.id());
        HIDDEN.remove(cosmetic.slot());
        VClientMod.LOGGER.info("[Cosmetics] Equipped {} ({})", cosmetic.id(), cosmetic.slot());
        changed();
        return true;
    }

    public static void unequip(CosmeticSlot slot) {
        if (EQUIPPED.remove(slot) != null) {
            VClientMod.LOGGER.info("[Cosmetics] Unequipped {}", slot);
            changed();
        }
    }

    /** Hides a whole slot without forgetting what is equipped there. */
    public static boolean slotShown(CosmeticSlot slot) {
        return !HIDDEN.contains(slot);
    }

    public static void setSlotShown(CosmeticSlot slot, boolean shown) {
        if (shown ? HIDDEN.remove(slot) : HIDDEN.add(slot)) changed();
    }

    public static void setPreview(Cosmetic cosmetic) {
        if (preview != cosmetic) {
            preview = cosmetic;
            version++;
        }
    }

    public static Cosmetic preview() {
        return preview;
    }

    private static void changed() {
        version++;
        save();
    }

    /**
     * What should be drawn in {@code slot} on the local player: the menu preview if one is active, else the
     * equipped item when it still exists, is unlocked and its slot is shown. Null means nothing.
     */
    public static Cosmetic effective(CosmeticSlot slot) {
        if (!enabled()) return null;
        Cosmetic previewing = preview;
        if (previewing != null && previewing.slot() == slot) return previewing;
        if (HIDDEN.contains(slot)) return null;
        Cosmetic equipped = CosmeticCatalog.byId(EQUIPPED.get(slot));
        return equipped != null && CosmeticCatalog.unlocked(equipped) ? equipped : null;
    }
}
