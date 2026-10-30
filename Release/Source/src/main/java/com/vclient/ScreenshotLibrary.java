package com.vclient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** The game's screenshots folder as a gallery source. Minecraft writes F2 screenshots there. */
final class ScreenshotLibrary {
    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg");

    record Screenshot(Path file, long modifiedMillis, long sizeBytes) {}

    private ScreenshotLibrary() {}

    static Path folder(Path minecraftDirectory) {
        return minecraftDirectory.resolve("screenshots");
    }

    static List<Screenshot> list(Path minecraftDirectory) {
        Path folder = folder(minecraftDirectory);
        List<Screenshot> shots = new ArrayList<>();
        if (!Files.isDirectory(folder)) return shots;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                int dot = name.lastIndexOf('.');
                if (dot < 0 || !EXTENSIONS.contains(name.substring(dot + 1))) continue;
                try {
                    shots.add(new Screenshot(file, Files.getLastModifiedTime(file).toMillis(), Files.size(file)));
                } catch (IOException e) {
                    // Vanished while listing.
                }
            }
        } catch (IOException e) {
            return shots;
        }
        shots.sort(Comparator.comparingLong(Screenshot::modifiedMillis).reversed());
        return shots;
    }

    /** A cheap fingerprint of the folder (count and newest time) to notice new screenshots without reloading. */
    static String fingerprint(Path minecraftDirectory) {
        List<Screenshot> shots = list(minecraftDirectory);
        return shots.size() + ":" + (shots.isEmpty() ? 0 : shots.getFirst().modifiedMillis());
    }

    static boolean delete(Path minecraftDirectory, Path file) {
        Path folder = folder(minecraftDirectory).toAbsolutePath().normalize();
        Path target = file.toAbsolutePath().normalize();
        if (!target.startsWith(folder)) return false;
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            return false;
        }
    }
}
