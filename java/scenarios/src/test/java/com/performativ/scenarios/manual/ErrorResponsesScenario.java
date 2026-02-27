package com.performativ.scenarios.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.scenarios.BaseScenario;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S8: Error Responses — verify the API returns RFC 7807 Problem Details
 * for validation errors (422) and not-found errors (404).
 *
 * <p>No entities are created or deleted — no cleanup needed.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ErrorResponsesScenario extends BaseScenario {

    private static String token;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();
    }

    @Test
    @Order(1)
    void createClientValidationError() throws Exception {
        HttpResponse<String> response = apiPost(token, "/api/v1/clients", "{}");
        assertEquals(422, response.statusCode(),
                "Empty client payload should return 422, got: " + response.statusCode() + " " + response.body());

        JsonNode body = objectMapper.readTree(response.body());

        // RFC 7807 required fields
        assertTrue(body.has("type"), "Response should have 'type' field (RFC 7807)");
        assertTrue(body.has("title"), "Response should have 'title' field (RFC 7807)");
        assertTrue(body.has("status"), "Response should have 'status' field (RFC 7807)");
        assertTrue(body.has("detail"), "Response should have 'detail' field (RFC 7807)");
        assertEquals(422, body.get("status").asInt(), "status field should be 422");

        // Validation errors
        assertTrue(body.has("errors"), "Validation error should include 'errors' field");
        JsonNode errors = body.get("errors");
        assertTrue(errors.has("name"), "Validation errors should include 'name'");
        assertTrue(errors.has("type"), "Validation errors should include 'type'");
        assertTrue(errors.has("currency_id"), "Validation errors should include 'currency_id'");
    }

    @Test
    @Order(2)
    void readNonExistentClient() throws Exception {
        HttpResponse<String> response = apiGet(token, "/api/v1/clients/0");
        assertEquals(404, response.statusCode(),
                "Non-existent client should return 404, got: " + response.statusCode() + " " + response.body());

        JsonNode body = objectMapper.readTree(response.body());

        // RFC 7807 required fields
        assertTrue(body.has("type"), "Response should have 'type' field (RFC 7807)");
        assertTrue(body.has("title"), "Response should have 'title' field (RFC 7807)");
        assertTrue(body.has("status"), "Response should have 'status' field (RFC 7807)");
        assertTrue(body.has("detail"), "Response should have 'detail' field (RFC 7807)");
        assertEquals(404, body.get("status").asInt(), "status field should be 404");
    }
}
