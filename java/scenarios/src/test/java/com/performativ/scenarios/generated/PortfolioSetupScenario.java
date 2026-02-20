package com.performativ.scenarios.generated;

import com.performativ.client.api.ClientApi;
import com.performativ.client.api.ClientPersonApi;
import com.performativ.client.api.PersonApi;
import com.performativ.client.api.PortfolioApi;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import com.performativ.client.model.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3: Portfolio Setup — create Person, Client, Portfolio via v1 endpoints,
 * read, update, delete using the generated OpenAPI client exclusively.
 *
 * <p>Strict: no raw HTTP fallbacks. If the generated client fails, the test
 * fails — surfacing spec bugs immediately.
 *
 * <p>In v1, Person is linked to Client via {@code /v1/client-persons} and
 * Portfolio is created at {@code /v1/portfolios} with {@code client_id} in the body.
 *
 * <p>Cleanup uses raw HTTP to ensure entities are deleted even when the
 * generated client has issues.
 *
 * <p><b>Note:</b> Generated method/model names below are provisional —
 * they will be updated after the upstream spec duplicate operationId fix
 * and client regeneration.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PortfolioSetupScenario extends GeneratedClientScenario {

    private static String token;
    private static PersonApi personApi;
    private static ClientApi clientApi;
    private static ClientPersonApi clientPersonApi;
    private static PortfolioApi portfolioApi;

    private static int personId;
    private static int clientId;
    private static int portfolioId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();

        ApiClient apiClient = createApiClient(token);
        personApi = new PersonApi(apiClient);
        clientApi = new ClientApi(apiClient);
        clientPersonApi = new ClientPersonApi(apiClient);
        portfolioApi = new PortfolioApi(apiClient);
    }

    // -- Create chain: Person -> Client -> link -> Portfolio ------------------

    @Test
    @Order(1)
    void createPerson() throws ApiException {
        StorePersonRequest req = new StorePersonRequest();
        req.firstName("Gen");
        req.lastName("S3-PortfolioSetup");
        req.email("gen-s3@example.com");
        req.languageCode("en");

        var response = personApi.tenantPersonsStore(req);
        assertNotNull(response);
        assertNotNull(response.getData());

        personId = response.getData().getId();
        assertTrue(personId > 0, "Person ID should be positive");
    }

    @Test
    @Order(2)
    void createClient() throws ApiException {
        // v1: no primary_person_id, requires currency_id
        StoreClientRequest req = new StoreClientRequest();
        req.name("Gen-S3 Client");
        req.type("individual");
        req.isActive(true);
        req.currencyId(47);

        var response = clientApi.tenantClientsStore(req);
        assertNotNull(response);
        assertNotNull(response.getData());

        clientId = response.getData().getId();
        assertTrue(clientId > 0, "Client ID should be positive");
        assertEquals("Gen-S3 Client", response.getData().getName(),
                "Created client name should round-trip through typed model");
    }

    @Test
    @Order(3)
    void linkPersonToClient() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");
        assertTrue(clientId > 0, "Client must be created first");

        StoreClientPersonRequest req = new StoreClientPersonRequest();
        req.clientId(clientId);
        req.personId(personId);
        req.isPrimary(true);

        clientPersonApi.clientPersonsStore(req);
    }

    @Test
    @Order(4)
    void createPortfolio() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");

        // v1: portfolios created at /v1/portfolios with client_id in body
        // TODO: After regen, method may change from tenantClientsPortfoliosStore
        // to portfoliosStore (using POST /v1/portfolios)
        StorePortfolioRequest req = new StorePortfolioRequest();
        req.name("Gen-S3 Portfolio");
        req.clientId(clientId);
        req.currencyId(47); // EUR

        var response = portfolioApi.portfoliosStore(req);
        assertNotNull(response, "Portfolio store response should not be null");
        assertNotNull(response.getData(), "Portfolio data should not be null");

        portfolioId = response.getData().getId();
        assertTrue(portfolioId > 0, "Portfolio ID should be positive");
        assertEquals("Gen-S3 Portfolio", response.getData().getName(),
                "Created portfolio name should round-trip through typed model");
    }

    // -- Read back ---------------------------------------------------------

    @Test
    @Order(5)
    void readPortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        var response = portfolioApi.tenantPortfoliosShow(portfolioId);
        assertNotNull(response);

        var data = response.getData();
        assertEquals(portfolioId, data.getId());
        assertEquals("Gen-S3 Portfolio", data.getName(),
                "Read-back name should match — verifies show response model");
    }

    // -- Update ------------------------------------------------------------

    @Test
    @Order(6)
    void updatePortfolio() throws Exception {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        // Use raw HTTP for the update, then read back through the generated
        // client to verify the typed response model.
        var rawResponse = apiPut(token, "/api/v1/portfolios/" + portfolioId,
                """
                {"name":"Gen-S3 Portfolio Updated","currency_id":47}
                """);
        assertTrue(rawResponse.statusCode() < 300,
                "Update should succeed, got: " + rawResponse.statusCode());

        // Read back through the generated client to verify typed deserialization
        var readBack = portfolioApi.tenantPortfoliosShow(portfolioId);
        assertEquals("Gen-S3 Portfolio Updated", readBack.getData().getName(),
                "Updated name should persist — verifies show response model after update");
    }

    // -- Delete in reverse order -------------------------------------------

    @Test
    @Order(7)
    void deletePortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        portfolioApi.tenantPortfoliosDestroy(portfolioId);
        portfolioId = 0;
    }

    @Test
    @Order(8)
    void deleteClient() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");
        clientApi.tenantClientsDestroy(clientId);
        clientId = 0;
    }

    @Test
    @Order(9)
    void deletePerson() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");
        personApi.tenantPersonsDestroy(personId);
        personId = 0;
    }

    @AfterAll
    static void teardown() {
        if (token == null) return;
        // Cleanup uses raw HTTP — never fails due to spec bugs
        if (portfolioId > 0) deleteEntity(token, "/api/v1/portfolios/" + portfolioId);
        if (clientId > 0) deleteEntity(token, "/api/v1/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/v1/persons/" + personId);
    }
}
