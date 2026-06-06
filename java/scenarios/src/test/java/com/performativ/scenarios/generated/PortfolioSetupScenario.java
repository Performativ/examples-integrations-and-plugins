package com.performativ.scenarios.generated;

import com.performativ.client.api.ClientsApi;
import com.performativ.client.api.PersonsApi;
import com.performativ.client.api.PortfoliosApi;
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
    private static PersonsApi personsApi;
    private static ClientsApi clientsApi;
    private static PortfoliosApi portfoliosApi;

    private static long personId;
    private static long clientId;
    private static long portfolioId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();

        ApiClient apiClient = createApiClient(token);
        personsApi = new PersonsApi(apiClient);
        clientsApi = new ClientsApi(apiClient);
        portfoliosApi = new PortfoliosApi(apiClient);
    }

    // -- Create chain: Person -> Client -> link -> Portfolio ------------------

    @Test
    @Order(SETUP + 1)
    void createPerson() throws ApiException {
        var req = new StorePersonRequest()
                .firstName("Gen")
                .lastName("S3-PortfolioSetup")
                .email("gen-s3@example.com")
                .languageCode("en");

        var response = personsApi.personsStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        personId = response.getData().getId();
        assertTrue(personId > 0, "Person ID should be positive");
        registerCleanup(token, "/api/v1/persons/" + personId);
    }

    @Test
    @Order(SETUP + 2)
    void createClient() throws ApiException {
        var req = new StoreClientRequest()
                .name("Gen-S3 Client")
                .type(StoreClientRequest.TypeEnum.INDIVIDUAL)
                .isActive(true)
                .currencyId(47L);

        var response = clientsApi.clientsStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        clientId = response.getData().getId();
        assertTrue(clientId > 0, "Client ID should be positive");
        registerCleanup(token, "/api/v1/clients/" + clientId);
        assertEquals("Gen-S3 Client", response.getData().getName(),
                "Created client name should round-trip through typed model");
    }

    @Test
    @Order(SETUP + 3)
    void linkPersonToClient() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");
        assertTrue(clientId > 0, "Client must be created first");

        var req = new StoreClientPersonRequest()
                .clientId(clientId)
                .personId(personId)
                .isPrimary(true);

        clientsApi.clientPersonsStore(req, idempotencyKey());
    }

    @Test
    @Order(SETUP + 4)
    void createPortfolio() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");

        var req = new StorePortfolioRequest()
                .name("Gen-S3 Portfolio")
                .addClientIdsItem(clientId)
                .currencyId(47L);

        var response = portfoliosApi.portfoliosStore(req, idempotencyKey());
        assertNotNull(response, "Portfolio store response should not be null");
        assertNotNull(response.getData(), "Portfolio data should not be null");

        portfolioId = response.getData().getId();
        assertTrue(portfolioId > 0, "Portfolio ID should be positive");
        registerCleanup(token, "/api/v1/portfolios/" + portfolioId);
        assertEquals("Gen-S3 Portfolio", response.getData().getName(),
                "Created portfolio name should round-trip through typed model");
    }

    // -- Read back ---------------------------------------------------------

    @Test
    @Order(VERIFY + 1)
    void readPortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        var response = portfoliosApi.portfoliosShow(String.valueOf(portfolioId), null);
        assertNotNull(response);

        var data = response.getData();
        assertEquals(portfolioId, data.getId());
        assertEquals("Gen-S3 Portfolio", data.getName(),
                "Read-back name should match — verifies show response model");
    }

    // -- Update ------------------------------------------------------------

    @Test
    @Order(VERIFY + 2)
    void updatePortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        var req = new UpdatePortfolioRequest()
                .name("Gen-S3 Portfolio Updated")
                .currencyId(47L);

        var response = portfoliosApi.portfoliosUpdate(String.valueOf(portfolioId), req);
        assertNotNull(response, "Portfolio update response should not be null");

        var readBack = portfoliosApi.portfoliosShow(String.valueOf(portfolioId), null);
        assertEquals("Gen-S3 Portfolio Updated", readBack.getData().getName(),
                "Updated name should persist — verifies show response model after update");
    }

    // -- Delete in reverse order -------------------------------------------

    @Test
    @Order(TEARDOWN + 1)
    void deletePortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        portfoliosApi.portfoliosDestroy(String.valueOf(portfolioId));
        portfolioId = 0;
    }

    @Test
    @Order(TEARDOWN + 2)
    void deleteClient() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");
        clientsApi.clientsDestroy(String.valueOf(clientId));
        clientId = 0;
    }

    @Test
    @Order(TEARDOWN + 3)
    void deletePerson() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");
        try {
            personsApi.personsDestroy(String.valueOf(personId));
        } catch (ApiException e) {
            // Person may already be cascade-deleted with the client
            if (e.getCode() != 404) throw e;
        }
        personId = 0;
    }

    @AfterAll
    static void teardown() {
        runCleanup();
    }
}
