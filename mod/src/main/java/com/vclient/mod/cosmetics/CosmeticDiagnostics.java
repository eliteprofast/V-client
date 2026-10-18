package com.vclient.mod.cosmetics;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vclient.mod.VClientMod;
import net.minecraft.client.MinecraftClient;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Diagnose" in the Cosmetics menu: checks, one by one and each with a 10-second limit, that everything cosmetics
 * and skins depend on is reachable, and says plainly which part is not. Results appear live in the menu and are
 * written to the game log. Tokens are only ever sent to the account's own login server and are never logged.
 */
public final class CosmeticDiagnostics {
    public enum Result { RUNNING, OK, FAIL, INFO }

    public record Check(String name, Result result, String detail) {}

    private static final Gson GSON = new Gson();
    private static final CopyOnWriteArrayList<Check> CHECKS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger RUN = new AtomicInteger();
    private static volatile boolean running;

    private CosmeticDiagnostics() {}

    public static List<Check> checks() {
        return CHECKS;
    }

    public static boolean running() {
        return running;
    }

    /** Starts a diagnostic run in the background; ignored while one is already running. */
    public static void run() {
        if (running) return;
        running = true;
        int run = RUN.incrementAndGet();
        CHECKS.clear();
        VClientMod.LOGGER.info("[Diagnostics] Run {} started.", run);
        CosmeticNet.run(() -> {
            try {
                backend();
                skinApi();
                skinTexture();
                mojangTextures();
                authentication();
                add("Launcher skin loading", SkinFix.skinFailed() ? Result.FAIL : Result.INFO, SkinFix.status());
                add("Cosmetic textures", Result.INFO, textureSummary());
            } catch (Throwable error) {
                CosmeticNet.logError("Diagnostics crashed", error);
                add("Diagnostics", Result.FAIL, CosmeticNet.describe(error));
            } finally {
                running = false;
                VClientMod.LOGGER.info("[Diagnostics] Run {} finished.", run);
            }
        });
    }

    private static String textureSummary() {
        int failed = 0;
        StringBuilder detail = new StringBuilder();
        for (Cosmetic cosmetic : CosmeticCatalog.items()) {
            if (CosmeticTextures.failed(cosmetic)) {
                failed++;
                if (failed <= 3) detail.append(cosmetic.id()).append(": ").append(CosmeticTextures.failureReason(cosmetic)).append("; ");
            }
        }
        return failed == 0 ? "no texture has failed" : failed + " failed - " + detail;
    }

    private static void add(String name, Result result, String detail) {
        CHECKS.add(new Check(name, result, detail));
        VClientMod.LOGGER.info("[Diagnostics] {} {} - {}", result, name, detail);
    }

    // ---- backend ---------------------------------------------------------------------------------

    private static void backend() {
        String base = CosmeticNet.backendUrl();
        if (base.isEmpty()) {
            add("Backend address", Result.INFO, "none configured - only the built-in cosmetics are available. "
                    + "Set it in the launcher (News page, Backend & updates).");
            return;
        }
        add("Backend address", Result.INFO, base);
        JsonObject catalog = null;
        try {
            HttpResponse<byte[]> health = CosmeticNet.get(URI.create(base + "/api/health"), null);
            add("Cosmetic API reachable", health.statusCode() == 200 ? Result.OK : Result.FAIL,
                    "GET /api/health -> HTTP " + health.statusCode());
        } catch (Exception e) {
            add("Cosmetic API reachable", Result.FAIL, CosmeticNet.describe(e));
            return;
        }
        try {
            HttpResponse<byte[]> response = CosmeticNet.get(URI.create(base + "/api/cosmetics/catalog"), null);
            if (response.statusCode() != 200) {
                add("Cosmetic catalog", Result.FAIL, "HTTP " + response.statusCode()
                        + (response.statusCode() == 404 ? " - the backend has no cosmetics API (redeploy it)" : ""));
            } else {
                catalog = GSON.fromJson(new String(response.body(), StandardCharsets.UTF_8), JsonObject.class);
                int count = catalog != null && catalog.get("items") instanceof JsonArray array ? array.size() : -1;
                add("Cosmetic catalog", count >= 0 ? Result.OK : Result.FAIL,
                        count >= 0 ? count + " items in the online catalog" : "the response has no items list");
            }
        } catch (Exception e) {
            add("Cosmetic catalog", Result.FAIL, CosmeticNet.describe(e));
        }
        UUID player = playerId();
        if (player != null) {
            try {
                HttpResponse<byte[]> owned = CosmeticNet.get(URI.create(
                        base + "/api/cosmetics/owned/" + player.toString().replace("-", "")), null);
                add("Owned cosmetics", owned.statusCode() == 200 ? Result.OK : Result.FAIL,
                        "GET /api/cosmetics/owned/<uuid> -> HTTP " + owned.statusCode());
            } catch (Exception e) {
                add("Owned cosmetics", Result.FAIL, CosmeticNet.describe(e));
            }
        }
        if (catalog != null && catalog.get("items") instanceof JsonArray items) {
            int checked = 0;
            int missing = 0;
            StringBuilder detail = new StringBuilder();
            for (JsonElement element : items) {
                if (!element.isJsonObject() || !(element.getAsJsonObject().get("assets") instanceof JsonArray assets)) continue;
                for (JsonElement assetElement : assets) {
                    if (checked >= 15) break;
                    JsonObject asset = assetElement.getAsJsonObject();
                    String file = Cosmetic.string(asset, "file", "?");
                    checked++;
                    try {
                        HttpResponse<byte[]> head = CosmeticNet.head(CosmeticNet.resolve(base, Cosmetic.string(asset, "url", "")));
                        if (head.statusCode() != 200) {
                            missing++;
                            detail.append(file).append(" (HTTP ").append(head.statusCode()).append(") ");
                        }
                    } catch (Exception e) {
                        missing++;
                        detail.append(file).append(" (").append(CosmeticNet.describe(e)).append(") ");
                    }
                }
            }
            add("Asset files exist", checked == 0 || missing == 0 ? (checked == 0 ? Result.INFO : Result.OK) : Result.FAIL,
                    checked == 0 ? "the online catalog has no downloadable assets"
                            : (checked - missing) + " of " + checked + " reachable" + (missing > 0 ? " - missing: " + detail : ""));
        }
    }

    // ---- skins -----------------------------------------------------------------------------------

    private static void skinApi() {
        String uuid = System.getProperty("vclient.skin.uuid", "");
        if (uuid.isEmpty()) {
            add("Skin API (Ely.by)", Result.INFO, "this is not an Ely.by launch, nothing to check");
            return;
        }
        try {
            HttpResponse<byte[]> response = CosmeticNet.get(URI.create(
                    "https://authserver.ely.by/api/authlib-injector/sessionserver/session/minecraft/profile/" + uuid), null);
            add("Skin API (Ely.by)", response.statusCode() == 200 ? Result.OK : Result.FAIL,
                    "session profile -> HTTP " + response.statusCode());
        } catch (Exception e) {
            add("Skin API (Ely.by)", Result.FAIL, CosmeticNet.describe(e));
        }
    }

    private static void skinTexture() {
        String url = System.getProperty("vclient.skin.url", "");
        if (url.isEmpty()) {
            add("Skin texture URL", Result.INFO, "the launcher did not supply one");
            return;
        }
        try {
            HttpResponse<byte[]> response = CosmeticNet.head(URI.create(url));
            String type = response.headers().firstValue("Content-Type").orElse("unknown");
            boolean ok = response.statusCode() == 200 && type.startsWith("image/");
            add("Skin texture URL", ok ? Result.OK : Result.FAIL,
                    "HTTP " + response.statusCode() + ", " + type + " - " + url);
        } catch (Exception e) {
            add("Skin texture URL", Result.FAIL, CosmeticNet.describe(e) + " - " + url);
        }
    }

    /**
     * Mojang serves other players' skins from textures.minecraft.net. Some networks (school/work filters, family
     * DNS, some routers) answer that name with a block page, so those skins never load. Detect it and say so.
     */
    private static void mojangTextures() {
        String host = "textures.minecraft.net";
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            String shown = addresses[0].getHostAddress();
            boolean sinkhole = false;
            for (InetAddress address : addresses) {
                String text = address.getHostAddress();
                if (text.startsWith("146.112.") || address.isLoopbackAddress() || address.isAnyLocalAddress()
                        || address.isSiteLocalAddress()) {
                    sinkhole = true;
                }
            }
            if (sinkhole) {
                add("Mojang skin server", Result.FAIL, host + " resolves to " + shown + ", a DNS filter's block page "
                        + "(OpenDNS/Cisco Umbrella style). Skins of other players and server-set skins cannot load on this "
                        + "network. Fix: set the PC's or router's DNS to 1.1.1.1 / 8.8.8.8.");
                return;
            }
            HttpResponse<byte[]> response = CosmeticNet.head(URI.create("https://" + host + "/"));
            add("Mojang skin server", Result.OK, host + " -> " + shown + ", HTTPS answered HTTP " + response.statusCode());
        } catch (java.net.UnknownHostException e) {
            add("Mojang skin server", Result.FAIL, "cannot resolve " + host + " (DNS blocked or offline)");
        } catch (Exception e) {
            add("Mojang skin server", Result.FAIL, CosmeticNet.describe(e));
        }
    }

    private static void authentication() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSession() == null) {
            add("Authentication", Result.INFO, "no session available");
            return;
        }
        String user = client.getSession().getUsername();
        boolean ely = !System.getProperty("vclient.skin.uuid", "").isEmpty();
        if (!ely) {
            add("Authentication", Result.INFO, "signed in as " + user + " (not an Ely.by account, token not checked)");
            return;
        }
        String token = client.getSession().getAccessToken();
        if (token == null || token.length() < 8) {
            add("Authentication", Result.FAIL, "signed in as " + user + " but the session has no access token");
            return;
        }
        try {
            JsonObject body = new JsonObject();
            body.addProperty("accessToken", token);
            HttpResponse<byte[]> response = CosmeticNet.postJson(URI.create("https://authserver.ely.by/auth/validate"),
                    GSON.toJson(body));
            boolean valid = response.statusCode() == 200 || response.statusCode() == 204;
            add("Authentication", valid ? Result.OK : Result.FAIL, user + ": Ely.by says the access token is "
                    + (valid ? "valid" : "invalid or expired (HTTP " + response.statusCode() + ") - sign in again in the launcher"));
        } catch (Exception e) {
            add("Authentication", Result.FAIL, "could not validate the token: " + CosmeticNet.describe(e));
        }
    }

    private static UUID playerId() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null || client.getSession() == null ? null : client.getSession().getUuidOrNull();
    }
}
