package com.performativ.scenarios.generated;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.client.api.*;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import com.performativ.client.model.*;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S6: Advisory Agreement — Signing Provider Plugin Perspective (generated client).
 *
 * <p>Tells the advisory agreement signing story from the viewpoint of a
 * signing-provider plugin. Uses the typed generated client for all operations
 * except document upload (multipart), advice context creation (complex
 * members array), and signing progress (spec types signing_progress as
 * array&lt;string&gt; but API accepts a richer object).
 *
 * <p>Cleanup: Client and Person are <b>not</b> deleted. Once an advice context
 * references these entities, they cannot be removed via the API (FK constraint,
 * backend #6183). Prefixed names ({@code Gen-S6}) make orphans identifiable.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdvisoryAgreementScenario extends GeneratedClientScenario {

    private static String token;
    private static ApiClient apiClient;
    private static AdvicePoliciesApi advicePolicyApi;
    private static PersonApi personApi;
    private static ClientApi clientApi;
    private static ClientPersonApi clientPersonApi;
    private static AdviceContextsApi adviceContextApi;
    private static SigningEnvelopesApi signingEnvelopeApi;
    private static AdvisoryAgreementsApi advisoryAgreementApi;

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

        apiClient = createApiClient(token);
        advicePolicyApi = new AdvicePoliciesApi(apiClient);
        personApi = new PersonApi(apiClient);
        clientApi = new ClientApi(apiClient);
        clientPersonApi = new ClientPersonApi(apiClient);
        adviceContextApi = new AdviceContextsApi(apiClient);
        signingEnvelopeApi = new SigningEnvelopesApi(apiClient);
        advisoryAgreementApi = new AdvisoryAgreementsApi(apiClient);
    }

    // ─── Setup ──────────────────────────────────────────────────────

    @Test
    @Order(SETUP + 1)
    void listAdvicePolicies() throws ApiException {
        var response = advicePolicyApi.advicePoliciesIndex(null, null, null, null);
        assertNotNull(response);
        assertNotNull(response.getData());
        assertFalse(response.getData().isEmpty(),
                "At least one advice policy must be configured on the tenant");

        advicePolicyId = response.getData().get(0).getId();
        assertTrue(advicePolicyId > 0, "Advice policy ID should be positive");
    }

    @Test
    @Order(SETUP + 2)
    void createPerson() throws ApiException {
        var req = new StorePersonRequest()
                .firstName("Gen")
                .lastName("S6-AdvisoryAgreement")
                .email("gen-s6@example.com")
                .languageCode("en");

        var response = personApi.personsStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        personId = response.getData().getId();
        assertTrue(personId > 0, "Person ID should be positive");
    }

    @Test
    @Order(SETUP + 3)
    void createClient() throws ApiException {
        var req = new StoreClientRequest()
                .name("Gen-S6 Client")
                .type(StoreClientRequest.TypeEnum.INDIVIDUAL)
                .isActive(true)
                .currencyId(47);

        var response = clientApi.clientsStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        clientId = response.getData().getId();
        assertTrue(clientId > 0, "Client ID should be positive");
    }

    @Test
    @Order(SETUP + 4)
    void linkPersonToClient() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");
        assertTrue(clientId > 0, "Client must be created first");

        var req = new StoreClientPersonRequest()
                .clientId(clientId)
                .personId(personId)
                .isPrimary(true);

        clientPersonApi.clientPersonsStore(req, idempotencyKey());
    }

    // ─── Plugin receives AdviceContext.Created webhook ───────────────

    @Test
    @Order(SETUP + 5)
    void createAdviceContext() throws Exception {
        assertTrue(advicePolicyId > 0, "Advice policy must be found first");
        assertTrue(personId > 0, "Person must be created first");
        assertTrue(clientId > 0, "Client must be created first");

        // Raw HTTP: the generated StoreAdviceContextRequest requires members
        // with client_id (v1 change from person-centric to client-centric).
        HttpResponse<String> response = apiPost(token, "/api/v1/advice-contexts",
                String.format("""
                {"advice_policy_id":%d,"type":"individual","name":"Gen-S6 Advice Context","reference_person_id":%d,"members":[{"person_id":%d,"client_id":%d,"power_of_attorney":false}]}
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
    @Order(SETUP + 6)
    void queryAdviceContextMembers() throws ApiException {
        // Plugin discovers who needs to sign by querying the member list.
        // Each member has a personId (the signer) and clientId (the entity
        // being advised). The plugin uses personId to add signer parties.
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        var response = adviceContextApi.adviceContextsClientsIndex(
                String.valueOf(adviceContextId), null, null);
        assertNotNull(response);
        assertNotNull(response.getData());
        assertFalse(response.getData().isEmpty(),
                "At least one member should exist in the advice context");

        AdviceContextMemberResource member = response.getData().get(0);
        memberPersonId = member.getPersonId();
        assertTrue(memberPersonId > 0, "Member person_id should be positive");
        assertEquals(personId, memberPersonId,
                "Member person_id should match the person we added");
    }

    // ─── Plugin prepares the signing envelope ───────────────────────

    @Test
    @Order(ENVELOPE + 1)
    void uploadSourceDocument() throws Exception {
        Path tempFile = Files.createTempFile("gen-s6-agreement-", ".txt");
        Files.writeString(tempFile, "Hello World - Gen S6 Advisory Agreement");

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
    void createSigningEnvelope() throws ApiException {
        var req = new StoreSigningEnvelopeRequest()
                .title("Gen-S6 Agreement Envelope");

        var response = signingEnvelopeApi.signingEnvelopesStore(idempotencyKey(), req);
        assertNotNull(response);
        assertNotNull(response.getData());

        envelopeId = response.getData().getId();
        assertTrue(envelopeId > 0, "Envelope ID should be positive");
    }

    @Test
    @Order(ENVELOPE + 3)
    void addDocumentToEnvelope() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        var req = new AddDocumentToEnvelopeRequest()
                .documentId(documentId)
                .role(AddDocumentToEnvelopeRequest.RoleEnum.SOURCE);

        signingEnvelopeApi.signingEnvelopesDocumentsStore(
                String.valueOf(envelopeId), req, idempotencyKey());
    }

    @Test
    @Order(ENVELOPE + 4)
    void addSignerParty() throws ApiException {
        // Uses person_id discovered from the member query, not the hard-coded
        // person_id from setup. In production, the plugin would iterate all
        // members and add each as a signer party.
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(memberPersonId > 0, "Member person_id must be discovered first");

        var req = new AddPartyToEnvelopeRequest()
                .personId(memberPersonId)
                .role(AddPartyToEnvelopeRequest.RoleEnum.SIGNER);

        signingEnvelopeApi.signingEnvelopesPartiesStore(
                String.valueOf(envelopeId), req, idempotencyKey());
    }

    @Test
    @Order(ENVELOPE + 5)
    void sendEnvelope() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");

        signingEnvelopeApi.signingEnvelopeActionSend(
                String.valueOf(envelopeId), idempotencyKey());
    }

    // ─── Create and walk the agreement through signing ──────────────

    @Test
    @Order(AGREEMENT + 1)
    void createAdvisoryAgreement() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");
        assertTrue(envelopeId > 0, "Signing envelope must be created first");

        var req = new StoreAdvisoryAgreementRequest()
                .version("1.0")
                .signingEnvelopeId(envelopeId);

        var response = advisoryAgreementApi.v1AdviceAgreementsStoreForContext(
                String.valueOf(adviceContextId), idempotencyKey(), req);
        assertNotNull(response);
        assertNotNull(response.getData());

        agreementId = response.getData().getId();
        assertTrue(agreementId > 0, "Agreement ID should be positive");
        assertEquals("draft", response.getData().getStatus().getValue(),
                "New advisory agreement should be in draft status");
    }

    @Test
    @Order(AGREEMENT + 2)
    void readAdvisoryAgreement() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        var response = advisoryAgreementApi.v1AdviceAgreementsShow(
                String.valueOf(agreementId), null);
        assertNotNull(response);
        assertEquals(agreementId, response.getData().getId());
        assertEquals("draft", response.getData().getStatus().getValue());
        assertEquals("1.0", response.getData().getVersion());
    }

    @Test
    @Order(AGREEMENT + 3)
    void submitSigning() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        var req = new SubmitSigningAdvisoryAgreementRequest();

        advisoryAgreementApi.advisoryAgreementActionSubmitSigning(
                String.valueOf(agreementId), idempotencyKey(), req);
    }

    @Test
    @Order(AGREEMENT + 4)
    void postSigningProgressInitial() throws Exception {
        // Raw HTTP: the spec types signing_progress as array<string> but the API
        // actually accepts a richer object with provider/status/signers fields.
        // Using raw HTTP until the spec catches up.
        assertTrue(agreementId > 0, "Agreement must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-agreements/" + agreementId + "/report-signing-progress",
                """
                {"substatus":"Waiting for signers","signing_progress":{"provider":"example-signing-provider","status":"in_progress","signed_count":0,"total_signers":1,"signers":[{"name":"Gen S6-AdvisoryAgreement","email":"gen-s6@example.com","status":"pending"}]}}
                """);

        assertEquals(200, response.statusCode(),
                "Report signing progress should return 200, got: " + response.statusCode() + " " + response.body());
    }

    // ─── Signing ceremony ───────────────────────────────────────────
    // Order matters: upload signed doc → add to envelope → mark party.
    // Marking the last party auto-completes the envelope, which locks
    // it — no documents can be added after that.

    @Test
    @Order(AGREEMENT + 5)
    void uploadSignedDocument() throws Exception {
        Path tempFile = Files.createTempFile("gen-s6-signed-", ".txt");
        Files.writeString(tempFile, "Signed Advisory Agreement - Gen S6");

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
    @Order(AGREEMENT + 6)
    void addSignedDocumentToEnvelope() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(signedDocId > 0, "Signed document must be uploaded first");

        var req = new AddDocumentToEnvelopeRequest()
                .documentId(signedDocId)
                .role(AddDocumentToEnvelopeRequest.RoleEnum.SIGNED);

        signingEnvelopeApi.signingEnvelopesDocumentsStore(
                String.valueOf(envelopeId), req, idempotencyKey());
    }

    @Test
    @Order(AGREEMENT + 7)
    void markSignerPartySigned() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");

        var envelope = signingEnvelopeApi.signingEnvelopesShow(
                String.valueOf(envelopeId));
        assertNotNull(envelope.getData().getParties());
        assertFalse(envelope.getData().getParties().isEmpty(),
                "Envelope should have at least one party");
        int partyId = envelope.getData().getParties().get(0).getId();

        signingEnvelopeApi.signingEnvelopePartyActionMarkSigned(
                String.valueOf(envelopeId), String.valueOf(partyId), idempotencyKey());
    }

    @Test
    @Order(AGREEMENT + 8)
    void postSigningProgressComplete() throws Exception {
        // Raw HTTP: same reason as postSigningProgressInitial.
        assertTrue(agreementId > 0, "Agreement must be created first");

        String signedAt = OffsetDateTime.now().toString();
        HttpResponse<String> response = apiPost(token,
                "/api/v1/advice-agreements/" + agreementId + "/report-signing-progress",
                String.format("""
                {"substatus":"All parties signed","signing_progress":{"provider":"example-signing-provider","status":"completed","signed_count":1,"total_signers":1,"signers":[{"name":"Gen S6-AdvisoryAgreement","email":"gen-s6@example.com","status":"signed","signed_at":"%s"}]}}
                """, signedAt));

        assertEquals(200, response.statusCode(),
                "Report signing progress should return 200, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(AGREEMENT + 9)
    void markAgreementSigned() throws Exception {
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
    @Order(VERIFY + 1)
    void readAgreementFinal() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        var response = advisoryAgreementApi.v1AdviceAgreementsShow(
                String.valueOf(agreementId), null);
        assertNotNull(response);
        assertEquals("signed", response.getData().getStatus().getValue(),
                "Agreement should be in signed status");
    }

    @Test
    @Order(VERIFY + 2)
    void closeAdviceContext() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        var req = new CloseAdviceContextRequest()
                .reason("Scenario complete");

        adviceContextApi.adviceContextActionClose(
                String.valueOf(adviceContextId), idempotencyKey(), req);
    }

    // No @AfterAll teardown. Client and Person cannot be deleted while
    // referenced by the advice context (FK constraint, backend #6183).
    // Prefixed names (Gen-S6) make orphans identifiable.
}
