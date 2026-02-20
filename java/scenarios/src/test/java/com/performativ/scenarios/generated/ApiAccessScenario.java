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
 * <p>Note: the v1 client index method name will be determined after regeneration
 * from the fixed upstream spec (see duplicate operationId issue on
 * {@code persons.address.upsert} and {@code clients.business.upsert}).
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
        // TODO: After spec fix + regen, update method name from tenantClientsIndex to
        // the v1 equivalent (likely clientsIndex). The generated method will use
        // GET /v1/clients via the spec's clients.index operationId.
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
