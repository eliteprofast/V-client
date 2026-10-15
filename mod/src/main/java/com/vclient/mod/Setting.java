package com.vclient.mod;

/**
 * One adjustable option on a module's settings page. Every value is stored as a double:
 * toggles are 0/1, keys are GLFW key codes (-1 for none), and choices are list indexes.
 */
public final class Setting {
    public enum Type { TOGGLE, NUMBER, CHOICE, KEY }

    public static final int NO_KEY = -1;

    private final String id;
    private final String label;
    private final Type type;
    private final double defaultValue;
    private final double min;
    private final double max;
    private final double step;
    private final String suffix;
    private final String[] choices;

    private Setting(String id, String label, Type type, double defaultValue, double min, double max,
                    double step, String suffix, String[] choices) {
        this.id = id;
        this.label = label;
        this.type = type;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
        this.choices = choices;
    }

    public static Setting toggle(String id, String label, boolean defaultValue) {
        return new Setting(id, label, Type.TOGGLE, defaultValue ? 1 : 0, 0, 1, 1, "", new String[0]);
    }

    public static Setting number(String id, String label, double defaultValue, double min, double max,
                                 double step, String suffix) {
        return new Setting(id, label, Type.NUMBER, defaultValue, min, max, step, suffix, new String[0]);
    }

    public static Setting choice(String id, String label, int defaultIndex, String... choices) {
        return new Setting(id, label, Type.CHOICE, defaultIndex, 0, choices.length - 1, 1, "", choices);
    }

    public static Setting key(String id, String label, int defaultKey) {
        return new Setting(id, label, Type.KEY, defaultKey, NO_KEY, Integer.MAX_VALUE, 1, "", new String[0]);
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public Type type() {
        return type;
    }

    public double defaultValue() {
        return defaultValue;
    }

    /** Keeps a stored or edited value inside this setting's range. */
    public double clamp(double value) {
        if (type == Type.KEY) return value < 0 ? NO_KEY : Math.rint(value);
        double clamped = Math.clamp(value, min, max);
        return type == Type.NUMBER ? Math.round(clamped / step) * step : Math.rint(clamped);
    }

    /** The next value when the right arrow is clicked (wraps for choices). */
    public double next(double value) {
        if (type == Type.CHOICE) return value >= max ? min : value + 1;
        return clamp(value + step);
    }

    /** The previous value when the left arrow is clicked (wraps for choices). */
    public double previous(double value) {
        if (type == Type.CHOICE) return value <= min ? max : value - 1;
        return clamp(value - step);
    }

    public String display(double value) {
        return switch (type) {
            case TOGGLE -> value >= 0.5 ? "ON" : "OFF";
            case CHOICE -> choices[(int) clamp(value)];
            case KEY -> KeyNames.name((int) value);
            case NUMBER -> (step >= 1 ? Long.toString(Math.round(value))
                    : String.format(java.util.Locale.ROOT, "%.1f", value)) + suffix;
        };
    }
}
