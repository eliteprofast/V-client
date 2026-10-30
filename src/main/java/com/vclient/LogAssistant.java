package com.vclient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The built-in VSMP Assistant. It is a rule-based log reader (no AI model, nothing leaves the PC): it scans the
 * newest game log, launch log and crash report for known failures and says what to do, with a one-click fix
 * where the launcher can apply it safely.
 */
final class LogAssistant {
    /** A fix the launcher itself can apply. */
    enum Fix { NONE, RAISE_MEMORY, LOWER_MEMORY, USE_JAVA_25, OPEN_FAQ_DRIVER, CLEAN_CACHE }

    record Finding(String title, String explanation, Fix fix) {}

    private static final long MAX_BYTES = 400_000;

    private LogAssistant() {}

    /** Reads the tail of each diagnostic file and diagnoses the combined text. */
    static List<Finding> diagnose(Path minecraftDirectory, int currentMemoryGb, long ramBytes) {
        StringBuilder text = new StringBuilder();
        appendTail(text, minecraftDirectory.resolve("logs").resolve("latest.log"));
        appendTail(text, minecraftDirectory.resolve("logs").resolve("vclient-launch.log"));
        newest(minecraftDirectory.resolve("crash-reports"), 3).forEach(file -> appendTail(text, file));
        return diagnose(text.toString(), currentMemoryGb, ramBytes);
    }

    static List<Finding> diagnose(String log, int currentMemoryGb, long ramBytes) {
        List<Finding> findings = new ArrayList<>();
        String lower = log.toLowerCase(Locale.ROOT);
        long ramGb = Math.max(1, ramBytes / (1024L * 1024 * 1024));

        if (lower.contains("outofmemoryerror") || lower.contains("java heap space") || lower.contains("gc overhead limit")) {
            int suggested = (int) Math.min(Math.max(currentMemoryGb + 2, 4), Math.max(2, ramGb / 2));
            findings.add(new Finding("The game ran out of memory",
                    "Minecraft was using all " + currentMemoryGb + " GB it was given. Raising it to " + suggested
                            + " GB usually fixes this" + (suggested <= currentMemoryGb
                            ? " (your PC has no more to spare: remove heavy mods or lower render distance instead)." : "."),
                    suggested > currentMemoryGb ? Fix.RAISE_MEMORY : Fix.NONE));
        }
        if (lower.contains("could not reserve enough space") || lower.contains("invalid maximum heap size")
                || lower.contains("could not create the java virtual machine")) {
            findings.add(new Finding("More memory was requested than this PC can give",
                    "Windows could not reserve " + currentMemoryGb + " GB for the game. Lower the memory in Settings.",
                    Fix.LOWER_MEMORY));
        }
        if (lower.contains("unsupportedclassversionerror") || lower.contains("class file version 69")
                || lower.contains("has been compiled by a more recent version")) {
            findings.add(new Finding("The game needs a newer Java",
                    "A game file or mod was built for a newer Java than the one used. Switch the runtime to Java 25 in Settings.",
                    Fix.USE_JAVA_25));
        }
        Matcher missing = Pattern.compile("requires (?:any version of )?(?:mod )?['\"]?([a-z0-9_-]+)['\"]?",
                Pattern.CASE_INSENSITIVE).matcher(log);
        if (lower.contains("could not find required mod") || lower.contains("incompatible mod set")
                || (lower.contains("mod resolution") && missing.find())) {
            String name = missing.reset().find() ? missing.group(1) : "a required mod";
            findings.add(new Finding("A mod is missing its dependency",
                    "Fabric reported that " + name + " is required but not installed. Install it from the Mods tab "
                            + "(for most mods this is Fabric API), or remove the mod that needs it.", Fix.NONE));
        }
        if (lower.contains("mixin apply failed") || lower.contains("mixintransformererror")
                || lower.contains("invalidinjectionexception") || lower.contains("mixin transformation")) {
            findings.add(new Finding("Two mods are fighting each other",
                    "A mixin failed to apply, which means a mod is incompatible with this Minecraft version or "
                            + "another mod. Remove the most recently added mod in the Mods tab and try again.", Fix.NONE));
        }
        if (lower.contains("glfw error") || lower.contains("pixel format not accepted")
                || lower.contains("opengl") && lower.contains("failed") || lower.contains("exception_access_violation")
                || lower.contains("atio6axx") || lower.contains("nvoglv64") || lower.contains("ig9icd64")) {
            findings.add(new Finding("A graphics driver problem",
                    "The game crashed inside the graphics driver. Update your GPU driver, and turn off overlays "
                            + "(Discord, GeForce, MSI Afterburner) while playing.", Fix.OPEN_FAQ_DRIVER));
        }
        if (lower.contains("unknownhostexception") || lower.contains("connect timed out")
                || lower.contains("failed to download") || lower.contains("sslhandshakeexception")) {
            findings.add(new Finding("A download or connection failed",
                    "The launcher could not reach a server. Check your internet, turn the VPN off and on again in "
                            + "Settings, and retry.", Fix.NONE));
        }
        if (lower.contains("sha-1") && lower.contains("mismatch") || lower.contains("zip file is empty")
                || lower.contains("zipexception") || lower.contains("corrupt")) {
            findings.add(new Finding("A game file is damaged",
                    "A downloaded file did not match what was expected. Clearing the cache and launching again "
                            + "re-downloads it.", Fix.CLEAN_CACHE));
        }
        if (lower.contains("exit code -1073740791") || lower.contains("exited with code -1073740791")) {
            findings.add(new Finding("The game process crashed hard (0xC0000409)",
                    "This is almost always an overlay or driver conflict. Close recording/overlay programs and update the GPU driver.",
                    Fix.OPEN_FAQ_DRIVER));
        }
        if (findings.isEmpty()) {
            boolean crashed = lower.contains("---- minecraft crash report ----") || lower.contains("exited with code");
            findings.add(crashed
                    ? new Finding("A crash was found, but not one the assistant knows",
                    "Open the Error logs tab and share the newest crash report in the VSMP Discord.", Fix.NONE)
                    : new Finding("No problems found",
                    "The newest logs show no known crash or launch error.", Fix.NONE));
        }
        return findings;
    }

    /** Whether the newest crash report is recent enough to count as a crash from the last launches. */
    static String latestCrashSummary(Path minecraftDirectory) {
        return newest(minecraftDirectory.resolve("crash-reports"), 1).stream().findFirst().map(file -> {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line.startsWith("Description:")) return file.getFileName() + " - " + line.substring(12).strip();
                }
            } catch (IOException e) {
                // Fall through.
            }
            return file.getFileName().toString();
        }).orElse("");
    }

    private static List<Path> newest(Path directory, int count) {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong((Path file) -> {
                        try {
                            return Files.getLastModifiedTime(file).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).reversed()).limit(count).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static void appendTail(StringBuilder out, Path file) {
        try {
            if (!Files.isRegularFile(file)) return;
            byte[] all = Files.readAllBytes(file);
            int from = (int) Math.max(0, all.length - MAX_BYTES);
            out.append(new String(all, from, all.length - from, StandardCharsets.UTF_8)).append('\n');
        } catch (IOException e) {
            // An unreadable file is simply not diagnosed.
        }
    }
}
