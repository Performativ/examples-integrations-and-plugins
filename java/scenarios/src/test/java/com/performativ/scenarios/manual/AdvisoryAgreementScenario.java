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
 * S6: Advisory Agreement — full signing envelope journey.
 *
 * <p>Flow: upload document (multipart) → create signing envelope → add document
 * and signer party → send envelope → create advice context → create advisory
 * agreement linked to envelope → submit-signing (draft → pending_signature) →
 * mark-signed (pending_signature → signed) → close context → clean up.
 *
 * <p>Uses raw HTTP throughout.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdvisoryAgreementScenario extends BaseScenario {

    private static String token;
    private static int advicePolicyId;
    private static int personId;
    private static int clientId;
    private static int documentId;
    private static int envelopeId;
    private static int adviceContextId;
    private static int agreementId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();
    }

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
                {"first_name":"Manual","last_name":"S6-AdvisoryAgreement","email":"manual-s6@example.com","language_code":"en"}
                """);

        personId = person.get("id").asInt();
        assertTrue(personId > 0, "Person ID should be positive");
        assertEquals("Manual", person.get("first_name").asText());
    }

    @Test
    @Order(3)
    void createClient() throws Exception {
        JsonNode client = createEntity(token, "/api/v1/clients",
                """
                {"name":"Manual-S6 Client","type":"individual","is_active":true,"currency_id":47}
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
                "Link person to client should succeed, got: " + response.statusCode() + " " + response.body());
    }

    // --- Document upload and signing envelope ---

    @Test
    @Order(5)
    void uploadDocument() throws Exception {
        // Create a temporary file for the multipart upload
        Path tempFile = Files.createTempFile("manual-s6-agreement-", ".txt");
        Files.writeString(tempFile, "Hello World - Manual S6 Advisory Agreement");

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
    @Order(6)
    void createSigningEnvelope() throws Exception {
        HttpResponse<String> response = apiPost(token, "/api/v1/signing-envelopes",
                """
                {"title":"Manual-S6 Agreement Envelope"}
                """);

        assertTrue(response.statusCode() < 300,
                "Create signing envelope should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        envelopeId = data.get("id").asInt();
        assertTrue(envelopeId > 0, "Envelope ID should be positive");
        assertEquals("draft", data.get("status").asText(), "New envelope should be in draft status");
    }

    @Test
    @Order(7)
    void addDocumentToEnvelope() throws Exception {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/signing-envelopes/" + envelopeId + "/documents",
                String.format("""
                {"document_id":%d,"role":"source"}
                """, documentId));

        assertTrue(response.statusCode() < 300,
                "Add document to envelope should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(8)
    void addSignerPartyToEnvelope() throws Exception {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(personId > 0, "Person must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/signing-envelopes/" + envelopeId + "/parties",
                String.format("""
                {"person_id":%d,"role":"signer"}
                """, personId));

        assertTrue(response.statusCode() < 300,
                "Add signer party to envelope should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(9)
    void sendEnvelope() throws Exception {
        assertTrue(envelopeId > 0, "Envelope must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/signing-envelopes/" + envelopeId + "/send",
                "{}");

        assertTrue(response.statusCode() < 300,
                "Send envelope should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals("sent", data.get("status").asText(), "Envelope should transition to 'sent'");
    }

    // --- Advice context and agreement ---

    @Test
    @Order(10)
    void createAdviceContext() throws Exception {
        assertTrue(advicePolicyId > 0, "Advice policy must be found first");
        assertTrue(personId > 0, "Person must be created first");

        HttpResponse<String> response = apiPost(token, "/api/v1/advice-contexts",
                String.format("""
                {"advice_policy_id":%d,"type":"individual","name":"Manual-S6 Advice Context","reference_person_id":%d,"members":[{"person_id":%d,"power_of_attorney":false}]}
                """, advicePolicyId, personId, personId));

        assertTrue(response.statusCode() < 300,
                "Create advice context should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        adviceContextId = data.get("id").asInt();
        assertTrue(adviceContextId > 0, "Advice context ID should be positive");
        assertEquals("active", data.get("status").asText(),
                "New advice context should be active");
    }

    @Test
    @Order(11)
    void createAdvisoryAgreement() throws Exception {
        assertTrue(adviceContextId > 0, "Advice context must be created first");
        assertTrue(envelopeId > 0, "Signing envelope must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-contexts/" + adviceContextId + "/agreements",
                String.format("""
                {"version":"1.0","signing_envelope_id":%d}
                """, envelopeId));

        assertTrue(response.statusCode() < 300,
                "Create advisory agreement should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        agreementId = data.get("id").asInt();
        assertTrue(agreementId > 0, "Agreement ID should be positive");
        assertEquals("draft", data.get("status").asText(),
                "New advisory agreement should be in draft status");
    }

    @Test
    @Order(12)
    void readAdvisoryAgreement() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");

        HttpResponse<String> response = apiGet(token, "/api/v1/advisory-agreements/" + agreementId);
        assertEquals(200, response.statusCode());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals(agreementId, data.get("id").asInt());
        assertEquals("draft", data.get("status").asText());
        assertEquals("1.0", data.get("version").asText());
    }

    @Test
    @Order(13)
    void submitSigning() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        // Transition: draft → pending_signature
        HttpResponse<String> response = apiPost(token,
                "/api/v1/advisory-agreements/" + agreementId + "/submit-signing",
                String.format("""
                {"idempotency_key":"manual-s6-submit-%d","document_id":%d}
                """, System.currentTimeMillis(), documentId));

        assertTrue(response.statusCode() < 300,
                "Submit-signing should succeed (draft → pending_signature), got: "
                        + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(14)
    void markAgreementSigned() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        // Transition: pending_signature → signed
        HttpResponse<String> response = apiPost(token,
                "/api/v1/advisory-agreements/" + agreementId + "/mark-signed",
                String.format("""
                {"idempotency_key":"manual-s6-signed-%d","signed_document_id":%d}
                """, System.currentTimeMillis(), documentId));

        assertTrue(response.statusCode() < 300,
                "Mark agreement signed should succeed (pending_signature → signed), got: "
                        + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(15)
    void readAgreementSigned() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");

        HttpResponse<String> response = apiGet(token, "/api/v1/advisory-agreements/" + agreementId);
        assertEquals(200, response.statusCode());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals("signed", data.get("status").asText(),
                "Agreement should now be in signed status");
    }

    @Test
    @Order(16)
    void closeAdviceContext() throws Exception {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-contexts/" + adviceContextId + "/close",
                String.format("""
                {"idempotency_key":"manual-s6-close-%d","reason":"Scenario complete"}
                """, System.currentTimeMillis()));

        assertTrue(response.statusCode() < 300,
                "Close advice context should succeed, got: " + response.statusCode() + " " + response.body());
    }

    // -- Teardown (best-effort) ------------------------------------------------
    // Client and Person cannot be deleted while referenced by the advice context
    // (FK constraint). Cleanup is best-effort via @AfterAll.

    @AfterAll
    static void teardown() {
        if (token == null) return;
        if (clientId > 0) deleteEntity(token, "/api/v1/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/v1/persons/" + personId);
    }
}
