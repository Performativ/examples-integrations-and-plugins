package com.performativ.scenarios.generated;

import com.performativ.client.api.ClientApi;
import com.performativ.client.api.PersonApi;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import com.performativ.client.model.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S2: Client Lifecycle — create Person and Client, read, update, delete
 * using the generated OpenAPI client exclusively.
 *
 * <p>Strict: no raw HTTP fallbacks. If the generated client fails
 * (deserialization, wrong types, etc.), the test fails — surfacing spec bugs.
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

    private static int personId;
    private static int clientId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();

        ApiClient apiClient = createApiClient(token);
        personApi = new PersonApi(apiClient);
        clientApi = new ClientApi(apiClient);
    }

    @Test
    @Order(1)
    void createPerson() throws ApiException {
        StorePersonRequest req = new StorePersonRequest();
        req.firstName("Scenario");
        req.lastName("ClientLifecycle");
        req.email("scenario-s2-gen@example.com");
        req.languageCode("en");

        var response = personApi.tenantPersonsStore(req);
        assertNotNull(response, "Person store response should not be null");
        assertNotNull(response.getData(), "Person data should not be null");

        personId = response.getData().getId();
        assertTrue(personId > 0, "Person ID should be positive");
    }

    @Test
    @Order(2)
    void createClient() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");

        StoreClientRequestPrimaryPerson primaryPerson = new StoreClientRequestPrimaryPerson();
        primaryPerson.setPersonId(personId);

        StoreClientRequest req = new StoreClientRequest();
        req.name("Scenario-S2 Client");
        req.type("individual");
        req.isActive(true);
        req.primaryPerson(primaryPerson);

        var response = clientApi.tenantClientsStore(req);
        assertNotNull(response, "Client store response should not be null");
        assertNotNull(response.getData(), "Client data should not be null");

        clientId = response.getData().getId();
        assertTrue(clientId > 0, "Client ID should be positive");
        assertEquals("Scenario-S2 Client", response.getData().getName(),
                "Created client name should match request — verifies typed model round-trip");
    }

    @Test
    @Order(3)
    void readClient() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");

        var response = clientApi.tenantClientsShow(clientId);
        assertNotNull(response);

        var data = response.getData();
        assertEquals(clientId, data.getId());
        assertEquals("Scenario-S2 Client", data.getName(),
                "Read-back name should match — verifies show response deserializes correctly");
    }

    @Test
    @Order(4)
    void updateClient() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");

        UpdateClientRequest req = new UpdateClientRequest();
        req.name("Scenario-S2 Client Updated");

        var response = clientApi.tenantClientsUpdate(clientId, req);
        assertNotNull(response, "Client update response should not be null");

        // Read back to verify the update through the typed model
        var readBack = clientApi.tenantClientsShow(clientId);
        assertEquals("Scenario-S2 Client Updated", readBack.getData().getName(),
                "Updated name should persist — verifies update response model");
    }

    @Test
    @Order(5)
    void readPerson() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");

        var response = personApi.tenantPersonsShow(personId);
        assertNotNull(response);

        var data = response.getData();
        assertEquals(personId, data.getId());
        assertEquals("Scenario", data.getFirstName(),
                "Person first_name should round-trip through typed model");
        assertEquals("ClientLifecycle", data.getLastName(),
                "Person last_name should round-trip through typed model");
    }

    @Test
    @Order(6)
    void deleteClient() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");
        clientApi.tenantClientsDestroy(clientId);
        clientId = 0;
    }

    @Test
    @Order(7)
    void deletePerson() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");
        personApi.tenantPersonsDestroy(personId);
        personId = 0;
    }

    @AfterAll
    static void teardown() {
        if (token == null) return;
        // Cleanup uses raw HTTP — never fails due to spec bugs
        if (clientId > 0) deleteEntity(token, "/api/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/persons/" + personId);
    }
}
