package com.vclient.mod.cosmetics;

import com.vclient.mod.VClientMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Network and disk plumbing shared by the catalog and the asset downloads. Never touches the render thread. */
final class CosmeticNet {
    /** Largest single asset or catalog document accepted. */
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final long RETRY_AFTER_MILLIS = 30_000;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "vclient-cosmetics-net");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentHashMap<String, CompletableFuture<byte[]>> DOWNLOADS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> FAILED_AT = new ConcurrentHashMap<>();
    private static HttpClient http;

    private CosmeticNet() {}

    static Path directory() {
        return FabricLoader.getInstance().getGameDir().resolve("vclient").resolve("cosmetics");
    }

    /** The backend base URL the launcher passed with -Dvclient.backend, or empty when none is configured. */
    static String backendUrl() {
        String value = System.getProperty("vclient.backend", "");
        if (value.isBlank()) value = System.getenv().getOrDefault("VCLIENT_BACKEND_URL", "");
        value = value.strip();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.isEmpty()) return "";
        URI uri = URI.create(value);
        return uri.getHost() != null && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) ? value : "";
    }

    static void run(Runnable task) {
        POOL.execute(task);
    }

    /** Resolves a possibly relative asset address and enforces https (plain http only for local testing). */
    static URI resolve(String base, String address) throws IOException {
        URI uri;
        try {
            uri = address.isBlank() ? null : URI.create(base + "/").resolve(address);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid cosmetic asset address: " + address, e);
        }
        if (uri == null || uri.getHost() == null) throw new IOException("Cosmetic asset has no download address.");
        boolean loopback = uri.getHost().equals("localhost") || uri.getHost().equals("127.0.0.1")
                || uri.getHost().equals("[::1]");
        if (!"https".equals(uri.getScheme()) && !(loopback && "http".equals(uri.getScheme()))) {
            throw new IOException("Cosmetic downloads must use https: " + uri);
        }
        return uri;
    }

    private static synchronized HttpClient client() {
        if (http == null) {
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
        }
        return http;
    }

    static HttpResponse<byte[]> get(URI uri, String etag) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json, image/png, */*").header("User-Agent", "V-Client-Mod");
        if (etag != null && !etag.isBlank()) request.header("If-None-Match", etag);
        HttpResponse<InputStream> response = client().send(request.GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            byte[] bytes = body.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Download is larger than " + MAX_BYTES + " bytes: " + uri);
            return new FixedResponse(response, bytes);
        }
    }

    /**
     * Downloads {@code uri} once (concurrent callers share the request), verifies the SHA-256 when one is
     * given, and caches the file in {@code cacheFile}. A cached file that still matches is returned without
     * touching the network.
     */
    static CompletableFuture<byte[]> download(URI uri, String sha256, Path cacheFile) {
        String key = uri + "|" + sha256;
        Long failedAt = FAILED_AT.get(key);
        if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_AFTER_MILLIS) {
            return CompletableFuture.failedFuture(new IOException("Download failed recently; retrying shortly."));
        }
        return DOWNLOADS.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> {
            try {
                if (Files.isRegularFile(cacheFile)) {
                    byte[] cached = Files.readAllBytes(cacheFile);
                    if (sha256.isEmpty() || sha256(cached).equals(sha256)) return cached;
                }
                VClientMod.LOGGER.info("[Cosmetics] Downloading {}", uri);
                HttpResponse<byte[]> response = get(uri, null);
                if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " for " + uri);
                byte[] bytes = response.body();
                if (!sha256.isEmpty() && !sha256(bytes).equals(sha256)) {
                    throw new IOException("Checksum mismatch for " + uri);
                }
                write(cacheFile, bytes);
                VClientMod.LOGGER.info("[Cosmetics] Downloaded {} ({} bytes)", uri, bytes.length);
                return bytes;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                FAILED_AT.put(key, System.currentTimeMillis());
                VClientMod.LOGGER.warn("[Cosmetics] Could not download {}: {}", uri, e.getMessage());
                throw new java.util.concurrent.CompletionException(e);
            } finally {
                DOWNLOADS.remove(key);
            }
        }, POOL));
    }

    static void write(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".vclient-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String safeName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** A response whose body has already been read into memory. */
    private record FixedResponse(HttpResponse<InputStream> source, byte[] body) implements HttpResponse<byte[]> {
        @Override public int statusCode() { return source.statusCode(); }
        @Override public HttpRequest request() { return source.request(); }
        @Override public java.util.Optional<HttpResponse<byte[]>> previousResponse() { return java.util.Optional.empty(); }
        @Override public java.net.http.HttpHeaders headers() { return source.headers(); }
        @Override public URI uri() { return source.uri(); }
        @Override public HttpClient.Version version() { return source.version(); }
        @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return source.sslSession(); }
    }
}
