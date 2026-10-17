package com.vclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ElyByAuthServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CLIENT_TOKEN = "client-token";
    private static final String UUID = "0123456789abcdef0123456789abcdef";

    @Test
    void parsesSuccessfulAuthenticationWithoutWaitingForOptionalTextureServices() throws Exception {
        ObjectNode response = authenticatedResponse();

        PlayerAccount account = ElyByAuthService.parseAccount(response, CLIENT_TOKEN, null);

        assertEquals(PlayerAccount.Type.ELY_BY, account.type());
        assertEquals("TestPlayer", account.name());
        assertEquals(UUID, account.uuid());
        assertEquals("elyby:" + UUID, account.id());
        assertEquals("", account.skinUrl());
        assertEquals("", account.capeUrl());
    }

    @Test
    void refreshKeepsPreviouslyLoadedSkinAndCape() throws Exception {
        ObjectNode response = authenticatedResponse();
        PlayerAccount previous = new PlayerAccount("elyby:" + UUID, PlayerAccount.Type.ELY_BY,
                "TestPlayer", UUID, "old-token", "", CLIENT_TOKEN, "", "[]",
                "https://skinsystem.ely.by/skin.png", "https://skinsystem.ely.by/cape.png");

        PlayerAccount refreshed = ElyByAuthService.parseAccount(response, CLIENT_TOKEN, previous);

        assertEquals(previous.skinUrl(), refreshed.skinUrl());
        assertEquals(previous.capeUrl(), refreshed.capeUrl());
        assertEquals("new-token", refreshed.accessToken());
    }

    @Test
    void rejectsUnexpectedClientToken() throws Exception {
        java.io.IOException error = assertThrows(java.io.IOException.class,
                () -> ElyByAuthService.parseAccount(authenticatedResponse(), "unexpected", null));

        assertTrue(error.getMessage().contains("unexpected client token"));
    }

    @Test
    void sessionProfileYieldsHttpsSkinCapeAndModel() throws Exception {
        String textures = "{\"textures\":{\"SKIN\":{\"url\":\"http://ely.by/storage/skins/abc.png\","
                + "\"metadata\":{\"model\":\"slim\"}},\"CAPE\":{\"url\":\"http://ely.by/storage/capes/def.png\"}}}";
        ObjectNode profile = JSON.createObjectNode();
        ObjectNode property = profile.putArray("properties").addObject();
        property.put("name", "textures");
        property.put("value", java.util.Base64.getEncoder().encodeToString(
                textures.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        ElyByAuthService.SkinProfile parsed = ElyByAuthService.parseSessionProfile(profile);

        assertEquals("https://ely.by/storage/skins/abc.png", parsed.skinUrl());
        assertEquals("https://ely.by/storage/capes/def.png", parsed.capeUrl());
        assertEquals("slim", parsed.model());
        assertTrue(parsed.hasSkin());
    }

    @Test
    void sessionProfileWithoutTexturesIsEmptyAndDefaultModel() throws Exception {
        ElyByAuthService.SkinProfile parsed = ElyByAuthService.parseSessionProfile(JSON.createObjectNode());

        assertEquals("", parsed.skinUrl());
        assertEquals("default", parsed.model());
    }

    @Test
    void modelNamesAreNormalized() {
        assertEquals("slim", ElyByAuthService.normalizeModel("slim"));
        assertEquals("slim", ElyByAuthService.normalizeModel("ALEX"));
        assertEquals("default", ElyByAuthService.normalizeModel(""));
        assertEquals("default", ElyByAuthService.normalizeModel("steve"));
    }

    @Test
    void textureUrlsOutsideElyByAreRejectedUnlessHttps() {
        assertEquals("https://ely.by/a.png", ElyByAuthService.secureTextureUrl("http://ely.by/a.png"));
        assertEquals("https://textures.example/a.png", ElyByAuthService.secureTextureUrl("https://textures.example/a.png"));
        assertEquals("", ElyByAuthService.secureTextureUrl("http://example.com/a.png"));
        assertEquals("", ElyByAuthService.secureTextureUrl("not a url"));
    }

    private static ObjectNode authenticatedResponse() {
        ObjectNode response = JSON.createObjectNode();
        response.put("accessToken", "new-token");
        response.put("clientToken", CLIENT_TOKEN);
        ObjectNode profile = response.putObject("selectedProfile");
        profile.put("id", UUID);
        profile.put("name", "TestPlayer");
        response.putObject("user").putArray("properties");
        return response;
    }
}
