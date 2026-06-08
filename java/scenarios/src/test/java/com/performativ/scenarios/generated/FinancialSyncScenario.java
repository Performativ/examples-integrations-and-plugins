package com.performativ.scenarios.generated;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.client.api.*;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import com.performativ.client.model.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S10: Financial-situation sync for an Advice Context — using the generated
 * OpenAPI client.
 *
 * <p>Models a couple holding a JOINT portfolio and JOINT cash account plus a solo
 * portfolio, then demonstrates the two discovery styles via the typed client:
 *
 * <ul>
 *   <li><b>Batched:</b> {@code portfoliosIndex} / {@code cashAccountsIndex} with a
 *       comma-separated {@code filter[client_id]}. A joint account is returned ONCE
 *       and the typed model exposes {@code getIsShared()} / {@code getClientIds()}.</li>
 *   <li><b>Per-member:</b> {@code portfolioIndexByClient} per member — a joint
 *       portfolio is returned once per holder, deduped here by id.</li>
 * </ul>
 *
 * <p>Then writes positions per portfolio ({@code v1ExternalPositionsBatchReplace})
 * and balances in one {@code v1ExternalBalancesBatchUpsert}.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FinancialSyncScenario extends GeneratedClientScenario {

    private static String token;
    private static PersonsApi personsApi;
    private static ClientsApi clientsApi;
    private static AdviceApi adviceApi;
    private static PortfoliosApi portfoliosApi;
    private static TransactionsApi transactionsApi;

    private static long advicePolicyId;
    private static long personAId;
    private static long personBId;
    private static long clientAId;
    private static long clientBId;
    private static long adviceContextId;
    private static long jointPortfolioId;
    private static long soloPortfolioId;
    private static long jointCashAccountId;

    private static final List<Long> memberClientIds = new ArrayList<>();
    private static final Set<Long> discoveredPortfolioIds = new LinkedHashSet<>();
    private static final Set<Long> discoveredCashAccountIds = new LinkedHashSet<>();

    private static final LocalDate TODAY = LocalDate.now();

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();

        ApiClient apiClient = createApiClient(token);
        personsApi = new PersonsApi(apiClient);
        clientsApi = new ClientsApi(apiClient);
        adviceApi = new AdviceApi(apiClient);
        portfoliosApi = new PortfoliosApi(apiClient);
        transactionsApi = new TransactionsApi(apiClient);
    }

    @AfterAll
    static void teardown() {
        runCleanup();
    }

    // -- Setup: a couple with a joint portfolio, joint cash account, solo portfolio --

    @Test
    @Order(SETUP + 1)
    void findAdvicePolicy() throws ApiException {
        var response = adviceApi.advicePoliciesIndex(null, null, null, null);
        assertNotNull(response.getData());
        assertFalse(response.getData().isEmpty(), "At least one advice policy must be configured");
        advicePolicyId = response.getData().get(0).getId();
    }

    @Test
    @Order(SETUP + 2)
    void createPersonA() throws ApiException {
        var req = new StorePersonRequest().firstName("Gen").lastName("S10-John")
                .email("gen-s10-john@example.com").languageCode("en");
        personAId = personsApi.personsStore(req, idempotencyKey()).getData().getId();
        registerCleanup(token, "/api/v1/persons/" + personAId);
    }

    @Test
    @Order(SETUP + 3)
    void createPersonB() throws ApiException {
        var req = new StorePersonRequest().firstName("Gen").lastName("S10-Jane")
                .email("gen-s10-jane@example.com").languageCode("en");
        personBId = personsApi.personsStore(req, idempotencyKey()).getData().getId();
        registerCleanup(token, "/api/v1/persons/" + personBId);
    }

    @Test
    @Order(SETUP + 4)
    void createClientA() throws ApiException {
        var req = new StoreClientRequest().name("Gen-S10 Client A")
                .type(StoreClientRequest.TypeEnum.INDIVIDUAL).isActive(true).currencyId(47L);
        clientAId = clientsApi.clientsStore(req, idempotencyKey()).getData().getId();
        registerCleanup(token, "/api/v1/clients/" + clientAId);
    }

    @Test
    @Order(SETUP + 5)
    void createClientB() throws ApiException {
        var req = new StoreClientRequest().name("Gen-S10 Client B")
                .type(StoreClientRequest.TypeEnum.INDIVIDUAL).isActive(true).currencyId(47L);
        clientBId = clientsApi.clientsStore(req, idempotencyKey()).getData().getId();
        registerCleanup(token, "/api/v1/clients/" + clientBId);
    }

    @Test
    @Order(SETUP + 6)
    void linkPersons() throws ApiException {
        clientsApi.clientPersonsStore(
                new StoreClientPersonRequest().clientId(clientAId).personId(personAId).isPrimary(true), idempotencyKey());
        clientsApi.clientPersonsStore(
                new StoreClientPersonRequest().clientId(clientBId).personId(personBId).isPrimary(true), idempotencyKey());
    }

    @Test
    @Order(SETUP + 7)
    void createAdviceContext() throws ApiException {
        // Client-centric contract: members carry client_id only (no person_id / power_of_attorney).
        var req = new StoreAdviceContextRequest()
                .advicePolicyId(advicePolicyId)
                .type(StoreAdviceContextRequest.TypeEnum.COUPLE)
                .name("Gen-S10 Advice Context")
                .referenceClientId(clientAId)
                .members(List.of(
                        new StoreAdviceContextRequestMembersInner().clientId((int) clientAId),
                        new StoreAdviceContextRequestMembersInner().clientId((int) clientBId)));
        adviceContextId = adviceApi.adviceContextsStore(req, idempotencyKey()).getData().getId();
        registerCleanup(token, "/api/v1/advice-contexts/" + adviceContextId);
    }

    @Test
    @Order(SETUP + 8)
    void createJointPortfolio() throws ApiException {
        var req = new StorePortfolioRequest().name("Gen-S10 Joint Portfolio")
                .clientIds(List.of(clientAId, clientBId)).currencyId(47L);
        jointPortfolioId = portfoliosApi.portfoliosStore(req, idempotencyKey()).getData().getId();
        registerCleanup(token, "/api/v1/portfolios/" + jointPortfolioId);
    }

    @Test
    @Order(SETUP + 9)
    void createSoloPortfolio() throws ApiException {
        var req = new StorePortfolioRequest().name("Gen-S10 Solo Portfolio")
                .clientIds(List.of(clientAId)).currencyId(47L);
        soloPortfolioId = portfoliosApi.portfoliosStore(req, idempotencyKey()).getData().getId();
        registerCleanup(token, "/api/v1/portfolios/" + soloPortfolioId);
    }

    @Test
    @Order(SETUP + 10)
    void createJointCashAccount() throws ApiException {
        var req = new StoreCashAccountRequest().name("Gen-S10 Joint Cash Account")
                .clientIds(List.of(clientAId, clientBId)).currencyId(47L);
        jointCashAccountId = portfoliosApi.cashAccountsStore(req, idempotencyKey()).getData().getId();
        registerCleanup(token, "/api/v1/cash-accounts/" + jointCashAccountId);
    }

    // -- Step 1: resolve the members of the advice context --------------------

    @Test
    @Order(VERIFY + 1)
    void resolveContextMembers() throws ApiException {
        var response = adviceApi.adviceContextsClientsIndex(String.valueOf(adviceContextId), null, null);
        assertNotNull(response.getData());
        for (AdviceContextMemberResource member : response.getData()) {
            memberClientIds.add(member.getClientId());
        }
        assertTrue(memberClientIds.contains(clientAId) && memberClientIds.contains(clientBId),
                "Members should be the two clients we attached");
    }

    // -- Step 2a: BATCHED discovery — typed models expose is_shared / client_ids --

    @Test
    @Order(VERIFY + 2)
    void batchedDiscoveryDedupesAndFlagsShared() throws ApiException {
        String clientIdList = memberClientIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElseThrow();

        var portfolios = portfoliosApi.portfoliosIndex(
                null, clientIdList, null, null, null, null, null, null, "clients", null, null);
        long jointAppearances = 0;
        for (PortfolioResource portfolio : portfolios.getData()) {
            discoveredPortfolioIds.add(portfolio.getId());
            if (portfolio.getId() == jointPortfolioId) {
                jointAppearances++;
                assertTrue(portfolio.getIsShared(), "Joint portfolio must report is_shared=true");
                assertTrue(portfolio.getClientIds().contains(clientAId) && portfolio.getClientIds().contains(clientBId),
                        "Joint portfolio client_ids must list both holders");
            }
            if (portfolio.getId() == soloPortfolioId) {
                assertFalse(portfolio.getIsShared(), "Solo portfolio must report is_shared=false");
            }
        }
        assertEquals(1, jointAppearances, "Joint portfolio must appear exactly ONCE in the batched result");

        var cashAccounts = portfoliosApi.cashAccountsIndex(
                null, clientIdList, null, null, null, null, null, null, null, null, "clients", null, null);
        long jointCashAppearances = 0;
        for (CashAccountResource cashAccount : cashAccounts.getData()) {
            discoveredCashAccountIds.add(cashAccount.getId());
            if (cashAccount.getId() == jointCashAccountId) {
                jointCashAppearances++;
                assertTrue(cashAccount.getIsShared(), "Joint cash account must report is_shared=true");
            }
        }
        assertEquals(1, jointCashAppearances, "Joint cash account must appear exactly ONCE in the batched result");
    }

    // -- Step 2b: PER-MEMBER discovery — joint returned per holder, dedup needed --

    @Test
    @Order(VERIFY + 3)
    void perMemberDiscoveryReturnsJointPerHolder() throws ApiException {
        int jointSeen = 0;
        for (long clientId : memberClientIds) {
            var response = portfoliosApi.portfolioIndexByClient(
                    String.valueOf(clientId), null, null, null, null, null, null, null, null);
            for (PortfolioResource portfolio : response.getData()) {
                if (portfolio.getId() == jointPortfolioId) {
                    jointSeen++;
                }
            }
        }
        assertEquals(2, jointSeen,
                "Per-member discovery returns the joint portfolio once per holder (dedup is the caller's job)");
    }

    // -- Step 3: write positions per portfolio, balances in one call ----------

    @Test
    @Order(VERIFY + 4)
    void writePositionsPerPortfolio() throws ApiException {
        assertFalse(discoveredPortfolioIds.isEmpty(), "Discovery must have run first");
        for (long portfolioId : discoveredPortfolioIds) {
            var req = new BatchReplaceExternalPositionsRequest()
                    .portfolioId(portfolioId)
                    .date(TODAY)
                    .positions(List.of(new BatchReplaceExternalPositionsRequestPositionsInner()
                            .instrumentIsin("US0378331005").instrumentName("Apple Inc.")
                            .quantity(BigDecimal.valueOf(100)).value(BigDecimal.valueOf(15000)).currencyId(47)));
            var response = transactionsApi.v1ExternalPositionsBatchReplace(req);
            assertNotNull(response.getData());
            assertTrue(response.getData().getInserted() >= 1, "One position should be inserted");
        }
    }

    @Test
    @Order(VERIFY + 5)
    void writeBalancesForAllCashAccounts() throws ApiException {
        assertFalse(discoveredCashAccountIds.isEmpty(), "Discovery must have run first");
        var balances = new ArrayList<BatchUpsertExternalBalancesRequestBalancesInner>();
        for (long cashAccountId : discoveredCashAccountIds) {
            balances.add(new BatchUpsertExternalBalancesRequestBalancesInner()
                    .cashAccountId((int) cashAccountId).date(TODAY).balance(BigDecimal.valueOf(50000)).currencyId(47));
        }
        var response = transactionsApi.v1ExternalBalancesBatchUpsert(
                new BatchUpsertExternalBalancesRequest().balances(balances));
        assertNotNull(response.getData());
        assertTrue(response.getData().getUpserted() >= 1, "Balances should be upserted");
    }

    // -- Teardown: clear batch-written slices so parent deletes are unblocked --

    @Test
    @Order(TEARDOWN + 1)
    void clearBatchWrittenData() throws Exception {
        for (long portfolioId : discoveredPortfolioIds) {
            try {
                transactionsApi.v1ExternalPositionsBatchReplace(new BatchReplaceExternalPositionsRequest()
                        .portfolioId(portfolioId).date(TODAY).positions(List.of()));
            } catch (ApiException ignored) {
                // best-effort cleanup
            }
        }
        for (long cashAccountId : discoveredCashAccountIds) {
            HttpResponse<String> response = apiGet(token,
                    "/api/v1/external-balances?filter%5Bcash_account_id%5D=" + cashAccountId);
            if (response.statusCode() == 200) {
                for (JsonNode balance : objectMapper.readTree(response.body()).path("data")) {
                    apiDelete(token, "/api/v1/external-balances/" + balance.get("id").asInt());
                }
            }
        }
    }
}
