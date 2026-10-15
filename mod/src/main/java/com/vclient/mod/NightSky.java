package com.vclient.mod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Random;

/**
 * The V-Client launcher's animated pixel night sky (stars, crescent moon, comet, dusk hills).
 *
 * <p>The static layers (sky, moon glow, moon, halo dots, hills) are painted once per window size
 * into two half-resolution textures; only the twinkling stars and the comet are drawn per frame.
 * Minecraft's GUI renderer compares every element with every other, so thousands of small
 * fills per frame would stall the menu.
 */
public final class NightSky {
    private static final int STAR_COUNT = 180;
    private static final double[] STAR_X = new double[STAR_COUNT];
    private static final double[] STAR_Y = new double[STAR_COUNT];
    private static final int[] STAR_SIZE = new int[STAR_COUNT];
    private static final double[] STAR_PHASE = new double[STAR_COUNT];
    private static final double[] STAR_DRIFT = new double[STAR_COUNT];
    private static final Identifier BASE_ID = Identifier.of("vclient", "night_sky_base");
    private static final Identifier HILLS_ID = Identifier.of("vclient", "night_sky_hills");
    private static final int DOWNSCALE = 2;

    private static int bakedWidth = -1;
    private static int bakedHeight = -1;
    private static int textureWidth;
    private static int textureHeight;

    static {
        // Same seed and sequence as the launcher, so the star field is identical.
        Random random = new Random(731921);
        for (int index = 0; index < STAR_COUNT; index++) {
            STAR_X[index] = random.nextDouble();
            STAR_Y[index] = random.nextDouble();
            STAR_SIZE[index] = 1 + random.nextInt(3);
            STAR_PHASE[index] = random.nextDouble() * Math.PI * 2;
            STAR_DRIFT[index] = 0.0003 + random.nextDouble() * 0.0011;
        }
    }

    private NightSky() {}

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        int scale = Math.max(1, client.getWindow().getScaleFactor());
        int width = client.getWindow().getScaledWidth() * scale;
        int height = client.getWindow().getScaledHeight() * scale;
        if (width <= 0 || height <= 0) return;
        bake(client, width, height);
        double time = System.nanoTime() / 1_000_000_000.0;
        int pixel = Math.max(1, scale / 2);

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(1.0f / scale, 1.0f / scale);
        try {
            texture(context, BASE_ID, width, height);
            stars(context, width, height, time, pixel);
            texture(context, HILLS_ID, width, height);
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    private static void texture(DrawContext context, Identifier id, int width, int height) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0, width, height,
                textureWidth, textureHeight, textureWidth, textureHeight);
    }

    private static void stars(DrawContext context, int width, int height, double time, int pixel) {
        for (int index = 0; index < STAR_COUNT; index++) {
            int x = (int) (((STAR_X[index] + time * STAR_DRIFT[index]) % 1.0) * width);
            int y = (int) (STAR_Y[index] * height);
            double twinkle = VConfig.bool(Module.MENU_BACKGROUND, "twinkle")
                    ? 0.42 + 0.58 * (0.5 + 0.5 * Math.sin(time * 1.8 + STAR_PHASE[index])) : 0.85;
            int size = STAR_SIZE[index] * pixel;
            context.fill(x, y, x + size, y + size, argb(twinkle, 234, 225, 190));
        }
        double cometPhase = (time % 13.0) / 13.0;
        if (cometPhase < 0.09 && VConfig.bool(Module.MENU_BACKGROUND, "comet")) {
            double headX = width * (0.23 + cometPhase * 5.2);
            double headY = height * (0.13 + cometPhase * 1.7);
            int color = argb((0.09 - cometPhase) * 8, 235, 220, 176);
            int thickness = 2 * pixel;
            for (int step = 0; step <= 42; step += 3) {
                double remaining = 1 - step / 42.0;
                int x = (int) (headX - 42 * pixel * remaining);
                int y = (int) (headY - 15 * pixel * remaining);
                context.fill(x, y, x + thickness, y + thickness, color);
            }
        }
    }

    /** Paints the static layers into textures whenever the window size changes. */
    private static void bake(MinecraftClient client, int width, int height) {
        if (width == bakedWidth && height == bakedHeight) return;
        bakedWidth = width;
        bakedHeight = height;
        textureWidth = Math.max(1, width / DOWNSCALE);
        textureHeight = Math.max(1, height / DOWNSCALE);

        Canvas base = new Canvas(textureWidth, textureHeight);
        paintSky(base);
        Canvas hills = new Canvas(textureWidth, textureHeight);
        paintHills(hills);
        upload(client, BASE_ID, base);
        upload(client, HILLS_ID, hills);
    }

    private static void upload(MinecraftClient client, Identifier id, Canvas canvas) {
        NativeImage image = new NativeImage(canvas.width, canvas.height, false);
        for (int y = 0; y < canvas.height; y++) {
            for (int x = 0; x < canvas.width; x++) {
                image.setColorArgb(x, y, canvas.pixels[y * canvas.width + x]);
            }
        }
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "V-Client night sky", image);
        client.getTextureManager().registerTexture(id, texture);
    }

    private static void paintSky(Canvas canvas) {
        int width = canvas.width;
        int height = canvas.height;
        canvas.fill(0, 0, width, height, rgb(8, 9, 26));

        double moonX = width * 0.76;
        double moonY = height * 0.31;
        double moonRadius = Math.max(38.0 / DOWNSCALE, Math.min(width * 0.09, height * 0.15));
        for (int layer = 12; layer >= 1; layer--) {
            double glowRadius = moonRadius * (1.0 + layer * 0.17);
            canvas.circle(moonX, moonY, glowRadius, argb(0.008 + (12 - layer) * 0.003, 115, 99, 190));
        }
        canvas.circle(moonX, moonY, moonRadius, argb(0.95, 230, 218, 168));
        double shadowRadius = moonRadius * 0.93;
        canvas.circle(moonX - moonRadius * 0.18 + shadowRadius, moonY - moonRadius * 1.10 + shadowRadius,
                shadowRadius, rgb(15, 15, 40));

        double dotSpacing = Math.max(7.0 / DOWNSCALE, moonRadius * 0.17);
        int dotSize = (int) Math.max(1, dotSpacing * 0.42);
        for (double y = moonY - moonRadius * 2.2; y <= moonY + moonRadius * 2.2; y += dotSpacing) {
            for (double x = moonX - moonRadius * 2.2; x <= moonX + moonRadius * 2.2; x += dotSpacing) {
                double distance = Math.hypot(x - moonX, y - moonY) / moonRadius;
                if (distance < 1.18 || distance > 2.15) continue;
                canvas.fill((int) x, (int) y, (int) x + dotSize, (int) y + dotSize,
                        argb((2.15 - distance) * 0.14, 157, 143, 218));
            }
        }
    }

    private static void paintHills(Canvas canvas) {
        ridge(canvas, argb(0.72, 12, 13, 34),
                new double[]{0, 0.15, 0.27, 0.42, 0.55, 0.68, 0.84, 1.0},
                new double[]{0.82, 0.77, 0.86, 0.79, 0.88, 0.78, 0.85, 0.76});
        ridge(canvas, argb(0.78, 8, 10, 26),
                new double[]{0, 0.12, 0.24, 0.38, 0.5, 0.63, 0.76, 0.9, 1.0},
                new double[]{1.0, 0.9, 0.94, 0.85, 0.94, 0.86, 0.93, 0.86, 0.92});
    }

    /** Fills the area under a polyline ridge (x and y as fractions of the canvas). */
    private static void ridge(Canvas canvas, int color, double[] xs, double[] ys) {
        for (int x = 0; x < canvas.width; x++) {
            double fraction = (x + 0.5) / canvas.width;
            int segment = 0;
            while (segment < xs.length - 2 && fraction > xs[segment + 1]) segment++;
            double span = xs[segment + 1] - xs[segment];
            double t = span <= 0 ? 0 : Math.clamp((fraction - xs[segment]) / span, 0, 1);
            int top = (int) (canvas.height * (ys[segment] + (ys[segment + 1] - ys[segment]) * t));
            canvas.fill(x, top, x + 1, canvas.height, color);
        }
    }

    /** A small ARGB software canvas with source-over blending. */
    private static final class Canvas {
        final int width;
        final int height;
        final int[] pixels;

        Canvas(int width, int height) {
            this.width = width;
            this.height = height;
            this.pixels = new int[width * height];
        }

        void fill(int x1, int y1, int x2, int y2, int color) {
            int left = Math.max(0, x1);
            int top = Math.max(0, y1);
            int right = Math.min(width, x2);
            int bottom = Math.min(height, y2);
            for (int y = top; y < bottom; y++) {
                for (int x = left; x < right; x++) blend(y * width + x, color);
            }
        }

        void circle(double centerX, double centerY, double radius, int color) {
            int top = (int) Math.floor(centerY - radius);
            int bottom = (int) Math.ceil(centerY + radius);
            for (int y = top; y < bottom; y++) {
                double middle = y + 0.5 - centerY;
                double half = Math.sqrt(Math.max(0, radius * radius - middle * middle));
                if (half <= 0) continue;
                fill((int) Math.round(centerX - half), y, (int) Math.round(centerX + half), y + 1, color);
            }
        }

        private void blend(int index, int source) {
            double sourceAlpha = (source >>> 24) / 255.0;
            if (sourceAlpha <= 0) return;
            int destination = pixels[index];
            double destinationAlpha = (destination >>> 24) / 255.0;
            double outAlpha = sourceAlpha + destinationAlpha * (1 - sourceAlpha);
            if (outAlpha <= 0) {
                pixels[index] = 0;
                return;
            }
            int red = channel(source >> 16, destination >> 16, sourceAlpha, destinationAlpha, outAlpha);
            int green = channel(source >> 8, destination >> 8, sourceAlpha, destinationAlpha, outAlpha);
            int blue = channel(source, destination, sourceAlpha, destinationAlpha, outAlpha);
            pixels[index] = ((int) Math.round(outAlpha * 255) << 24) | (red << 16) | (green << 8) | blue;
        }

        private static int channel(int source, int destination, double sourceAlpha, double destinationAlpha,
                                   double outAlpha) {
            double value = ((source & 0xFF) * sourceAlpha
                    + (destination & 0xFF) * destinationAlpha * (1 - sourceAlpha)) / outAlpha;
            return (int) Math.round(Math.clamp(value, 0, 255));
        }
    }

    private static int rgb(int red, int green, int blue) {
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int argb(double opacity, int red, int green, int blue) {
        int alpha = (int) Math.round(Math.clamp(opacity, 0, 1) * 255);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
