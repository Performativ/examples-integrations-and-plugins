package com.performativ.scenarios;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acquires an OAuth2 token and calls GET /api/clients to verify API access.
 */
class ApiAccessScenario extends BaseScenario {

    @BeforeAll
    static void checkCredentials() {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
    }

    @Test
    void acquireTokenAndListClients() throws Exception {
        String token = acquireToken();
        assertNotNull(token);
        assertFalse(token.isBlank());

        HttpResponse<String> response = apiGet(token, "/api/clients");

        assertEquals(200, response.statusCode(), "Expected 200 from GET /api/clients");

        JsonNode body = objectMapper.readTree(response.body());
        assertNotNull(body);
    }
}
