package com.performativ.scenarios.generated;

import com.performativ.client.api.ClientApi;
import com.performativ.client.api.PersonApi;
import com.performativ.client.api.PortfolioApi;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import com.performativ.client.model.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3: Portfolio Setup — create Person, Client, Portfolio, read, update, delete
 * using the generated OpenAPI client exclusively.
 *
 * <p>Strict: no raw HTTP fallbacks. If the generated client fails, the test
 * fails — surfacing spec bugs immediately.
 *
 * <p>Cleanup uses raw HTTP to ensure entities are deleted even when the
 * generated client has issues.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PortfolioSetupScenario extends GeneratedClientScenario {

    private static String token;
    private static PersonApi personApi;
    private static ClientApi clientApi;
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
        portfolioApi = new PortfolioApi(apiClient);
    }

    // -- Create chain: Person -> Client -> Portfolio -----------------------

    @Test
    @Order(1)
    void createPerson() throws ApiException {
        StorePersonRequest req = new StorePersonRequest();
        req.firstName("Scenario");
        req.lastName("PortfolioSetup");
        req.email("scenario-s3-gen@example.com");
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
        assertTrue(personId > 0, "Person must be created first");

        StoreClientRequest req = new StoreClientRequest();
        req.name("Scenario-S3 Client");
        req.type("individual");
        req.isActive(true);

        var response = clientApi.tenantClientsStore(req);
        assertNotNull(response);
        assertNotNull(response.getData());

        clientId = response.getData().getId();
        assertTrue(clientId > 0, "Client ID should be positive");
    }

    @Test
    @Order(3)
    void createPortfolio() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");

        StorePortfolioRequest req = new StorePortfolioRequest();
        req.name("Scenario-S3 Portfolio");
        req.currencyId(47); // EUR

        var response = portfolioApi.tenantClientsPortfoliosStore(clientId, req);
        assertNotNull(response, "Portfolio store response should not be null");
        assertNotNull(response.getData(), "Portfolio data should not be null");

        portfolioId = response.getData().getId();
        assertTrue(portfolioId > 0, "Portfolio ID should be positive");
    }

    // -- Read back ---------------------------------------------------------

    @Test
    @Order(4)
    void readPortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        var response = portfolioApi.tenantPortfoliosShow(portfolioId);
        assertNotNull(response);
        assertEquals(portfolioId, response.getData().getId());
    }

    // -- Update ------------------------------------------------------------

    @Test
    @Order(5)
    void updatePortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        UpdatePortfolioRequest req = new UpdatePortfolioRequest();
        req.name("Scenario-S3 Portfolio Updated");

        portfolioApi.tenantPortfoliosUpdate(portfolioId, req);
    }

    // -- Delete in reverse order -------------------------------------------

    @Test
    @Order(6)
    void deletePortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        portfolioApi.tenantPortfoliosDestroy(portfolioId);
        portfolioId = 0;
    }

    @Test
    @Order(7)
    void deleteClient() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");
        clientApi.tenantClientsDestroy(clientId);
        clientId = 0;
    }

    @Test
    @Order(8)
    void deletePerson() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");
        personApi.tenantPersonsDestroy(personId);
        personId = 0;
    }

    @AfterAll
    static void teardown() {
        if (token == null) return;
        // Cleanup uses raw HTTP — never fails due to spec bugs
        if (portfolioId > 0) deleteEntity(token, "/api/portfolios/" + portfolioId);
        if (clientId > 0) deleteEntity(token, "/api/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/persons/" + personId);
    }
}
