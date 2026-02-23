package com.performativ.scenarios.generated;

import com.performativ.client.api.ClientApi;
import com.performativ.client.api.ClientPersonApi;
import com.performativ.client.api.PersonApi;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import com.performativ.client.model.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S2: Client Lifecycle — create Person and Client, link them, read, update, delete
 * using the generated OpenAPI client exclusively (v1 endpoints).
 *
 * <p>Strict: no raw HTTP fallbacks. If the generated client fails
 * (deserialization, wrong types, etc.), the test fails — surfacing spec bugs.
 *
 * <p>In v1, Person is linked to Client via the {@code /api/v1/client-persons}
 * join resource instead of {@code primary_person_id} on the client.
 *
 * <p>Cleanup uses raw HTTP to ensure entities are deleted even when the
 * generated client has issues.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClientLifecycleScenario extends GeneratedClientScenario {

    private static String token;
    private static PersonApi personApi;
    private static ClientApi clientApi;
    private static ClientPersonApi clientPersonApi;

    private static int personId;
    private static int clientId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();

        ApiClient apiClient = createApiClient(token);
        personApi = new PersonApi(apiClient);
        clientApi = new ClientApi(apiClient);
        clientPersonApi = new ClientPersonApi(apiClient);
    }

    @Test
    @Order(1)
    void createPerson() throws ApiException {
        var req = new StorePersonRequest()
                .firstName("Gen")
                .lastName("S2-ClientLifecycle")
                .email("gen-s2@example.com")
                .languageCode("en");

        var response = personApi.personsStore(req);
        assertNotNull(response, "Person store response should not be null");
        assertNotNull(response.getData(), "Person data should not be null");

        personId = response.getData().getId();
        assertTrue(personId > 0, "Person ID should be positive");
    }

    @Test
    @Order(2)
    void createClient() throws ApiException {
        var req = new StoreClientRequest()
                .name("Gen-S2 Client")
                .type("individual")
                .isActive(true)
                .currencyId(47);

        var response = clientApi.clientsStore(req);
        assertNotNull(response, "Client store response should not be null");
        assertNotNull(response.getData(), "Client data should not be null");

        clientId = response.getData().getId();
        assertTrue(clientId > 0, "Client ID should be positive");
        assertEquals("Gen-S2 Client", response.getData().getName(),
                "Created client name should match request — verifies typed model round-trip");
    }

    @Test
    @Order(3)
    void linkPersonToClient() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");
        assertTrue(clientId > 0, "Client must be created first");

        var req = new StoreClientPersonRequest()
                .clientId(clientId)
                .personId(personId)
                .isPrimary(true);

        clientPersonApi.clientPersonsStore(req);
    }

    @Test
    @Order(4)
    void readClient() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");

        var response = clientApi.clientsShow(String.valueOf(clientId), String.valueOf(clientId), null);
        assertNotNull(response);

        var data = response.getData();
        assertEquals(clientId, data.getId());
        assertEquals("Gen-S2 Client", data.getName(),
                "Read-back name should match — verifies show response deserializes correctly");
    }

    @Test
    @Order(5)
    void updateClient() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");

        var req = new UpdateClientRequest()
                .name("Gen-S2 Client Updated")
                .type("individual")
                .currencyId(47);

        var response = clientApi.clientsUpdate(String.valueOf(clientId), String.valueOf(clientId), req);
        assertNotNull(response, "Client update response should not be null");

        var readBack = clientApi.clientsShow(String.valueOf(clientId), String.valueOf(clientId), null);
        assertEquals("Gen-S2 Client Updated", readBack.getData().getName(),
                "Updated name should persist — verifies update response model");
    }

    @Test
    @Order(6)
    void readPerson() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");

        var response = personApi.personsShow(String.valueOf(personId), String.valueOf(personId), null);
        assertNotNull(response);

        var data = response.getData();
        assertEquals(personId, data.getId());
        assertEquals("Gen", data.getFirstName(),
                "Person first_name should round-trip through typed model");
        assertEquals("S2-ClientLifecycle", data.getLastName(),
                "Person last_name should round-trip through typed model");
    }

    @Test
    @Order(7)
    void deleteClient() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");
        clientApi.clientsDestroy(String.valueOf(clientId), String.valueOf(clientId));
        clientId = 0;
    }

    @Test
    @Order(8)
    void deletePerson() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");
        try {
            personApi.personsDestroy(String.valueOf(personId), String.valueOf(personId));
        } catch (ApiException e) {
            // Person may already be cascade-deleted with the client
            if (e.getCode() != 404) throw e;
        }
        personId = 0;
    }

    @AfterAll
    static void teardown() {
        if (token == null) return;
        // Cleanup uses raw HTTP — never fails due to spec bugs
        if (clientId > 0) deleteEntity(token, "/api/v1/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/v1/persons/" + personId);
    }
}
