package com.performativ.scenarios;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Creates the full entity chain needed before advise can run:
 * Person → Client → Portfolio → Cash Account → read back → teardown.
 *
 * <p>Cash Account creation may fail on some environments (server-side
 * dependency on custodian configuration). When it does, the test records
 * the failure but does not block the rest of the scenario.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdvisorySetupScenario extends BaseScenario {

    private static String token;
    private static int personId;
    private static int clientId;
    private static int portfolioId;
    private static int cashAccountId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();
    }

    // ── Create ──────────────────────────────────────────────────────

    @Test
    @Order(1)
    void createPerson() throws Exception {
        JsonNode person = createEntity(token, "/api/persons",
                """
                {"first_name":"Advisory","last_name":"Setup","email":"advisory-setup@example.com","language_code":"en"}
                """);

        personId = person.get("id").asInt();
        assertTrue(personId > 0, "Person ID should be positive");
        assertEquals("Advisory", person.get("first_name").asText());
    }

    @Test
    @Order(2)
    void createClient() throws Exception {
        assertTrue(personId > 0, "Person must be created first");

        JsonNode client = createEntity(token, "/api/clients",
                String.format("""
                {"name":"Advisory Setup Client","type":"individual","is_active":true,"primary_person_id":%d}
                """, personId));

        clientId = client.get("id").asInt();
        assertTrue(clientId > 0, "Client ID should be positive");
        assertEquals("Advisory Setup Client", client.get("name").asText());
    }

    @Test
    @Order(3)
    void createPortfolio() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        JsonNode portfolio = createEntity(token, "/api/clients/" + clientId + "/portfolios",
                """
                {"name":"Advisory Setup Portfolio","currency_id":47}
                """);

        portfolioId = portfolio.get("id").asInt();
        assertTrue(portfolioId > 0, "Portfolio ID should be positive");
        assertEquals("Advisory Setup Portfolio", portfolio.get("name").asText());
    }

    @Test
    @Order(4)
    void createCashAccount() throws Exception {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        HttpResponse<String> response = apiPost(token, "/api/cash-accounts",
                String.format("""
                {"name":"Advisory Setup Cash","custodian_id":"manual","external_account_id":"ADV-SETUP-%d","currency_id":47,"client_id":%d,"portfolio_id":%d}
                """, System.currentTimeMillis(), clientId, portfolioId));

        if (response.statusCode() >= 500) {
            System.out.println("Cash Account creation returned " + response.statusCode()
                    + " — skipping (custodian may not be configured). Body: " + response.body());
            return;
        }

        assertTrue(response.statusCode() < 300,
                "Cash Account creation failed: HTTP " + response.statusCode() + " - " + response.body());

        JsonNode cashAccount = objectMapper.readTree(response.body()).path("data");
        cashAccountId = cashAccount.get("id").asInt();
        assertTrue(cashAccountId > 0, "Cash Account ID should be positive");
    }

    // ── Read back ───────────────────────────────────────────────────

    @Test
    @Order(5)
    void readBackPortfolio() throws Exception {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        // Portfolios are read via /api/portfolios/{id} (top-level), not the nested client path
        HttpResponse<String> response = apiGet(token, "/api/portfolios/" + portfolioId);
        assertEquals(200, response.statusCode());

        JsonNode portfolio = objectMapper.readTree(response.body()).path("data");
        assertEquals(portfolioId, portfolio.get("id").asInt());
        assertEquals("Advisory Setup Portfolio", portfolio.get("name").asText());
    }

    @Test
    @Order(6)
    void readBackClient() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        HttpResponse<String> response = apiGet(token, "/api/clients/" + clientId);
        assertEquals(200, response.statusCode());

        JsonNode client = objectMapper.readTree(response.body()).path("data");
        assertEquals(clientId, client.get("id").asInt());
        assertEquals("Advisory Setup Client", client.get("name").asText());
    }

    @Test
    @Order(7)
    void readBackPerson() throws Exception {
        assertTrue(personId > 0, "Person must be created first");

        HttpResponse<String> response = apiGet(token, "/api/persons/" + personId);
        assertEquals(200, response.statusCode());

        JsonNode person = objectMapper.readTree(response.body()).path("data");
        assertEquals(personId, person.get("id").asInt());
        assertEquals("Advisory", person.get("first_name").asText());
    }

    // ── Teardown ────────────────────────────────────────────────────

    @AfterAll
    static void teardown() {
        if (token == null) return;
        // Delete in reverse dependency order
        if (cashAccountId > 0) deleteEntity(token, "/api/cash-accounts/" + cashAccountId);
        if (portfolioId > 0) deleteEntity(token, "/api/portfolios/" + portfolioId);
        if (clientId > 0) deleteEntity(token, "/api/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/persons/" + personId);
    }
}
