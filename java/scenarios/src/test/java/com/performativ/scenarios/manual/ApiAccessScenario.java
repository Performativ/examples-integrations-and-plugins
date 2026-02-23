package com.performativ.scenarios.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.scenarios.BaseScenario;
import org.junit.jupiter.api.*;

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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiAccessScenario extends BaseScenario {

    @BeforeAll
    static void checkCredentials() {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
    }

    @Test
    @Order(1)
    void acquireTokenAndListClients() throws Exception {
        String token = acquireToken();
        assertNotNull(token);
        assertFalse(token.isBlank());

        HttpResponse<String> response = apiGet(token, "/api/v1/clients");

        assertEquals(200, response.statusCode(), "Expected 200 from GET /api/v1/clients");

        JsonNode body = objectMapper.readTree(response.body());
        assertTrue(body.has("data"), "Response should have 'data' field");
    }

    @Test
    @Order(2)
    void rejectUnauthenticatedAccess() throws Exception {
        // Verify the API rejects requests without a valid token
        String baseUrl = dotenv.get("API_BASE_URL");
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(baseUrl + "/api/v1/clients"))
                .header("Accept", "application/json")
                .GET()
                .build();

        var response = java.net.http.HttpClient.newHttpClient()
                .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode(),
                "Unauthenticated request should return 401, got: " + response.statusCode());
    }
}
