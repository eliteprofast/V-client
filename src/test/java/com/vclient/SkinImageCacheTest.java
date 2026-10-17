package com.vclient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SkinImageCacheTest {
    @TempDir
    Path directory;

    @Test
    void secondLoadComesFromDiskWithoutDownloading() throws Exception {
        List<String> log = new ArrayList<>();
        SkinImageCache cache = new SkinImageCache(directory, log::add);
        AtomicInteger downloads = new AtomicInteger();
        byte[] png = {1, 2, 3, 4};

        byte[] first = cache.load("https://ely.by/skin.png", url -> {
            downloads.incrementAndGet();
            return png;
        }, true);
        byte[] second = cache.load("https://ely.by/skin.png", url -> {
            downloads.incrementAndGet();
            return new byte[]{9};
        }, true);

        assertArrayEquals(png, first);
        assertArrayEquals(png, second);
        assertEquals(1, downloads.get());
        assertTrue(log.stream().anyMatch(line -> line.startsWith("Skin download started")));
        assertTrue(log.stream().anyMatch(line -> line.startsWith("Skin cache hit")));
    }

    @Test
    void cachedSkinStillLoadsWhileOffline() throws Exception {
        SkinImageCache cache = new SkinImageCache(directory, line -> {});
        cache.load("https://ely.by/skin.png", url -> new byte[]{5, 6}, true);

        assertArrayEquals(new byte[]{5, 6}, cache.load("https://ely.by/skin.png", url -> null, false));
        assertNull(cache.load("https://ely.by/other.png", url -> new byte[]{1}, false));
    }

    @Test
    void changedSkinUrlIsFetchedFreshAndFailedDownloadsAreNotCached() throws Exception {
        SkinImageCache cache = new SkinImageCache(directory, line -> {});

        assertNull(cache.load("https://ely.by/new.png", url -> null, true));
        assertArrayEquals(new byte[]{7}, cache.load("https://ely.by/new.png", url -> new byte[]{7}, true));
        assertNotEquals(SkinImageCache.key("https://ely.by/old.png"), SkinImageCache.key("https://ely.by/new.png"));
    }
}
