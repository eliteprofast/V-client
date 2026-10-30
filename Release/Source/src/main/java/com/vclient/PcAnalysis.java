package com.vclient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Reads this PC's hardware and turns it into settings advice: render distance, memory and performance mods. */
final class PcAnalysis {
    enum Tier {
        LOW("Entry-level"), MID("Mid-range"), HIGH("High-end");

        private final String label;

        Tier(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record Hardware(long ramBytes, int cpuThreads, String gpuName) {
        long ramGb() {
            return Math.max(1, Math.round(ramBytes / (1024.0 * 1024 * 1024)));
        }
    }

    record Advice(Tier tier, int renderDistance, int simulationDistance, int memoryGb, BoostProfile profile,
                  List<BoostProfile.BoostMod> mods, List<String> notes) {}

    private PcAnalysis() {}

    static Hardware detect() {
        return new Hardware(BoostProfile.physicalRamBytes(), Runtime.getRuntime().availableProcessors(), detectGpu());
    }

    /** Asks Windows for the graphics adapter name; empty when it cannot be read. */
    static String detectGpu() {
        if (!WinInput.available()) return "";
        try {
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "(Get-CimInstance Win32_VideoController | Select-Object -ExpandProperty Name) -join '; '")
                    .redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (!process.waitFor(8, TimeUnit.SECONDS)) process.destroyForcibly();
                return line == null ? "" : line.strip();
            }
        } catch (Exception e) {
            return "";
        }
    }

    static boolean integratedGraphics(String gpuName) {
        String name = gpuName.toLowerCase(Locale.ROOT);
        if (name.isBlank()) return false;
        boolean discreteName = name.contains("geforce") || name.contains("rtx") || name.contains("gtx")
                || name.contains("radeon rx") || name.contains("radeon pro") || name.contains("arc a")
                || name.contains("quadro");
        if (discreteName) return false;
        return name.contains("intel") || name.contains("uhd") || name.contains("iris") || name.contains("vega")
                || name.contains("radeon(tm) graphics") || name.contains("radeon graphics") || name.contains("basic");
    }

    static Advice advise(Hardware hardware) {
        boolean integrated = integratedGraphics(hardware.gpuName());
        long ramGb = hardware.ramGb();
        Tier tier;
        if (ramGb < 6 || hardware.cpuThreads() < 4 || integrated) tier = Tier.LOW;
        else if (ramGb >= 16 && hardware.cpuThreads() >= 8) tier = Tier.HIGH;
        else tier = Tier.MID;

        int render = switch (tier) {
            case LOW -> ramGb < 6 ? 6 : 8;
            case MID -> 12;
            case HIGH -> 18;
        };
        int simulation = Math.max(5, Math.min(render, switch (tier) {
            case LOW -> 6;
            case MID -> 8;
            case HIGH -> 12;
        }));
        BoostProfile profile = switch (tier) {
            case LOW -> BoostProfile.POTATO;
            case MID -> BoostProfile.BALANCED;
            case HIGH -> BoostProfile.ULTRA;
        };
        int memory = switch (tier) {
            case LOW -> 3;
            case MID -> 5;
            case HIGH -> 8;
        };
        // Never give the game more than half the installed memory: Windows and other programs need the rest.
        memory = (int) Math.max(2, Math.min(memory, ramGb / 2));

        java.util.ArrayList<String> notes = new java.util.ArrayList<>();
        if (ramGb < 6) notes.add("Only " + ramGb + " GB of RAM: close other programs before playing.");
        if (integrated) notes.add("Integrated graphics detected: Sodium matters most here, and shaders will be slow.");
        if (hardware.cpuThreads() < 4) notes.add("Few CPU threads: keep simulation distance low.");
        if (tier == Tier.HIGH) notes.add("Plenty of headroom: you can raise render distance further if FPS stays high.");
        return new Advice(tier, render, simulation, memory, profile, profile.mods(), notes);
    }
}
