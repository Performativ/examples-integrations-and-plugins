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
 * S8: Start Advise — full advice session lifecycle.
 *
 * <p>Creates the prerequisite chain (Person → Client → Portfolio),
 * opens an advice context, creates an advisory agreement, attaches and
 * completes a signing envelope, then starts an advice session and walks it
 * through the state machine: created → data_ready → active → ready_to_sign → signed.
 *
 * <p>v1 signing model: the advisory agreement is created first, then the
 * signing envelope is attached to it via {@code signable_type=advisory_agreement}
 * + {@code signable_id}. Documents carry a {@code ceremony_role}
 * ({@code input} = to-be-signed, {@code output} = signed result).
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
    private static int signedDocId;
    private static int sessionId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();
    }

    // -- Prerequisites --------------------------------------------------------

    @Test
    @Order(SETUP + 1)
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
    @Order(SETUP + 2)
    void createPerson() throws Exception {
        JsonNode person = createEntity(token, "/api/v1/persons",
                """
                {"first_name":"Manual","last_name":"S8-StartAdvise","email":"manual-s8@example.com","language_code":"en"}
                """);

        personId = person.get("id").asInt();
        assertTrue(personId > 0, "Person ID should be positive");
        registerCleanup(token, "/api/v1/persons/" + personId);
    }

    @Test
    @Order(SETUP + 3)
    void createClient() throws Exception {
        JsonNode client = createEntity(token, "/api/v1/clients",
                """
                {"name":"Manual-S8 Client","type":"individual","is_active":true,"currency_id":47}
                """);

        clientId = client.get("id").asInt();
        assertTrue(clientId > 0, "Client ID should be positive");
        registerCleanup(token, "/api/v1/clients/" + clientId);
    }

    @Test
    @Order(SETUP + 4)
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
    @Order(SETUP + 5)
    void createPortfolio() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        JsonNode portfolio = createEntity(token, "/api/v1/portfolios",
                String.format("""
                {"name":"Manual-S8 Portfolio","client_ids":[%d],"currency_id":47}
                """, clientId));

        portfolioId = portfolio.get("id").asInt();
        assertTrue(portfolioId > 0, "Portfolio ID should be positive");
        registerCleanup(token, "/api/v1/portfolios/" + portfolioId);
    }

    // -- Advice Context + Advisory Agreement (prerequisites for the session) --
    // v1: the agreement is created before the signing envelope, since the
    // envelope references the agreement as its signable.

    @Test
    @Order(SETUP + 6)
    void createAdviceContext() throws Exception {
        assertTrue(advicePolicyId > 0, "Advice policy must be found first");
        assertTrue(personId > 0, "Person must be created first");

        HttpResponse<String> response = apiPost(token, "/api/v1/advice-contexts",
                String.format("""
                {"advice_policy_id":%d,"type":"individual","name":"Manual-S8 Advice Context","reference_person_id":%d,"members":[{"person_id":%d,"client_id":%d,"power_of_attorney":false}]}
                """, advicePolicyId, personId, personId, clientId));

        assertTrue(response.statusCode() < 300,
                "Create advice context should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        adviceContextId = data.get("id").asInt();
        assertTrue(adviceContextId > 0, "Advice context ID should be positive");
        assertEquals("active", data.get("status").asText());
    }

    @Test
    @Order(SETUP + 7)
    void createAdvisoryAgreement() throws Exception {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-contexts/" + adviceContextId + "/agreements",
                """
                {"version":"1.0","external_reference":"manual-s8-agreement"}
                """);

        assertTrue(response.statusCode() < 300,
                "Create advisory agreement should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        agreementId = data.get("id").asInt();
        assertTrue(agreementId > 0, "Agreement ID should be positive");
        assertEquals("draft", data.get("status").asText());
    }

    // -- Document upload and signing envelope (attached to the agreement) -----

    @Test
    @Order(ENVELOPE + 1)
    void uploadDocument() throws Exception {
        Path tempFile = Files.createTempFile("manual-s8-agreement-", ".txt");
        Files.writeString(tempFile, "Hello World - Manual S8 Advisory Agreement");

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
    @Order(ENVELOPE + 2)
    void createSigningEnvelope() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");

        // v1: the envelope attaches to a polymorphic signable —
        // signable_type=advisory_agreement + signable_id.
        HttpResponse<String> response = apiPost(token, "/api/v1/signing-envelopes",
                String.format("""
                {"title":"Manual-S8 Agreement Envelope","signable_type":"advisory_agreement","signable_id":%d}
                """, agreementId));

        assertTrue(response.statusCode() < 300,
                "Create envelope should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        envelopeId = data.get("id").asInt();
        assertTrue(envelopeId > 0, "Envelope ID should be positive");
    }

    @Test
    @Order(ENVELOPE + 3)
    void addDocumentToEnvelope() throws Exception {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        // ceremony_role=input marks the document that will be signed.
        HttpResponse<String> response = apiPost(token,
                "/api/v1/signing-envelopes/" + envelopeId + "/documents",
                String.format("""
                {"document_id":%d,"ceremony_role":"input"}
                """, documentId));

        assertTrue(response.statusCode() < 300,
                "Add document should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(ENVELOPE + 4)
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
    @Order(ENVELOPE + 5)
    void sendEnvelope() throws Exception {
        assertTrue(envelopeId > 0, "Envelope must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/signing-envelopes/" + envelopeId + "/send", "{}");

        assertTrue(response.statusCode() < 300,
                "Send envelope should succeed, got: " + response.statusCode() + " " + response.body());
    }

    // -- Walk the agreement through signing -----------------------------------

    @Test
    @Order(AGREEMENT + 1)
    void submitSigning() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");

        // v1: submit-signing has no body; idempotency travels via the
        // Idempotency-Key header (handled by apiPost).
        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-agreements/" + agreementId + "/submit-signing",
                "{}");

        assertTrue(response.statusCode() < 300,
                "Submit signing should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(AGREEMENT + 2)
    void uploadSignedDocumentAndAddToEnvelope() throws Exception {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        // Upload a separate signed document
        var tempFile = java.nio.file.Files.createTempFile("manual-s8-signed-", ".txt");
        java.nio.file.Files.writeString(tempFile, "Signed Advisory Agreement - Manual S8");
        try {
            HttpResponse<String> docResp = apiPostMultipart(token, "/api/v1/documents",
                    tempFile, "file", Map.of("type", "advisory_agreement"));
            signedDocId = objectMapper.readTree(docResp.body()).path("data").get("id").asInt();
            assertTrue(signedDocId > 0, "Signed document ID should be positive");
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }

        // Add signed document to envelope BEFORE marking the party signed —
        // marking all parties signed completes the envelope, after which
        // documents can no longer be added. ceremony_role=output marks the
        // resulting signed document.
        HttpResponse<String> response = apiPost(token,
                "/api/v1/signing-envelopes/" + envelopeId + "/documents",
                String.format("""
                {"document_id":%d,"ceremony_role":"output"}
                """, signedDocId));

        assertTrue(response.statusCode() < 300,
                "Add signed document to envelope should succeed, got: "
                        + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(AGREEMENT + 3)
    void markSignerPartySigned() throws Exception {
        assertTrue(envelopeId > 0, "Envelope must be created first");

        HttpResponse<String> envelopeResp = apiGet(token, "/api/v1/signing-envelopes/" + envelopeId);
        JsonNode parties = objectMapper.readTree(envelopeResp.body()).path("data").path("parties");
        assertTrue(parties.size() > 0, "Envelope should have at least one party");
        int partyId = parties.get(0).get("id").asInt();

        HttpResponse<String> response = apiPost(token,
                "/api/v1/signing-envelopes/" + envelopeId + "/parties/" + partyId + "/mark-signed",
                String.format("""
                {"signed_at":"%s"}
                """, java.time.Instant.now().toString()));

        assertTrue(response.statusCode() < 300,
                "Mark signer party as signed should succeed, got: "
                        + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(AGREEMENT + 4)
    void markAgreementSigned() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");
        assertTrue(signedDocId > 0, "Signed document must be uploaded first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-agreements/" + agreementId + "/mark-signed",
                String.format("""
                {"signed_document_id":%d}
                """, signedDocId));

        assertTrue(response.statusCode() < 300,
                "Mark agreement signed should succeed, got: "
                        + response.statusCode() + " " + response.body());
    }

    // -- Advice Session lifecycle ---------------------------------------------

    @Test
    @Order(SESSION + 1)
    void createAdviceSession() throws Exception {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-contexts/" + adviceContextId + "/sessions",
                """
                {"external_reference":"manual-s8-session"}
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
    @Order(SESSION + 2)
    void readAdviceSession() throws Exception {
        assertTrue(sessionId > 0, "Session must be created first");

        HttpResponse<String> response = apiGet(token, "/api/v1/advice-sessions/" + sessionId);
        assertEquals(200, response.statusCode());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals(sessionId, data.get("id").asInt());
        assertEquals("created", data.get("status").asText());
    }

    @Test
    @Order(SESSION + 3)
    void markDataReady() throws Exception {
        assertTrue(sessionId > 0, "Session must be created first");

        // In v1, idempotency moved from body to Idempotency-Key header (handled by apiPost).
        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-sessions/" + sessionId + "/mark-data-ready",
                "{}");

        assertTrue(response.statusCode() < 300,
                "Mark data ready should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals("data_ready", data.get("status").asText(),
                "Session should transition to 'data_ready'");
    }

    @Test
    @Order(SESSION + 4)
    void activateSession() throws Exception {
        assertTrue(sessionId > 0, "Session must be created first");

        // v1: activate no longer takes a redirect_url in the request body; the
        // advisor-UI redirect is returned by the API on the activated session.
        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-sessions/" + sessionId + "/activate",
                "{}");

        assertTrue(response.statusCode() < 300,
                "Activate session should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals("active", data.get("status").asText(),
                "Session should transition to 'active'");
    }

    @Test
    @Order(SESSION + 5)
    void markReadyToSign() throws Exception {
        assertTrue(sessionId > 0, "Session must be created first");

        // In v1, idempotency moved from body to Idempotency-Key header (handled by apiPost).
        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-sessions/" + sessionId + "/mark-ready-to-sign",
                "{}");

        assertTrue(response.statusCode() < 300,
                "Mark ready to sign should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals("ready_to_sign", data.get("status").asText(),
                "Session should transition to 'ready_to_sign'");
    }

    @Test
    @Order(SESSION + 6)
    void markSessionSigned() throws Exception {
        assertTrue(sessionId > 0, "Session must be created first");

        // In v1, idempotency moved from body to Idempotency-Key header (handled by apiPost).
        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-sessions/" + sessionId + "/mark-signed",
                "{}");

        assertTrue(response.statusCode() < 300,
                "Mark session signed should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals("signed", data.get("status").asText(),
                "Session should transition to 'signed'");
    }

    @Test
    @Order(VERIFY + 1)
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

    // -- Teardown: delete session → expire+delete agreement → delete context → entities --

    @Test
    @Order(TEARDOWN + 1)
    void deleteSession() throws Exception {
        assertTrue(sessionId > 0, "Session must be created first");

        HttpResponse<String> response = apiDelete(token, "/api/v1/advice-sessions/" + sessionId);
        assertTrue(response.statusCode() < 300,
                "Delete session should succeed, got: " + response.statusCode());
        sessionId = 0;
    }

    @Test
    @Order(TEARDOWN + 2)
    void expireAgreement() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-agreements/" + agreementId + "/expire", "{}");
        assertTrue(response.statusCode() < 300,
                "Expire agreement should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(TEARDOWN + 3)
    void deleteAgreement() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");

        HttpResponse<String> response = apiDelete(token, "/api/v1/advice-agreements/" + agreementId);
        assertTrue(response.statusCode() < 300,
                "Delete agreement should succeed, got: " + response.statusCode());
        agreementId = 0;
    }

    @Test
    @Order(TEARDOWN + 4)
    void deleteAdviceContext() throws Exception {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        HttpResponse<String> response = apiDelete(token, "/api/v1/advice-contexts/" + adviceContextId);
        assertTrue(response.statusCode() < 300,
                "Delete advice context should succeed, got: " + response.statusCode());
        adviceContextId = 0;
    }

    @Test
    @Order(TEARDOWN + 5)
    void deletePortfolio() throws Exception {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/portfolios/" + portfolioId);
        assertTrue(response.statusCode() < 300,
                "Delete portfolio should succeed, got: " + response.statusCode());
        portfolioId = 0;
    }

    @Test
    @Order(TEARDOWN + 6)
    void deleteClient() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/clients/" + clientId);
        assertTrue(response.statusCode() < 300,
                "Delete client should succeed, got: " + response.statusCode());
        clientId = 0;
    }

    @Test
    @Order(TEARDOWN + 7)
    void deletePerson() throws Exception {
        assertTrue(personId > 0, "Person must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/persons/" + personId);
        // Person may already be cascade-deleted with the client
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete person should succeed, got: " + response.statusCode());
        personId = 0;
    }

    @AfterAll
    static void teardown() {
        runCleanup();
    }
}
