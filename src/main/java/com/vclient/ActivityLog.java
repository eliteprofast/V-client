package com.vclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A short local history of what happened in the launcher (launches, clips, backups...) for the Hub's activity feed. */
final class ActivityLog {
    record Entry(long epochMillis, String text) {}

    private static final int MAX_ENTRIES = 100;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path file;
    private final List<Entry> entries = new ArrayList<>();

    ActivityLog(Path file) {
        this.file = file;
        try {
            if (Files.isRegularFile(file)) {
                for (JsonNode node : JSON.readTree(file.toFile())) {
                    entries.add(new Entry(node.path("t").asLong(), node.path("text").asText()));
                }
            }
        } catch (IOException | RuntimeException e) {
            entries.clear();
        }
    }

    static Path defaultFile() {
        return PlayerStats.defaultFile().resolveSibling("activity.json");
    }

    synchronized void add(String text) {
        entries.add(new Entry(System.currentTimeMillis(), text));
        while (entries.size() > MAX_ENTRIES) entries.removeFirst();
        try {
            Files.createDirectories(file.getParent());
            ArrayNode array = JSON.createArrayNode();
            for (Entry entry : entries) array.addObject().put("t", entry.epochMillis()).put("text", entry.text());
            JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), array);
        } catch (IOException e) {
            // The feed is a convenience.
        }
    }

    /** Newest first. */
    synchronized List<Entry> recent(int count) {
        List<Entry> copy = new ArrayList<>(entries);
        java.util.Collections.reverse(copy);
        return copy.subList(0, Math.min(count, copy.size()));
    }
}
