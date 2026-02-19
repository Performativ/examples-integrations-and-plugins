package com.performativ.scenarios.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.scenarios.BaseScenario;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3: Portfolio Setup — create Person, Client, Portfolio, read back, update, delete all.
 *
 * <p>Uses raw HTTP throughout. Deletes in reverse dependency order.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PortfolioSetupScenario extends BaseScenario {

    private static String token;
    private static int personId;
    private static int clientId;
    private static int portfolioId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();
    }

    // -- Create chain: Person -> Client -> Portfolio -----------------------

    @Test
    @Order(1)
    void createPerson() throws Exception {
        JsonNode person = createEntity(token, "/api/persons",
                """
                {"first_name":"Manual","last_name":"S3-PortfolioSetup","email":"manual-s3@example.com","language_code":"en"}
                """);

        personId = person.get("id").asInt();
        assertTrue(personId > 0, "Person ID should be positive");
    }

    @Test
    @Order(2)
    void createClient() throws Exception {
        assertTrue(personId > 0, "Person must be created first");

        JsonNode client = createEntity(token, "/api/clients",
                String.format("""
                {"name":"Manual-S3 Client","type":"individual","is_active":true,"primary_person_id":%d}
                """, personId));

        clientId = client.get("id").asInt();
        assertTrue(clientId > 0, "Client ID should be positive");
    }

    @Test
    @Order(3)
    void createPortfolio() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        JsonNode portfolio = createEntity(token, "/api/clients/" + clientId + "/portfolios",
                """
                {"name":"Manual-S3 Portfolio","currency_id":47}
                """);

        portfolioId = portfolio.get("id").asInt();
        assertTrue(portfolioId > 0, "Portfolio ID should be positive");
        assertEquals("Manual-S3 Portfolio", portfolio.get("name").asText());
    }

    // -- Read back ---------------------------------------------------------

    @Test
    @Order(4)
    void readBackPortfolio() throws Exception {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        HttpResponse<String> response = apiGet(token, "/api/portfolios/" + portfolioId);
        assertEquals(200, response.statusCode());

        JsonNode portfolio = objectMapper.readTree(response.body()).path("data");
        assertEquals(portfolioId, portfolio.get("id").asInt());
        assertEquals("Manual-S3 Portfolio", portfolio.get("name").asText());
    }

    // -- Update ------------------------------------------------------------

    @Test
    @Order(5)
    void updatePortfolio() throws Exception {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        HttpResponse<String> response = apiPut(token, "/api/portfolios/" + portfolioId,
                """
                {"name":"Manual-S3 Portfolio Updated"}
                """);
        assertTrue(response.statusCode() < 300,
                "Update should succeed, got: " + response.statusCode() + " " + response.body());
    }

    // -- Delete in reverse order -------------------------------------------

    @Test
    @Order(6)
    void deletePortfolio() throws Exception {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/portfolios/" + portfolioId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        portfolioId = 0;
    }

    @Test
    @Order(7)
    void deleteClient() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/clients/" + clientId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        clientId = 0;
    }

    @Test
    @Order(8)
    void deletePerson() throws Exception {
        assertTrue(personId > 0, "Person must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/persons/" + personId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        personId = 0;
    }

    @AfterAll
    static void teardown() {
        if (token == null) return;
        if (portfolioId > 0) deleteEntity(token, "/api/portfolios/" + portfolioId);
        if (clientId > 0) deleteEntity(token, "/api/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/persons/" + personId);
    }
}
