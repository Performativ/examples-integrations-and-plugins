package com.performativ.scenarios.generated;

import com.performativ.client.api.ClientApi;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import com.performativ.client.model.ClientsIndex200Response;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S1: API Access — acquire token and list clients via the generated client.
 *
 * <p>Strict: if the generated {@link ClientApi} fails, the test fails.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
    @Order(1)
    void listClientsViaGeneratedClient() throws ApiException {
        ClientsIndex200Response response = clientApi.clientsIndex(
                null, null, null, null, null, null, null, null, null, null, null);

        assertNotNull(response, "Client index response should not be null");
        assertNotNull(response.getData(), "Response data should not be null");
        assertFalse(response.getData().isEmpty(), "Client list should not be empty");
    }

    @Test
    @Order(2)
    void rejectUnauthenticatedAccess() {
        // Verify the API rejects requests without a valid token
        ApiClient unauthClient = new ApiClient();
        unauthClient.setBasePath(apiBaseUrl() + "/api");
        // deliberately do NOT set bearer token

        ClientApi unauthApi = new ClientApi(unauthClient);

        ApiException ex = assertThrows(ApiException.class, () ->
                unauthApi.clientsIndex(null, null, null, null, null, null, null, null, null, null, null),
                "API should reject unauthenticated requests");

        assertEquals(401, ex.getCode(),
                "Unauthenticated request should return 401, got: " + ex.getCode());
    }
}
