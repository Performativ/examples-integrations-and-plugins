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
 * S8: Start Advise — full advice session lifecycle using generated client.
 *
 * <p>Creates prerequisites (Person → Client → Portfolio), opens an advice
 * context, creates an advisory agreement, attaches and completes a signing
 * envelope, then starts an advice session and walks it through:
 * created → data_ready → active → ready_to_sign → signed.
 *
 * <p>v1 signing model: the advisory agreement is created first, then the
 * signing envelope is attached to it via {@code signable_type=advisory_agreement}
 * + {@code signable_id}. Documents carry a {@code ceremony_role}
 * ({@code input} = to-be-signed, {@code output} = signed result).
 *
 * <p>Strict: no raw HTTP fallbacks for API operations. Cleanup uses raw HTTP.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StartAdviseScenario extends GeneratedClientScenario {

    private static String token;
    private static ApiClient apiClient;
    private static AdviceApi adviceApi;
    private static PersonsApi personsApi;
    private static ClientsApi clientsApi;
    private static PortfoliosApi portfoliosApi;

    private static long advicePolicyId;
    private static long personId;
    private static long clientId;
    private static long portfolioId;
    private static long documentId;
    private static long envelopeId;
    private static long adviceContextId;
    private static long agreementId;
    private static long signedDocId;
    private static long sessionId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();

        apiClient = createApiClient(token);
        adviceApi = new AdviceApi(apiClient);
        personsApi = new PersonsApi(apiClient);
        clientsApi = new ClientsApi(apiClient);
        portfoliosApi = new PortfoliosApi(apiClient);
    }

    // -- Prerequisites --------------------------------------------------------

    @Test
    @Order(SETUP + 1)
    void listAdvicePolicies() throws ApiException {
        var response = adviceApi.advicePoliciesIndex(null, null, null, null);
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
                .lastName("S8-StartAdvise")
                .email("gen-s8@example.com")
                .languageCode("en");

        var response = personsApi.personsStore(req, idempotencyKey());
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
                .name("Gen-S8 Client")
                .type(StoreClientRequest.TypeEnum.INDIVIDUAL)
                .isActive(true)
                .currencyId(47L);

        var response = clientsApi.clientsStore(req, idempotencyKey());
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

        clientsApi.clientPersonsStore(req, idempotencyKey());
    }

    @Test
    @Order(SETUP + 5)
    void createPortfolio() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");

        var req = new StorePortfolioRequest()
                .name("Gen-S8 Portfolio")
                .addClientIdsItem(clientId)
                .currencyId(47L);

        var response = portfoliosApi.portfoliosStore(req, idempotencyKey());
        assertNotNull(response);
        assertNotNull(response.getData());

        portfolioId = response.getData().getId();
        assertTrue(portfolioId > 0);
        registerCleanup(token, "/api/v1/portfolios/" + portfolioId);
    }

    // -- Advice Context + Advisory Agreement (prerequisites for the session) --
    // v1: the agreement is created before the signing envelope, since the
    // envelope references the agreement as its signable.

    @Test
    @Order(SETUP + 6)
    void createAdviceContext() throws ApiException {
        assertTrue(advicePolicyId > 0, "Advice policy must be found first");
        assertTrue(clientId > 0, "Client must be created first");

        // Advice contexts are client-centric: members carry client_id only.
        var req = new StoreAdviceContextRequest()
                .advicePolicyId(advicePolicyId)
                .type(StoreAdviceContextRequest.TypeEnum.INDIVIDUAL)
                .name("Gen-S8 Advice Context")
                .referenceClientId(clientId)
                .members(List.of(new StoreAdviceContextRequestMembersInner().clientId((int) clientId)));

        var response = adviceApi.adviceContextsStore(req, idempotencyKey());
        assertNotNull(response.getData());
        adviceContextId = response.getData().getId();
        assertTrue(adviceContextId > 0);
        assertNotNull(response.getData().getStatus(), "Created context should carry a status");
    }

    @Test
    @Order(SETUP + 7)
    void createAdvisoryAgreement() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        var req = new StoreAdvisoryAgreementRequest()
                .version("1.0")
                .externalReference("gen-s8-agreement");

        var response = adviceApi.v1AdviceAgreementsStoreForContext(
                String.valueOf(adviceContextId), idempotencyKey(), req);
        assertNotNull(response);
        assertNotNull(response.getData());

        agreementId = response.getData().getId();
        assertTrue(agreementId > 0);
        assertEquals("draft", response.getData().getStatus().getValue());
    }

    // -- Document upload and signing envelope (attached to the agreement) -----

    @Test
    @Order(ENVELOPE + 1)
    void uploadDocument() throws Exception {
        // Multipart upload uses raw HTTP — generated clients often lack clean
        // multipart support, and this keeps the upload pattern consistent.
        Path tempFile = Files.createTempFile("gen-s8-agreement-", ".txt");
        Files.writeString(tempFile, "Hello World - Gen S8 Advisory Agreement");

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

        // v1: the envelope attaches to a polymorphic signable —
        // signable_type=advisory_agreement + signable_id.
        var req = new StoreSigningEnvelopeRequest()
                .title("Gen-S8 Agreement Envelope")
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
    void addSignerPartyToEnvelope() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(personId > 0, "Person must be created first");

        var req = new AddPartyToEnvelopeRequest()
                .personId(personId)
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

    // -- Walk the agreement through signing -----------------------------------

    @Test
    @Order(AGREEMENT + 1)
    void submitSigning() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        // v1: submit-signing has no body; idempotency travels via the header.
        adviceApi.advisoryAgreementActionSubmitSigning(
                String.valueOf(agreementId), idempotencyKey());
    }

    @Test
    @Order(AGREEMENT + 2)
    void uploadSignedDocumentAndAddToEnvelope() throws Exception {
        assertTrue(envelopeId > 0, "Envelope must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        // Upload a separate signed document
        var tempFile = Files.createTempFile("gen-s8-signed-", ".txt");
        Files.writeString(tempFile, "Signed Advisory Agreement - Gen S8");
        try {
            HttpResponse<String> docResp = apiPostMultipart(token, "/api/v1/documents",
                    tempFile, "file", Map.of("type", "advisory_agreement"));
            signedDocId = objectMapper.readTree(docResp.body()).path("data").get("id").asLong();
            assertTrue(signedDocId > 0, "Signed document ID should be positive");
        } finally {
            Files.deleteIfExists(tempFile);
        }

        // Add signed document to envelope BEFORE marking the party signed —
        // marking all parties signed completes the envelope, after which
        // documents can no longer be added. ceremony_role=output marks the
        // resulting signed document.
        var req = new AddDocumentToEnvelopeRequest()
                .documentId(signedDocId)
                .ceremonyRole(AddDocumentToEnvelopeRequest.CeremonyRoleEnum.OUTPUT);

        adviceApi.signingEnvelopesDocumentsStore(
                String.valueOf(envelopeId), req, idempotencyKey());
    }

    @Test
    @Order(AGREEMENT + 3)
    void markSignerPartySigned() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");

        // Get party ID from the envelope
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
    @Order(AGREEMENT + 4)
    void markAgreementSigned() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");
        assertTrue(signedDocId > 0, "Signed document must be uploaded first");

        var req = new MarkSignedAdvisoryAgreementRequest()
                .signedDocumentId(signedDocId);

        adviceApi.advisoryAgreementActionMarkSigned(
                String.valueOf(agreementId), idempotencyKey(), req);
    }

    // -- Advice Session lifecycle ---------------------------------------------

    @Test
    @Order(SESSION + 1)
    void createAdviceSession() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        var req = new StoreAdviceSessionRequest()
                .externalReference("gen-s8-session");

        var response = adviceApi.v1AdviceSessionsStoreForContext(
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

        var response = adviceApi.v1AdviceSessionsShow(
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

        var response = adviceApi.adviceSessionActionMarkDataReady(
                String.valueOf(sessionId), idempotencyKey(), req);
        assertNotNull(response);
        assertEquals("data_ready", response.getData().getStatus().getValue(),
                "Session should transition to 'data_ready'");
    }

    @Test
    @Order(SESSION + 4)
    void activateSession() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        // v1: activate no longer takes a redirect_url in the request body; the
        // advisor-UI redirect is returned by the API on the activated session.
        var req = new ActivateAdviceSessionRequest();

        var response = adviceApi.adviceSessionActionActivate(
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

        var response = adviceApi.adviceSessionActionMarkReadyToSign(
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

        var response = adviceApi.adviceSessionActionMarkSigned(
                String.valueOf(sessionId), idempotencyKey(), req);
        assertNotNull(response);
        assertEquals("signed", response.getData().getStatus().getValue(),
                "Session should transition to 'signed'");
    }

    @Test
    @Order(VERIFY + 1)
    void readSessionFinal() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        var response = adviceApi.v1AdviceSessionsShow(
                String.valueOf(sessionId), null);
        assertNotNull(response);
        assertEquals("signed", response.getData().getStatus().getValue(),
                "Final session status should be 'signed'");
    }

    // -- Teardown: delete session → expire+delete agreement → delete context → entities --

    @Test
    @Order(TEARDOWN + 1)
    void deleteSession() throws ApiException {
        assertTrue(sessionId > 0, "Session must be created first");

        adviceApi.v1AdviceSessionsDestroy(String.valueOf(sessionId));
        sessionId = 0;
    }

    @Test
    @Order(TEARDOWN + 2)
    void expireAgreement() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        // expire takes no body — use the 2-arg overload (passing null would
        // bind to the additionalHeaders Map overload and NPE).
        adviceApi.advisoryAgreementActionExpire(
                String.valueOf(agreementId), idempotencyKey());
    }

    @Test
    @Order(TEARDOWN + 3)
    void deleteAgreement() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        adviceApi.v1AdviceAgreementsDestroy(String.valueOf(agreementId));
        agreementId = 0;
    }

    @Test
    @Order(TEARDOWN + 4)
    void deleteAdviceContext() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        adviceApi.adviceContextsDestroy(String.valueOf(adviceContextId));
        adviceContextId = 0;
    }

    @Test
    @Order(TEARDOWN + 5)
    void deletePortfolio() throws ApiException {
        assertTrue(portfolioId > 0, "Portfolio must be created first");
        portfoliosApi.portfoliosDestroy(String.valueOf(portfolioId));
        portfolioId = 0;
    }

    @Test
    @Order(TEARDOWN + 6)
    void deleteClient() throws ApiException {
        assertTrue(clientId > 0, "Client must be created first");
        clientsApi.clientsDestroy(String.valueOf(clientId));
        clientId = 0;
    }

    @Test
    @Order(TEARDOWN + 7)
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
