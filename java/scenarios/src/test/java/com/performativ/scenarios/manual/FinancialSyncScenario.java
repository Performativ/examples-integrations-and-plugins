package com.performativ.scenarios.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.scenarios.BaseScenario;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S10: Financial-situation sync for an Advice Context — the upstream → Performativ
 * direction a custodian/banking plugin runs on a {@code sync.AdviceContext}
 * intention (hint {@code data_refresh}).
 *
 * <p>Models a couple: two clients, a JOINT portfolio and JOINT cash account held by
 * both, plus a solo portfolio held by one. It then demonstrates the two equivalent
 * ways to discover what Performativ already holds before writing:
 *
 * <ul>
 *   <li><b>Batched (recommended):</b> one
 *       {@code GET /v1/portfolios?filter[client_id]=A,B} and one
 *       {@code GET /v1/cash-accounts?filter[client_id]=A,B} for the whole context.
 *       A joint account is returned ONCE and carries {@code client_ids} / {@code is_shared}.</li>
 *   <li><b>Per-member:</b> one call per member via {@code /clients/{id}/portfolios}.
 *       A joint account is returned once PER HOLDER, so the caller must dedup by id.</li>
 * </ul>
 *
 * <p>Finally it writes positions per portfolio via
 * {@code POST /v1/external-positions/batch/replace} and all balances in one
 * {@code POST /v1/external-balances/batch/upsert}.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FinancialSyncScenario extends BaseScenario {

    private static String token;
    private static int advicePolicyId;
    private static int personAId;
    private static int personBId;
    private static int clientAId;
    private static int clientBId;
    private static int adviceContextId;
    private static int jointPortfolioId;
    private static int soloPortfolioId;
    private static int jointCashAccountId;

    /** Client ids resolved from the advice context's members. */
    private static final List<Integer> memberClientIds = new ArrayList<>();
    /** Deduped portfolio / cash-account ids discovered for the context. */
    private static final Set<Integer> discoveredPortfolioIds = new LinkedHashSet<>();
    private static final Set<Integer> discoveredCashAccountIds = new LinkedHashSet<>();

    private static final String TODAY = LocalDate.now().toString();
    /** filter[client_id] — brackets must be percent-encoded for java.net.URI. */
    private static final String CLIENT_ID_FILTER = "filter%5Bclient_id%5D=";

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();
    }

    @AfterAll
    static void teardown() {
        runCleanup();
    }

    // -- Setup: a couple with a joint portfolio, joint cash account, solo portfolio --

    @Test
    @Order(SETUP + 1)
    void listAdvicePolicies() throws Exception {
        HttpResponse<String> response = apiGet(token, "/api/v1/advice-policies");
        assertEquals(200, response.statusCode(),
                "List advice policies should return 200, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertTrue(data.size() > 0, "At least one advice policy must be configured on the tenant");
        advicePolicyId = data.get(0).get("id").asInt();
    }

    @Test
    @Order(SETUP + 2)
    void createPersonA() throws Exception {
        JsonNode person = createEntity(token, "/api/v1/persons",
                """
                {"first_name":"Manual","last_name":"S10-John","email":"manual-s10-john@example.com","language_code":"en"}
                """);
        personAId = person.get("id").asInt();
        registerCleanup(token, "/api/v1/persons/" + personAId);
    }

    @Test
    @Order(SETUP + 3)
    void createPersonB() throws Exception {
        JsonNode person = createEntity(token, "/api/v1/persons",
                """
                {"first_name":"Manual","last_name":"S10-Jane","email":"manual-s10-jane@example.com","language_code":"en"}
                """);
        personBId = person.get("id").asInt();
        registerCleanup(token, "/api/v1/persons/" + personBId);
    }

    @Test
    @Order(SETUP + 4)
    void createClientA() throws Exception {
        JsonNode client = createEntity(token, "/api/v1/clients",
                """
                {"name":"Manual-S10 Client A","type":"individual","is_active":true,"currency_id":47}
                """);
        clientAId = client.get("id").asInt();
        registerCleanup(token, "/api/v1/clients/" + clientAId);
    }

    @Test
    @Order(SETUP + 5)
    void createClientB() throws Exception {
        JsonNode client = createEntity(token, "/api/v1/clients",
                """
                {"name":"Manual-S10 Client B","type":"individual","is_active":true,"currency_id":47}
                """);
        clientBId = client.get("id").asInt();
        registerCleanup(token, "/api/v1/clients/" + clientBId);
    }

    @Test
    @Order(SETUP + 6)
    void linkPersons() throws Exception {
        HttpResponse<String> a = apiPost(token, "/api/v1/client-persons",
                String.format("{\"client_id\":%d,\"person_id\":%d,\"is_primary\":true}", clientAId, personAId));
        assertTrue(a.statusCode() < 300, "Link Person A should succeed, got: " + a.statusCode() + " " + a.body());

        HttpResponse<String> b = apiPost(token, "/api/v1/client-persons",
                String.format("{\"client_id\":%d,\"person_id\":%d,\"is_primary\":true}", clientBId, personBId));
        assertTrue(b.statusCode() < 300, "Link Person B should succeed, got: " + b.statusCode() + " " + b.body());
    }

    @Test
    @Order(SETUP + 7)
    void createAdviceContext() throws Exception {
        // Advice contexts are client-centric: members carry client_id only.
        HttpResponse<String> response = apiPost(token, "/api/v1/advice-contexts",
                String.format("""
                {"advice_policy_id":%d,"type":"couple","name":"Manual-S10 Advice Context","members":[{"client_id":%d},{"client_id":%d}]}
                """, advicePolicyId, clientAId, clientBId));

        assertTrue(response.statusCode() < 300,
                "Create advice context should succeed, got: " + response.statusCode() + " " + response.body());
        adviceContextId = objectMapper.readTree(response.body()).path("data").get("id").asInt();
        registerCleanup(token, "/api/v1/advice-contexts/" + adviceContextId);
    }

    @Test
    @Order(SETUP + 8)
    void createJointPortfolio() throws Exception {
        // Held by BOTH members — a joint portfolio.
        JsonNode portfolio = createEntity(token, "/api/v1/portfolios",
                String.format("{\"name\":\"Manual-S10 Joint Portfolio\",\"client_ids\":[%d,%d],\"currency_id\":47}",
                        clientAId, clientBId));
        jointPortfolioId = portfolio.get("id").asInt();
        registerCleanup(token, "/api/v1/portfolios/" + jointPortfolioId);
    }

    @Test
    @Order(SETUP + 9)
    void createSoloPortfolio() throws Exception {
        JsonNode portfolio = createEntity(token, "/api/v1/portfolios",
                String.format("{\"name\":\"Manual-S10 Solo Portfolio\",\"client_ids\":[%d],\"currency_id\":47}", clientAId));
        soloPortfolioId = portfolio.get("id").asInt();
        registerCleanup(token, "/api/v1/portfolios/" + soloPortfolioId);
    }

    @Test
    @Order(SETUP + 10)
    void createJointCashAccount() throws Exception {
        JsonNode cashAccount = createEntity(token, "/api/v1/cash-accounts",
                String.format("{\"name\":\"Manual-S10 Joint Cash Account\",\"client_ids\":[%d,%d],\"currency_id\":47}",
                        clientAId, clientBId));
        jointCashAccountId = cashAccount.get("id").asInt();
        registerCleanup(token, "/api/v1/cash-accounts/" + jointCashAccountId);
    }

    // -- Step 1: resolve the members of the advice context --------------------

    @Test
    @Order(VERIFY + 1)
    void resolveContextMembers() throws Exception {
        HttpResponse<String> response = apiGet(token, "/api/v1/advice-contexts/" + adviceContextId + "?include=members");
        assertEquals(200, response.statusCode(),
                "Read advice context members, got: " + response.statusCode() + " " + response.body());

        JsonNode members = objectMapper.readTree(response.body()).path("data").path("members");
        assertTrue(members.isArray() && members.size() == 2, "Couple context should have two members");
        for (JsonNode member : members) {
            memberClientIds.add(member.get("client_id").asInt());
        }
        assertTrue(memberClientIds.contains(clientAId) && memberClientIds.contains(clientBId),
                "Members should be the two clients we attached");
    }

    // -- Step 2a: BATCHED discovery — one call per resource, joint deduped -----

    @Test
    @Order(VERIFY + 2)
    void batchedDiscoveryDedupesAndFlagsShared() throws Exception {
        String clientIdList = memberClientIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElseThrow();

        // One call for every member's portfolios.
        HttpResponse<String> portfolios = apiGet(token,
                "/api/v1/portfolios?" + CLIENT_ID_FILTER + clientIdList + "&include=clients");
        assertEquals(200, portfolios.statusCode(),
                "Batched portfolio discovery, got: " + portfolios.statusCode() + " " + portfolios.body());

        JsonNode portfolioData = objectMapper.readTree(portfolios.body()).path("data");
        long jointAppearances = 0;
        for (JsonNode portfolio : portfolioData) {
            int id = portfolio.get("id").asInt();
            discoveredPortfolioIds.add(id);
            if (id == jointPortfolioId) {
                jointAppearances++;
                assertTrue(portfolio.get("is_shared").asBoolean(), "Joint portfolio must report is_shared=true");
                List<Integer> holders = new ArrayList<>();
                portfolio.path("client_ids").forEach(n -> holders.add(n.asInt()));
                assertTrue(holders.contains(clientAId) && holders.contains(clientBId),
                        "Joint portfolio client_ids must list both holders");
            }
            if (id == soloPortfolioId) {
                assertFalse(portfolio.get("is_shared").asBoolean(), "Solo portfolio must report is_shared=false");
            }
        }
        assertEquals(1, jointAppearances, "Joint portfolio must appear exactly ONCE in the batched result");
        assertTrue(discoveredPortfolioIds.contains(jointPortfolioId) && discoveredPortfolioIds.contains(soloPortfolioId),
                "Batched discovery should surface both portfolios");

        // One call for every member's cash accounts.
        HttpResponse<String> cashAccounts = apiGet(token,
                "/api/v1/cash-accounts?" + CLIENT_ID_FILTER + clientIdList + "&include=clients");
        assertEquals(200, cashAccounts.statusCode(),
                "Batched cash-account discovery, got: " + cashAccounts.statusCode() + " " + cashAccounts.body());

        JsonNode cashData = objectMapper.readTree(cashAccounts.body()).path("data");
        long jointCashAppearances = 0;
        for (JsonNode cashAccount : cashData) {
            int id = cashAccount.get("id").asInt();
            discoveredCashAccountIds.add(id);
            if (id == jointCashAccountId) {
                jointCashAppearances++;
                assertTrue(cashAccount.get("is_shared").asBoolean(), "Joint cash account must report is_shared=true");
            }
        }
        assertEquals(1, jointCashAppearances, "Joint cash account must appear exactly ONCE in the batched result");
    }

    // -- Step 2b: PER-MEMBER discovery — joint returned per holder, dedup needed --

    @Test
    @Order(VERIFY + 3)
    void perMemberDiscoveryReturnsJointPerHolder() throws Exception {
        int jointSeen = 0;
        for (int clientId : memberClientIds) {
            HttpResponse<String> response = apiGet(token, "/api/v1/clients/" + clientId + "/portfolios");
            assertEquals(200, response.statusCode(),
                    "Per-member portfolio discovery, got: " + response.statusCode() + " " + response.body());
            for (JsonNode portfolio : objectMapper.readTree(response.body()).path("data")) {
                if (portfolio.get("id").asInt() == jointPortfolioId) {
                    jointSeen++;
                }
            }
        }
        // The joint portfolio surfaces once for EACH holder — the caller must dedup by id.
        assertEquals(2, jointSeen,
                "Per-member discovery returns the joint portfolio once per holder (dedup is the caller's job)");
    }

    // -- Step 3: write positions per portfolio, balances in one call ----------

    @Test
    @Order(VERIFY + 4)
    void writePositionsPerPortfolio() throws Exception {
        assertFalse(discoveredPortfolioIds.isEmpty(), "Discovery must have run first");
        for (int portfolioId : discoveredPortfolioIds) {
            String body = String.format("""
                    {"portfolio_id":%d,"date":"%s","positions":[{"instrument_isin":"US0378331005","instrument_name":"Apple Inc.","quantity":100,"value":15000,"currency_id":47}]}
                    """, portfolioId, TODAY);
            HttpResponse<String> response = apiPost(token, "/api/v1/external-positions/batch/replace", body);
            assertTrue(response.statusCode() < 300,
                    "Replace positions for portfolio " + portfolioId + ", got: " + response.statusCode() + " " + response.body());
        }
    }

    @Test
    @Order(VERIFY + 5)
    void writeBalancesForAllCashAccounts() throws Exception {
        assertFalse(discoveredCashAccountIds.isEmpty(), "Discovery must have run first");
        StringBuilder rows = new StringBuilder();
        for (int cashAccountId : discoveredCashAccountIds) {
            if (rows.length() > 0) {
                rows.append(",");
            }
            rows.append(String.format("{\"cash_account_id\":%d,\"date\":\"%s\",\"balance\":50000.00,\"currency_id\":47}",
                    cashAccountId, TODAY));
        }
        HttpResponse<String> response = apiPost(token, "/api/v1/external-balances/batch/upsert",
                "{\"balances\":[" + rows + "]}");
        assertTrue(response.statusCode() < 300,
                "Upsert balances, got: " + response.statusCode() + " " + response.body());
    }

    // -- Verify the written data is readable -----------------------------------

    @Test
    @Order(VERIFY + 6)
    void verifyPositionsReadable() throws Exception {
        HttpResponse<String> response = apiGet(token,
                "/api/v1/external-positions?" + "filter%5Bportfolio_id%5D=" + jointPortfolioId);
        assertEquals(200, response.statusCode(),
                "Read external positions, got: " + response.statusCode() + " " + response.body());
        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertTrue(data.size() > 0, "Positions written for the joint portfolio should be readable");
    }

    // -- Teardown: clear batch-written slices so parent deletes are unblocked --

    @Test
    @Order(TEARDOWN + 1)
    void clearBatchWrittenData() throws Exception {
        // Replace each portfolio's slice with an empty set to remove the positions.
        for (int portfolioId : discoveredPortfolioIds) {
            apiPost(token, "/api/v1/external-positions/batch/replace",
                    String.format("{\"portfolio_id\":%d,\"date\":\"%s\",\"positions\":[]}", portfolioId, TODAY));
        }
        // Delete external balances by id (upsert has no "replace with empty" semantics).
        for (int cashAccountId : discoveredCashAccountIds) {
            HttpResponse<String> response = apiGet(token,
                    "/api/v1/external-balances?" + "filter%5Bcash_account_id%5D=" + cashAccountId);
            if (response.statusCode() == 200) {
                for (JsonNode balance : objectMapper.readTree(response.body()).path("data")) {
                    apiDelete(token, "/api/v1/external-balances/" + balance.get("id").asInt());
                }
            }
        }
    }
}
