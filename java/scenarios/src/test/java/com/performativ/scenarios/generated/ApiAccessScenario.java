package com.performativ.scenarios.generated;

import com.performativ.client.api.ClientApi;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S1: API Access — acquire token and list clients via the generated client.
 *
 * <p>Strict: if the generated {@link ClientApi} fails, the test fails.
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
    void listClientsViaGeneratedClient() throws ApiException {
        // tenantClientsIndex has 19 query parameters — all optional, pass null.
        // The generated return type is a typed response model with getData() — if the
        // API response structure doesn't match the spec, Jackson strict mode will fail here.
        var response = clientApi.tenantClientsIndex(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        assertNotNull(response, "Client index response should not be null");
        assertNotNull(response.getData(), "Response 'data' field should deserialize to a typed list");
    }
}
