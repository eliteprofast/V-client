package com.vclient.mod;

/** Every feature shown in the Right Shift mod menu. */
public enum Module {
    MENU_BACKGROUND("Night Sky Menus", "Launcher sky on menus", true, false),
    FPS("FPS", "Frames per second", true, true),
    CPS("CPS", "Clicks per second", true, true),
    COORDINATES("Coordinates", "Position and facing", true, true),
    PING("Ping", "Server latency", false, true),
    KEYSTROKES("Keystrokes", "WASD, mouse, jump", true, true),
    ARMOR_STATUS("Armor Status", "Armor durability", true, true),
    ZOOM("Zoom", "Hold C to zoom", true, false),
    FULLBRIGHT("Fullbright", "See in the dark", false, false),
    TOGGLE_SPRINT("Toggle Sprint", "Tap to keep sprinting", false, true),
    TOGGLE_SNEAK("Toggle Sneak", "Tap to keep sneaking", false, false);

    private final String title;
    private final String description;
    private final boolean enabledByDefault;
    private final boolean hudElement;

    Module(String title, String description, boolean enabledByDefault, boolean hudElement) {
        this.title = title;
        this.description = description;
        this.enabledByDefault = enabledByDefault;
        this.hudElement = hudElement;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public boolean enabledByDefault() {
        return enabledByDefault;
    }

    /** Whether the module draws a movable element on the in-game HUD. */
    public boolean hudElement() {
        return hudElement;
    }
}
