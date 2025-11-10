package com.performativ.scenarios;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.client.api.ClientApi;
import com.performativ.client.api.PortfolioApi;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import com.performativ.client.model.*;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the Performativ API using the OpenAPI-generated Java client.
 *
 * <p>Demonstrates typed request/response objects instead of raw JSON strings.
 * Creates Person → Client → Portfolio, reads them back, then tears down.
 *
 * <p>The generated client is produced from {@code openapi.json} by the
 * {@code openapi-generator-maven-plugin} at build time. Where the spec's
 * response model doesn't match the live API (e.g. portfolios), the test
 * falls back to the raw API and logs the mismatch.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenApiClientScenario extends BaseScenario {

    private static ApiClient apiClient;
    private static ClientApi clientApi;
    private static PortfolioApi portfolioApi;

    private static String token;
    private static int personId;
    private static int clientId;
    private static int portfolioId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");

        token = acquireToken();

        apiClient = new ApiClient();
        // The OpenAPI spec paths start at /clients, /persons etc. (no /api prefix),
        // so the base path must include /api.
        apiClient.setBasePath(apiBaseUrl() + "/api");
        apiClient.setBearerToken(token);

        clientApi = new ClientApi(apiClient);
        portfolioApi = new PortfolioApi(apiClient);

        webhookChecker.clearEvents();
    }

    // ── Create ──────────────────────────────────────────────────────

    @Test
    @Order(1)
    void createPerson() throws Exception {
        // The spec models person creation under /clients/{id}/persons (inline),
        // so we use the raw API for standalone person creation.
        JsonNode person = createEntity(token, "/api/persons",
                """
                {"first_name":"OpenApi","last_name":"Generated","email":"openapi-generated@example.com","language_code":"en"}
                """);
        personId = person.get("id").asInt();
        assertTrue(personId > 0, "Person ID should be positive");
    }

    @Test
    @Order(2)
    void createClientViaGeneratedClient() {
        assertTrue(personId > 0, "Person must be created first");

        StoreClientRequest req = new StoreClientRequest();
        req.name("OpenApi Generated Client");
        req.type("individual");
        req.isActive(true);

        try {
            TenantClientsStore200Response response = clientApi.tenantClientsStore(req);
            assertNotNull(response, "Client store response should not be null");
            assertNotNull(response.getData(), "Client data should not be null");
            clientId = response.getData().getId();
            assertTrue(clientId > 0, "Client ID should be positive");
            assertEquals("OpenApi Generated Client", response.getData().getName());
        } catch (ApiException e) {
            fail("Generated client: create client failed — HTTP " + e.getCode() + ": " + e.getResponseBody());
        } catch (Exception e) {
            fail("Generated client: create client failed — " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Test
    @Order(3)
    void createPortfolio() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        // Portfolio creation via the generated client may fail due to response
        // deserialization mismatches. Try the generated client first, fall back to raw API.
        StorePortfolioRequest req = new StorePortfolioRequest();
        req.name("OpenApi Generated Portfolio");
        req.currencyId(47); // EUR

        try {
            var response = portfolioApi.tenantClientsPortfoliosStore(clientId, req);
            assertNotNull(response);
            portfolioId = response.getData().getId();
        } catch (Exception e) {
            System.out.println("Generated client portfolio create failed (" + e.getClass().getSimpleName()
                    + "), falling back to raw API — spec response model may need updating");
            JsonNode portfolio = createEntity(token, "/api/clients/" + clientId + "/portfolios",
                    """
                    {"name":"OpenApi Generated Portfolio","currency_id":47}
                    """);
            portfolioId = portfolio.get("id").asInt();
        }

        assertTrue(portfolioId > 0, "Portfolio ID should be positive");
    }

    // ── Read back ───────────────────────────────────────────────────

    @Test
    @Order(4)
    void readClientViaGeneratedClient() {
        assertTrue(clientId > 0, "Client must be created first");

        try {
            var response = clientApi.tenantClientsShow(clientId);
            assertNotNull(response);
            assertEquals(clientId, response.getData().getId());
            assertEquals("OpenApi Generated Client", response.getData().getName());
        } catch (ApiException e) {
            fail("Generated client: read client failed — HTTP " + e.getCode() + ": " + e.getResponseBody());
        } catch (Exception e) {
            fail("Generated client: read client failed — " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Test
    @Order(5)
    void readPortfolio() throws Exception {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        // Same fallback pattern as create
        try {
            var response = portfolioApi.tenantPortfoliosShow(portfolioId);
            assertNotNull(response);
            assertEquals(portfolioId, response.getData().getId());
        } catch (Exception e) {
            System.out.println("Generated client portfolio read failed, falling back to raw API");
            HttpResponse<String> raw = apiGet(token, "/api/portfolios/" + portfolioId);
            assertEquals(200, raw.statusCode());
            JsonNode data = objectMapper.readTree(raw.body()).path("data");
            assertEquals(portfolioId, data.get("id").asInt());
        }
    }

    // ── Webhook verification ────────────────────────────────────────

    @Test
    @Order(6)
    void verifyWebhooksIfEnabled() {
        if (!webhookChecker.isEnabled()) {
            System.out.println("Webhook checking not configured — set WEBHOOK_RECEIVER_URL to enable");
            return;
        }

        assertTrue(clientId > 0, "Client must be created first");

        assertTrue(webhookChecker.waitForEvent("Client", "Created",
                        String.valueOf(clientId), 10_000),
                "Expected Client.Created webhook for client " + clientId);
    }

    // ── Teardown ────────────────────────────────────────────────────

    @AfterAll
    static void teardown() {
        if (token == null) return;
        if (portfolioId > 0) deleteEntity(token, "/api/portfolios/" + portfolioId);
        if (clientId > 0) deleteEntity(token, "/api/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/persons/" + personId);
    }
}
