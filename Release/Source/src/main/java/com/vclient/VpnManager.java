package com.vclient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

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

    static void connect() throws IOException {
        Path exe = wireGuardExecutable();
        if (exe == null) {
            throw new IOException("WireGuard is not installed. Install it from https://www.wireguard.com/install/ "
                    + "and try again.");
        }
        if (!hasConfig()) throw new IOException("Import a WireGuard config first.");
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
