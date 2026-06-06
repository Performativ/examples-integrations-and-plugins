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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S7: Advisory Agreement — Signing Provider Plugin Perspective (generated client).
 *
 * <p>Tells the advisory agreement signing story from the viewpoint of a
 * signing-provider plugin, using the typed generated client for all operations
 * except document upload (multipart) and advice context creation (complex
 * members array).
 *
 * <p>v1 signing model: the advisory agreement is created first (snapshotting
 * the context members), then a signing envelope is attached to it via the
 * polymorphic {@code signable_type=advisory_agreement} + {@code signable_id}.
 * Documents carry a {@code ceremony_role} ({@code input} = to-be-signed,
 * {@code output} = signed result). The agreement walks the state machine
 * {@code draft → pending_signature → signed} via {@code submit-signing} and
 * {@code mark-signed}; {@code cancel-signing} is the negative path.
 *
 * <p>Cleanup: expire agreement → delete agreement (cascade-deletes envelope) →
 * delete advice context → delete client → delete person.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdvisoryAgreementScenario extends GeneratedClientScenario {

    private static String token;
    private static ApiClient apiClient;
    private static AdviceApi adviceApi;
    private static PersonsApi personsApi;
    private static ClientsApi clientsApi;

    private static long advicePolicyId;
    private static long personId;
    private static long clientId;
    private static long adviceContextId;
    private static long signerPersonId;
    private static long documentId;
    private static long envelopeId;
    private static long agreementId;
    private static long signedDocId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();

        apiClient = createApiClient(token);
        adviceApi = new AdviceApi(apiClient);
        personsApi = new PersonsApi(apiClient);
        clientsApi = new ClientsApi(apiClient);
    }

    // ─── Setup ──────────────────────────────────────────────────────

    @Test
    @Order(SETUP + 1)
    void listAdvicePolicies() throws ApiException {
        var response = adviceApi.advicePoliciesIndex(null, null, null, null);
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
                .lastName("S7-AdvisoryAgreement")
                .email("gen-s7@example.com")
                .languageCode("en");

        var response = personsApi.personsStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        personId = response.getData().getId();
        assertTrue(personId > 0, "Person ID should be positive");
    }

    @Test
    @Order(SETUP + 3)
    void createClient() throws ApiException {
        var req = new StoreClientRequest()
                .name("Gen-S7 Client")
                .type(StoreClientRequest.TypeEnum.INDIVIDUAL)
                .isActive(true)
                .currencyId(47L);

        var response = clientsApi.clientsStore(req, idempotencyKey());
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

        clientsApi.clientPersonsStore(req, idempotencyKey());
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
                {"advice_policy_id":%d,"type":"individual","name":"Gen-S7 Advice Context","reference_person_id":%d,"members":[{"person_id":%d,"client_id":%d,"power_of_attorney":false}]}
                """, advicePolicyId, personId, personId, clientId));

        assertTrue(response.statusCode() < 300,
                "Create advice context should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        adviceContextId = data.get("id").asLong();
        assertTrue(adviceContextId > 0, "Advice context ID should be positive");
        assertEquals("active", data.get("status").asText(),
                "New advice context should be active");
    }

    @Test
    @Order(SETUP + 6)
    void queryAdviceContextMembers() throws ApiException {
        // Plugin discovers the advised entities by querying the member list.
        // In v1 the member resource is client-centric: each member exposes the
        // clientId being advised. The signer person is resolved from the client
        // (here, the person we created and linked above).
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        var response = adviceApi.adviceContextsClientsIndex(
                String.valueOf(adviceContextId), null, null);
        assertNotNull(response);
        assertNotNull(response.getData());
        assertFalse(response.getData().isEmpty(),
                "At least one member should exist in the advice context");

        AdviceContextMemberResource member = response.getData().get(0);
        assertEquals(clientId, member.getClientId(),
                "Member client_id should match the client we added");
        // The v1 member resource is client-centric and exposes no person_id;
        // the signer person is resolved from the agreement's member_snapshot
        // once the agreement is created (see createAdvisoryAgreement).
    }

    // ─── Create the agreement (snapshots members), then attach an envelope ──
    // v1: the envelope references the agreement (signable), so the agreement
    // must exist first — the inverse of the pre-v1 flow.

    @Test
    @Order(SETUP + 7)
    void createAdvisoryAgreement() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        var req = new StoreAdvisoryAgreementRequest()
                .version("1.0")
                .externalReference("gen-s7-agreement");

        var response = adviceApi.v1AdviceAgreementsStoreForContext(
                String.valueOf(adviceContextId), idempotencyKey(), req);
        assertNotNull(response);
        assertNotNull(response.getData());

        agreementId = response.getData().getId();
        assertTrue(agreementId > 0, "Agreement ID should be positive");
        assertEquals("draft", response.getData().getStatus().getValue(),
                "New advisory agreement should be in draft status");

        // Resolve the signer from the agreement's member_snapshot. The platform
        // freezes (client_id, person_id, person_name) per member at creation and
        // rejects creation unless every member resolves to a person — so this is
        // the canonical, reliable signer-discovery source (member_snapshot is a
        // free-form object in the spec, so it is read via JSON).
        JsonNode snapshot = objectMapper.valueToTree(response.getData().getMemberSnapshot());
        assertTrue(snapshot.isArray() && !snapshot.isEmpty(),
                "member_snapshot should be a non-empty array");
        signerPersonId = snapshot.get(0).path("person_id").asLong();
        assertEquals(personId, signerPersonId,
                "Snapshot signer person should match the linked person");
    }

    @Test
    @Order(SETUP + 8)
    void readAdvisoryAgreement() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        var response = adviceApi.v1AdviceAgreementsShow(
                String.valueOf(agreementId), null);
        assertNotNull(response);
        assertEquals(agreementId, response.getData().getId());
        assertEquals("draft", response.getData().getStatus().getValue());
        assertEquals("1.0", response.getData().getVersion());
    }

    // ─── Plugin prepares the signing envelope ───────────────────────

    @Test
    @Order(ENVELOPE + 1)
    void uploadSourceDocument() throws Exception {
        Path tempFile = Files.createTempFile("gen-s7-agreement-", ".txt");
        Files.writeString(tempFile, "Hello World - Gen S7 Advisory Agreement");

        try {
            HttpResponse<String> response = apiPostMultipart(token, "/api/v1/documents",
                    tempFile, "file", Map.of("type", "advisory_agreement"));

            assertTrue(response.statusCode() < 300,
                    "Document upload should succeed, got: " + response.statusCode() + " " + response.body());

            JsonNode data = objectMapper.readTree(response.body()).path("data");
            documentId = data.get("id").asLong();
            assertTrue(documentId > 0, "Document ID should be positive");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(ENVELOPE + 2)
    void createSigningEnvelope() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        // v1: the envelope attaches to a polymorphic signable. For an advisory
        // agreement that is signable_type=advisory_agreement + signable_id.
        var req = new StoreSigningEnvelopeRequest()
                .title("Gen-S7 Agreement Envelope")
                .signableType(StoreSigningEnvelopeRequest.SignableTypeEnum.ADVISORY_AGREEMENT)
                .signableId(agreementId);

        var response = adviceApi.signingEnvelopesStore(req, idempotencyKey());
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

        // ceremony_role=input marks the document that will be signed.
        var req = new AddDocumentToEnvelopeRequest()
                .documentId(documentId)
                .ceremonyRole(AddDocumentToEnvelopeRequest.CeremonyRoleEnum.INPUT);

        adviceApi.signingEnvelopesDocumentsStore(
                String.valueOf(envelopeId), req, idempotencyKey());
    }

    @Test
    @Order(ENVELOPE + 4)
    void addSignerParty() throws ApiException {
        // Uses the signer person resolved from the agreement's member_snapshot.
        // In production, the plugin would iterate all snapshot members and add
        // each person as a signer party.
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(signerPersonId > 0, "Signer person_id must be resolved first");

        var req = new AddPartyToEnvelopeRequest()
                .personId(signerPersonId)
                .role(AddPartyToEnvelopeRequest.RoleEnum.SIGNER);

        adviceApi.signingEnvelopesPartiesStore(
                String.valueOf(envelopeId), req, idempotencyKey());
    }

    @Test
    @Order(ENVELOPE + 5)
    void sendEnvelope() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");

        adviceApi.signingEnvelopeActionSend(
                String.valueOf(envelopeId), idempotencyKey());
    }

    // ─── Walk the agreement through signing ─────────────────────────

    @Test
    @Order(AGREEMENT + 1)
    void submitSigning() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        // v1: submit-signing has no body; idempotency travels via the header.
        adviceApi.advisoryAgreementActionSubmitSigning(
                String.valueOf(agreementId), idempotencyKey());
    }

    // ─── Signing ceremony ───────────────────────────────────────────
    // Order matters: upload signed doc → add to envelope → mark party.
    // Marking the last party auto-completes the envelope, which locks
    // it — no documents can be added after that.

    @Test
    @Order(AGREEMENT + 2)
    void uploadSignedDocument() throws Exception {
        Path tempFile = Files.createTempFile("gen-s7-signed-", ".txt");
        Files.writeString(tempFile, "Signed Advisory Agreement - Gen S7");

        try {
            HttpResponse<String> response = apiPostMultipart(token, "/api/v1/documents",
                    tempFile, "file", Map.of("type", "advisory_agreement"));

            assertTrue(response.statusCode() < 300,
                    "Signed document upload should succeed, got: " + response.statusCode() + " " + response.body());

            signedDocId = objectMapper.readTree(response.body()).path("data").get("id").asLong();
            assertTrue(signedDocId > 0, "Signed document ID should be positive");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(AGREEMENT + 3)
    void addSignedDocumentToEnvelope() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(signedDocId > 0, "Signed document must be uploaded first");

        // ceremony_role=output marks the resulting signed document.
        var req = new AddDocumentToEnvelopeRequest()
                .documentId(signedDocId)
                .ceremonyRole(AddDocumentToEnvelopeRequest.CeremonyRoleEnum.OUTPUT);

        adviceApi.signingEnvelopesDocumentsStore(
                String.valueOf(envelopeId), req, idempotencyKey());
    }

    @Test
    @Order(AGREEMENT + 4)
    void markSignerPartySigned() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");

        var envelope = adviceApi.signingEnvelopesShow(
                String.valueOf(envelopeId));
        assertNotNull(envelope.getData().getParties());
        assertFalse(envelope.getData().getParties().isEmpty(),
                "Envelope should have at least one party");
        long partyId = envelope.getData().getParties().get(0).getId();

        adviceApi.signingEnvelopePartyActionMarkSigned(
                String.valueOf(envelopeId), String.valueOf(partyId), idempotencyKey());
    }

    @Test
    @Order(AGREEMENT + 5)
    void markAgreementSigned() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");
        assertTrue(signedDocId > 0, "Signed document must be uploaded first");

        // v1: mark-signed optionally accepts signed_document_id (stored in
        // metadata for audit) and transitions pending_signature → signed.
        var req = new MarkSignedAdvisoryAgreementRequest()
                .signedDocumentId(signedDocId);

        adviceApi.advisoryAgreementActionMarkSigned(
                String.valueOf(agreementId), idempotencyKey(), req);
    }

    @Test
    @Order(VERIFY + 1)
    void readAgreementFinal() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        var response = adviceApi.v1AdviceAgreementsShow(
                String.valueOf(agreementId), null);
        assertNotNull(response);
        assertEquals("signed", response.getData().getStatus().getValue(),
                "Agreement should be in signed status");
    }

    // ─── Teardown: expire agreement → delete agreement → delete context → delete client/person ──

    @Test
    @Order(TEARDOWN + 1)
    void expireAgreement() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        // expire takes no body — use the 2-arg overload (passing null would
        // bind to the additionalHeaders Map overload and NPE).
        adviceApi.advisoryAgreementActionExpire(
                String.valueOf(agreementId), idempotencyKey());
    }

    @Test
    @Order(TEARDOWN + 2)
    void deleteAgreement() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        adviceApi.v1AdviceAgreementsDestroy(String.valueOf(agreementId));
        agreementId = 0;
    }

    @Test
    @Order(TEARDOWN + 3)
    void deleteAdviceContext() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        adviceApi.adviceContextsDestroy(String.valueOf(adviceContextId));
        adviceContextId = 0;
    }

    @Test
    @Order(TEARDOWN + 4)
    void deleteClient() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");
        clientsApi.clientsDestroy(String.valueOf(clientId));
        clientId = 0;
    }

    @Test
    @Order(TEARDOWN + 5)
    void deletePerson() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");
        try {
            personsApi.personsDestroy(String.valueOf(personId));
        } catch (ApiException e) {
            // Person may already be cascade-deleted with the client
            if (e.getCode() != 404) throw e;
        }
        personId = 0;
    }

    @AfterAll
    static void teardown() {
        runCleanup();
    }
}
