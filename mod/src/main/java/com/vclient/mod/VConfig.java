package com.vclient.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/** Module toggles and HUD positions, saved to config/vclient.json. */
public final class VConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("vclient.json");

    private static final Map<Module, Boolean> ENABLED = new EnumMap<>(Module.class);
    private static final Map<Module, int[]> POSITIONS = new EnumMap<>(Module.class);

    private VConfig() {}

    public static boolean enabled(Module module) {
        return ENABLED.getOrDefault(module, module.enabledByDefault());
    }

    public static void setEnabled(Module module, boolean value) {
        ENABLED.put(module, value);
        save();
    }

    /** Saved HUD position, or null when the element still uses its default spot. */
    public static int[] position(Module module) {
        return POSITIONS.get(module);
    }

    public static void setPosition(Module module, int x, int y) {
        POSITIONS.put(module, new int[]{x, y});
    }

    public static void resetPositions() {
        POSITIONS.clear();
        save();
    }

    public static void load() {
        if (!Files.isRegularFile(FILE)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) return;
            JsonObject modules = root.getAsJsonObject("modules");
            JsonObject positions = root.getAsJsonObject("positions");
            for (Module module : Module.values()) {
                if (modules != null && modules.has(module.name())) {
                    ENABLED.put(module, modules.get(module.name()).getAsBoolean());
                }
                if (positions != null && positions.has(module.name())) {
                    JsonObject position = positions.getAsJsonObject(module.name());
                    POSITIONS.put(module, new int[]{position.get("x").getAsInt(), position.get("y").getAsInt()});
                }
            }
        } catch (IOException | RuntimeException e) {
            VClientMod.LOGGER.warn("Could not read V-Client settings; using defaults.", e);
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        JsonObject modules = new JsonObject();
        JsonObject positions = new JsonObject();
        for (Module module : Module.values()) {
            modules.addProperty(module.name(), enabled(module));
            int[] position = POSITIONS.get(module);
            if (position != null) {
                JsonObject saved = new JsonObject();
                saved.addProperty("x", position[0]);
                saved.addProperty("y", position[1]);
                positions.add(module.name(), saved);
            }
        }
        root.add("modules", modules);
        root.add("positions", positions);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            VClientMod.LOGGER.warn("Could not save V-Client settings.", e);
        }
    }
}
