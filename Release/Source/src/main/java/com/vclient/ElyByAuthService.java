package com.vclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.URLEncoder;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class ElyByAuthService {
    private static final String AUTH_URL = "https://authserver.ely.by/auth/";
    private static final String TEXTURE_URL = "https://skinsystem.ely.by/textures/";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = PlatformTls.httpClientBuilder(Duration.ofSeconds(20)).build();

    CompletableFuture<PlayerAccount> signIn(String username, char[] password, Executor executor,
                                           java.util.function.Consumer<String> onProgress) {
        char[] secret = password.clone();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return authenticate(username, new String(secret), onProgress);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException(e);
            } finally {
                java.util.Arrays.fill(secret, '\0');
            }
        }, executor);
    }

    PlayerAccount refresh(PlayerAccount account) throws IOException, InterruptedException {
        ObjectNode request = JSON.createObjectNode();
        request.put("accessToken", account.accessToken());
        request.put("clientToken", account.clientToken());
        request.put("requestUser", true);
        return parseAccount(send(AUTH_URL + "refresh", request, ignored -> {}),
                account.clientToken(), account);
    }

    private PlayerAccount authenticate(String username, String password,
                                       java.util.function.Consumer<String> onProgress)
            throws IOException, InterruptedException {
        if (username == null || username.isBlank() || password.isBlank()) {
            throw new IOException("Enter your Ely.by username/email and password.");
        }
        String clientToken = UUID.randomUUID().toString();
        ObjectNode request = JSON.createObjectNode();
        ObjectNode agent = request.putObject("agent");
        agent.put("name", "Minecraft");
        agent.put("version", 1);
        request.put("username", username.strip());
        request.put("password", password);
        request.put("clientToken", clientToken);
        request.put("requestUser", true);
        return parseAccount(send(AUTH_URL + "authenticate", request, onProgress), clientToken, null);
    }

    static PlayerAccount parseAccount(JsonNode response, String expectedClientToken, PlayerAccount previous)
            throws IOException {
        String token = required(response, "accessToken");
        String clientToken = required(response, "clientToken");
        if (!expectedClientToken.equals(clientToken)) {
            throw new IOException("Ely.by returned an unexpected client token.");
        }
        JsonNode selectedProfile = response.path("selectedProfile");
        String uuid = required(selectedProfile, "id");
        String name = required(selectedProfile, "name");
        JsonNode properties = response.path("user").path("properties");
        String normalizedUuid = normalizeUuid(uuid);
        com.fasterxml.jackson.databind.node.ArrayNode userProperties = JSON.createArrayNode();
        if (properties.isArray()) userProperties.addAll((com.fasterxml.jackson.databind.node.ArrayNode) properties);
        String skin = previous == null ? "" : previous.skinUrl();
        String cape = previous == null ? "" : previous.capeUrl();
        return new PlayerAccount("elyby:" + normalizedUuid, PlayerAccount.Type.ELY_BY, name, normalizedUuid,
                token, "", clientToken, "", userProperties.toString(), skin, cape);
    }

    PlayerAccount loadTextures(PlayerAccount account) throws IOException, InterruptedException {
        Textures textures = fetchTextures(account.name());
        if (textures.skin().isBlank() && textures.cape().isBlank() && textures.property() == null) {
            return account;
        }
        com.fasterxml.jackson.databind.node.ArrayNode userProperties = JSON.createArrayNode();
        String existingValue = account.userProperties();
        JsonNode existingProperties = existingValue == null || existingValue.isBlank()
                ? null : JSON.readTree(existingValue);
        if (existingProperties != null && existingProperties.isArray()) {
            for (JsonNode property : existingProperties) {
                if (!"textures".equals(property.path("name").asText())) userProperties.add(property);
            }
        }
        if (textures.property() != null) userProperties.add(textures.property());
        return new PlayerAccount(account.id(), account.type(), account.name(), account.uuid(),
                account.accessToken(), account.refreshToken(), account.clientToken(), account.xuid(),
                userProperties.toString(),
                textures.skin().isBlank() ? account.skinUrl() : textures.skin(),
                textures.cape().isBlank() ? account.capeUrl() : textures.cape());
    }

    private record Textures(String skin, String cape, JsonNode property) {}

    private static Textures fetchTextures(String username) throws IOException, InterruptedException {
        String encodedName = URLEncoder.encode(username, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(TEXTURE_URL + encodedName))
                .timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw JavaRuntimeManager.explainTlsFailure(request.uri().toString(), e);
        }
        Textures directTextures = response.statusCode() == 204
                ? new Textures("", "", null) : parseTextureResponse(readResponse(response));
        JsonNode property = null;
        HttpRequest profileRequest = HttpRequest.newBuilder(URI.create(
                        "https://skinsystem.ely.by/profile/" + encodedName + "?unsigned=false"))
                .timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> profileResponse;
        try {
            profileResponse = HTTP.send(profileRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw JavaRuntimeManager.explainTlsFailure(profileRequest.uri().toString(), e);
        }
        String skin = directTextures.skin();
        String cape = directTextures.cape();
        if (profileResponse.statusCode() >= 200 && profileResponse.statusCode() < 300) {
            JsonNode profile = readResponse(profileResponse);
            for (JsonNode candidate : profile.path("properties")) {
                if ("textures".equals(candidate.path("name").asText())) {
                    property = candidate;
                    if (skin.isBlank()) {
                        String encodedProperties = candidate.path("value").asText("");
                        skin = secureTextureUrl(PlayerAccount.textureUrl(encodedProperties, "SKIN"));
                    }
                    if (cape.isBlank()) {
                        String encodedProperties = candidate.path("value").asText("");
                        cape = secureTextureUrl(PlayerAccount.textureUrl(encodedProperties, "CAPE"));
                    }
                    if (!skin.isBlank() && !cape.isBlank()) break;
                }
            }
        }
        return new Textures(skin, cape, property);
    }

    static Textures parseTextureResponse(JsonNode textures) {
        String skin = secureTextureUrl(textures.path("SKIN").path("url").asText(""));
        String cape = secureTextureUrl(textures.path("CAPE").path("url").asText(""));
        return new Textures(skin, cape, null);
    }

    private static String secureTextureUrl(String value) {
        if (value.isBlank()) return "";
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            return "";
        }
        String host = uri.getHost();
        if (host == null) return "";
        if ("http".equalsIgnoreCase(uri.getScheme())
                && (host.equalsIgnoreCase("ely.by") || host.toLowerCase(java.util.Locale.ROOT).endsWith(".ely.by"))) {
            uri = URI.create("https://" + uri.getRawAuthority().substring(uri.getRawAuthority().indexOf('@') + 1)
                    + uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? uri.toString() : "";
    }

    private static JsonNode send(String endpoint, ObjectNode body,
                                 java.util.function.Consumer<String> onProgress)
            throws IOException, InterruptedException {
        URI uri = URI.create(endpoint);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            onProgress.accept("Sending Ely.by HTTPS request via " + PlatformTls.proxyDescription(uri)
                    + " using TLS " + PlatformTls.enabledProtocols() + ".");
            HttpResponse<String> response = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            onProgress.accept("Ely.by returned HTTP " + response.statusCode() + ".");
            return readResponse(response);
        } catch (HttpTimeoutException e) {
            String message = "Ely.by did not respond within 12 seconds at " + uri.getHost()
                    + " (route: " + PlatformTls.proxyDescription(uri) + ").";
            onProgress.accept(message);
            throw new IOException(message, e);
        } catch (IOException e) {
            onProgress.accept("Ely.by request failed: " + PlatformTls.describeFailure(e));
            throw JavaRuntimeManager.explainTlsFailure(endpoint, e);
        }
    }

    private static JsonNode readResponse(HttpResponse<String> response) throws IOException {
        JsonNode body;
        try {
            body = JSON.readTree(response.body());
        } catch (IOException e) {
            throw new IOException("Ely.by returned an invalid authentication response.", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = body == null ? "" : body.path("errorMessage").asText(
                    body.path("error").asText(""));
            throw new IOException(message.isBlank()
                    ? "Ely.by authentication failed (HTTP " + response.statusCode() + ")."
                    : "Ely.by sign-in failed: " + message);
        }
        if (body == null) throw new IOException("Ely.by returned an empty response.");
        return body;
    }

    private static String required(JsonNode response, String field) throws IOException {
        String value = response.path(field).asText("");
        if (value.isBlank()) throw new IOException("Ely.by response is missing " + field + ".");
        return value;
    }

    private static String normalizeUuid(String uuid) throws IOException {
        String normalized = uuid.replace("-", "");
        if (!normalized.matches("(?i)[0-9a-f]{32}")) throw new IOException("Ely.by returned an invalid player UUID.");
        return normalized.toLowerCase(java.util.Locale.ROOT);
    }
}
