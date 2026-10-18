package com.vclient.mod;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Every feature shown in the Right Shift mod menu, with the options on its settings page. */
public enum Module {
    CLIENT("V-Client", "Menu key", true, Kind.ALWAYS_ON,
            Setting.key("menuKey", "Mod menu key", GLFW.GLFW_KEY_RIGHT_SHIFT)),
    MENU_BACKGROUND("Night Sky Menus", "Animated menu sky", true, Kind.FEATURE,
            Setting.toggle("comet", "Shooting comet", true),
            Setting.toggle("twinkle", "Twinkling stars", true)),
    FPS("FPS", "Frames per second", true, Kind.HUD),
    CPS("CPS", "Clicks per second", true, Kind.HUD,
            Setting.toggle("rightClicks", "Show right clicks", true)),
    PING("Ping", "Server latency", false, Kind.HUD),
    COORDINATES("Coordinates", "Position and facing", true, Kind.HUD,
            Setting.toggle("facing", "Show facing", true)),
    SPEED("Speed", "Movement speed", false, Kind.HUD,
            Setting.choice("unit", "Unit", 0, "blocks/s", "km/h")),
    CLOCK("Clock", "Real-world time", false, Kind.HUD,
            Setting.toggle("twentyFour", "24-hour time", false),
            Setting.toggle("seconds", "Show seconds", false)),
    MEMORY("Memory", "Game RAM usage", false, Kind.HUD),
    DAY_COUNTER("Day Counter", "In-game day number", false, Kind.HUD),
    SERVER_ADDRESS("Server Address", "Server IP", false, Kind.HUD),
    POTION_EFFECTS("Potion Effects", "Effects and timers", true, Kind.HUD,
            Setting.toggle("level", "Show level", true)),
    KEYSTROKES("Keystrokes", "WASD, mouse, jump", true, Kind.HUD,
            Setting.toggle("mouse", "Show mouse buttons", true),
            Setting.toggle("space", "Show space bar", true)),
    ARMOR_STATUS("Armor Status", "Armor durability", true, Kind.HUD,
            Setting.toggle("held", "Show held item", true),
            Setting.toggle("numbers", "Show durability", true)),
    ZOOM("Zoom", "Hold key to zoom", true, Kind.FEATURE,
            Setting.key("key", "Zoom key", GLFW.GLFW_KEY_C),
            Setting.number("level", "Zoom level", 4, 2, 10, 1, "x"),
            Setting.toggle("smooth", "Smooth zoom", true),
            Setting.toggle("cinematic", "Cinematic camera", true),
            Setting.toggle("scroll", "Scroll to adjust", true)),
    FULLBRIGHT("Fullbright", "See in the dark", false, Kind.FEATURE,
            Setting.number("brightness", "Brightness", 15, 2, 15, 1, "x")),
    COSMETICS("Cosmetics", "Capes, wings, hats, trails", true, Kind.FEATURE,
            Setting.key("menuKey", "Cosmetics menu key", GLFW.GLFW_KEY_K),
            Setting.key("emoteKey", "Play emote key", GLFW.GLFW_KEY_B),
            Setting.number("trailDensity", "Trail density", 100, 25, 200, 25, "%"),
            Setting.toggle("debug", "Debug mode (requests + stack traces)", false)),
    NO_HURT_CAM("No Hurt Cam", "No shake when hit", false, Kind.FEATURE),
    TOGGLE_SPRINT("Toggle Sprint", "Tap to keep sprinting", false, Kind.HUD),
    TOGGLE_SNEAK("Toggle Sneak", "Tap to keep sneaking", false, Kind.FEATURE),
    // Hacker mode only: shown and usable when the launcher started the game with -Dvclient.cheats=true.
    XRAY("X-Ray", "See ores through walls", false, Kind.CHEAT),
    REACH("Reach-Hits", "Hit from further away", false, Kind.CHEAT,
            Setting.number("distance", "Reach", 6, 3, 8, 0.5, " blocks")),
    SPEED_HACK("Speed Hack", "Move faster", false, Kind.CHEAT,
            Setting.number("multiplier", "Speed", 2, 1, 10, 0.5, "x")),
    FREECAM("Freecam", "Fly the camera around", false, Kind.CHEAT,
            Setting.number("speed", "Camera speed", 3, 1, 10, 1, "x")),
    FLY("Fly", "Fly like in creative", false, Kind.CHEAT,
            Setting.number("speed", "Flight speed", 1.5, 0.5, 5, 0.5, "x"),
            Setting.toggle("hover", "Anti-kick hover", true)),
    AUTO_SPRINT("Auto Sprint", "Always sprint while moving", false, Kind.CHEAT),
    STEP("Step", "Hop up blocks automatically", false, Kind.CHEAT),
    SPIDER("Spider", "Climb walls", false, Kind.CHEAT,
            Setting.number("speed", "Climb speed", 1, 0.5, 3, 0.5, "x")),
    HIGH_JUMP("High Jump", "Jump much higher", false, Kind.CHEAT,
            Setting.number("power", "Jump power", 2, 1.5, 5, 0.5, "x")),
    FAST_PLACE("Fast Place", "No delay between placing blocks", false, Kind.CHEAT),
    GLOW_ESP("Glow ESP", "Outline players through walls", false, Kind.CHEAT,
            Setting.toggle("mobs", "Include mobs", false)),
    HITBOXES("Hitboxes", "Bigger enemy hitboxes", false, Kind.CHEAT,
            Setting.number("size", "Extra size", 0.2, 0.1, 1, 0.1, " blocks")),
    NO_FALL("No Fall", "No fall damage", false, Kind.CHEAT),
    SAFE_WALK("Safe Walk", "Never walk off an edge", false, Kind.CHEAT),
    ANTI_KNOCKBACK("Anti Knockback", "Ignore being pushed by hits", false, Kind.CHEAT),
    AIR_JUMP("Air Jump", "Jump again in mid-air", false, Kind.CHEAT),
    GLIDE("Glide", "Hold jump to fall slowly", false, Kind.CHEAT,
            Setting.number("speed", "Fall speed", 0.1, 0.02, 0.3, 0.02, "")),
    FAST_BREAK("Fast Break", "No delay between breaking blocks", false, Kind.CHEAT),
    TIME_CHANGER("Time Changer", "Pick the time of day you see", false, Kind.CHEAT,
            Setting.choice("time", "Time", 1, "Day", "Noon", "Sunset", "Night", "Midnight", "Sunrise")),
    AUTO_WALK("Auto Walk", "Keep walking forward", false, Kind.CHEAT),
    BUNNY_HOP("Bunny Hop", "Jump automatically while running", false, Kind.CHEAT),
    LONG_JUMP("Long Jump", "Jump much further forward", false, Kind.CHEAT,
            Setting.number("boost", "Distance", 1.8, 1.2, 4, 0.2, "x")),
    FAST_CLIMB("Fast Climb", "Climb ladders and vines faster", false, Kind.CHEAT,
            Setting.number("speed", "Climb speed", 0.4, 0.2, 1, 0.1, "")),
    WATER_FLOAT("Water Float", "Walk on water", false, Kind.CHEAT),
    AUTO_RESPAWN("Auto Respawn", "Respawn the moment you die", false, Kind.CHEAT),
    ANTI_AFK("Anti AFK", "Stay active so you are not kicked", false, Kind.CHEAT,
            Setting.number("interval", "Every", 30, 10, 120, 10, " s")),
    AUTO_SNEAK("Auto Sneak", "Always sneak", false, Kind.CHEAT),
    FAST_SWIM("Fast Swim", "Swim much faster", false, Kind.CHEAT,
            Setting.number("speed", "Swim speed", 1.6, 1.2, 4, 0.2, "x"));

    private enum Kind { ALWAYS_ON, FEATURE, HUD, CHEAT }


    private final String title;
    private final String description;
    private final boolean enabledByDefault;
    private final Kind kind;
    private final List<Setting> settings;

    Module(String title, String description, boolean enabledByDefault, Kind kind, Setting... own) {
        this.title = title;
        this.description = description;
        this.enabledByDefault = enabledByDefault;
        this.kind = kind;
        List<Setting> all = new ArrayList<>(List.of(own));
        if (kind == Kind.HUD) {
            all.add(CommonSettings.SCALE);
            all.add(CommonSettings.BACKGROUND);
            all.add(CommonSettings.LABEL_COLOR);
        }
        if (kind != Kind.ALWAYS_ON) all.add(CommonSettings.TOGGLE_KEY);
        this.settings = List.copyOf(all);
    }

    /** Whether the module is a hacker-mode cheat, available only to admin accounts. */
    public boolean cheat() {
        return kind == Kind.CHEAT;
    }

    /** The modules shown in the menu: everything except cheats the player is not allowed to use. */
    public static Module[] available() {
        return java.util.Arrays.stream(values()).filter(module -> !module.cheat() || VClientMod.cheatsAllowed())
                .toArray(Module[]::new);
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
        return kind == Kind.HUD;
    }

    /** Whether the module can be switched off (V-Client's own controls cannot). */
    public boolean toggleable() {
        return kind != Kind.ALWAYS_ON;
    }

    public List<Setting> settings() {
        return settings;
    }

    public Setting setting(String id) {
        for (Setting setting : settings) if (setting.id().equals(id)) return setting;
        throw new IllegalArgumentException(name() + " has no setting " + id);
    }
}
