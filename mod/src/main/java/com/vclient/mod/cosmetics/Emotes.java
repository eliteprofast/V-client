package com.vclient.mod.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vclient.mod.VClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keyframe emotes. The definition is
 * <pre>
 * "emote": { "duration": 40, "loop": false, "cancelOnMove": true, "tracks": [
 *     { "part": "rightArm", "channel": "rotation|position", "axis": "x|y|z", "ease": "linear|smooth",
 *       "absolute": false, "keys": [[tick, value], ...] } ] }
 * </pre>
 * Rotation values are degrees, position values are pixels. Tracks add to the normal pose unless
 * {@code absolute} is set. Only the model pose changes: nothing is sent to the server and the
 * player's movement and hitbox are untouched.
 */
public final class Emotes {
    private record Track(String part, boolean rotation, int axis, boolean smooth, boolean absolute, float[][] keys) {}

    private record Spec(float duration, boolean loop, boolean cancelOnMove, List<Track> tracks) {}

    private static final Map<String, Spec> SPECS = new ConcurrentHashMap<>();
    private static volatile Cosmetic playing;
    private static volatile long startNanos;

    private Emotes() {}

    public static boolean active() {
        return playing != null;
    }

    public static Cosmetic current() {
        return playing;
    }

    public static void play(Cosmetic cosmetic) {
        if (cosmetic == null || cosmetic.slot() != CosmeticSlot.EMOTE || spec(cosmetic) == null) return;
        startNanos = System.nanoTime();
        playing = cosmetic;
        VClientMod.LOGGER.info("[Cosmetics] Playing emote {}", cosmetic.id());
    }

    public static void stop() {
        playing = null;
    }

    /** Ends emotes that asked to stop when the player moves. */
    public static void tick(MinecraftClient client) {
        Cosmetic emote = playing;
        if (emote == null) return;
        if (client.player == null || !CosmeticState.enabled()) {
            playing = null;
            return;
        }
        Spec spec = spec(emote);
        if (spec != null && spec.cancelOnMove && client.currentScreen == null
                && (client.options.forwardKey.isPressed() || client.options.backKey.isPressed()
                || client.options.leftKey.isPressed() || client.options.rightKey.isPressed()
                || client.options.jumpKey.isPressed() || client.options.sneakKey.isPressed())) {
            playing = null;
        }
    }

    /** Offsets the model pose for the current moment of the emote. Called while posing the local player. */
    public static void pose(PlayerEntityModel model) {
        Cosmetic emote = playing;
        if (emote == null) return;
        Spec spec = spec(emote);
        if (spec == null) {
            playing = null;
            return;
        }
        float tick = (System.nanoTime() - startNanos) / 50_000_000f;
        if (tick >= spec.duration) {
            if (!spec.loop) {
                playing = null;
                return;
            }
            tick %= spec.duration;
        }
        for (Track track : spec.tracks) {
            ModelPart part = part(model, track.part);
            if (part == null) continue;
            float value = sample(track, tick);
            if (track.rotation) {
                float radians = (float) Math.toRadians(value);
                switch (track.axis) {
                    case 0 -> part.pitch = track.absolute ? radians : part.pitch + radians;
                    case 1 -> part.yaw = track.absolute ? radians : part.yaw + radians;
                    default -> part.roll = track.absolute ? radians : part.roll + radians;
                }
            } else {
                switch (track.axis) {
                    case 0 -> part.originX = track.absolute ? value : part.originX + value;
                    case 1 -> part.originY = track.absolute ? value : part.originY + value;
                    default -> part.originZ = track.absolute ? value : part.originZ + value;
                }
            }
        }
        // The outer clothing layers were copied from the base parts before this ran.
        model.hat.setTransform(model.head.getTransform());
        model.jacket.setTransform(model.body.getTransform());
        model.leftSleeve.setTransform(model.leftArm.getTransform());
        model.rightSleeve.setTransform(model.rightArm.getTransform());
        model.leftPants.setTransform(model.leftLeg.getTransform());
        model.rightPants.setTransform(model.rightLeg.getTransform());
    }

    private static float sample(Track track, float tick) {
        float[][] keys = track.keys;
        if (tick <= keys[0][0]) return keys[0][1];
        for (int i = 1; i < keys.length; i++) {
            if (tick <= keys[i][0]) {
                float span = Math.max(1.0E-4f, keys[i][0] - keys[i - 1][0]);
                float t = (tick - keys[i - 1][0]) / span;
                if (track.smooth) t = t * t * (3 - 2 * t);
                return keys[i - 1][1] + (keys[i][1] - keys[i - 1][1]) * t;
            }
        }
        return keys[keys.length - 1][1];
    }

    private static ModelPart part(PlayerEntityModel model, String name) {
        return switch (name) {
            case "head" -> model.head;
            case "body" -> model.body;
            case "rightArm" -> model.rightArm;
            case "leftArm" -> model.leftArm;
            case "rightLeg" -> model.rightLeg;
            case "leftLeg" -> model.leftLeg;
            default -> null;
        };
    }

    private static Spec spec(Cosmetic cosmetic) {
        Spec spec = SPECS.computeIfAbsent(cosmetic.cacheKey(), ignored -> {
            Spec parsed = parse(cosmetic);
            return parsed == null ? INVALID : parsed;
        });
        return spec == INVALID ? null : spec;
    }

    private static final Spec INVALID = new Spec(1, false, true, List.of());

    private static Spec parse(Cosmetic cosmetic) {
        try {
            JsonObject json = cosmetic.definition().getAsJsonObject("emote");
            List<Track> tracks = new ArrayList<>();
            for (JsonElement trackElement : json.getAsJsonArray("tracks")) {
                JsonObject track = trackElement.getAsJsonObject();
                JsonArray keyArray = track.getAsJsonArray("keys");
                float[][] keys = new float[keyArray.size()][];
                for (int i = 0; i < keys.length; i++) {
                    JsonArray key = keyArray.get(i).getAsJsonArray();
                    keys[i] = new float[]{key.get(0).getAsFloat(), key.get(1).getAsFloat()};
                }
                if (keys.length == 0) continue;
                String axis = Cosmetic.string(track, "axis", "x");
                tracks.add(new Track(Cosmetic.string(track, "part", ""),
                        !"position".equals(Cosmetic.string(track, "channel", "rotation")),
                        "y".equals(axis) ? 1 : "z".equals(axis) ? 2 : 0,
                        "smooth".equals(Cosmetic.string(track, "ease", "smooth")),
                        track.has("absolute") && track.get("absolute").getAsBoolean(), keys));
            }
            float duration = json.has("duration") ? Math.max(1, json.get("duration").getAsFloat()) : 40;
            return new Spec(duration, json.has("loop") && json.get("loop").getAsBoolean(),
                    !json.has("cancelOnMove") || json.get("cancelOnMove").getAsBoolean(), List.copyOf(tracks));
        } catch (RuntimeException e) {
            VClientMod.LOGGER.warn("[Cosmetics] Emote {} is invalid: {}", cosmetic.id(), e.toString());
            return null;
        }
    }
}
