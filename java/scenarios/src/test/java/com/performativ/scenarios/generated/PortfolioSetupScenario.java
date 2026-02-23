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
 * <p>In v1, Person is linked to Client via {@code /api/v1/client-persons} and
 * Portfolio is created at {@code /api/v1/portfolios} with {@code client_id} in the body.
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
        var req = new StorePersonRequest()
                .firstName("Gen")
                .lastName("S3-PortfolioSetup")
                .email("gen-s3@example.com")
                .languageCode("en");

        var response = personApi.personsStore(req);
        assertNotNull(response);
        assertNotNull(response.getData());

        personId = response.getData().getId();
        assertTrue(personId > 0, "Person ID should be positive");
    }

    @Test
    @Order(2)
    void createClient() throws ApiException {
        var req = new StoreClientRequest()
                .name("Gen-S3 Client")
                .type("individual")
                .isActive(true)
                .currencyId(47);

        var response = clientApi.clientsStore(req);
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

        var req = new StoreClientPersonRequest()
                .clientId(clientId)
                .personId(personId)
                .isPrimary(true);

        clientPersonApi.clientPersonsStore(req);
    }

    @Test
    @Order(4)
    void createPortfolio() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");

        var req = new StorePortfolioRequest()
                .name("Gen-S3 Portfolio")
                .clientId(clientId)
                .currencyId(47);

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

        var response = portfolioApi.portfoliosShow(String.valueOf(portfolioId), String.valueOf(portfolioId), null);
        assertNotNull(response);

        var data = response.getData();
        assertEquals(portfolioId, data.getId());
        assertEquals("Gen-S3 Portfolio", data.getName(),
                "Read-back name should match — verifies show response model");
    }

    // -- Update ------------------------------------------------------------

    @Test
    @Order(6)
    void updatePortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        var req = new UpdatePortfolioRequest()
                .name("Gen-S3 Portfolio Updated")
                .currencyId(47);

        var response = portfolioApi.portfoliosUpdate(String.valueOf(portfolioId), String.valueOf(portfolioId), req);
        assertNotNull(response, "Portfolio update response should not be null");

        var readBack = portfolioApi.portfoliosShow(String.valueOf(portfolioId), String.valueOf(portfolioId), null);
        assertEquals("Gen-S3 Portfolio Updated", readBack.getData().getName(),
                "Updated name should persist — verifies show response model after update");
    }

    // -- Delete in reverse order -------------------------------------------

    @Test
    @Order(7)
    void deletePortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        portfolioApi.portfoliosDestroy(String.valueOf(portfolioId), String.valueOf(portfolioId));
        portfolioId = 0;
    }

    @Test
    @Order(8)
    void deleteClient() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");
        clientApi.clientsDestroy(String.valueOf(clientId), String.valueOf(clientId));
        clientId = 0;
    }

    @Test
    @Order(9)
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
        if (portfolioId > 0) deleteEntity(token, "/api/v1/portfolios/" + portfolioId);
        if (clientId > 0) deleteEntity(token, "/api/v1/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/v1/persons/" + personId);
    }
}
