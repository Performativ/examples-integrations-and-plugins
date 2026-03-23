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
 * S7: Advisory Agreement — Signing Provider Plugin Perspective.
 *
 * <p>Tells the advisory agreement signing story from the viewpoint of a
 * signing-provider plugin. The plugin receives an AdviceContext.Created
 * webhook, queries members to discover signers, prepares a signing envelope,
 * posts signing progress at each stage, and walks the agreement through
 * {@code draft} → {@code pending_signature} → {@code signed}.
 *
 * <p>Uses raw HTTP throughout.
 *
 * <p>Cleanup: expire agreement → delete agreement (cascade-deletes envelope) →
 * delete advice context → delete client → delete person.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdvisoryAgreementScenario extends BaseScenario {

    private static String token;
    private static int advicePolicyId;
    private static int personId;
    private static int clientId;
    private static int adviceContextId;
    private static int memberPersonId;
    private static int documentId;
    private static int envelopeId;
    private static int agreementId;
    private static int signedDocId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();
    }

    // ─── Setup ──────────────────────────────────────────────────────

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
                {"first_name":"Manual","last_name":"S7-AdvisoryAgreement","email":"manual-s7@example.com","language_code":"en"}
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
                "Link person to client should succeed, got: " + response.statusCode() + " " + response.body());
    }

    // ─── Plugin receives AdviceContext.Created webhook ───────────────

    @Test
    @Order(5)
    void createAdviceContext() throws Exception {
        // Plugin webhook: AdviceContext.Created
        //   The signing-provider plugin listens for this event to begin
        //   the signing flow. The webhook payload includes the context ID.
        assertTrue(advicePolicyId > 0, "Advice policy must be found first");
        assertTrue(personId > 0, "Person must be created first");

        HttpResponse<String> response = apiPost(token, "/api/v1/advice-contexts",
                String.format("""
                {"advice_policy_id":%d,"type":"individual","name":"Manual-S7 Advice Context","reference_person_id":%d,"members":[{"person_id":%d,"client_id":%d,"power_of_attorney":false}]}
                """, advicePolicyId, personId, personId, clientId));

        assertTrue(response.statusCode() < 300,
                "Create advice context should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        adviceContextId = data.get("id").asInt();
        assertTrue(adviceContextId > 0, "Advice context ID should be positive");
        assertEquals("active", data.get("status").asText(),
                "New advice context should be active");
    }

    @Test
    @Order(6)
    void queryAdviceContextMembers() throws Exception {
        // Plugin discovers who needs to sign by querying the member list.
        // Each member has a person_id (the signer) and client_id (the entity
        // being advised). The plugin uses person_id to add signer parties.
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        HttpResponse<String> response = apiGet(token,
                "/api/v1/advice-contexts/" + adviceContextId + "/clients");
        assertEquals(200, response.statusCode(),
                "Query members should return 200, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertTrue(data.size() > 0, "At least one member should exist");

        memberPersonId = data.get(0).get("person_id").asInt();
        assertTrue(memberPersonId > 0, "Member person_id should be positive");
        assertEquals(personId, memberPersonId,
                "Member person_id should match the person we added");
    }

    // ─── Plugin prepares the signing envelope ───────────────────────

    @Test
    @Order(7)
    void uploadSourceDocument() throws Exception {
        // Plugin generates the advisory agreement document (e.g., from a
        // template engine) and uploads it via multipart POST.
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
    @Order(8)
    void createSigningEnvelope() throws Exception {
        HttpResponse<String> response = apiPost(token, "/api/v1/signing-envelopes",
                """
                {"title":"Manual-S7 Agreement Envelope"}
                """);

        assertTrue(response.statusCode() < 300,
                "Create signing envelope should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        envelopeId = data.get("id").asInt();
        assertTrue(envelopeId > 0, "Envelope ID should be positive");
        assertEquals("draft", data.get("status").asText(), "New envelope should be in draft status");
    }

    @Test
    @Order(9)
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
    @Order(10)
    void addSignerParty() throws Exception {
        // Uses person_id discovered from the member query (step 6), not
        // the hard-coded person_id from setup. In production, the plugin
        // would iterate all members and add each as a signer party.
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(memberPersonId > 0, "Member person_id must be discovered first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/signing-envelopes/" + envelopeId + "/parties",
                String.format("""
                {"person_id":%d,"role":"signer"}
                """, memberPersonId));

        assertTrue(response.statusCode() < 300,
                "Add signer party to envelope should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(11)
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

    // ─── Create and walk the agreement through signing ──────────────

    @Test
    @Order(12)
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
    @Order(13)
    void readAdvisoryAgreement() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");

        HttpResponse<String> response = apiGet(token, "/api/v1/advice-agreements/" + agreementId);
        assertEquals(200, response.statusCode());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals(agreementId, data.get("id").asInt());
        assertEquals("draft", data.get("status").asText());
        assertEquals("1.0", data.get("version").asText());
    }

    @Test
    @Order(14)
    void submitSigning() throws Exception {
        // Idempotency: a replayed submit-signing request returns the cached
        // response with an Idempotent-Replayed header. Safe to retry on
        // network timeout.
        //
        // Plugin webhook: AdvisoryAgreement.Updated
        //   { "state": { "current": "pending_signature", "previous": "draft" } }
        assertTrue(agreementId > 0, "Agreement must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-agreements/" + agreementId + "/submit-signing",
                "{}");

        assertTrue(response.statusCode() < 300,
                "Submit-signing should succeed (draft → pending_signature), got: "
                        + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(15)
    void postSigningProgressInitial() throws Exception {
        // Idempotency: safe to retry if plugin crashes mid-flight. The platform
        // stores the latest progress snapshot; retries overwrite with the same data.
        //
        // The plugin posts a progress update showing 0 of 1 signers have signed.
        // This surfaces in the advisor UI as a substatus on the agreement.
        assertTrue(agreementId > 0, "Agreement must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-agreements/" + agreementId + "/report-signing-progress",
                """
                {"substatus":"Waiting for signers","signing_progress":{"provider":"example-signing-provider","status":"in_progress","signed_count":0,"total_signers":1,"signers":[{"name":"Manual S7-AdvisoryAgreement","email":"manual-s7@example.com","status":"pending"}]}}
                """);

        assertTrue(response.statusCode() < 300,
                "Update signing progress should succeed, got: " + response.statusCode() + " " + response.body());
    }

    // ─── Signing ceremony ───────────────────────────────────────────
    // Order matters: upload signed doc → add to envelope → mark party.
    // Marking the last party auto-completes the envelope, which locks
    // it — no documents can be added after that.

    @Test
    @Order(16)
    void uploadSignedDocument() throws Exception {
        // The signing provider has collected all signatures. The plugin
        // downloads the signed copy from the provider and uploads it.
        Path tempFile = Files.createTempFile("manual-s7-signed-", ".txt");
        Files.writeString(tempFile, "Signed Advisory Agreement - Manual S7");

        try {
            HttpResponse<String> response = apiPostMultipart(token, "/api/v1/documents",
                    tempFile, "file", Map.of("type", "advisory_agreement"));

            assertTrue(response.statusCode() < 300,
                    "Signed document upload should succeed, got: " + response.statusCode() + " " + response.body());

            signedDocId = objectMapper.readTree(response.body()).path("data").get("id").asInt();
            assertTrue(signedDocId > 0, "Signed document ID should be positive");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(17)
    void addSignedDocumentToEnvelope() throws Exception {
        // IMPORTANT: add the signed document BEFORE marking the signer party
        // as signed (step 18). Marking the last party auto-completes the
        // envelope, which locks it — no more documents can be added after that.
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(signedDocId > 0, "Signed document must be uploaded first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/signing-envelopes/" + envelopeId + "/documents",
                String.format("""
                {"document_id":%d,"role":"signed"}
                """, signedDocId));

        assertTrue(response.statusCode() < 300,
                "Add signed document to envelope should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(18)
    void markSignerPartySigned() throws Exception {
        // Idempotency: prevents double-signing if the signing provider's
        // callback fires twice. The platform returns the same response.
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
    @Order(19)
    void postSigningProgressComplete() throws Exception {
        // All parties have signed. The plugin posts a final progress update
        // so the advisor UI reflects completion.
        assertTrue(agreementId > 0, "Agreement must be created first");

        String signedAt = java.time.Instant.now().toString();
        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-agreements/" + agreementId + "/report-signing-progress",
                String.format("""
                {"substatus":"All parties signed","signing_progress":{"provider":"example-signing-provider","status":"completed","signed_count":1,"total_signers":1,"signers":[{"name":"Manual S7-AdvisoryAgreement","email":"manual-s7@example.com","status":"signed","signed_at":"%s"}]}}
                """, signedAt));

        assertTrue(response.statusCode() < 300,
                "Update signing progress (complete) should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(20)
    void markAgreementSigned() throws Exception {
        // Idempotency: prevents double-signing if callback fires twice.
        //
        // Plugin webhook: AdvisoryAgreement.Updated
        //   { "state": { "current": "signed", "previous": "pending_signature" } }
        assertTrue(agreementId > 0, "Agreement must be created first");
        assertTrue(signedDocId > 0, "Signed document must be uploaded first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-agreements/" + agreementId + "/mark-signed",
                String.format("""
                {"signed_document_id":%d}
                """, signedDocId));

        assertTrue(response.statusCode() < 300,
                "Mark-signed should succeed (pending_signature → signed), got: "
                        + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(21)
    void readAgreementFinal() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");

        HttpResponse<String> response = apiGet(token, "/api/v1/advice-agreements/" + agreementId);
        assertEquals(200, response.statusCode());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        assertEquals("signed", data.get("status").asText(),
                "Agreement should be in signed status");
    }

    // ─── Teardown: expire agreement → delete agreement → delete context → delete client/person ──

    @Test
    @Order(TEARDOWN + 1)
    void expireAgreement() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-agreements/" + agreementId + "/expire", "{}");
        assertTrue(response.statusCode() < 300,
                "Expire agreement should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(TEARDOWN + 2)
    void deleteAgreement() throws Exception {
        assertTrue(agreementId > 0, "Agreement must be created first");

        HttpResponse<String> response = apiDelete(token, "/api/v1/advice-agreements/" + agreementId);
        assertTrue(response.statusCode() < 300,
                "Delete agreement should succeed, got: " + response.statusCode());
        agreementId = 0;
    }

    @Test
    @Order(TEARDOWN + 3)
    void deleteAdviceContext() throws Exception {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        HttpResponse<String> response = apiDelete(token, "/api/v1/advice-contexts/" + adviceContextId);
        assertTrue(response.statusCode() < 300,
                "Delete advice context should succeed, got: " + response.statusCode());
        adviceContextId = 0;
    }

    @Test
    @Order(TEARDOWN + 4)
    void deleteClient() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/clients/" + clientId);
        assertTrue(response.statusCode() < 300,
                "Delete client should succeed, got: " + response.statusCode());
        clientId = 0;
    }

    @Test
    @Order(TEARDOWN + 5)
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
