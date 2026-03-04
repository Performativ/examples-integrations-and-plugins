package com.performativ.scenarios.generated;

import com.performativ.client.api.*;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import com.performativ.client.model.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S4: External Holdings — create person relationships, cash accounts,
 * external positions and balances using the generated OpenAPI client.
 *
 * <p>Creates two persons with a relationship (married couple), links both
 * to a client, adds a portfolio with a cash account, then creates external
 * positions and balances. Verifies reads and updates, then deletes all in
 * reverse order.
 *
 * <p>Cleanup uses raw HTTP to ensure entities are deleted even when the
 * generated client has issues.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExternalHoldingsScenario extends GeneratedClientScenario {

    private static String token;
    private static PersonRelationshipTypeApi relationshipTypeApi;
    private static PersonRelationshipApi relationshipApi;
    private static PersonApi personApi;
    private static ClientApi clientApi;
    private static ClientPersonApi clientPersonApi;
    private static PortfolioApi portfolioApi;
    private static CashAccountApi cashAccountApi;
    private static PortfolioCashAccountApi portfolioCashAccountApi;
    private static ExternalPositionApi externalPositionApi;
    private static ExternalBalanceApi externalBalanceApi;

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

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();

        ApiClient apiClient = createApiClient(token);
        relationshipTypeApi = new PersonRelationshipTypeApi(apiClient);
        relationshipApi = new PersonRelationshipApi(apiClient);
        personApi = new PersonApi(apiClient);
        clientApi = new ClientApi(apiClient);
        clientPersonApi = new ClientPersonApi(apiClient);
        portfolioApi = new PortfolioApi(apiClient);
        cashAccountApi = new CashAccountApi(apiClient);
        portfolioCashAccountApi = new PortfolioCashAccountApi(apiClient);
        externalPositionApi = new ExternalPositionApi(apiClient);
        externalBalanceApi = new ExternalBalanceApi(apiClient);
    }

    // -- Setup: relationship type, persons, relationship, client, links --------

    @Test
    @Order(SETUP + 1)
    void createRelationshipType() throws ApiException {
        var req = new StorePersonRelationshipTypeRequest()
                .key("gen-s4-married")
                .label("Married")
                .isSymmetric(true);

        var response = relationshipTypeApi.personRelationshipTypesStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        relationshipTypeId = response.getData().getId();
        assertTrue(relationshipTypeId > 0, "Relationship type ID should be positive");
        registerCleanup(token, "/api/v1/person-relationship-types/" + relationshipTypeId);
    }

    @Test
    @Order(SETUP + 2)
    void createPersonA() throws ApiException {
        var req = new StorePersonRequest()
                .firstName("Gen")
                .lastName("S4-ExternalHoldings-John")
                .email("gen-s4-john@example.com")
                .languageCode("en");

        var response = personApi.personsStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        personAId = response.getData().getId();
        assertTrue(personAId > 0, "Person A ID should be positive");
        registerCleanup(token, "/api/v1/persons/" + personAId);
    }

    @Test
    @Order(SETUP + 3)
    void createPersonB() throws ApiException {
        var req = new StorePersonRequest()
                .firstName("Gen")
                .lastName("S4-ExternalHoldings-Jane")
                .email("gen-s4-jane@example.com")
                .languageCode("en");

        var response = personApi.personsStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        personBId = response.getData().getId();
        assertTrue(personBId > 0, "Person B ID should be positive");
        registerCleanup(token, "/api/v1/persons/" + personBId);
    }

    @Test
    @Order(SETUP + 4)
    void createRelationship() throws ApiException {
        assertTrue(personAId > 0, "Person A must be created first");
        assertTrue(personBId > 0, "Person B must be created first");
        assertTrue(relationshipTypeId > 0, "Relationship type must be created first");

        var req = new StorePersonRelationshipRequest()
                .personId(personAId)
                .relatedPersonId(personBId)
                .personRelationshipTypeId(relationshipTypeId);

        var response = relationshipApi.personRelationshipsStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        relationshipId = response.getData().getId();
        assertTrue(relationshipId > 0, "Relationship ID should be positive");
        registerCleanup(token, "/api/v1/person-relationships/" + relationshipId);
    }

    @Test
    @Order(SETUP + 5)
    void createClient() throws ApiException {
        var req = new StoreClientRequest()
                .name("Gen-S4 Client")
                .type(StoreClientRequest.TypeEnum.INDIVIDUAL)
                .isActive(true)
                .currencyId(47);

        var response = clientApi.clientsStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        clientId = response.getData().getId();
        assertTrue(clientId > 0, "Client ID should be positive");
        registerCleanup(token, "/api/v1/clients/" + clientId);
        assertEquals("Gen-S4 Client", response.getData().getName());
    }

    @Test
    @Order(SETUP + 6)
    void linkPersonAPrimary() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");
        assertTrue(personAId > 0, "Person A must be created first");

        var req = new StoreClientPersonRequest()
                .clientId(clientId)
                .personId(personAId)
                .isPrimary(true);

        clientPersonApi.clientPersonsStore(req, idempotencyKey());
    }

    @Test
    @Order(SETUP + 7)
    void linkPersonBSecondary() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");
        assertTrue(personBId > 0, "Person B must be created first");

        var req = new StoreClientPersonRequest()
                .clientId(clientId)
                .personId(personBId)
                .isPrimary(false);

        clientPersonApi.clientPersonsStore(req, idempotencyKey());
    }

    @Test
    @Order(SETUP + 8)
    void createPortfolio() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");

        var req = new StorePortfolioRequest()
                .name("Gen-S4 Portfolio")
                .clientId(clientId)
                .currencyId(47);

        var response = portfolioApi.portfoliosStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        portfolioId = response.getData().getId();
        assertTrue(portfolioId > 0, "Portfolio ID should be positive");
        registerCleanup(token, "/api/v1/portfolios/" + portfolioId);
    }

    @Test
    @Order(SETUP + 9)
    void createCashAccount() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");

        var req = new StoreCashAccountRequest()
                .name("Gen-S4 Cash Account")
                .clientId(clientId)
                .currencyId(47);

        var response = cashAccountApi.cashAccountsStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        cashAccountId = response.getData().getId();
        assertTrue(cashAccountId > 0, "Cash Account ID should be positive");
        registerCleanup(token, "/api/v1/cash-accounts/" + cashAccountId);
    }

    @Test
    @Order(SETUP + 10)
    void linkCashAccountToPortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        assertTrue(cashAccountId > 0, "Cash Account must be created first");

        var req = new StorePortfolioCashAccountRequest()
                .portfolioId(portfolioId)
                .cashAccountId(cashAccountId);

        var response = portfolioCashAccountApi.portfolioCashAccountsStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        portfolioCashAccountId = response.getData().getId();
        assertTrue(portfolioCashAccountId > 0, "Portfolio Cash Account link ID should be positive");
        registerCleanup(token, "/api/v1/portfolio-cash-accounts/" + portfolioCashAccountId);
    }

    @Test
    @Order(SETUP + 11)
    void createExternalPosition() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        var req = new StoreExternalPositionRequest()
                .clientId(clientId)
                .portfolioId(portfolioId)
                .instrumentIsin("US0378331005")
                .instrumentName("Apple Inc.")
                .quantity(new BigDecimal("100"))
                .currencyId(47)
                .date(LocalDate.now());

        var response = externalPositionApi.externalPositionsStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        externalPositionId = response.getData().getId();
        assertTrue(externalPositionId > 0, "External Position ID should be positive");
        registerCleanup(token, "/api/v1/external-positions/" + externalPositionId);
    }

    @Test
    @Order(SETUP + 12)
    void createExternalBalance() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");
        assertTrue(cashAccountId > 0, "Cash Account must be created first");

        var req = new StoreExternalBalanceRequest()
                .clientId(clientId)
                .cashAccountId(cashAccountId)
                .balance(new BigDecimal("50000.00"))
                .currencyId(47)
                .date(LocalDate.now());

        var response = externalBalanceApi.externalBalancesStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        externalBalanceId = response.getData().getId();
        assertTrue(externalBalanceId > 0, "External Balance ID should be positive");
        registerCleanup(token, "/api/v1/external-balances/" + externalBalanceId);
    }

    // -- Verify ---------------------------------------------------------------

    @Test
    @Order(VERIFY + 1)
    void readExternalPosition() throws ApiException {
        assertTrue(externalPositionId > 0, "External Position must be created first");

        var response = externalPositionApi.externalPositionsShow(
                String.valueOf(externalPositionId), null);
        assertNotNull(response);
        assertEquals(externalPositionId, response.getData().getId());
    }

    @Test
    @Order(VERIFY + 2)
    void readExternalBalance() throws ApiException {
        assertTrue(externalBalanceId > 0, "External Balance must be created first");

        var response = externalBalanceApi.externalBalancesShow(
                String.valueOf(externalBalanceId), null);
        assertNotNull(response);
        assertEquals(externalBalanceId, response.getData().getId());
    }

    @Test
    @Order(VERIFY + 3)
    void listPersonRelationships() throws Exception {
        assertTrue(personAId > 0, "Person A must be created first");

        // Use raw HTTP for the nested person relationships endpoint
        HttpResponse<String> response = apiGet(token,
                "/api/v1/persons/" + personAId + "/relationships");
        assertEquals(200, response.statusCode());

        var data = objectMapper.readTree(response.body()).path("data");
        assertTrue(data.isArray(), "Response should contain a data array");
        assertTrue(data.size() > 0, "Person A should have at least one relationship");
    }

    @Test
    @Order(VERIFY + 4)
    void updateExternalPosition() throws ApiException {
        assertTrue(externalPositionId > 0, "External Position must be created first");

        var req = new UpdateExternalPositionRequest()
                .instrumentIsin("US0378331005")
                .instrumentName("Apple Inc.")
                .quantity(new BigDecimal("200"))
                .currencyId(47)
                .portfolioId(portfolioId)
                .date(LocalDate.now());

        var response = externalPositionApi.externalPositionsUpdate(
                String.valueOf(externalPositionId), req);
        assertNotNull(response);
    }

    // -- Teardown (reverse order) ---------------------------------------------

    @Test
    @Order(TEARDOWN + 1)
    void deleteExternalBalance() throws ApiException {
        assertTrue(externalBalanceId > 0, "External Balance must be created first");
        externalBalanceApi.externalBalancesDestroy(String.valueOf(externalBalanceId));
        externalBalanceId = 0;
    }

    @Test
    @Order(TEARDOWN + 2)
    void deleteExternalPosition() throws ApiException {
        assertTrue(externalPositionId > 0, "External Position must be created first");
        externalPositionApi.externalPositionsDestroy(String.valueOf(externalPositionId));
        externalPositionId = 0;
    }

    @Test
    @Order(TEARDOWN + 3)
    void deletePortfolioCashAccount() throws ApiException {
        assertTrue(portfolioCashAccountId > 0, "Portfolio Cash Account link must be created first");
        portfolioCashAccountApi.portfolioCashAccountsDestroy(String.valueOf(portfolioCashAccountId));
        portfolioCashAccountId = 0;
    }

    @Test
    @Order(TEARDOWN + 4)
    void deleteCashAccount() throws ApiException {
        assertTrue(cashAccountId > 0, "Cash Account must be created first");
        cashAccountApi.cashAccountsDestroy(String.valueOf(cashAccountId));
        cashAccountId = 0;
    }

    @Test
    @Order(TEARDOWN + 5)
    void deletePortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        portfolioApi.portfoliosDestroy(String.valueOf(portfolioId));
        portfolioId = 0;
    }

    @Test
    @Order(TEARDOWN + 6)
    void deleteRelationship() throws ApiException {
        assertTrue(relationshipId > 0, "Relationship must be created first");
        relationshipApi.personRelationshipsDestroy(String.valueOf(relationshipId));
        relationshipId = 0;
    }

    @Test
    @Order(TEARDOWN + 7)
    void deletePersonB() throws ApiException {
        assertTrue(personBId > 0, "Person B must be created first");
        personApi.personsDestroy(String.valueOf(personBId));
        personBId = 0;
    }

    @Test
    @Order(TEARDOWN + 8)
    void deletePersonA() throws ApiException {
        assertTrue(personAId > 0, "Person A must be created first");
        personApi.personsDestroy(String.valueOf(personAId));
        personAId = 0;
    }

    @Test
    @Order(TEARDOWN + 9)
    void deleteClient() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");
        clientApi.clientsDestroy(String.valueOf(clientId));
        clientId = 0;
    }

    @Test
    @Order(TEARDOWN + 10)
    void deleteRelationshipType() throws ApiException {
        assertTrue(relationshipTypeId > 0, "Relationship type must be created first");
        relationshipTypeApi.personRelationshipTypesDestroy(String.valueOf(relationshipTypeId));
        relationshipTypeId = 0;
    }

    @AfterAll
    static void teardown() {
        runCleanup();
    }
}
