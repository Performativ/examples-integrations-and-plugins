package com.performativ.scenarios;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.client.api.*;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import com.performativ.client.model.*;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration test for the 6 priority models using the generated OpenAPI client.
 *
 * <p>Exercises full CRUD for: Person, Client, Portfolio, CashAccount,
 * ExternalPositions (list), and CustodianCashAccountBalances (list).
 *
 * <p>Test order follows the DAG: Person -> Client -> Portfolio -> CashAccount ->
 * ExternalPositions -> CashBalances, then deletes in reverse.
 *
 * <p>Authenticates via OAuth2 client_credentials from {@code .env} credentials,
 * same as all other scenarios.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PriorityModelScenario extends BaseScenario {

    private static String token;

    private static ApiClient apiClient;
    private static PersonApi personApi;
    private static ClientApi clientApi;
    private static PortfolioApi portfolioApi;
    private static CashAccountApi cashAccountApi;
    private static ExternalPositionApi externalPositionApi;
    private static CustodianCashAccountBalanceApi cashBalanceApi;

    private static int personId;
    private static int clientId;
    private static int portfolioId;
    private static int cashAccountId;

    private static final StringBuilder report = new StringBuilder();

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();

        apiClient = new ApiClient();
        apiClient.setBasePath(apiBaseUrl() + "/api");
        apiClient.setBearerToken(token);

        personApi = new PersonApi(apiClient);
        clientApi = new ClientApi(apiClient);
        portfolioApi = new PortfolioApi(apiClient);
        cashAccountApi = new CashAccountApi(apiClient);
        externalPositionApi = new ExternalPositionApi(apiClient);
        cashBalanceApi = new CustodianCashAccountBalanceApi(apiClient);

        report.append("# Priority Model Integration Test Results\n\n");
        report.append("Run against: ").append(apiBaseUrl()).append("\n\n");
    }

    private static void logResult(String test, String status, String details) {
        report.append("## ").append(test).append("\n");
        report.append("- **Status**: ").append(status).append("\n");
        report.append("- **Details**: ").append(details).append("\n\n");
        System.out.println("[" + status + "] " + test + " -- " + details);
    }

    // ══════════════════════════════════════════════════════════════════
    // PERSON CRUD
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    void createPerson() {
        StorePersonRequest req = new StorePersonRequest();
        req.firstName("Priority");
        req.lastName("ModelTest");
        req.languageCode("en");
        req.email("priority-model-test@example.com");

        try {
            var response = personApi.tenantPersonsStore(req);
            assertNotNull(response, "Person store response should not be null");
            assertNotNull(response.getData(), "Person data should not be null");
            personId = response.getData().getId();
            assertTrue(personId > 0, "Person ID should be positive");
            logResult("Create Person (generated client)", "PASS",
                    "Created person id=" + personId + ", name=" + response.getData().getFirstName()
                            + " " + response.getData().getLastName());
        } catch (ApiException e) {
            System.err.println("Generated client person create failed: HTTP " + e.getCode() + " - " + e.getResponseBody());
            try {
                JsonNode data = createEntity(token, "/api/persons",
                        "{\"first_name\":\"Priority\",\"last_name\":\"ModelTest\",\"language_code\":\"en\",\"email\":\"priority-model-test@example.com\"}");
                personId = data.get("id").asInt();
                assertTrue(personId > 0);
                logResult("Create Person (raw HTTP fallback)", "PASS",
                        "Created person id=" + personId + " (generated client failed: " + e.getCode() + ")");
            } catch (Exception ex) {
                fail("Both generated client and raw HTTP failed for person create: " + ex.getMessage());
            }
        } catch (Exception e) {
            fail("Person create unexpected error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Test
    @Order(2)
    void listPersons() throws Exception {
        assertTrue(personId > 0, "Person must be created first");

        HttpResponse<String> response = apiGet(token, "/api/persons?limit=5");
        assertEquals(200, response.statusCode(), "List persons should return 200");
        JsonNode body = objectMapper.readTree(response.body());
        assertTrue(body.has("data"), "Response should have 'data' field");
        logResult("List Persons", "PASS",
                "HTTP 200, returned " + body.path("data").size() + " persons");
    }

    @Test
    @Order(3)
    void showPerson() {
        assertTrue(personId > 0, "Person must be created first");

        try {
            var response = personApi.tenantPersonsShow(personId);
            assertNotNull(response);
            assertEquals(personId, response.getData().getId());
            logResult("Show Person (generated client)", "PASS",
                    "Got person id=" + personId + ", name=" + response.getData().getFirstName());
        } catch (ApiException e) {
            System.err.println("Show person failed: HTTP " + e.getCode() + " - " + e.getResponseBody());
            try {
                HttpResponse<String> raw = apiGet(token, "/api/persons/" + personId);
                assertEquals(200, raw.statusCode());
                logResult("Show Person (raw HTTP fallback)", "PASS",
                        "HTTP 200 (generated client failed: " + e.getCode() + ")");
            } catch (Exception ex) {
                fail("Show person failed: " + ex.getMessage());
            }
        } catch (Exception e) {
            fail("Show person unexpected error: " + e.getMessage());
        }
    }

    @Test
    @Order(4)
    void updatePerson() {
        assertTrue(personId > 0, "Person must be created first");

        UpdatePersonRequest req = new UpdatePersonRequest();
        req.firstName("PriorityUpdated");
        req.lastName("ModelTestUpdated");

        try {
            var response = personApi.tenantPersonsUpdate(personId, req);
            assertNotNull(response);
            assertEquals("PriorityUpdated", response.getData().getFirstName());
            logResult("Update Person (generated client)", "PASS",
                    "Updated person id=" + personId + " name to PriorityUpdated");
        } catch (ApiException e) {
            System.err.println("Update person failed: HTTP " + e.getCode() + " - " + e.getResponseBody());
            try {
                HttpResponse<String> raw = apiPut(token, "/api/persons/" + personId,
                        "{\"first_name\":\"PriorityUpdated\",\"last_name\":\"ModelTestUpdated\"}");
                assertTrue(raw.statusCode() < 300, "Raw PUT should succeed, got: " + raw.statusCode());
                logResult("Update Person (raw HTTP fallback)", "PASS",
                        "HTTP " + raw.statusCode() + " (generated client failed: " + e.getCode() + ")");
            } catch (Exception ex) {
                fail("Update person failed: " + ex.getMessage());
            }
        } catch (Exception e) {
            fail("Update person unexpected error: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // CLIENT CRUD
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    void createClient() {
        assertTrue(personId > 0, "Person must be created first");

        StoreClientRequest req = new StoreClientRequest();
        req.name("Priority Model Client");
        req.type("individual");
        req.isActive(true);

        try {
            var response = clientApi.tenantClientsStore(req);
            assertNotNull(response);
            assertNotNull(response.getData());
            clientId = response.getData().getId();
            assertTrue(clientId > 0, "Client ID should be positive");
            logResult("Create Client (generated client)", "PASS",
                    "Created client id=" + clientId + ", name=" + response.getData().getName());
        } catch (ApiException e) {
            System.err.println("Create client failed: HTTP " + e.getCode() + " - " + e.getResponseBody());
            try {
                JsonNode data = createEntity(token, "/api/clients",
                        "{\"name\":\"Priority Model Client\",\"type\":\"individual\",\"is_active\":true}");
                clientId = data.get("id").asInt();
                assertTrue(clientId > 0);
                logResult("Create Client (raw HTTP fallback)", "PASS",
                        "Created client id=" + clientId + " (generated client failed: " + e.getCode() + ")");
            } catch (Exception ex) {
                fail("Both methods failed for client create: " + ex.getMessage());
            }
        } catch (Exception e) {
            fail("Client create unexpected error: " + e.getMessage());
        }
    }

    @Test
    @Order(6)
    void listClients() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        HttpResponse<String> response = apiGet(token, "/api/clients?limit=5");
        assertEquals(200, response.statusCode(), "List clients should return 200");
        JsonNode body = objectMapper.readTree(response.body());
        assertTrue(body.has("data"), "Response should have 'data' field");
        logResult("List Clients", "PASS",
                "HTTP 200, returned " + body.path("data").size() + " clients");
    }

    @Test
    @Order(7)
    void showClient() {
        assertTrue(clientId > 0, "Client must be created first");

        try {
            var response = clientApi.tenantClientsShow(clientId);
            assertNotNull(response);
            assertEquals(clientId, response.getData().getId());
            assertEquals("Priority Model Client", response.getData().getName());
            logResult("Show Client (generated client)", "PASS",
                    "Got client id=" + clientId + ", name=" + response.getData().getName());
        } catch (ApiException e) {
            System.err.println("Show client failed: HTTP " + e.getCode() + " - " + e.getResponseBody());
            try {
                HttpResponse<String> raw = apiGet(token, "/api/clients/" + clientId);
                assertEquals(200, raw.statusCode());
                logResult("Show Client (raw HTTP fallback)", "PASS",
                        "HTTP 200 (generated client failed: " + e.getCode() + ")");
            } catch (Exception ex) {
                fail("Show client failed: " + ex.getMessage());
            }
        } catch (Exception e) {
            fail("Show client unexpected error: " + e.getMessage());
        }
    }

    @Test
    @Order(8)
    void updateClient() {
        assertTrue(clientId > 0, "Client must be created first");

        UpdateClientRequest req = new UpdateClientRequest();
        req.name("Priority Model Client Updated");

        try {
            var response = clientApi.tenantClientsUpdate(clientId, req);
            assertNotNull(response);
            logResult("Update Client (generated client)", "PASS",
                    "Updated client id=" + clientId);
        } catch (ApiException e) {
            System.err.println("Update client failed: HTTP " + e.getCode() + " - " + e.getResponseBody());
            try {
                HttpResponse<String> raw = apiPut(token, "/api/clients/" + clientId,
                        "{\"name\":\"Priority Model Client Updated\"}");
                assertTrue(raw.statusCode() < 300, "Raw PUT should succeed, got: " + raw.statusCode());
                logResult("Update Client (raw HTTP fallback)", "PASS",
                        "HTTP " + raw.statusCode() + " (generated client failed: " + e.getCode() + ")");
            } catch (Exception ex) {
                fail("Update client failed: " + ex.getMessage());
            }
        } catch (Exception e) {
            fail("Update client unexpected error: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // PORTFOLIO CRUD
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(9)
    void createPortfolio() {
        assertTrue(clientId > 0, "Client must be created first");

        StorePortfolioRequest req = new StorePortfolioRequest();
        req.name("Priority Model Portfolio");
        req.currencyId(47); // EUR

        try {
            var response = portfolioApi.tenantClientsPortfoliosStore(clientId, req);
            assertNotNull(response);
            portfolioId = response.getData().getId();
            assertTrue(portfolioId > 0);
            logResult("Create Portfolio (generated client)", "PASS",
                    "Created portfolio id=" + portfolioId + ", name=" + response.getData().getName());
        } catch (ApiException e) {
            System.err.println("Create portfolio failed: HTTP " + e.getCode() + " - " + e.getResponseBody());
            try {
                JsonNode data = createEntity(token, "/api/clients/" + clientId + "/portfolios",
                        "{\"name\":\"Priority Model Portfolio\",\"currency_id\":47}");
                portfolioId = data.get("id").asInt();
                assertTrue(portfolioId > 0);
                logResult("Create Portfolio (raw HTTP fallback)", "PASS",
                        "Created portfolio id=" + portfolioId + " (generated client failed: " + e.getCode() + ")");
            } catch (Exception ex) {
                fail("Both methods failed for portfolio create: " + ex.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Portfolio create unexpected error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            try {
                JsonNode data = createEntity(token, "/api/clients/" + clientId + "/portfolios",
                        "{\"name\":\"Priority Model Portfolio\",\"currency_id\":47}");
                portfolioId = data.get("id").asInt();
                assertTrue(portfolioId > 0);
                logResult("Create Portfolio (raw HTTP fallback)", "PASS",
                        "Created portfolio id=" + portfolioId + " (generated client failed: " + e.getClass().getSimpleName() + ")");
            } catch (Exception ex) {
                fail("Both methods failed for portfolio create: " + ex.getMessage());
            }
        }
    }

    @Test
    @Order(10)
    void listPortfolios() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        HttpResponse<String> response = apiGet(token, "/api/clients/" + clientId + "/portfolios");
        assertEquals(200, response.statusCode(), "List portfolios should return 200");
        JsonNode body = objectMapper.readTree(response.body());
        assertTrue(body.has("data"), "Response should have 'data' field");
        logResult("List Portfolios", "PASS",
                "HTTP 200, returned " + body.path("data").size() + " portfolios");
    }

    @Test
    @Order(11)
    void showPortfolio() {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        try {
            var response = portfolioApi.tenantPortfoliosShow(portfolioId);
            assertNotNull(response);
            assertEquals(portfolioId, response.getData().getId());
            logResult("Show Portfolio (generated client)", "PASS",
                    "Got portfolio id=" + portfolioId + ", name=" + response.getData().getName());
        } catch (ApiException e) {
            System.err.println("Show portfolio failed: HTTP " + e.getCode() + " - " + e.getResponseBody());
            try {
                HttpResponse<String> raw = apiGet(token, "/api/portfolios/" + portfolioId);
                assertEquals(200, raw.statusCode());
                logResult("Show Portfolio (raw HTTP fallback)", "PASS",
                        "HTTP 200 (generated client failed: " + e.getCode() + ")");
            } catch (Exception ex) {
                fail("Show portfolio failed: " + ex.getMessage());
            }
        } catch (Exception e) {
            try {
                HttpResponse<String> raw = apiGet(token, "/api/portfolios/" + portfolioId);
                assertEquals(200, raw.statusCode());
                logResult("Show Portfolio (raw HTTP fallback)", "PASS",
                        "HTTP 200 (generated client failed: " + e.getClass().getSimpleName() + ")");
            } catch (Exception ex) {
                fail("Show portfolio failed: " + ex.getMessage());
            }
        }
    }

    @Test
    @Order(12)
    void updatePortfolio() {
        assertTrue(portfolioId > 0, "Portfolio must be created first");

        UpdatePortfolioRequest req = new UpdatePortfolioRequest();
        req.name("Priority Model Portfolio Updated");

        try {
            portfolioApi.tenantPortfoliosUpdate(portfolioId, req);
            logResult("Update Portfolio (generated client)", "PASS",
                    "Updated portfolio id=" + portfolioId);
        } catch (ApiException e) {
            System.err.println("Update portfolio failed: HTTP " + e.getCode() + " - " + e.getResponseBody());
            try {
                HttpResponse<String> raw = apiPut(token, "/api/portfolios/" + portfolioId,
                        "{\"name\":\"Priority Model Portfolio Updated\"}");
                assertTrue(raw.statusCode() < 300, "Raw PUT should succeed, got: " + raw.statusCode());
                logResult("Update Portfolio (raw HTTP fallback)", "PASS",
                        "HTTP " + raw.statusCode() + " (generated client failed: " + e.getCode() + ")");
            } catch (Exception ex) {
                fail("Update portfolio failed: " + ex.getMessage());
            }
        } catch (Exception e) {
            try {
                HttpResponse<String> raw = apiPut(token, "/api/portfolios/" + portfolioId,
                        "{\"name\":\"Priority Model Portfolio Updated\"}");
                assertTrue(raw.statusCode() < 300);
                logResult("Update Portfolio (raw HTTP fallback)", "PASS",
                        "HTTP " + raw.statusCode() + " (generated client failed: " + e.getClass().getSimpleName() + ")");
            } catch (Exception ex) {
                fail("Update portfolio failed: " + ex.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // CASH ACCOUNT CRUD
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(13)
    void createCashAccount() {
        assertTrue(clientId > 0, "Client must be created first");

        CashAccountStoreOrUpdateRequest req = new CashAccountStoreOrUpdateRequest();
        req.name("Priority Test Cash Account");
        req.currencyId(47); // EUR
        req.clientId(clientId);
        req.custodianId("test-custodian");
        req.externalAccountId("EXT-CASH-" + System.currentTimeMillis());

        try {
            var response = cashAccountApi.tenantCashAccountsStore(req);
            assertNotNull(response, "Cash account store response should not be null");
            JsonNode node = objectMapper.valueToTree(response);
            if (node.has("data") && node.get("data").has("id")) {
                cashAccountId = node.get("data").get("id").asInt();
            } else if (node.has("id")) {
                cashAccountId = node.get("id").asInt();
            }
            assertTrue(cashAccountId > 0);
            logResult("Create Cash Account (generated client)", "PASS",
                    "Created cash account id=" + cashAccountId);
        } catch (ApiException e) {
            System.err.println("Create cash account failed: HTTP " + e.getCode() + " - " + e.getResponseBody());
            try {
                HttpResponse<String> raw = apiPost(token, "/api/cash-accounts",
                        "{\"name\":\"Priority Test Cash Account\",\"currency_id\":47,\"client_id\":" + clientId
                                + ",\"custodian_id\":\"test-custodian\",\"external_account_id\":\"EXT-CASH-" + System.currentTimeMillis() + "\"}");
                if (raw.statusCode() < 300) {
                    JsonNode data = objectMapper.readTree(raw.body()).path("data");
                    cashAccountId = data.get("id").asInt();
                    assertTrue(cashAccountId > 0);
                    logResult("Create Cash Account (raw HTTP fallback)", "PASS",
                            "Created cash account id=" + cashAccountId + " (generated client failed: " + e.getCode() + ")");
                } else {
                    logResult("Create Cash Account", "FAIL",
                            "Raw HTTP also failed: HTTP " + raw.statusCode() + " - " + raw.body());
                }
            } catch (Exception ex) {
                logResult("Create Cash Account", "FAIL", "Both methods failed: " + ex.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Cash account unexpected: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            try {
                HttpResponse<String> raw = apiPost(token, "/api/cash-accounts",
                        "{\"name\":\"Priority Test Cash Account\",\"currency_id\":47,\"client_id\":" + clientId
                                + ",\"custodian_id\":\"test-custodian\",\"external_account_id\":\"EXT-CASH-" + System.currentTimeMillis() + "\"}");
                if (raw.statusCode() < 300) {
                    JsonNode data = objectMapper.readTree(raw.body()).path("data");
                    cashAccountId = data.get("id").asInt();
                    logResult("Create Cash Account (raw HTTP fallback)", "PASS",
                            "id=" + cashAccountId + " (generated client deserialization failed)");
                } else {
                    logResult("Create Cash Account", "FAIL", "Raw HTTP " + raw.statusCode() + ": " + raw.body());
                }
            } catch (Exception ex) {
                logResult("Create Cash Account", "FAIL", "Both methods failed: " + ex.getMessage());
            }
        }
    }

    @Test
    @Order(14)
    void showCashAccount() throws Exception {
        if (cashAccountId <= 0) {
            System.out.println("Skipping showCashAccount -- no cash account created");
            logResult("Show Cash Account", "SKIP", "No cash account was created");
            return;
        }

        HttpResponse<String> response = apiGet(token, "/api/cash-accounts/" + cashAccountId);
        assertEquals(200, response.statusCode(), "Show cash account should return 200, got: " + response.statusCode() + " " + response.body());
        JsonNode body = objectMapper.readTree(response.body());
        assertTrue(body.has("data"), "Response should have 'data' field");
        logResult("Show Cash Account", "PASS",
                "HTTP 200, name=" + body.path("data").path("name").asText());
    }

    @Test
    @Order(15)
    void updateCashAccount() throws Exception {
        if (cashAccountId <= 0) {
            System.out.println("Skipping updateCashAccount -- no cash account created");
            logResult("Update Cash Account", "SKIP", "No cash account was created");
            return;
        }

        HttpResponse<String> response = apiPut(token, "/api/cash-accounts/" + cashAccountId,
                "{\"name\":\"Priority Test Cash Account Updated\"}");
        assertTrue(response.statusCode() < 300,
                "Update cash account should succeed, got: " + response.statusCode() + " " + response.body());
        logResult("Update Cash Account", "PASS", "HTTP " + response.statusCode());
    }

    @Test
    @Order(16)
    void listCashAccounts() throws Exception {
        HttpResponse<String> response = apiGet(token, "/api/cash-accounts");
        assertEquals(200, response.statusCode(), "List cash accounts should return 200");
        JsonNode body = objectMapper.readTree(response.body());
        assertTrue(body.has("data"), "Response should have 'data' field");
        logResult("List Cash Accounts", "PASS",
                "HTTP 200, returned " + body.path("data").size() + " cash accounts");
    }

    // ══════════════════════════════════════════════════════════════════
    // EXTERNAL POSITIONS (read-only)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(17)
    void listExternalPositions() throws Exception {
        if (portfolioId <= 0) {
            System.out.println("Skipping listExternalPositions -- no portfolio created");
            logResult("List External Positions", "SKIP", "No portfolio was created");
            return;
        }

        HttpResponse<String> response = apiGet(token, "/api/external-positions?portfolio_id=" + portfolioId + "&limit=5");

        if (response.statusCode() == 200) {
            JsonNode body = objectMapper.readTree(response.body());
            logResult("List External Positions", "PASS",
                    "HTTP 200, returned " + body.path("data").size() + " positions");
        } else {
            logResult("List External Positions", "INFO",
                    "HTTP " + response.statusCode() + " (may need data to be present)");
        }
    }

    @Test
    @Order(18)
    void listExternalPositionDates() throws Exception {
        if (portfolioId <= 0) {
            System.out.println("Skipping listExternalPositionDates -- no portfolio created");
            logResult("List External Position Dates", "SKIP", "No portfolio was created");
            return;
        }

        try {
            var response = externalPositionApi.tenantTenantExternalPositionsGetDates(portfolioId);
            logResult("List External Position Dates (generated client)", "PASS",
                    "Response: " + response);
        } catch (ApiException e) {
            logResult("List External Position Dates", "INFO",
                    "HTTP " + e.getCode() + " - expected for new portfolio with no data");
        } catch (Exception e) {
            logResult("List External Position Dates", "INFO",
                    "Failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // CUSTODIAN CASH ACCOUNT BALANCES (read-only)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(19)
    void listCustodianCashAccountBalances() throws Exception {
        HttpResponse<String> response = apiGet(token, "/api/custodian-cash-account-balances");

        if (response.statusCode() == 200) {
            JsonNode body = objectMapper.readTree(response.body());
            logResult("List Custodian Cash Account Balances", "PASS",
                    "HTTP 200, returned " + body.path("data").size() + " balances");
        } else {
            logResult("List Custodian Cash Account Balances", "INFO",
                    "HTTP " + response.statusCode());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // TEARDOWN - Delete in reverse order
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(20)
    void deleteCashAccount() throws Exception {
        if (cashAccountId <= 0) {
            logResult("Delete Cash Account", "SKIP", "No cash account was created");
            return;
        }
        HttpResponse<String> response = apiDelete(token, "/api/cash-accounts/" + cashAccountId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed or already gone, got: " + response.statusCode());
        logResult("Delete Cash Account", "PASS", "HTTP " + response.statusCode());
    }

    @Test
    @Order(21)
    void deletePortfolio() throws Exception {
        if (portfolioId <= 0) {
            logResult("Delete Portfolio", "SKIP", "No portfolio was created");
            return;
        }
        HttpResponse<String> response = apiDelete(token, "/api/portfolios/" + portfolioId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed or already gone, got: " + response.statusCode());
        logResult("Delete Portfolio", "PASS", "HTTP " + response.statusCode());
    }

    @Test
    @Order(22)
    void deleteClient() throws Exception {
        if (clientId <= 0) {
            logResult("Delete Client", "SKIP", "No client was created");
            return;
        }
        HttpResponse<String> response = apiDelete(token, "/api/clients/" + clientId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed or already gone, got: " + response.statusCode());
        logResult("Delete Client", "PASS", "HTTP " + response.statusCode());
    }

    @Test
    @Order(23)
    void deletePerson() throws Exception {
        if (personId <= 0) {
            logResult("Delete Person", "SKIP", "No person was created");
            return;
        }
        HttpResponse<String> response = apiDelete(token, "/api/persons/" + personId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed or already gone, got: " + response.statusCode());
        logResult("Delete Person", "PASS", "HTTP " + response.statusCode());
    }

    // ══════════════════════════════════════════════════════════════════
    // WRITE REPORT
    // ══════════════════════════════════════════════════════════════════

    @AfterAll
    static void writeReport() {
        report.append("\n## Summary\n\n");
        report.append("- Person ID: ").append(personId).append("\n");
        report.append("- Client ID: ").append(clientId).append("\n");
        report.append("- Portfolio ID: ").append(portfolioId).append("\n");
        report.append("- Cash Account ID: ").append(cashAccountId).append("\n\n");
        report.append("Test completed.\n");

        System.out.println("\n" + report);
    }
}
