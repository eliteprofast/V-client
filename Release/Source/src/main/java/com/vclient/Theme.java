package com.vclient;

import java.util.List;
import java.util.Locale;

/**
 * Launcher colour themes. A theme is an accent colour plus a very dark background tinted toward it. The default
 * "Night" theme is the original gold-on-indigo look; the others are Blue, Purple, Dark, Red and a Custom RGB accent.
 */
final class Theme {
    record Preset(String id, String label, String accent, String background) {}

    static final String CUSTOM = "custom";
    static final List<Preset> PRESETS = List.of(
            new Preset("night", "Night (default)", "#e6bd59", "#08091a"),
            new Preset("blue", "Blue", "#5aa9ff", "#06101f"),
            new Preset("purple", "Purple", "#b48cff", "#0e0820"),
            new Preset("dark", "Dark", "#d4d4d8", "#050506"),
            new Preset("red", "Red", "#ff5a64", "#1a0709"));

    private static String accent = PRESETS.getFirst().accent();
    private static String background = PRESETS.getFirst().background();

    private Theme() {}

    static String accent() {
        return accent;
    }

    static String background() {
        return background;
    }

    /** Applies a preset id, or {@link #CUSTOM} with {@code customAccent} (a #rrggbb string). */
    static void apply(String id, String customAccent) {
        if (CUSTOM.equals(id) && validHex(customAccent)) {
            accent = customAccent.toLowerCase(Locale.ROOT);
            background = tintedBackground(accent);
        } else {
            Preset preset = PRESETS.stream().filter(p -> p.id().equals(id)).findFirst().orElse(PRESETS.getFirst());
            accent = preset.accent();
            background = preset.background();
        }
        ModsUi.ACCENT = accent;
        LauncherApp.setThemeColors(accent, background);
    }

    static boolean validHex(String value) {
        return value != null && value.matches("#[0-9a-fA-F]{6}");
    }

    /** {@code rgba(r,g,b,alpha)} for the current accent, for translucent highlights. */
    static String accentRgba(double alpha) {
        int[] rgb = rgb(accent);
        return "rgba(" + rgb[0] + ", " + rgb[1] + ", " + rgb[2] + ", " + alpha + ")";
    }

    static int[] rgb(String hex) {
        int value = Integer.parseInt(hex.substring(1), 16);
        return new int[]{(value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF};
    }

    /** A near-black colour carrying a hint of the accent's hue, so any custom accent still gets a readable page. */
    static String tintedBackground(String accentHex) {
        int[] rgb = rgb(accentHex);
        int max = Math.max(1, Math.max(rgb[0], Math.max(rgb[1], rgb[2])));
        int r = 5 + rgb[0] * 14 / max;
        int g = 5 + rgb[1] * 14 / max;
        int b = 5 + rgb[2] * 14 / max;
        return String.format(Locale.ROOT, "#%02x%02x%02x", r, g, b);
    }

    /** The background nudged lighter, for the layered hills and glow drawn over it. */
    static int[] lifted(String backgroundHex, int amount) {
        int[] rgb = rgb(backgroundHex);
        return new int[]{Math.min(255, rgb[0] + amount), Math.min(255, rgb[1] + amount), Math.min(255, rgb[2] + amount)};
    }

    static int[] glow() {
        // The default theme keeps its original indigo moon glow; every other theme glows in its own accent.
        return accent.equals(PRESETS.getFirst().accent()) ? new int[]{115, 99, 190} : rgb(accent);
    }
}
