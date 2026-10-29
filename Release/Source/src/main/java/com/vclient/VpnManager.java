package com.vclient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs a real VPN by driving WireGuard's Windows tunnel service with a config the player imports (from their VPN
 * provider or their own server). The tunnel is system-wide, so Minecraft's traffic goes through it too. Starting and
 * stopping the service needs administrator rights, so those two calls trigger one Windows UAC prompt.
 */
final class VpnManager {
    /** Also the tunnel name: WireGuard names the service "WireGuardTunnel$" + the config file name. */
    private static final String TUNNEL = "vclient-vpn";
    private static final String SERVICE = "WireGuardTunnel$" + TUNNEL;
    private static final Path CONFIG = Path.of(
            System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
            "V-Client", "vpn", TUNNEL + ".conf");

    private static final String DOWNLOAD_INDEX = "https://download.wireguard.com/windows-client/";
    private static final Pattern MSI_NAME = Pattern.compile("wireguard-amd64-([0-9]+(?:\\.[0-9]+)*)\\.msi");
    private static final HttpClient HTTP = PlatformTls.httpClientBuilder(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private VpnManager() {}

    static Path wireGuardExecutable() {
        for (String root : new String[]{System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"),
                "C:\\Program Files"}) {
            if (root == null) continue;
            Path exe = Path.of(root, "WireGuard", "wireguard.exe");
            if (Files.isRegularFile(exe)) return exe;
        }
        return null;
    }

    static boolean hasConfig() {
        return Files.isRegularFile(CONFIG);
    }

    /** Checks the file looks like a WireGuard client config, then stores it (replacing any previous one). */
    static void importConfig(Path source) throws IOException {
        String text = Files.readString(source, StandardCharsets.UTF_8);
        String lower = text.toLowerCase(Locale.ROOT);
        if (!lower.contains("[interface]") || !lower.contains("[peer]") || !lower.contains("privatekey")
                || !lower.contains("endpoint")) {
            throw new IOException("That is not a WireGuard client config (it needs [Interface], PrivateKey, "
                    + "[Peer] and Endpoint).");
        }
        Files.createDirectories(CONFIG.getParent());
        Files.writeString(CONFIG, text, StandardCharsets.UTF_8);
    }

    static void removeConfig() throws IOException {
        Files.deleteIfExists(CONFIG);
    }

    /** True while the tunnel service is running. Querying needs no administrator rights. */
    static boolean isConnected() {
        try {
            Process process = new ProcessBuilder("sc.exe", "query", SERVICE).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(10, TimeUnit.SECONDS);
            return output.contains("RUNNING");
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Downloads the latest official WireGuard installer, checks it carries a valid WireGuard LLC signature, and
     * installs it silently (one UAC prompt).
     */
    private static Path installWireGuard(Consumer<String> log) throws IOException {
        log.accept("WireGuard is missing; downloading the official installer...");
        Path msi;
        try {
            String listing = fetch(HttpRequest.newBuilder(URI.create(DOWNLOAD_INDEX)).timeout(Duration.ofSeconds(30))
                    .build(), HttpResponse.BodyHandlers.ofString(), log).body();
            Matcher matcher = MSI_NAME.matcher(listing);
            String best = null;
            int[] bestVersion = null;
            while (matcher.find()) {
                int[] version = parseVersion(matcher.group(1));
                if (bestVersion == null || compareVersions(version, bestVersion) > 0) {
                    best = matcher.group();
                    bestVersion = version;
                }
            }
            if (best == null) throw new IOException("Could not find the WireGuard installer on wireguard.com.");
            Path directory = Files.createTempDirectory("vclient-wireguard");
            msi = directory.resolve(best);
            fetch(HttpRequest.newBuilder(URI.create(DOWNLOAD_INDEX + best)).timeout(Duration.ofMinutes(5)).build(),
                    HttpResponse.BodyHandlers.ofFile(msi), log);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted.", e);
        }
        try {
            Process check = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "$s = Get-AuthenticodeSignature -LiteralPath '" + msi.toString().replace("'", "''") + "';"
                            + " if ($s.Status -eq 'Valid' -and $s.SignerCertificate.Subject -match 'WireGuard LLC')"
                            + " { exit 0 } else { exit 1 }")
                    .redirectErrorStream(true).start();
            check.getInputStream().readAllBytes();
            if (!check.waitFor(60, TimeUnit.SECONDS) || check.exitValue() != 0) {
                throw new IOException("The downloaded WireGuard installer is not signed by WireGuard LLC; "
                        + "refusing to run it.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted.", e);
        }
        log.accept("Installing WireGuard (Windows will ask for administrator permission)...");
        runElevated(Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "System32", "msiexec.exe"),
                "/i \"" + msi + "\" /qn /norestart");
        Path exe = wireGuardExecutable();
        if (exe == null) throw new IOException("WireGuard did not install.");
        return exe;
    }

    private static <T> HttpResponse<T> fetch(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                                             Consumer<String> log) throws IOException, InterruptedException {
        HttpResponse<T> response = PlatformTls.sendWithRetries(HTTP, request, handler, log);
        if (response.statusCode() != 200) {
            throw new IOException("wireguard.com answered HTTP " + response.statusCode() + " for " + request.uri());
        }
        return response;
    }

    private static int[] parseVersion(String text) {
        String[] parts = text.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Integer.parseInt(parts[i]);
        return result;
    }

    private static int compareVersions(int[] a, int[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    static void connect(Consumer<String> log) throws IOException {
        if (!hasConfig()) throw new IOException("Import a WireGuard config first.");
        Path exe = wireGuardExecutable();
        if (exe == null) exe = installWireGuard(log);
        if (isConnected()) return;
        runElevated(exe, "/installtunnelservice \"" + CONFIG + "\"");
        for (int attempt = 0; attempt < 20; attempt++) {
            if (isConnected()) return;
            sleep(500);
        }
        throw new IOException("The VPN service was installed but did not start. Check the config's Endpoint and keys.");
    }

    static void disconnect() throws IOException {
        Path exe = wireGuardExecutable();
        if (exe == null) throw new IOException("WireGuard is not installed.");
        runElevated(exe, "/uninstalltunnelservice " + TUNNEL);
        for (int attempt = 0; attempt < 20; attempt++) {
            if (!isConnected()) return;
            sleep(500);
        }
        throw new IOException("The VPN service is still running.");
    }

    private static void runElevated(Path exe, String arguments) throws IOException {
        String script = "$p = Start-Process -FilePath '" + exe.toString().replace("'", "''")
                + "' -ArgumentList '" + arguments.replace("'", "''") + "' -Verb RunAs -Wait -PassThru -WindowStyle Hidden;"
                + " exit $p.ExitCode";
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", script).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Timed out waiting for the administrator prompt.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted.", e);
        }
        if (process.exitValue() != 0) {
            throw new IOException(output.contains("canceled") || output.contains("cancelled")
                    ? "Administrator permission was declined."
                    : "WireGuard failed (exit " + process.exitValue() + ")" + (output.isEmpty() ? "." : ": " + output));
        }
    }

    private static void sleep(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted.", e);
        }
    }
}
