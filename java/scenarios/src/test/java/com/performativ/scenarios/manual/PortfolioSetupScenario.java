package com.performativ.scenarios.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.scenarios.BaseScenario;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3: Portfolio Setup — create Person, Client, Portfolio via v1 API,
 * read back, update, delete all.
 *
 * <p>Uses raw HTTP throughout. Deletes in reverse dependency order.
 * In v1, Person is linked to Client via {@code /v1/client-persons} and
 * Portfolio is created at {@code /v1/portfolios} with {@code client_id} in the body.
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

    // -- Create chain: Person -> Client -> link -> Portfolio ------------------

    @Test
    @Order(1)
    void createPerson() throws Exception {
        JsonNode person = createEntity(token, "/api/v1/persons",
                """
                {"first_name":"Manual","last_name":"S3-PortfolioSetup","email":"manual-s3@example.com","language_code":"en"}
                """);

        personId = person.get("id").asInt();
        assertTrue(personId > 0, "Person ID should be positive");
    }

    @Test
    @Order(2)
    void createClient() throws Exception {
        JsonNode client = createEntity(token, "/api/v1/clients",
                """
                {"name":"Manual-S3 Client","type":"individual","is_active":true,"currency_id":47}
                """);

        clientId = client.get("id").asInt();
        assertTrue(clientId > 0, "Client ID should be positive");
    }

    @Test
    @Order(3)
    void linkPersonToClient() throws Exception {
        assertTrue(personId > 0, "Person must be created first");
        assertTrue(clientId > 0, "Client must be created first");

        HttpResponse<String> response = apiPost(token, "/api/v1/client-persons",
                String.format("""
                {"client_id":%d,"person_id":%d,"is_primary":true}
                """, clientId, personId));

        assertTrue(response.statusCode() < 300,
                "Link person to client should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(4)
    void createPortfolio() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        JsonNode portfolio = createEntity(token, "/api/v1/portfolios",
                String.format("""
                {"name":"Manual-S3 Portfolio","client_id":%d,"currency_id":47}
                """, clientId));

        portfolioId = portfolio.get("id").asInt();
        assertTrue(portfolioId > 0, "Portfolio ID should be positive");
        assertEquals("Manual-S3 Portfolio", portfolio.get("name").asText());
    }

    // -- Read back ---------------------------------------------------------

    @Test
    @Order(5)
    void readBackPortfolio() throws Exception {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        HttpResponse<String> response = apiGet(token, "/api/v1/portfolios/" + portfolioId);
        assertEquals(200, response.statusCode());

        JsonNode portfolio = objectMapper.readTree(response.body()).path("data");
        assertEquals(portfolioId, portfolio.get("id").asInt());
        assertEquals("Manual-S3 Portfolio", portfolio.get("name").asText());
    }

    // -- Update ------------------------------------------------------------

    @Test
    @Order(6)
    void updatePortfolio() throws Exception {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        HttpResponse<String> response = apiPut(token, "/api/v1/portfolios/" + portfolioId,
                """
                {"name":"Manual-S3 Portfolio Updated","currency_id":47}
                """);
        assertTrue(response.statusCode() < 300,
                "Update should succeed, got: " + response.statusCode() + " " + response.body());
    }

    // -- Delete in reverse order -------------------------------------------

    @Test
    @Order(7)
    void deletePortfolio() throws Exception {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/portfolios/" + portfolioId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        portfolioId = 0;
    }

    @Test
    @Order(8)
    void deleteClient() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/clients/" + clientId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        clientId = 0;
    }

    @Test
    @Order(9)
    void deletePerson() throws Exception {
        assertTrue(personId > 0, "Person must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/persons/" + personId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        personId = 0;
    }

    @AfterAll
    static void teardown() {
        if (token == null) return;
        if (portfolioId > 0) deleteEntity(token, "/api/v1/portfolios/" + portfolioId);
        if (clientId > 0) deleteEntity(token, "/api/v1/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/v1/persons/" + personId);
    }
}
