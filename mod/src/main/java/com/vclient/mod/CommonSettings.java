package com.vclient.mod;

/**
 * Settings every HUD element (or every module) shares. Kept outside {@link Module} because
 * enum constants are constructed before the enum's own static fields exist.
 */
public final class CommonSettings {
    public static final String[] COLOR_NAMES = {"Gold", "White", "Aqua", "Green", "Pink", "Red", "Purple"};
    public static final int[] COLORS = {0xFFE6BD59, 0xFFFFFFFF, 0xFF55FFFF, 0xFF7CF29A, 0xFFFF8FD8, 0xFFFF6B6B,
            0xFFB58CFF};
    public static final Setting SCALE = Setting.number("scale", "Size", 100, 50, 200, 10, "%");
    public static final Setting BACKGROUND = Setting.toggle("background", "Background", true);
    public static final Setting LABEL_COLOR = Setting.choice("labelColor", "Label color", 0, COLOR_NAMES);
    public static final Setting TOGGLE_KEY = Setting.key("toggleKey", "Toggle key", Setting.NO_KEY);

    private CommonSettings() {}
}
