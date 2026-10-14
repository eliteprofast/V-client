package com.vclient;

public final class LaunchProbe {
    private LaunchProbe() {}

    public static void main(String[] arguments) {
        System.out.println("MINECRAFT_MAIN_STARTED");
        System.out.println("CLASSPATH=" + System.getProperty("java.class.path"));
        for (String argument : arguments) System.out.println("ARG=" + argument);
    }
}
