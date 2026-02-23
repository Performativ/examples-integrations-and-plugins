package com.performativ.scenarios.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.scenarios.BaseScenario;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S7: Start Advise — full advice session lifecycle.
 *
 * <p>Creates the prerequisite chain (Person → Client → Portfolio),
 * opens an advice context, creates an advisory agreement, starts an
 * advice session, and walks it through the state machine:
 * created → data_ready → active → ready_to_sign → signed.
 *
 * <p>Uses raw HTTP throughout.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StartAdviseScenario extends BaseScenario {

    private static String token;
    private static int advicePolicyId;
    private static int personId;
    private static int clientId;
    private static int portfolioId;
    private static int documentId;
    private static int envelopeId;
    private static int adviceContextId;
    private static int agreementId;
    private static int sessionId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();
    }

    // -- Prerequisites --------------------------------------------------------

    @Test
    @Order(1)
    void listAdvicePolicies() throws Exception {
        HttpResponse<String> response = apiGet(token, "/api/v1/advice-policies");
        assertEquals(200, response.statusCode(),
                "List advice policies should return 200, got: " + response.statusCode() + " " + response.body());

        JsonNode body = objectMapper.readTree(response.body());
        assertTrue(body.has("data"), "Response should have 'data' field");
        assertTrue(body.get("data").size() > 0,
                "At least one advice policy must be configured on the tenant");

        advicePolicyId = body.get("data").get(0).get("id").asInt();
        assertTrue(advicePolicyId > 0, "Advice policy ID should be positive");
    }

    @Test
    @Order(2)
    void createPerson() throws Exception {
        JsonNode person = createEntity(token, "/api/v1/persons",
                """
                {"first_name":"Manual","last_name":"S7-StartAdvise","email":"manual-s7@example.com","language_code":"en"}
                """);

        personId = person.get("id").asInt();
        assertTrue(personId > 0, "Person ID should be positive");
    }

    @Test
    @Order(3)
    void createClient() throws Exception {
        JsonNode client = createEntity(token, "/api/v1/clients",
                """
                {"name":"Manual-S7 Client","type":"individual","is_active":true,"currency_id":47}
                """);

        clientId = client.get("id").asInt();
        assertTrue(clientId > 0, "Client ID should be positive");
    }

    @Test
    @Order(4)
    void linkPersonToClient() throws Exception {
        assertTrue(personId > 0, "Person must be created first");
        assertTrue(clientId > 0, "Client must be created first");

        HttpResponse<String> response = apiPost(token, "/api/v1/client-persons",
                String.format("""
                {"client_id":%d,"person_id":%d,"is_primary":true}
                """, clientId, personId));

        assertTrue(response.statusCode() < 300,
                "Link should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(5)
    void createPortfolio() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        JsonNode portfolio = createEntity(token, "/api/v1/portfolios",
                String.format("""
                {"name":"Manual-S7 Portfolio","client_id":%d,"currency_id":47}
                """, clientId));

        portfolioId = portfolio.get("id").asInt();
        assertTrue(portfolioId > 0, "Portfolio ID should be positive");
    }

    // -- Document upload and signing (required for signed agreement) ----------

    @Test
    @Order(6)
    void uploadDocument() throws Exception {
        Path tempFile = Files.createTempFile("manual-s7-agreement-", ".txt");
        Files.writeString(tempFile, "Hello World - Manual S7 Advisory Agreement");

        try {
            HttpResponse<String> response = apiPostMultipart(token, "/api/v1/documents",
                    tempFile, "file", Map.of("type", "advisory_agreement"));

            assertTrue(response.statusCode() < 300,
                    "Document upload should succeed, got: " + response.statusCode() + " " + response.body());

            JsonNode data = objectMapper.readTree(response.body()).path("data");
            documentId = data.get("id").asInt();
            assertTrue(documentId > 0, "Document ID should be positive");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(7)
    void createSigningEnvelope() throws Exception {
        HttpResponse<String> response = apiPost(token, "/api/v1/signing-envelopes",
                """
                {"title":"Manual-S7 Agreement Envelope"}
                """);

        assertTrue(response.statusCode() < 300,
                "Create envelope should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        envelopeId = data.get("id").asInt();
        assertTrue(envelopeId > 0, "Envelope ID should be positive");
    }

    @Test
    @Order(8)
    void addDocumentToEnvelope() throws Exception {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/signing-envelopes/" + envelopeId + "/documents",
                String.format("""
                {"document_id":%d,"role":"source"}
                """, documentId));

        assertTrue(response.statusCode() < 300,
                "Add document should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(9)
    void addSignerPartyToEnvelope() throws Exception {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(personId > 0, "Person must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/signing-envelopes/" + envelopeId + "/parties",
                String.format("""
                {"person_id":%d,"role":"signer"}
                """, personId));

        assertTrue(response.statusCode() < 300,
                "Add party should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(10)
    void sendEnvelope() throws Exception {
        assertTrue(envelopeId > 0, "Envelope must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/signing-envelopes/" + envelopeId + "/send", "{}");

        assertTrue(response.statusCode() < 300,
                "Send envelope should succeed, got: " + response.statusCode() + " " + response.body());
    }

    // -- Advice Context -------------------------------------------------------

    @Test
    @Order(11)
    void createAdviceContext() throws Exception {
        assertTrue(advicePolicyId > 0, "Advice policy must be found first");
        assertTrue(personId > 0, "Person must be created first");

        HttpResponse<String> response = apiPost(token, "/api/v1/advice-contexts",
                String.format("""
                {"advice_policy_id":%d,"type":"individual","name":"Manual-S7 Advice Context","reference_person_id":%d,"members":[{"person_id":%d,"power_of_attorney":false}]}
                """, advicePolicyId, personId, personId));

        assertTrue(response.statusCode() < 300,
                "Create advice context should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        adviceContextId = data.get("id").asInt();
        assertTrue(adviceContextId > 0, "Advice context ID should be positive");
        assertEquals("active", data.get("status").asText());
    }

    // -- Advisory Agreement (linked to signing envelope) ---------------------

    @Test
    @Order(12)
    void createAdvisoryAgreement() throws Exception {
        assertTrue(adviceContextId > 0, "Advice context must be created first");
        assertTrue(envelopeId > 0, "Signing envelope must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-contexts/" + adviceContextId + "/agreements",
                String.format("""
                {"version":"1.0","signing_envelope_id":%d,"external_reference":"manual-s7-agreement"}
                """, envelopeId));

        assertTrue(response.statusCode() < 300,
                "Create advisory agreement should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        agreementId = data.get("id").asInt();
        assertTrue(agreementId > 0, "Agreement ID should be positive");
        assertEquals("draft", data.get("status").asText());
    }

    @Test
    @Order(13)
    void submitSigning() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advisory-agreements/" + agreementId + "/submit-signing",
                String.format("""
                {"idempotency_key":"manual-s7-submit-%d","document_id":%d}
                """, System.currentTimeMillis(), documentId));

        assertTrue(response.statusCode() < 300,
                "Submit signing should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(14)
    void markAgreementSigned() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advisory-agreements/" + agreementId + "/mark-signed",
                String.format("""
                {"idempotency_key":"manual-s7-signed-%d","signed_document_id":%d}
                """, System.currentTimeMillis(), documentId));

        assertTrue(response.statusCode() < 300,
                "Mark signed should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals("signed", data.get("status").asText(),
                "Agreement should now be in signed status");
    }

    // -- Advice Session lifecycle ---------------------------------------------

    @Test
    @Order(15)
    void createAdviceSession() throws Exception {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-contexts/" + adviceContextId + "/sessions",
                """
                {"external_session_id":"manual-s7-session","external_reference":"manual-s7"}
                """);

        assertTrue(response.statusCode() < 300,
                "Create advice session should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        sessionId = data.get("id").asInt();
        assertTrue(sessionId > 0, "Session ID should be positive");
        assertEquals("created", data.get("status").asText(),
                "New session should be in 'created' status");
    }

    @Test
    @Order(16)
    void readAdviceSession() throws Exception {
        assertTrue(sessionId > 0, "Session must be created first");

        HttpResponse<String> response = apiGet(token, "/api/v1/advice-sessions/" + sessionId);
        assertEquals(200, response.statusCode());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals(sessionId, data.get("id").asInt());
        assertEquals("created", data.get("status").asText());
    }

    @Test
    @Order(17)
    void markDataReady() throws Exception {
        assertTrue(sessionId > 0, "Session must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-sessions/" + sessionId + "/data-ready",
                String.format("""
                {"idempotency_key":"manual-s7-data-ready-%d"}
                """, System.currentTimeMillis()));

        assertTrue(response.statusCode() < 300,
                "Mark data ready should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals("data_ready", data.get("status").asText(),
                "Session should transition to 'data_ready'");
    }

    @Test
    @Order(18)
    void activateSession() throws Exception {
        assertTrue(sessionId > 0, "Session must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-sessions/" + sessionId + "/activate",
                String.format("""
                {"idempotency_key":"manual-s7-activate-%d","redirect_url":"https://example.com/advisor-ui/session"}
                """, System.currentTimeMillis()));

        assertTrue(response.statusCode() < 300,
                "Activate session should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals("active", data.get("status").asText(),
                "Session should transition to 'active'");
    }

    @Test
    @Order(19)
    void markReadyToSign() throws Exception {
        assertTrue(sessionId > 0, "Session must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-sessions/" + sessionId + "/ready-to-sign",
                String.format("""
                {"idempotency_key":"manual-s7-ready-to-sign-%d"}
                """, System.currentTimeMillis()));

        assertTrue(response.statusCode() < 300,
                "Mark ready to sign should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals("ready_to_sign", data.get("status").asText(),
                "Session should transition to 'ready_to_sign'");
    }

    @Test
    @Order(20)
    void markSessionSigned() throws Exception {
        assertTrue(sessionId > 0, "Session must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-sessions/" + sessionId + "/signed",
                String.format("""
                {"idempotency_key":"manual-s7-signed-%d"}
                """, System.currentTimeMillis()));

        assertTrue(response.statusCode() < 300,
                "Mark session signed should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals("signed", data.get("status").asText(),
                "Session should transition to 'signed'");
    }

    @Test
    @Order(21)
    void readSessionFinal() throws Exception {
        assertTrue(sessionId > 0, "Session must be created first");

        HttpResponse<String> response = apiGet(token, "/api/v1/advice-sessions/" + sessionId);
        assertEquals(200, response.statusCode());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals("signed", data.get("status").asText(),
                "Final session status should be 'signed'");
        assertNotNull(data.get("signed_at").asText(),
                "signed_at should be populated");
    }

    @Test
    @Order(22)
    void closeAdviceContext() throws Exception {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-contexts/" + adviceContextId + "/close",
                String.format("""
                {"idempotency_key":"manual-s7-close-%d","reason":"Scenario complete"}
                """, System.currentTimeMillis()));

        assertTrue(response.statusCode() < 300,
                "Close advice context should succeed, got: " + response.statusCode() + " " + response.body());
    }

    // -- Teardown --------------------------------------------------------------
    // Portfolio can be deleted normally. Client and Person cannot be deleted
    // while referenced by the advice context (FK constraint), so their cleanup
    // is best-effort via @AfterAll.

    @Test
    @Order(23)
    void deletePortfolio() throws Exception {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/portfolios/" + portfolioId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        portfolioId = 0;
    }

    @AfterAll
    static void teardown() {
        if (token == null) return;
        if (portfolioId > 0) deleteEntity(token, "/api/v1/portfolios/" + portfolioId);
        if (clientId > 0) deleteEntity(token, "/api/v1/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/v1/persons/" + personId);
    }
}
