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
