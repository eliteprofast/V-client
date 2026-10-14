package com.vclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

final class MicrosoftAuthService {
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String DEVICE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = PlatformTls.httpClientBuilder(Duration.ofSeconds(20)).build();

    record DeviceCode(String code, String url) {}

    CompletableFuture<PlayerAccount> signIn(Executor executor, Consumer<DeviceCode> onCode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode device = postForm(DEVICE_URL, form(
                        "client_id", CLIENT_ID, "scope", "XboxLive.signin offline_access"));
                String deviceCode = required(device, "device_code");
                onCode.accept(new DeviceCode(required(device, "user_code"),
                        device.path("verification_uri").asText("https://microsoft.com/devicelogin")));
                int interval = Math.max(5, device.path("interval").asInt(5));
                long deadline = System.nanoTime()
                        + Duration.ofSeconds(device.path("expires_in").asLong(900)).toNanos();
                JsonNode token = null;
                while (System.nanoTime() < deadline) {
                    Thread.sleep(Duration.ofSeconds(interval));
                    try {
                        token = postForm(TOKEN_URL, form(
                                "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                                "client_id", CLIENT_ID, "device_code", deviceCode));
                        break;
                    } catch (AuthException e) {
                        if ("authorization_pending".equals(e.code)) continue;
                        if ("slow_down".equals(e.code)) {
                            interval += 5;
                            continue;
                        }
                        throw e;
                    }
                }
                if (token == null) throw new IOException("Microsoft sign-in expired. Please try again.");
                return exchangeMinecraftToken(required(token, "access_token"),
                        token.path("refresh_token").asText(""));
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    PlayerAccount refresh(PlayerAccount account) throws IOException, InterruptedException {
        if (account.refreshToken().isBlank()) {
            throw new IOException("This Microsoft account needs to be signed in again.");
        }
        JsonNode refreshed = postForm(TOKEN_URL, form(
                "client_id", CLIENT_ID,
                "grant_type", "refresh_token",
                "refresh_token", account.refreshToken(),
                "scope", "XboxLive.signin offline_access"));
        String refreshToken = refreshed.path("refresh_token").asText(account.refreshToken());
        return exchangeMinecraftToken(required(refreshed, "access_token"), refreshToken);
    }

    private PlayerAccount exchangeMinecraftToken(String microsoftToken, String refreshToken)
            throws IOException, InterruptedException {
        JsonNode xbox = postJson("https://user.auth.xboxlive.com/user/authenticate",
                JSON.createObjectNode().put("RelyingParty", "http://auth.xboxlive.com")
                        .put("TokenType", "JWT")
                        .set("Properties", JSON.createObjectNode().put("AuthMethod", "RPS")
                                .put("SiteName", "user.auth.xboxlive.com").put("RpsTicket", "d=" + microsoftToken)));
        String xboxToken = required(xbox, "Token");
        JsonNode xboxProfile = xbox.path("DisplayClaims").path("xui").path(0);
        String userHash = required(xboxProfile, "uhs");
        String xuid = xboxProfile.path("xid").asText("");
        JsonNode xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize",
                JSON.createObjectNode().put("RelyingParty", "http://xboxlive.com")
                        .put("TokenType", "JWT")
                        .set("Properties", JSON.createObjectNode().put("SandboxId", "RETAIL")
                                .put("UserTokens", JSON.createArrayNode().add(xboxToken))));
        JsonNode minecraft = postJson("https://api.minecraftservices.com/authentication/login_with_xbox",
                JSON.createObjectNode().put("identityToken",
                        "XBL3.0 x=" + userHash + ";" + required(xsts, "Token")));
        String accessToken = required(minecraft, "access_token");
        JsonNode profile = getJson("https://api.minecraftservices.com/minecraft/profile", accessToken);
        String uuid = required(profile, "id").replace("-", "");
        String name = required(profile, "name");
        JsonNode textures = getJson("https://sessionserver.mojang.com/session/minecraft/profile/"
                + encodePath(uuid) + "?unsigned=false", "");
        JsonNode properties = textures.path("properties");
        String encodedProperties = properties.isArray() ? properties.toString() : "[]";
        JsonNode textureProperty = null;
        if (properties.isArray()) {
            for (JsonNode property : properties) {
                if ("textures".equals(property.path("name").asText())) {
                    textureProperty = property;
                    break;
                }
            }
        }
        String textureData = textureProperty == null ? "" : textureProperty.path("value").asText("");
        String skinUrl = PlayerAccount.textureUrl(textureData, "SKIN");
        String capeUrl = PlayerAccount.textureUrl(textureData, "CAPE");
        return new PlayerAccount("microsoft:" + uuid, PlayerAccount.Type.MICROSOFT,
                name, uuid, accessToken, refreshToken, "", xuid,
                encodedProperties, skinUrl, capeUrl);
    }

    private static JsonNode postForm(String endpoint, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return send(request);
    }

    private static JsonNode postJson(String endpoint, JsonNode body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        return send(request);
    }

    private static JsonNode getJson(String endpoint, String bearerToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30)).GET();
        if (!bearerToken.isBlank()) request.header("Authorization", "Bearer " + bearerToken);
        return send(request.build());
    }

    private static JsonNode send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw JavaRuntimeManager.explainTlsFailure(request.uri().toString(), e);
        }
        if (response.statusCode() == 204) return JSON.createObjectNode();
        JsonNode body;
        try {
            body = JSON.readTree(response.body());
        } catch (IOException e) {
            throw new IOException("Authentication server returned an invalid JSON response.", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String code = body == null ? "" : body.path("error").asText("");
            String message = body == null ? "" : body.path("error_description").asText(
                    body.path("Message").asText(""));
            throw new AuthException(code, message.isBlank()
                    ? "Sign-in failed (HTTP " + response.statusCode() + ")." : message);
        }
        return body;
    }

    private static String required(JsonNode node, String field) throws IOException {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new IOException("Authentication response is missing " + field + ".");
        return value;
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String form(String... values) {
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < values.length; i += 2) {
            if (!encoded.isEmpty()) encoded.append('&');
            encoded.append(URLEncoder.encode(values[i], StandardCharsets.UTF_8))
                    .append('=').append(URLEncoder.encode(values[i + 1], StandardCharsets.UTF_8));
        }
        return encoded.toString();
    }

    private static final class AuthException extends IOException {
        private final String code;

        private AuthException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
