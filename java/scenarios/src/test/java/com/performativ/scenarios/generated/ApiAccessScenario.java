package com.performativ.scenarios.generated;

import com.performativ.client.api.ClientApi;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S1: API Access — acquire token and list clients via the generated client.
 *
 * <p>Strict: if the generated {@link ClientApi} fails, the test fails.
 *
 * <p>Note: {@code tenantClientsIndex} returns {@code Object} because the OpenAPI spec
 * does not define a typed response schema for the index endpoint. This is itself a
 * spec gap — the manual scenario verifies the same endpoint with raw HTTP. Here we
 * verify that the call succeeds and the response structure is a JSON object with a
 * {@code data} array, which exercises the generated client's HTTP layer and auth.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
class ApiAccessScenario extends GeneratedClientScenario {

    private static ClientApi clientApi;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        String token = acquireToken();
        ApiClient apiClient = createApiClient(token);
        clientApi = new ClientApi(apiClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listClientsViaGeneratedClient() throws ApiException {
        // tenantClientsIndex returns Object (untyped) — the spec doesn't define a
        // response schema for this endpoint. Jackson deserializes it to a Map.
        Object response = clientApi.tenantClientsIndex(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        assertNotNull(response, "Client index response should not be null");
        assertInstanceOf(Map.class, response, "Response should deserialize to a Map");

        Map<String, Object> body = (Map<String, Object>) response;
        assertTrue(body.containsKey("data"), "Response should have 'data' field");
        assertInstanceOf(List.class, body.get("data"), "'data' should be an array");
    }
}
