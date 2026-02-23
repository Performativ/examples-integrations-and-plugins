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
    private static AdvicePolicyApi advicePolicyApi;
    private static PersonApi personApi;
    private static ClientApi clientApi;
    private static ClientPersonApi clientPersonApi;
    private static PortfolioApi portfolioApi;
    private static AdviceContextApi adviceContextApi;
    private static AdvisoryAgreementApi advisoryAgreementApi;
    private static SigningEnvelopeApi signingEnvelopeApi;
    private static SigningEnvelopeDocumentApi signingEnvelopeDocumentApi;
    private static SigningEnvelopePartyApi signingEnvelopePartyApi;
    private static SigningEnvelopeActionApi signingEnvelopeActionApi;
    private static AdvisoryAgreementActionApi advisoryAgreementActionApi;
    private static AdviceSessionApi adviceSessionApi;
    private static AdviceSessionActionApi adviceSessionActionApi;
    private static AdviceContextActionApi adviceContextActionApi;

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

        apiClient = createApiClient(token);
        advicePolicyApi = new AdvicePolicyApi(apiClient);
        personApi = new PersonApi(apiClient);
        clientApi = new ClientApi(apiClient);
        clientPersonApi = new ClientPersonApi(apiClient);
        portfolioApi = new PortfolioApi(apiClient);
        signingEnvelopeApi = new SigningEnvelopeApi(apiClient);
        signingEnvelopeDocumentApi = new SigningEnvelopeDocumentApi(apiClient);
        signingEnvelopePartyApi = new SigningEnvelopePartyApi(apiClient);
        signingEnvelopeActionApi = new SigningEnvelopeActionApi(apiClient);
        adviceContextApi = new AdviceContextApi(apiClient);
        advisoryAgreementApi = new AdvisoryAgreementApi(apiClient);
        advisoryAgreementActionApi = new AdvisoryAgreementActionApi(apiClient);
        adviceSessionApi = new AdviceSessionApi(apiClient);
        adviceSessionActionApi = new AdviceSessionActionApi(apiClient);
        adviceContextActionApi = new AdviceContextActionApi(apiClient);
    }

    // -- Prerequisites --------------------------------------------------------

    @Test
    @Order(1)
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
    @Order(2)
    void createPerson() throws ApiException {
        var req = new StorePersonRequest()
                .firstName("Gen")
                .lastName("S7-StartAdvise")
                .email("gen-s7@example.com")
                .languageCode("en");

        var response = personApi.personsStore(req);
        assertNotNull(response);
        assertNotNull(response.getData());

        personId = response.getData().getId();
        assertTrue(personId > 0);
    }

    @Test
    @Order(3)
    void createClient() throws ApiException {
        var req = new StoreClientRequest()
                .name("Gen-S7 Client")
                .type("individual")
                .isActive(true)
                .currencyId(47);

        var response = clientApi.clientsStore(req);
        assertNotNull(response);
        assertNotNull(response.getData());

        clientId = response.getData().getId();
        assertTrue(clientId > 0);
    }

    @Test
    @Order(4)
    void linkPersonToClient() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");
        assertTrue(clientId > 0, "Client must be created first");

        var req = new StoreClientPersonRequest()
                .clientId(clientId)
                .personId(personId)
                .isPrimary(true);

        clientPersonApi.clientPersonsStore(req);
    }

    @Test
    @Order(5)
    void createPortfolio() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");

        var req = new StorePortfolioRequest()
                .name("Gen-S7 Portfolio")
                .clientId(clientId)
                .currencyId(47);

        var response = portfolioApi.portfoliosStore(req);
        assertNotNull(response);
        assertNotNull(response.getData());

        portfolioId = response.getData().getId();
        assertTrue(portfolioId > 0);
    }

    // -- Document upload and signing envelope (required for signed agreement) --

    @Test
    @Order(6)
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
    @Order(7)
    void createSigningEnvelope() throws ApiException {
        var req = new StoreSigningEnvelopeRequest()
                .title("Gen-S7 Agreement Envelope");

        var response = signingEnvelopeApi.signingEnvelopesStore(req);
        assertNotNull(response);
        assertNotNull(response.getData());

        envelopeId = response.getData().getId();
        assertTrue(envelopeId > 0, "Envelope ID should be positive");
    }

    @Test
    @Order(8)
    void addDocumentToEnvelope() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        var req = new AddDocumentToEnvelopeRequest()
                .documentId(documentId)
                .role(AddDocumentToEnvelopeRequest.RoleEnum.SOURCE);

        signingEnvelopeDocumentApi.signingEnvelopesDocumentsStore(
                String.valueOf(envelopeId), req);
    }

    @Test
    @Order(9)
    void addSignerPartyToEnvelope() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(personId > 0, "Person must be created first");

        var req = new AddPartyToEnvelopeRequest()
                .personId(personId)
                .role(AddPartyToEnvelopeRequest.RoleEnum.SIGNER);

        signingEnvelopePartyApi.signingEnvelopesPartiesStore(
                String.valueOf(envelopeId), req);
    }

    @Test
    @Order(10)
    void sendEnvelope() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");

        signingEnvelopeActionApi.signingEnvelopeActionSend(
                String.valueOf(envelopeId), String.valueOf(envelopeId));
    }

    // -- Advice Context -------------------------------------------------------

    @Test
    @Order(11)
    void createAdviceContext() throws ApiException {
        assertTrue(advicePolicyId > 0, "Advice policy must be found first");
        assertTrue(personId > 0, "Person must be created first");

        var member = new StoreAdviceContextRequestMembersInner()
                .personId(personId)
                .powerOfAttorney(false);

        var req = new StoreAdviceContextRequest()
                .advicePolicyId(advicePolicyId)
                .type(StoreAdviceContextRequest.TypeEnum.INDIVIDUAL)
                .name("Gen-S7 Advice Context")
                .referencePersonId(personId)
                .members(List.of(member));

        var response = adviceContextApi.adviceContextsStore(req);
        assertNotNull(response);
        assertNotNull(response.getData());

        adviceContextId = response.getData().getId();
        assertTrue(adviceContextId > 0);
        assertEquals("active", response.getData().getStatus().getValue());
    }

    // -- Advisory Agreement (linked to signing envelope) ----------------------

    @Test
    @Order(12)
    void createAdvisoryAgreement() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");
        assertTrue(envelopeId > 0, "Signing envelope must be created first");

        var req = new StoreAdvisoryAgreementRequest()
                .version("1.0")
                .signingEnvelopeId(envelopeId)
                .externalReference("gen-s7-agreement");

        var response = advisoryAgreementApi.advisoryAgreementStoreForContext(
                String.valueOf(adviceContextId), req);
        assertNotNull(response);
        assertNotNull(response.getData());

        agreementId = response.getData().getId();
        assertTrue(agreementId > 0);
        assertEquals("draft", response.getData().getStatus().getValue());
    }

    @Test
    @Order(13)
    void submitSigning() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        var req = new SubmitSigningAdvisoryAgreementRequest()
                .idempotencyKey("gen-s7-submit-" + System.currentTimeMillis())
                .documentId(documentId);

        advisoryAgreementActionApi.advisoryAgreementActionSubmitSigning(
                String.valueOf(agreementId), req);
    }

    @Test
    @Order(14)
    void markAgreementSigned() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        var req = new MarkSignedAdvisoryAgreementRequest()
                .idempotencyKey("gen-s7-signed-" + System.currentTimeMillis())
                .signedDocumentId(documentId);

        advisoryAgreementActionApi.advisoryAgreementActionMarkSigned(
                String.valueOf(agreementId), req);
    }

    // -- Advice Session lifecycle ---------------------------------------------

    @Test
    @Order(15)
    void createAdviceSession() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        var req = new StoreAdviceSessionRequest()
                .externalSessionId("gen-s7-session")
                .externalReference("gen-s7");

        var response = adviceSessionApi.adviceSessionStoreForContext(
                String.valueOf(adviceContextId), req);
        assertNotNull(response);
        assertNotNull(response.getData());

        sessionId = response.getData().getId();
        assertTrue(sessionId > 0);
        assertEquals("created", response.getData().getStatus().getValue(),
                "New session should be in 'created' status");
    }

    @Test
    @Order(16)
    void readAdviceSession() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        var response = adviceSessionApi.adviceSessionShow(
                String.valueOf(sessionId));
        assertNotNull(response);
        assertEquals(sessionId, response.getData().getId());
        assertEquals("created", response.getData().getStatus().getValue());
    }

    @Test
    @Order(17)
    void markDataReady() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        var req = new DataReadyAdviceSessionRequest()
                .idempotencyKey("gen-s7-data-ready-" + System.currentTimeMillis());

        var response = adviceSessionActionApi.adviceSessionActionDataReady(
                String.valueOf(sessionId), req);
        assertNotNull(response);
        assertEquals("data_ready", response.getData().getStatus().getValue(),
                "Session should transition to 'data_ready'");
    }

    @Test
    @Order(18)
    void activateSession() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        var req = new ActivateAdviceSessionRequest()
                .idempotencyKey("gen-s7-activate-" + System.currentTimeMillis())
                .redirectUrl("https://example.com/advisor-ui/session");

        var response = adviceSessionActionApi.adviceSessionActionActivate(
                String.valueOf(sessionId), req);
        assertNotNull(response);
        assertEquals("active", response.getData().getStatus().getValue(),
                "Session should transition to 'active'");
    }

    @Test
    @Order(19)
    void markReadyToSign() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        var req = new ReadyToSignAdviceSessionRequest()
                .idempotencyKey("gen-s7-ready-to-sign-" + System.currentTimeMillis());

        var response = adviceSessionActionApi.adviceSessionActionReadyToSign(
                String.valueOf(sessionId), req);
        assertNotNull(response);
        assertEquals("ready_to_sign", response.getData().getStatus().getValue(),
                "Session should transition to 'ready_to_sign'");
    }

    @Test
    @Order(20)
    void markSessionSigned() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        var req = new SignedAdviceSessionRequest()
                .idempotencyKey("gen-s7-signed-" + System.currentTimeMillis());

        var response = adviceSessionActionApi.adviceSessionActionSigned(
                String.valueOf(sessionId), req);
        assertNotNull(response);
        assertEquals("signed", response.getData().getStatus().getValue(),
                "Session should transition to 'signed'");
    }

    @Test
    @Order(21)
    void readSessionFinal() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        var response = adviceSessionApi.adviceSessionShow(
                String.valueOf(sessionId));
        assertNotNull(response);
        assertEquals("signed", response.getData().getStatus().getValue(),
                "Final session status should be 'signed'");
    }

    @Test
    @Order(22)
    void closeAdviceContext() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        var req = new CloseAdviceContextRequest()
                .idempotencyKey("gen-s7-close-" + System.currentTimeMillis())
                .reason("Scenario complete");

        adviceContextActionApi.adviceContextActionClose(
                String.valueOf(adviceContextId), req);
    }

    // -- Teardown --------------------------------------------------------------
    // Portfolio can be deleted normally. Client and Person cannot be deleted
    // while referenced by the advice context (FK constraint), so their cleanup
    // is best-effort via @AfterAll.

    @Test
    @Order(23)
    void deletePortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        portfolioApi.portfoliosDestroy(String.valueOf(portfolioId), String.valueOf(portfolioId));
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
