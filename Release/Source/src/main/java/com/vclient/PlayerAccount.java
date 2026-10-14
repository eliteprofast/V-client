package com.vclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

record PlayerAccount(String id, Type type, String name, String uuid, String accessToken,
                     String refreshToken, String clientToken, String xuid, String userProperties,
                     String skinUrl, String capeUrl) {
    enum Type {
        MICROSOFT("Premium"),
        ELY_BY("Ely.by"),
        OFFLINE("Offline");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        String minecraftUserType() {
            return switch (this) {
                case MICROSOFT -> "msa";
                case ELY_BY -> "ely";
                case OFFLINE -> "legacy";
            };
        }
    }

    static PlayerAccount offline(String username) {
        String normalized = username == null ? "" : username.strip();
        if (!normalized.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IllegalArgumentException("Offline usernames must be 3–16 letters, numbers, or underscores.");
        }
        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + normalized)
                .getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        return new PlayerAccount("offline:" + normalized, Type.OFFLINE,
                normalized, uuid, "0", "", "", "", "{}", "", "");
    }

    String gameProperties(ObjectMapper mapper) throws java.io.IOException {
        if (userProperties == null || userProperties.isBlank()) return "{}";
        JsonNode properties = mapper.readTree(userProperties);
        return properties == null ? "{}" : properties.toString();
    }

    static String textureUrl(String base64Properties, String textureName) throws java.io.IOException {
        if (base64Properties == null || base64Properties.isBlank()) return "";
        try {
            JsonNode properties = new ObjectMapper().readTree(
                    java.util.Base64.getDecoder().decode(base64Properties));
            String url = properties.path("textures").path(textureName).path("url").asText("");
            return url.startsWith("https://") ? url : "";
        } catch (IllegalArgumentException | java.io.IOException e) {
            throw new java.io.IOException("The player skin response contains an invalid texture property.", e);
        }
    }
}
