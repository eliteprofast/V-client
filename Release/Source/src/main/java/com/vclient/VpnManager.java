package com.vclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;
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

    private static final String WARP_REGISTER = "https://api.cloudflareclient.com/v0a2158/reg";
    private static final ObjectMapper JSON = new ObjectMapper();

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
        log.accept("WireGuard is missing; running the bundled official WireGuard installer...");
        try {
            runBundledInstaller();
            Path installed = wireGuardExecutable();
            if (installed != null) return installed;
            log.accept("The bundled installer did not finish installing WireGuard; trying a direct download.");
        } catch (IOException e) {
            log.accept("Bundled WireGuard installer failed (" + e.getMessage() + "); trying a direct download.");
        }
        Path msi;
        try {
            String listing = http("GET", DOWNLOAD_INDEX, null, NO_HEADERS, null, log);
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
            http("GET", DOWNLOAD_INDEX + best, null, NO_HEADERS, msi, log);
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

    /**
     * Runs WireGuard's own signed web installer (shipped inside V-Client) with administrator rights and waits for it
     * to put wireguard.exe in place. It is only run if its signature is a valid WireGuard LLC one.
     */
    private static void runBundledInstaller() throws IOException {
        Path installer = Files.createTempDirectory("vclient-wireguard").resolve("wireguard-installer.exe");
        try (var in = VpnManager.class.getResourceAsStream("/wireguard-installer.exe")) {
            if (in == null) throw new IOException("the installer is not bundled in this build");
            Files.copy(in, installer);
        }
        try {
            Process check = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "$s = Get-AuthenticodeSignature -LiteralPath '" + installer.toString().replace("'", "''") + "';"
                            + " if ($s.Status -eq 'Valid' -and $s.SignerCertificate.Subject -match 'WireGuard LLC')"
                            + " { exit 0 } else { exit 1 }")
                    .redirectErrorStream(true).start();
            check.getInputStream().readAllBytes();
            if (!check.waitFor(60, TimeUnit.SECONDS) || check.exitValue() != 0) {
                throw new IOException("the bundled installer's WireGuard LLC signature is not valid");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted.", e);
        }
        try {
            runElevated(installer, "");
        } catch (IOException e) {
            // The installer's own exit code is not reliable; what matters is whether WireGuard ended up installed.
            if (wireGuardExecutable() == null) throw e;
        }
        for (int attempt = 0; attempt < 20 && wireGuardExecutable() == null; attempt++) sleep(500);
    }

    private static final String[] NO_HEADERS = new String[0];

    /**
     * One HTTPS request; returns the body text (or saves it to {@code outFile}). Java's TLS stack goes first; if the
     * server refuses that handshake, Windows' own curl.exe (Schannel TLS) retries it.
     */
    private static String http(String method, String url, String body, String[] headers, Path outFile,
                               Consumer<String> log) throws IOException, InterruptedException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5));
            for (String header : headers) {
                int colon = header.indexOf(':');
                builder.header(header.substring(0, colon), header.substring(colon + 1).trim());
            }
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<?> response = outFile == null
                    ? PlatformTls.sendWithRetries(HTTP, builder.build(), HttpResponse.BodyHandlers.ofString(), log)
                    : PlatformTls.sendWithRetries(HTTP, builder.build(), HttpResponse.BodyHandlers.ofFile(outFile), log);
            if (response.statusCode() / 100 != 2) {
                throw new IOException(URI.create(url).getHost() + " answered HTTP " + response.statusCode());
            }
            return outFile == null ? (String) response.body() : "";
        } catch (IOException e) {
            log.accept("Java's secure connection to " + URI.create(url).getHost() + " failed ("
                    + PlatformTls.describeFailure(e) + "); retrying with Windows' own TLS (curl.exe).");
            return curl(method, url, body, headers, outFile);
        }
    }

    private static String curl(String method, String url, String body, String[] headers, Path outFile)
            throws IOException, InterruptedException {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
                Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "System32", "curl.exe").toString(),
                "-sS", "-f", "-L", "--max-time", "300", "-X", method));
        for (String header : headers) {
            command.add("-H");
            command.add(header);
        }
        if (body != null) command.addAll(java.util.List.of("--data-binary", "@-"));
        if (outFile != null) command.addAll(java.util.List.of("-o", outFile.toString()));
        command.add(url);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (var stdin = process.getOutputStream()) {
            if (body != null) stdin.write(body.getBytes(StandardCharsets.UTF_8));
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(310, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Timed out contacting " + URI.create(url).getHost() + ".");
        }
        if (process.exitValue() != 0) {
            throw new IOException("Could not reach " + URI.create(url).getHost() + ": " + output.trim());
        }
        return outFile == null ? output : "";
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

    /**
     * Registers a free Cloudflare WARP device (the same thing the wgcf tool does) and stores its WireGuard config,
     * so the VPN works without the player importing anything.
     */
    private static void provisionWarp(Consumer<String> log) throws IOException {
        log.accept("No VPN config yet; creating a free Cloudflare WARP one...");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
            KeyPair pair = generator.generateKeyPair();
            byte[] privateEncoded = pair.getPrivate().getEncoded();
            byte[] publicEncoded = pair.getPublic().getEncoded();
            Base64.Encoder base64 = Base64.getEncoder();
            String privateKey = base64.encodeToString(Arrays.copyOfRange(privateEncoded, privateEncoded.length - 32,
                    privateEncoded.length));
            String publicKey = base64.encodeToString(Arrays.copyOfRange(publicEncoded, publicEncoded.length - 32,
                    publicEncoded.length));
            ObjectNode body = JSON.createObjectNode();
            body.put("key", publicKey);
            body.put("install_id", "");
            body.put("fcm_token", "");
            body.put("tos", java.time.Instant.now().toString());
            body.put("model", "PC");
            body.put("serial_number", java.util.UUID.randomUUID().toString());
            body.put("locale", "en_US");
            var root = JSON.readTree(http("POST", WARP_REGISTER, JSON.writeValueAsString(body), new String[]{
                    "Content-Type: application/json", "User-Agent: okhttp/3.12.1",
                    "CF-Client-Version: a-6.3-1922"}, null, log));
            var config = root.path("config");
            String peerKey = config.path("peers").path(0).path("public_key").asText("");
            String endpoint = config.path("peers").path(0).path("endpoint").path("host").asText("");
            String v4 = config.path("interface").path("addresses").path("v4").asText("");
            String v6 = config.path("interface").path("addresses").path("v6").asText("");
            if (peerKey.isBlank() || endpoint.isBlank() || v4.isBlank()) {
                throw new IOException("Cloudflare WARP answered with an unexpected response.");
            }
            String text = "[Interface]\nPrivateKey = " + privateKey + "\nAddress = " + v4 + "/32"
                    + (v6.isBlank() ? "" : ", " + v6 + "/128") + "\nDNS = 1.1.1.1\nMTU = 1280\n\n[Peer]\nPublicKey = "
                    + peerKey + "\nAllowedIPs = 0.0.0.0/0, ::/0\nEndpoint = " + endpoint + "\n";
            Files.createDirectories(CONFIG.getParent());
            Files.writeString(CONFIG, text, StandardCharsets.UTF_8);
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("Could not generate a VPN key: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted.", e);
        }
    }

    static void connect(Consumer<String> log) throws IOException {
        if (!hasConfig()) provisionWarp(log);
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
                + "'" + (arguments.isEmpty() ? " -WindowStyle Normal"
                        : " -ArgumentList '" + arguments.replace("'", "''") + "' -WindowStyle Hidden")
                + " -Verb RunAs -Wait -PassThru; exit $p.ExitCode";
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
