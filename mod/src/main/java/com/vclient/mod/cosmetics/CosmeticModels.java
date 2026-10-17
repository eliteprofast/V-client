package com.vclient.mod.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vclient.mod.VClientMod;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the small cuboid models used by hats, wings and animated cosmetics from their JSON definition:
 *
 * <pre>
 * "model": { "parts": [ {
 *     "bone": "head" | "body",          // which part of the player it follows
 *     "pivot": [x, y, z],               // pixels, relative to the bone
 *     "rotation": [x, y, z],            // degrees
 *     "mirror": true,                   // also draw a copy on the other side (wings)
 *     "cuboids": [ { "origin": [x,y,z], "size": [w,h,d], "uv": [u,v], "inflate": 0 } ],
 *     "anim": { "axis": "x|y|z", "target": "rotation|position|scale",
 *               "mode": "sine|spin|bob|pulse", "speed": 1, "amplitude": 20, "phase": 0 }
 * } ] }
 * </pre>
 *
 * A model is built once per cosmetic version and only has its transforms updated per frame.
 */
public final class CosmeticModels {
    /** One animated part of a built model. */
    private static final class PartState {
        final ModelPart part;
        final float baseX, baseY, baseZ, baseRX, baseRY, baseRZ;
        final int axis;
        final String target;
        final String mode;
        final double speed, amplitude, phase;
        final double sign;

        PartState(ModelPart part, float[] pivot, float[] rotation, JsonObject anim, double sign) {
            this.part = part;
            baseX = pivot[0];
            baseY = pivot[1];
            baseZ = pivot[2];
            baseRX = (float) Math.toRadians(rotation[0]);
            baseRY = (float) Math.toRadians(rotation[1]);
            baseRZ = (float) Math.toRadians(rotation[2]);
            if (anim == null) {
                axis = -1;
                target = mode = "";
                speed = amplitude = phase = 0;
                this.sign = 1;
            } else {
                String axisName = Cosmetic.string(anim, "axis", "y");
                axis = "x".equals(axisName) ? 0 : "z".equals(axisName) ? 2 : 1;
                target = Cosmetic.string(anim, "target", "rotation");
                mode = Cosmetic.string(anim, "mode", "sine");
                speed = number(anim, "speed", 1);
                amplitude = number(anim, "amplitude", 10);
                phase = number(anim, "phase", 0);
                this.sign = sign;
            }
        }

        void update(double seconds) {
            part.setOrigin(baseX, baseY, baseZ);
            part.setAngles(baseRX, baseRY, baseRZ);
            part.xScale = part.yScale = part.zScale = 1;
            if (axis < 0) return;
            double wave = Math.sin(Math.PI * 2 * speed * seconds + Math.toRadians(phase));
            switch (target) {
                case "position" -> {
                    double offset = ("bob".equals(mode) ? wave * 0.5 + 0.5 : wave) * amplitude * sign;
                    part.setOrigin(baseX + (axis == 0 ? (float) offset : 0), baseY + (axis == 1 ? (float) offset : 0),
                            baseZ + (axis == 2 ? (float) offset : 0));
                }
                case "scale" -> {
                    float scale = (float) (1 + wave * amplitude / 100.0);
                    if (axis == 0) part.xScale = scale;
                    else if (axis == 1) part.yScale = scale;
                    else part.zScale = scale;
                    if ("pulse".equals(mode)) part.xScale = part.yScale = part.zScale = scale;
                }
                default -> {
                    double degrees = "spin".equals(mode) ? 360.0 * speed * seconds * sign + phase : wave * amplitude * sign;
                    float radians = (float) Math.toRadians(degrees);
                    part.setAngles(baseRX + (axis == 0 ? radians : 0), baseRY + (axis == 1 ? radians : 0),
                            baseRZ + (axis == 2 ? radians : 0));
                }
            }
        }
    }

    /** A built model: one root per bone so each follows its own player body part. */
    public static final class Built {
        final ModelPart headRoot;
        final ModelPart bodyRoot;
        final List<PartState> headParts = new ArrayList<>();
        final List<PartState> bodyParts = new ArrayList<>();
        final long startNanos = System.nanoTime();

        private Built(ModelPart headRoot, ModelPart bodyRoot) {
            this.headRoot = headRoot;
            this.bodyRoot = bodyRoot;
        }

        public ModelPart head() {
            return headRoot;
        }

        public ModelPart body() {
            return bodyRoot;
        }

        /** Applies the animation for the current moment; cheap, no allocation. */
        public void animate() {
            double seconds = (System.nanoTime() - startNanos) / 1.0E9;
            for (PartState state : headParts) state.update(seconds);
            for (PartState state : bodyParts) state.update(seconds);
        }
    }

    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();
    private static final Object FAILED = new Object();

    private CosmeticModels() {}

    /** The built model for the cosmetic, or null when it has none or the texture sheet is not loaded yet. */
    public static Built get(Cosmetic cosmetic) {
        String key = cosmetic.cacheKey();
        Object cached = CACHE.get(key);
        if (cached == FAILED) return null;
        if (cached instanceof Built built) return built;
        int[] sheet = CosmeticTextures.size(cosmetic);
        if (sheet == null) return null;
        try {
            Built built = build(cosmetic, sheet[0], sheet[1]);
            CACHE.put(key, built);
            return built;
        } catch (RuntimeException e) {
            CACHE.put(key, FAILED);
            VClientMod.LOGGER.warn("[Cosmetics] Model for {} is invalid: {}", cosmetic.id(), e.toString());
            return null;
        }
    }

    private static Built build(Cosmetic cosmetic, int textureWidth, int textureHeight) {
        JsonObject model = cosmetic.definition().get("model") instanceof JsonObject json ? json : null;
        if (model == null || !(model.get("parts") instanceof JsonArray parts)) {
            throw new IllegalArgumentException("definition has no model.parts");
        }
        ModelData headData = new ModelData();
        ModelData bodyData = new ModelData();
        List<Object[]> pending = new ArrayList<>();
        int index = 0;
        for (JsonElement element : parts) {
            if (!element.isJsonObject()) continue;
            JsonObject part = element.getAsJsonObject();
            boolean head = "head".equals(Cosmetic.string(part, "bone", "body"));
            ModelData data = head ? headData : bodyData;
            float[] pivot = vector(part.get("pivot"), 0, 0, 0);
            float[] rotation = vector(part.get("rotation"), 0, 0, 0);
            JsonObject anim = part.get("anim") instanceof JsonObject json ? json : null;
            boolean mirror = part.has("mirror") && part.get("mirror").getAsBoolean();
            for (int side = 0; side < (mirror ? 2 : 1); side++) {
                boolean mirrored = side == 1;
                ModelPartBuilder builder = ModelPartBuilder.create();
                if (part.get("cuboids") instanceof JsonArray cuboids) {
                    for (JsonElement cuboidElement : cuboids) {
                        JsonObject cuboid = cuboidElement.getAsJsonObject();
                        float[] origin = vector(cuboid.get("origin"), 0, 0, 0);
                        float[] size = vector(cuboid.get("size"), 1, 1, 1);
                        float[] uv = vector(cuboid.get("uv"), 0, 0, 0);
                        float inflate = (float) number(cuboid, "inflate", 0);
                        float originX = mirrored ? -(origin[0] + size[0]) : origin[0];
                        builder.uv((int) uv[0], (int) uv[1]).mirrored(mirrored)
                                .cuboid(originX, origin[1], origin[2], size[0], size[1], size[2], new Dilation(inflate));
                    }
                }
                float[] sidePivot = mirrored ? new float[]{-pivot[0], pivot[1], pivot[2]} : pivot;
                float[] sideRotation = mirrored ? new float[]{rotation[0], -rotation[1], -rotation[2]} : rotation;
                String name = "p" + (index++);
                data.getRoot().addChild(name, builder, ModelTransform.of(sidePivot[0], sidePivot[1], sidePivot[2],
                        (float) Math.toRadians(sideRotation[0]), (float) Math.toRadians(sideRotation[1]),
                        (float) Math.toRadians(sideRotation[2])));
                pending.add(new Object[]{head, name, sidePivot, sideRotation, anim, mirrored ? -1.0 : 1.0});
            }
        }
        ModelPart headRoot = TexturedModelData.of(headData, textureWidth, textureHeight).createModel();
        ModelPart bodyRoot = TexturedModelData.of(bodyData, textureWidth, textureHeight).createModel();
        Built built = new Built(headRoot, bodyRoot);
        for (Object[] entry : pending) {
            boolean head = (Boolean) entry[0];
            ModelPart child = (head ? headRoot : bodyRoot).getChild((String) entry[1]);
            // Mirrored copies swing the opposite way on y/z so both wings flap outward together.
            double sign = (Double) entry[5];
            PartState state = new PartState(child, (float[]) entry[2], (float[]) entry[3], (JsonObject) entry[4],
                    axisFlips((JsonObject) entry[4]) ? sign : 1.0);
            (head ? built.headParts : built.bodyParts).add(state);
        }
        return built;
    }

    private static boolean axisFlips(JsonObject anim) {
        if (anim == null) return false;
        String axis = Cosmetic.string(anim, "axis", "y");
        return !"x".equals(axis) && "rotation".equals(Cosmetic.string(anim, "target", "rotation"))
                || "position".equals(Cosmetic.string(anim, "target", "")) && "x".equals(axis);
    }

    static float[] vector(JsonElement element, float x, float y, float z) {
        float[] result = {x, y, z};
        if (element instanceof JsonArray array) {
            for (int i = 0; i < 3 && i < array.size(); i++) result[i] = array.get(i).getAsFloat();
        }
        return result;
    }

    static double number(JsonObject json, String key, double fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : fallback;
    }
}
