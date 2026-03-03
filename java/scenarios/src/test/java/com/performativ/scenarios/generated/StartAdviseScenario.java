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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S7: Start Advise — full advice session lifecycle using generated client.
 *
 * <p>Creates prerequisites (Person → Client → Portfolio), opens an advice
 * context, creates an advisory agreement, starts an advice session, and
 * walks it through: created → data_ready → active → ready_to_sign → signed.
 *
 * <p>Strict: no raw HTTP fallbacks for API operations. Cleanup uses raw HTTP.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StartAdviseScenario extends GeneratedClientScenario {

    private static String token;
    private static ApiClient apiClient;
    private static AdvicePoliciesApi advicePolicyApi;
    private static PersonApi personApi;
    private static ClientApi clientApi;
    private static ClientPersonApi clientPersonApi;
    private static PortfolioApi portfolioApi;
    private static AdviceContextsApi adviceContextApi;
    private static AdvisoryAgreementsApi advisoryAgreementApi;
    private static SigningEnvelopesApi signingEnvelopeApi;
    private static AdviceSessionsApi adviceSessionApi;

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

        apiClient = createApiClient(token);
        advicePolicyApi = new AdvicePoliciesApi(apiClient);
        personApi = new PersonApi(apiClient);
        clientApi = new ClientApi(apiClient);
        clientPersonApi = new ClientPersonApi(apiClient);
        portfolioApi = new PortfolioApi(apiClient);
        signingEnvelopeApi = new SigningEnvelopesApi(apiClient);
        adviceContextApi = new AdviceContextsApi(apiClient);
        advisoryAgreementApi = new AdvisoryAgreementsApi(apiClient);
        adviceSessionApi = new AdviceSessionsApi(apiClient);
    }

    // -- Prerequisites --------------------------------------------------------

    @Test
    @Order(SETUP + 1)
    void listAdvicePolicies() throws ApiException {
        var response = advicePolicyApi.advicePoliciesIndex(null, null, null, null);
        assertNotNull(response);
        assertNotNull(response.getData());
        assertFalse(response.getData().isEmpty(),
                "At least one advice policy must be configured on the tenant");

        advicePolicyId = response.getData().get(0).getId();
        assertTrue(advicePolicyId > 0);
    }

    @Test
    @Order(SETUP + 2)
    void createPerson() throws ApiException {
        var req = new StorePersonRequest()
                .firstName("Gen")
                .lastName("S7-StartAdvise")
                .email("gen-s7@example.com")
                .languageCode("en");

        var response = personApi.personsStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        personId = response.getData().getId();
        assertTrue(personId > 0);
        registerCleanup(token, "/api/v1/persons/" + personId);
    }

    @Test
    @Order(SETUP + 3)
    void createClient() throws ApiException {
        var req = new StoreClientRequest()
                .name("Gen-S7 Client")
                .type(StoreClientRequest.TypeEnum.INDIVIDUAL)
                .isActive(true)
                .currencyId(47);

        var response = clientApi.clientsStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        clientId = response.getData().getId();
        assertTrue(clientId > 0);
        registerCleanup(token, "/api/v1/clients/" + clientId);
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

    @Test
    @Order(SETUP + 5)
    void createPortfolio() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");

        var req = new StorePortfolioRequest()
                .name("Gen-S7 Portfolio")
                .clientId(clientId)
                .currencyId(47);

        var response = portfolioApi.portfoliosStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        portfolioId = response.getData().getId();
        assertTrue(portfolioId > 0);
        registerCleanup(token, "/api/v1/portfolios/" + portfolioId);
    }

    // -- Document upload and signing envelope (required for signed agreement) --

    @Test
    @Order(ENVELOPE + 1)
    void uploadDocument() throws Exception {
        // Multipart upload uses raw HTTP — generated clients often lack clean
        // multipart support, and this keeps the upload pattern consistent.
        Path tempFile = Files.createTempFile("gen-s7-agreement-", ".txt");
        Files.writeString(tempFile, "Hello World - Gen S7 Advisory Agreement");

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
                .title("Gen-S7 Agreement Envelope");

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
    void addSignerPartyToEnvelope() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(personId > 0, "Person must be created first");

        var req = new AddPartyToEnvelopeRequest()
                .personId(personId)
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

    // -- Advice Context -------------------------------------------------------

    @Test
    @Order(AGREEMENT + 1)
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
        adviceContextId = data.get("id").asInt();
        assertTrue(adviceContextId > 0);
        assertEquals("active", data.get("status").asText());
    }

    // -- Advisory Agreement (linked to signing envelope) ----------------------

    @Test
    @Order(AGREEMENT + 2)
    void createAdvisoryAgreement() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");
        assertTrue(envelopeId > 0, "Signing envelope must be created first");

        var req = new StoreAdvisoryAgreementRequest()
                .version("1.0")
                .signingEnvelopeId(envelopeId)
                .externalReference("gen-s7-agreement");

        var response = advisoryAgreementApi.v1AdviceAgreementsStoreForContext(
                String.valueOf(adviceContextId), idempotencyKey(), req);
        assertNotNull(response);
        assertNotNull(response.getData());

        agreementId = response.getData().getId();
        assertTrue(agreementId > 0);
        assertEquals("draft", response.getData().getStatus().getValue());
    }

    @Test
    @Order(AGREEMENT + 3)
    void submitSigning() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        // In v1, idempotency moved from body to Idempotency-Key header.
        // submit-signing body no longer has document_id — only signing_envelope_id (optional).
        var req = new SubmitSigningAdvisoryAgreementRequest();

        advisoryAgreementApi.advisoryAgreementActionSubmitSigning(
                String.valueOf(agreementId), idempotencyKey(), req);
    }

    @Test
    @Order(AGREEMENT + 4)
    void uploadSignedDocumentAndAddToEnvelope() throws Exception {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        // Upload a separate signed document
        var tempFile = Files.createTempFile("gen-s7-signed-", ".txt");
        Files.writeString(tempFile, "Signed Advisory Agreement - Gen S7");
        try {
            HttpResponse<String> docResp = apiPostMultipart(token, "/api/v1/documents",
                    tempFile, "file", Map.of("type", "advisory_agreement"));
            signedDocId = objectMapper.readTree(docResp.body()).path("data").get("id").asInt();
            assertTrue(signedDocId > 0, "Signed document ID should be positive");
        } finally {
            Files.deleteIfExists(tempFile);
        }

        // Add signed document to envelope BEFORE marking the party signed —
        // marking all parties signed completes the envelope, after which
        // documents can no longer be added.
        var req = new AddDocumentToEnvelopeRequest()
                .documentId(signedDocId)
                .role(AddDocumentToEnvelopeRequest.RoleEnum.SIGNED);

        signingEnvelopeApi.signingEnvelopesDocumentsStore(
                String.valueOf(envelopeId), req, idempotencyKey());
    }

    @Test
    @Order(AGREEMENT + 5)
    void markSignerPartySigned() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");

        // Get party ID from the envelope
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
    @Order(AGREEMENT + 6)
    void markAgreementSigned() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");
        assertTrue(signedDocId > 0, "Signed document must be uploaded first");

        var req = new MarkSignedAdvisoryAgreementRequest()
                .signedDocumentId(signedDocId);

        advisoryAgreementApi.advisoryAgreementActionMarkSigned(
                String.valueOf(agreementId), idempotencyKey(), req);
    }

    // -- Advice Session lifecycle ---------------------------------------------

    @Test
    @Order(SESSION + 1)
    void createAdviceSession() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        var req = new StoreAdviceSessionRequest()
                .externalSessionId("gen-s7-session")
                .externalReference("gen-s7");

        var response = adviceSessionApi.v1AdviceSessionsStoreForContext(
                String.valueOf(adviceContextId), idempotencyKey(), req);
        assertNotNull(response);
        assertNotNull(response.getData());

        sessionId = response.getData().getId();
        assertTrue(sessionId > 0);
        assertEquals("created", response.getData().getStatus().getValue(),
                "New session should be in 'created' status");
    }

    @Test
    @Order(SESSION + 2)
    void readAdviceSession() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        var response = adviceSessionApi.v1AdviceSessionsShow(
                String.valueOf(sessionId), null);
        assertNotNull(response);
        assertEquals(sessionId, response.getData().getId());
        assertEquals("created", response.getData().getStatus().getValue());
    }

    @Test
    @Order(SESSION + 3)
    void markDataReady() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        var req = new MarkDataReadyAdviceSessionRequest();

        var response = adviceSessionApi.adviceSessionActionMarkDataReady(
                String.valueOf(sessionId), idempotencyKey(), req);
        assertNotNull(response);
        assertEquals("data_ready", response.getData().getStatus().getValue(),
                "Session should transition to 'data_ready'");
    }

    @Test
    @Order(SESSION + 4)
    void activateSession() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        var req = new ActivateAdviceSessionRequest()
                .redirectUrl("https://example.com/advisor-ui/session");

        var response = adviceSessionApi.adviceSessionActionActivate(
                String.valueOf(sessionId), idempotencyKey(), req);
        assertNotNull(response);
        assertEquals("active", response.getData().getStatus().getValue(),
                "Session should transition to 'active'");
    }

    @Test
    @Order(SESSION + 5)
    void markReadyToSign() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        var req = new MarkReadyToSignAdviceSessionRequest();

        var response = adviceSessionApi.adviceSessionActionMarkReadyToSign(
                String.valueOf(sessionId), idempotencyKey(), req);
        assertNotNull(response);
        assertEquals("ready_to_sign", response.getData().getStatus().getValue(),
                "Session should transition to 'ready_to_sign'");
    }

    @Test
    @Order(SESSION + 6)
    void markSessionSigned() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        var req = new MarkSignedAdviceSessionRequest();

        var response = adviceSessionApi.adviceSessionActionMarkSigned(
                String.valueOf(sessionId), idempotencyKey(), req);
        assertNotNull(response);
        assertEquals("signed", response.getData().getStatus().getValue(),
                "Session should transition to 'signed'");
    }

    @Test
    @Order(VERIFY + 1)
    void readSessionFinal() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        var response = adviceSessionApi.v1AdviceSessionsShow(
                String.valueOf(sessionId), null);
        assertNotNull(response);
        assertEquals("signed", response.getData().getStatus().getValue(),
                "Final session status should be 'signed'");
    }

    @Test
    @Order(TEARDOWN + 1)
    void closeAdviceContext() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        var req = new CloseAdviceContextRequest()
                .reason("Scenario complete");

        adviceContextApi.adviceContextActionClose(
                String.valueOf(adviceContextId), idempotencyKey(), req);
    }

    // -- Teardown --------------------------------------------------------------
    // Portfolio can be deleted normally. Client and Person cannot be deleted
    // while referenced by the advice context (FK constraint), so their cleanup
    // is best-effort via @AfterAll.

    @Test
    @Order(TEARDOWN + 2)
    void deletePortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        portfolioApi.portfoliosDestroy(String.valueOf(portfolioId));
        portfolioId = 0;
    }

    @AfterAll
    static void teardown() {
        runCleanup();
    }
}
