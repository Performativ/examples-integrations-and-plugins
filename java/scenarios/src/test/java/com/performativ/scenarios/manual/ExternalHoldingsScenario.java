package com.performativ.scenarios.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.scenarios.BaseScenario;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S4: External Holdings — create person relationships, cash accounts,
 * external positions and balances via raw HTTP.
 *
 * <p>Creates two persons with a relationship (married couple), links both
 * to a client, adds a portfolio with a cash account, then creates external
 * positions and balances. Verifies reads and updates, then deletes all in
 * reverse order.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExternalHoldingsScenario extends BaseScenario {

    private static String token;
    private static int relationshipTypeId;
    private static int personAId;
    private static int personBId;
    private static int relationshipId;
    private static int clientId;
    private static int portfolioId;
    private static int cashAccountId;
    private static int portfolioCashAccountId;
    private static int externalPositionId;
    private static int externalBalanceId;
    private static int instrumentId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();
    }

    // -- Setup: relationship type, persons, relationship, client, links --------

    @Test
    @Order(SETUP + 1)
    void createRelationshipType() throws Exception {
        JsonNode relType = createEntity(token, "/api/v1/person-relationship-types",
                """
                {"key":"manual-s4-married","label":"Married","is_symmetric":true}
                """);

        relationshipTypeId = relType.get("id").asInt();
        assertTrue(relationshipTypeId > 0, "Relationship type ID should be positive");
        registerCleanup(token, "/api/v1/person-relationship-types/" + relationshipTypeId);
    }

    @Test
    @Order(SETUP + 2)
    void createPersonA() throws Exception {
        JsonNode person = createEntity(token, "/api/v1/persons",
                """
                {"first_name":"Manual","last_name":"S4-ExternalHoldings-John","email":"manual-s4-john@example.com","language_code":"en"}
                """);

        personAId = person.get("id").asInt();
        assertTrue(personAId > 0, "Person A ID should be positive");
        registerCleanup(token, "/api/v1/persons/" + personAId);
    }

    @Test
    @Order(SETUP + 3)
    void createPersonB() throws Exception {
        JsonNode person = createEntity(token, "/api/v1/persons",
                """
                {"first_name":"Manual","last_name":"S4-ExternalHoldings-Jane","email":"manual-s4-jane@example.com","language_code":"en"}
                """);

        personBId = person.get("id").asInt();
        assertTrue(personBId > 0, "Person B ID should be positive");
        registerCleanup(token, "/api/v1/persons/" + personBId);
    }

    @Test
    @Order(SETUP + 4)
    void createRelationship() throws Exception {
        assertTrue(personAId > 0, "Person A must be created first");
        assertTrue(personBId > 0, "Person B must be created first");
        assertTrue(relationshipTypeId > 0, "Relationship type must be created first");

        JsonNode rel = createEntity(token, "/api/v1/person-relationships",
                String.format("""
                {"person_id":%d,"related_person_id":%d,"person_relationship_type_id":%d}
                """, personAId, personBId, relationshipTypeId));

        relationshipId = rel.get("id").asInt();
        assertTrue(relationshipId > 0, "Relationship ID should be positive");
        registerCleanup(token, "/api/v1/person-relationships/" + relationshipId);
    }

    @Test
    @Order(SETUP + 5)
    void createClient() throws Exception {
        JsonNode client = createEntity(token, "/api/v1/clients",
                """
                {"name":"Manual-S4 Client","type":"individual","is_active":true,"currency_id":47}
                """);

        clientId = client.get("id").asInt();
        assertTrue(clientId > 0, "Client ID should be positive");
        registerCleanup(token, "/api/v1/clients/" + clientId);
    }

    @Test
    @Order(SETUP + 6)
    void linkPersonAPrimary() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");
        assertTrue(personAId > 0, "Person A must be created first");

        HttpResponse<String> response = apiPost(token, "/api/v1/client-persons",
                String.format("""
                {"client_id":%d,"person_id":%d,"is_primary":true}
                """, clientId, personAId));

        assertTrue(response.statusCode() < 300,
                "Link Person A should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(SETUP + 7)
    void linkPersonBSecondary() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");
        assertTrue(personBId > 0, "Person B must be created first");

        HttpResponse<String> response = apiPost(token, "/api/v1/client-persons",
                String.format("""
                {"client_id":%d,"person_id":%d,"is_primary":false}
                """, clientId, personBId));

        assertTrue(response.statusCode() < 300,
                "Link Person B should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(SETUP + 8)
    void createPortfolio() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        JsonNode portfolio = createEntity(token, "/api/v1/portfolios",
                String.format("""
                {"name":"Manual-S4 Portfolio","client_ids":[%d],"currency_id":47}
                """, clientId));

        portfolioId = portfolio.get("id").asInt();
        assertTrue(portfolioId > 0, "Portfolio ID should be positive");
        registerCleanup(token, "/api/v1/portfolios/" + portfolioId);
    }

    @Test
    @Order(SETUP + 9)
    void createCashAccount() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        JsonNode cashAccount = createEntity(token, "/api/v1/cash-accounts",
                String.format("""
                {"name":"Manual-S4 Cash Account","client_ids":[%d],"currency_id":47}
                """, clientId));

        cashAccountId = cashAccount.get("id").asInt();
        assertTrue(cashAccountId > 0, "Cash Account ID should be positive");
        registerCleanup(token, "/api/v1/cash-accounts/" + cashAccountId);
    }

    @Test
    @Order(SETUP + 10)
    void linkCashAccountToPortfolio() throws Exception {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        assertTrue(cashAccountId > 0, "Cash Account must be created first");

        JsonNode pca = createEntity(token, "/api/v1/portfolio-cash-accounts",
                String.format("""
                {"portfolio_id":%d,"cash_account_id":%d}
                """, portfolioId, cashAccountId));

        portfolioCashAccountId = pca.get("id").asInt();
        assertTrue(portfolioCashAccountId > 0, "Portfolio Cash Account link ID should be positive");
        registerCleanup(token, "/api/v1/portfolio-cash-accounts/" + portfolioCashAccountId);
    }

    @Test
    @Order(SETUP + 11)
    void createExternalPosition() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        // v1 external positions reference a pre-registered Instrument by id.
        // Instruments are pre-registered (typically via bulk file);
        // instrument_isin/name are optional audit fields. Reference an existing
        // instrument from the tenant universe.
        HttpResponse<String> instruments = apiGet(token, "/api/v1/instruments?per_page=1");
        JsonNode instrData = objectMapper.readTree(instruments.body()).path("data");
        assertTrue(instrData.isArray() && instrData.size() > 0,
                "Tenant must have at least one pre-registered instrument");
        instrumentId = instrData.get(0).get("id").asInt();
        assertTrue(instrumentId > 0, "Instrument ID should be positive");

        JsonNode extPos = createEntity(token, "/api/v1/external-positions",
                String.format("""
                {"portfolio_id":%d,"instrument_id":%d,"quantity":100,"currency_id":47,"date":"%s"}
                """, portfolioId, instrumentId, LocalDate.now().toString()));

        externalPositionId = extPos.get("id").asInt();
        assertTrue(externalPositionId > 0, "External Position ID should be positive");
        registerCleanup(token, "/api/v1/external-positions/" + externalPositionId);
    }

    @Test
    @Order(SETUP + 12)
    void createExternalBalance() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");
        assertTrue(cashAccountId > 0, "Cash Account must be created first");

        JsonNode extBal = createEntity(token, "/api/v1/external-balances",
                String.format("""
                {"cash_account_id":%d,"balance":50000.00,"currency_id":47,"date":"%s"}
                """, cashAccountId, LocalDate.now().toString()));

        externalBalanceId = extBal.get("id").asInt();
        assertTrue(externalBalanceId > 0, "External Balance ID should be positive");
        registerCleanup(token, "/api/v1/external-balances/" + externalBalanceId);
    }

    // -- Verify ---------------------------------------------------------------

    @Test
    @Order(VERIFY + 1)
    void readExternalPosition() throws Exception {
        assertTrue(externalPositionId > 0, "External Position must be created first");

        HttpResponse<String> response = apiGet(token, "/api/v1/external-positions/" + externalPositionId);
        assertEquals(200, response.statusCode());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals(externalPositionId, data.get("id").asInt());
    }

    @Test
    @Order(VERIFY + 2)
    void readExternalBalance() throws Exception {
        assertTrue(externalBalanceId > 0, "External Balance must be created first");

        HttpResponse<String> response = apiGet(token, "/api/v1/external-balances/" + externalBalanceId);
        assertEquals(200, response.statusCode());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals(externalBalanceId, data.get("id").asInt());
    }

    @Test
    @Order(VERIFY + 3)
    void listPersonRelationships() throws Exception {
        assertTrue(personAId > 0, "Person A must be created first");

        HttpResponse<String> response = apiGet(token, "/api/v1/persons/" + personAId + "/relationships");
        assertEquals(200, response.statusCode());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertTrue(data.isArray(), "Response should contain a data array");
        assertTrue(data.size() > 0, "Person A should have at least one relationship");
    }

    @Test
    @Order(VERIFY + 4)
    void updateExternalPosition() throws Exception {
        assertTrue(externalPositionId > 0, "External Position must be created first");
        assertTrue(clientId > 0, "Client must be created first");
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        HttpResponse<String> response = apiPut(token, "/api/v1/external-positions/" + externalPositionId,
                String.format("""
                {"portfolio_id":%d,"instrument_id":%d,"quantity":200,"currency_id":47,"date":"%s"}
                """, portfolioId, instrumentId, LocalDate.now().toString()));

        assertTrue(response.statusCode() < 300,
                "Update should succeed, got: " + response.statusCode() + " " + response.body());
    }

    // -- Teardown (reverse order) ---------------------------------------------

    @Test
    @Order(TEARDOWN + 1)
    void deleteExternalBalance() throws Exception {
        assertTrue(externalBalanceId > 0, "External Balance must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/external-balances/" + externalBalanceId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        externalBalanceId = 0;
    }

    @Test
    @Order(TEARDOWN + 2)
    void deleteExternalPosition() throws Exception {
        assertTrue(externalPositionId > 0, "External Position must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/external-positions/" + externalPositionId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        externalPositionId = 0;
    }

    @Test
    @Order(TEARDOWN + 3)
    void deletePortfolioCashAccount() throws Exception {
        assertTrue(portfolioCashAccountId > 0, "Portfolio Cash Account link must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/portfolio-cash-accounts/" + portfolioCashAccountId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        portfolioCashAccountId = 0;
    }

    @Test
    @Order(TEARDOWN + 4)
    void deleteCashAccount() throws Exception {
        assertTrue(cashAccountId > 0, "Cash Account must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/cash-accounts/" + cashAccountId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        cashAccountId = 0;
    }

    @Test
    @Order(TEARDOWN + 5)
    void deletePortfolio() throws Exception {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/portfolios/" + portfolioId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        portfolioId = 0;
    }

    @Test
    @Order(TEARDOWN + 6)
    void deleteRelationship() throws Exception {
        assertTrue(relationshipId > 0, "Relationship must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/person-relationships/" + relationshipId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        relationshipId = 0;
    }

    @Test
    @Order(TEARDOWN + 7)
    void deletePersonB() throws Exception {
        assertTrue(personBId > 0, "Person B must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/persons/" + personBId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        personBId = 0;
    }

    @Test
    @Order(TEARDOWN + 8)
    void deletePersonA() throws Exception {
        assertTrue(personAId > 0, "Person A must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/persons/" + personAId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        personAId = 0;
    }

    @Test
    @Order(TEARDOWN + 9)
    void deleteClient() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/clients/" + clientId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        clientId = 0;
    }

    @Test
    @Order(TEARDOWN + 10)
    void deleteRelationshipType() throws Exception {
        assertTrue(relationshipTypeId > 0, "Relationship type must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/person-relationship-types/" + relationshipTypeId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        relationshipTypeId = 0;
    }

    @AfterAll
    static void teardown() {
        runCleanup();
    }
}
