package com.vclient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * While Minecraft runs (and for a short time after it closes, while recorders finish their files) this looks in
 * the usual recorder folders for new videos and imports them into the {@link RecordingLibrary}.
 */
final class RecordingWatcher implements GameSession.Listener {
    private static final long POLL_SECONDS = 5;
    private static final long GRACE_MILLIS = 90_000;

    private final RecordingLibrary library;
    private final Supplier<Path> minecraftDirectory;
    private final Consumer<Path> onImported;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "vclient-recording-watcher");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Path, Long> lastSizes = new HashMap<>();
    private ScheduledFuture<?> task;
    private volatile long since;
    private volatile long stopAt = Long.MAX_VALUE;

    RecordingWatcher(RecordingLibrary library, Supplier<Path> minecraftDirectory, Consumer<Path> onImported) {
        this.library = library;
        this.minecraftDirectory = minecraftDirectory;
        this.onImported = onImported;
    }

    @Override
    public synchronized void started(String versionId, long startedAtMillis) {
        since = startedAtMillis - 2_000;
        stopAt = Long.MAX_VALUE;
        lastSizes.clear();
        if (task == null || task.isDone()) {
            task = scheduler.scheduleWithFixedDelay(this::poll, POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Override
    public synchronized void ended(String versionId, long startedAtMillis, long endedAtMillis) {
        stopAt = endedAtMillis + GRACE_MILLIS;
    }

    private void poll() {
        try {
            List<Path> sources = RecordingLibrary.watchedFolders(minecraftDirectory.get());
            for (Path file : library.findImportable(sources, since, lastSizes)) {
                try {
                    onImported.accept(library.importFile(file));
                } catch (IOException e) {
                    // Try again on the next round; the recorder may still hold the file.
                }
            }
        } catch (RuntimeException e) {
            // Never let one bad round stop the watcher.
        }
        if (System.currentTimeMillis() > stopAt) {
            synchronized (this) {
                if (task != null) task.cancel(false);
            }
        }
    }
}
