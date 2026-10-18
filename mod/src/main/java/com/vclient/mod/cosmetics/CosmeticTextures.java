package com.vclient.mod.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vclient.mod.VClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a cosmetic's {@code texture} definition into GPU textures, off the render thread where possible.
 * A texture comes from a downloaded PNG ({@code "asset"}) or is painted from a small recipe
 * ({@code "generator"}), optionally with several animation frames. Frames are registered once and then only
 * looked up while rendering.
 */
public final class CosmeticTextures {
    private record Entry(Identifier[] frames, int frameTicks, int width, int height) {}

    /** A load in progress; it is declared failed if it is still running after the network timeout. */
    private record Loading(long startNanos) {}

    private static final Object FAILED = new Object();
    private static final Map<String, Object> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<String, String> FAILURES = new ConcurrentHashMap<>();
    private static final Map<String, Identifier[]> REGISTERED_BY_ID = new ConcurrentHashMap<>();

    private CosmeticTextures() {}

    /** The texture frame to draw right now, or null while it is still loading or when loading failed. */
    public static Identifier current(Cosmetic cosmetic) {
        Entry entry = entry(cosmetic);
        if (entry == null) return null;
        return entry.frames[frameOf(entry)];
    }

    /**
     * Which animation frame is showing right now: -1 while the texture is not ready, 0 for a still texture.
     * Callers that cache anything built from {@link #current} must include this number in the cache key,
     * otherwise an animated texture freezes on the frame that was current when the cache was filled.
     */
    public static int frameIndex(Cosmetic cosmetic) {
        Entry entry = entry(cosmetic);
        return entry == null ? -1 : frameOf(entry);
    }

    private static int frameOf(Entry entry) {
        if (entry.frames.length == 1) return 0;
        long tick = System.nanoTime() / 50_000_000L;
        return (int) ((tick / Math.max(1, entry.frameTicks)) % entry.frames.length);
    }

    /** Pixel size of the texture sheet (used to size model UVs), or null when not loaded. */
    public static int[] size(Cosmetic cosmetic) {
        Entry entry = entry(cosmetic);
        return entry == null ? null : new int[]{entry.width, entry.height};
    }

    public static boolean failed(Cosmetic cosmetic) {
        return ENTRIES.get(cosmetic.cacheKey()) == FAILED;
    }

    /** Why the texture could not be loaded, for the menu; empty when it did not fail. */
    public static String failureReason(Cosmetic cosmetic) {
        return FAILURES.getOrDefault(cosmetic.cacheKey(), "");
    }

    /** Forgets a failure so the next draw tries again (used by the menu's Refresh button). */
    public static void retryFailed() {
        ENTRIES.entrySet().removeIf(entry -> entry.getValue() == FAILED);
        FAILURES.clear();
    }

    private static Entry entry(Cosmetic cosmetic) {
        String key = cosmetic.cacheKey();
        Object value = ENTRIES.get(key);
        if (value instanceof Entry entry) return entry;
        if (value instanceof Loading loading) {
            if ((System.nanoTime() - loading.startNanos) / 1_000_000_000L > CosmeticNet.TIMEOUT_SECONDS + 3) {
                fail(cosmetic, key, "timed out after " + CosmeticNet.TIMEOUT_SECONDS + " seconds");
            }
            return null;
        }
        if (value == null && ENTRIES.putIfAbsent(key, new Loading(System.nanoTime())) == null) load(cosmetic, key);
        return null;
    }

    private static void load(Cosmetic cosmetic, String key) {
        JsonObject spec = cosmetic.definition().get("texture") instanceof JsonObject texture ? texture : null;
        if (spec == null) {
            ENTRIES.put(key, FAILED);
            return;
        }
        int frames = Math.clamp(intValue(spec, "frames", 1), 1, 64);
        int frameTicks = Math.clamp(intValue(spec, "frameTicks", 4), 1, 200);
        int width = Math.clamp(intValue(spec, "width", 64), 1, 512);
        int height = Math.clamp(intValue(spec, "height", 32), 1, 512);
        String assetFile = Cosmetic.string(spec, "asset", "");
        if (!assetFile.isEmpty()) {
            Cosmetic.Asset asset = cosmetic.asset(assetFile);
            if (asset == null) {
                fail(cosmetic, key, "definition names asset " + assetFile + " but the catalog does not list it");
                return;
            }
            URI uri;
            try {
                uri = CosmeticNet.resolve(CosmeticNet.backendUrl(), asset.url());
            } catch (IOException e) {
                fail(cosmetic, key, e.getMessage());
                return;
            }
            String cacheName = (asset.sha256().isEmpty() ? CosmeticNet.safeName(cosmetic.cacheKey()) : asset.sha256())
                    + "-" + CosmeticNet.safeName(asset.file());
            VClientMod.LOGGER.info("[Cosmetics] Loading texture for {} from {}", cosmetic.id(), uri);
            CosmeticNet.download(uri, asset.sha256(), CosmeticNet.directory().resolve("assets").resolve(cacheName))
                    .whenComplete((bytes, error) -> {
                        if (error != null) {
                            fail(cosmetic, key, CosmeticNet.describe(error));
                            return;
                        }
                        try {
                            List<NativeImage> images = new ArrayList<>();
                            try (NativeImage sheet = NativeImage.read(bytes)) {
                                int frameHeight = sheet.getHeight() / frames;
                                for (int frame = 0; frame < frames; frame++) {
                                    NativeImage image = new NativeImage(sheet.getWidth(), frameHeight, true);
                                    for (int y = 0; y < frameHeight; y++) {
                                        for (int x = 0; x < sheet.getWidth(); x++) {
                                            image.setColorArgb(x, y, sheet.getColorArgb(x, frame * frameHeight + y));
                                        }
                                    }
                                    images.add(image);
                                }
                            }
                            register(cosmetic, key, images, frameTicks);
                        } catch (IOException | RuntimeException e) {
                            CosmeticNet.logError("Texture image for " + cosmetic.id() + " is not a valid PNG", e);
                            fail(cosmetic, key, "the downloaded file is not a valid PNG (" + e.getMessage() + ")");
                        }
                    });
            return;
        }
        if (spec.get("generator") instanceof JsonObject generator) {
            CosmeticNet.run(() -> {
                try {
                    List<NativeImage> images = new ArrayList<>();
                    for (int frame = 0; frame < frames; frame++) {
                        images.add(paint(generator, cosmetic.id(), width, height, frame, frames));
                    }
                    register(cosmetic, key, images, frameTicks);
                } catch (RuntimeException e) {
                    CosmeticNet.logError("Generating the texture for " + cosmetic.id(), e);
                    fail(cosmetic, key, String.valueOf(e.getMessage()));
                }
            });
            return;
        }
        fail(cosmetic, key, "texture needs an \"asset\" or a \"generator\"");
    }

    private static void fail(Cosmetic cosmetic, String key, String reason) {
        ENTRIES.put(key, FAILED);
        FAILURES.put(key, reason);
        VClientMod.LOGGER.warn("[Cosmetics] Texture for {} could not be loaded: {}", cosmetic.id(), reason);
    }

    /** Hands finished images to the render thread, which creates the GPU textures. */
    private static void register(Cosmetic cosmetic, String key, List<NativeImage> images, int frameTicks) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            try {
            Identifier[] ids = new Identifier[images.size()];
            for (int frame = 0; frame < ids.length; frame++) {
                ids[frame] = Identifier.of("vclient", "cosmetics/" + cosmetic.id() + "/v" + cosmetic.version() + "/" + frame);
                final int number = frame;
                client.getTextureManager().registerTexture(ids[frame], new NativeImageBackedTexture(
                        () -> "vclient cosmetic " + cosmetic.id() + " frame " + number, images.get(frame)));
            }
            Identifier[] previous = REGISTERED_BY_ID.put(cosmetic.id(), ids);
            if (previous != null) for (Identifier old : previous) client.getTextureManager().destroyTexture(old);
            NativeImage first = images.getFirst();
            ENTRIES.put(key, new Entry(ids, frameTicks, first.getWidth(), first.getHeight()));
            VClientMod.LOGGER.info("[Cosmetics] Texture ready for {} ({} frame(s), {}x{}).", cosmetic.id(),
                    ids.length, first.getWidth(), first.getHeight());
            } catch (RuntimeException e) {
                CosmeticNet.logError("Registering the texture for " + cosmetic.id(), e);
                fail(cosmetic, key, "could not create the GPU texture (" + e.getMessage() + ")");
            }
        });
    }

    // ---- recipes ---------------------------------------------------------------------------------

    private static NativeImage paint(JsonObject generator, String seed, int width, int height, int frame, int frames) {
        String type = Cosmetic.string(generator, "type", "solid");
        if (CapeArt.handles(type)) return CapeArt.paint(type, seed, width, height, frame, frames);
        int[] colors = colors(generator);
        boolean horizontal = "horizontal".equals(Cosmetic.string(generator, "direction", "vertical"));
        int size = Math.max(1, intValue(generator, "size", 2));
        int border = generator.has("border") ? parseColor(generator.get("border").getAsString(), 0) : 0;
        Random random = new Random(seed.hashCode() * 31L + frame);
        NativeImage image = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = switch (type) {
                    case "gradient" -> blend(colors, (horizontal ? x / (double) Math.max(1, width - 1)
                            : y / (double) Math.max(1, height - 1)));
                    case "stripes" -> colors[((horizontal ? y : x) / size) % colors.length];
                    case "checker" -> colors[((x / size) + (y / size)) % colors.length];
                    case "shimmer" -> {
                        double base = (horizontal ? x : y) / (double) Math.max(1, (horizontal ? width : height));
                        double sweep = frame / (double) Math.max(1, frames);
                        double distance = Math.abs(((base - sweep) % 1.0 + 1.5) % 1.0 - 0.5);
                        yield blend(colors.length > 1 ? new int[]{colors[0], colors[1]} : colors,
                                Math.max(0, 1 - distance * 6));
                    }
                    case "sparkle" -> random.nextInt(40) == 0 ? colors[colors.length - 1] : colors[0];
                    default -> colors[0];
                };
                if (border != 0 && (x == 0 || y == 0 || x == width - 1 || y == height - 1)) argb = border;
                image.setColorArgb(x, y, argb);
            }
        }
        return image;
    }

    private static int[] colors(JsonObject generator) {
        List<Integer> parsed = new ArrayList<>();
        if (generator.get("colors") instanceof JsonArray array) {
            for (JsonElement element : array) parsed.add(parseColor(element.getAsString(), 0xFFFFFFFF));
        }
        if (parsed.isEmpty()) parsed.add(0xFFFFFFFF);
        return parsed.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int blend(int[] colors, double t) {
        if (colors.length == 1) return colors[0];
        double scaled = Math.clamp(t, 0, 1) * (colors.length - 1);
        int index = Math.min(colors.length - 2, (int) scaled);
        double local = scaled - index;
        int a = colors[index];
        int b = colors[index + 1];
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            out |= ((int) Math.round(ca + (cb - ca) * local) & 0xFF) << shift;
        }
        return out;
    }

    /** "#RRGGBB" or "#AARRGGBB". */
    static int parseColor(String text, int fallback) {
        try {
            String hex = text.startsWith("#") ? text.substring(1) : text;
            long value = Long.parseLong(hex, 16);
            return hex.length() <= 6 ? (int) (0xFF000000L | value) : (int) value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static int intValue(JsonObject json, String key, int fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : fallback;
    }
}
