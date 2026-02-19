package com.performativ.scenarios.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.scenarios.BaseScenario;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S1: API Access — acquire an OAuth2 token and call GET /api/v1/clients.
 *
 * <p>Verifies that credentials are valid and the API is reachable.
 * No entities are created or deleted.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
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

        HttpResponse<String> response = apiGet(token, "/api/v1/clients");

        assertEquals(200, response.statusCode(), "Expected 200 from GET /api/v1/clients");

        JsonNode body = objectMapper.readTree(response.body());
        assertTrue(body.has("data"), "Response should have 'data' field");
    }
}
