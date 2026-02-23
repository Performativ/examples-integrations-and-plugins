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
 * S6: Advisory Agreement — full signing envelope journey using the generated client.
 *
 * <p>Flow: upload document (multipart via raw HTTP) → create signing envelope →
 * add document and signer party → send envelope → create advice context →
 * create advisory agreement linked to envelope → submit-signing →
 * mark-signed → close context → clean up.
 *
 * <p>Strict: no raw HTTP fallbacks for typed API operations. If the generated
 * client fails (deserialization, wrong types, etc.), the test fails — surfacing
 * spec bugs. Document upload uses raw HTTP (multipart) since the generated
 * client may not support multipart form uploads cleanly.
 *
 * <p>Cleanup uses raw HTTP to ensure entities are deleted even when the
 * generated client has issues.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdvisoryAgreementScenario extends GeneratedClientScenario {

    private static String token;
    private static ApiClient apiClient;
    private static AdvicePolicyApi advicePolicyApi;
    private static PersonApi personApi;
    private static ClientApi clientApi;
    private static ClientPersonApi clientPersonApi;
    private static SigningEnvelopeApi signingEnvelopeApi;
    private static SigningEnvelopeDocumentApi signingEnvelopeDocumentApi;
    private static SigningEnvelopePartyApi signingEnvelopePartyApi;
    private static SigningEnvelopeActionApi signingEnvelopeActionApi;
    private static AdviceContextApi adviceContextApi;
    private static AdvisoryAgreementApi advisoryAgreementApi;
    private static AdvisoryAgreementActionApi advisoryAgreementActionApi;
    private static AdviceContextActionApi adviceContextActionApi;

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

        apiClient = createApiClient(token);
        advicePolicyApi = new AdvicePolicyApi(apiClient);
        personApi = new PersonApi(apiClient);
        clientApi = new ClientApi(apiClient);
        clientPersonApi = new ClientPersonApi(apiClient);
        signingEnvelopeApi = new SigningEnvelopeApi(apiClient);
        signingEnvelopeDocumentApi = new SigningEnvelopeDocumentApi(apiClient);
        signingEnvelopePartyApi = new SigningEnvelopePartyApi(apiClient);
        signingEnvelopeActionApi = new SigningEnvelopeActionApi(apiClient);
        adviceContextApi = new AdviceContextApi(apiClient);
        advisoryAgreementApi = new AdvisoryAgreementApi(apiClient);
        advisoryAgreementActionApi = new AdvisoryAgreementActionApi(apiClient);
        adviceContextActionApi = new AdviceContextActionApi(apiClient);
    }

    @Test
    @Order(1)
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
    @Order(2)
    void createPerson() throws ApiException {
        var req = new StorePersonRequest()
                .firstName("Gen")
                .lastName("S6-AdvisoryAgreement")
                .email("gen-s6@example.com")
                .languageCode("en");

        var response = personApi.personsStore(req);
        assertNotNull(response);
        assertNotNull(response.getData());

        personId = response.getData().getId();
        assertTrue(personId > 0, "Person ID should be positive");
    }

    @Test
    @Order(3)
    void createClient() throws ApiException {
        var req = new StoreClientRequest()
                .name("Gen-S6 Client")
                .type("individual")
                .isActive(true)
                .currencyId(47);

        var response = clientApi.clientsStore(req);
        assertNotNull(response);
        assertNotNull(response.getData());

        clientId = response.getData().getId();
        assertTrue(clientId > 0, "Client ID should be positive");
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

    // --- Document upload and signing envelope ---

    @Test
    @Order(5)
    void uploadDocument() throws Exception {
        // Multipart upload uses raw HTTP — generated clients often lack clean
        // multipart support, and this keeps the upload pattern consistent.
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
    @Order(6)
    void createSigningEnvelope() throws ApiException {
        var req = new StoreSigningEnvelopeRequest()
                .title("Gen-S6 Agreement Envelope");

        var response = signingEnvelopeApi.signingEnvelopesStore(req);
        assertNotNull(response);
        assertNotNull(response.getData());

        envelopeId = response.getData().getId();
        assertTrue(envelopeId > 0, "Envelope ID should be positive");
    }

    @Test
    @Order(7)
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
    @Order(8)
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
    @Order(9)
    void sendEnvelope() throws ApiException {
        assertTrue(envelopeId > 0, "Envelope must be created first");

        signingEnvelopeActionApi.signingEnvelopeActionSend(
                String.valueOf(envelopeId), String.valueOf(envelopeId));
    }

    // --- Advice context and agreement ---

    @Test
    @Order(10)
    void createAdviceContext() throws ApiException {
        assertTrue(advicePolicyId > 0, "Advice policy must be found first");
        assertTrue(personId > 0, "Person must be created first");

        var member = new StoreAdviceContextRequestMembersInner()
                .personId(personId)
                .powerOfAttorney(false);

        var req = new StoreAdviceContextRequest()
                .advicePolicyId(advicePolicyId)
                .type(StoreAdviceContextRequest.TypeEnum.INDIVIDUAL)
                .name("Gen-S6 Advice Context")
                .referencePersonId(personId)
                .members(List.of(member));

        var response = adviceContextApi.adviceContextsStore(req);
        assertNotNull(response);
        assertNotNull(response.getData());

        adviceContextId = response.getData().getId();
        assertTrue(adviceContextId > 0, "Advice context ID should be positive");
        assertEquals("active", response.getData().getStatus().getValue(),
                "New advice context should be active");
    }

    @Test
    @Order(11)
    void createAdvisoryAgreement() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");
        assertTrue(envelopeId > 0, "Signing envelope must be created first");

        var req = new StoreAdvisoryAgreementRequest()
                .version("1.0")
                .signingEnvelopeId(envelopeId);

        var response = advisoryAgreementApi.advisoryAgreementStoreForContext(
                String.valueOf(adviceContextId), req);
        assertNotNull(response);
        assertNotNull(response.getData());

        agreementId = response.getData().getId();
        assertTrue(agreementId > 0, "Agreement ID should be positive");
        assertEquals("draft", response.getData().getStatus().getValue(),
                "New advisory agreement should be in draft status");
    }

    @Test
    @Order(12)
    void readAdvisoryAgreement() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        var response = advisoryAgreementApi.advisoryAgreementShow(
                String.valueOf(agreementId), null);
        assertNotNull(response);
        assertEquals(agreementId, response.getData().getId());
        assertEquals("draft", response.getData().getStatus().getValue());
        assertEquals("1.0", response.getData().getVersion());
    }

    @Test
    @Order(13)
    void submitSigning() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        // Transition: draft → pending_signature
        var req = new SubmitSigningAdvisoryAgreementRequest()
                .idempotencyKey("gen-s6-submit-" + System.currentTimeMillis())
                .documentId(documentId);

        advisoryAgreementActionApi.advisoryAgreementActionSubmitSigning(
                String.valueOf(agreementId), req);
    }

    @Test
    @Order(14)
    void markAgreementSigned() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");
        assertTrue(documentId > 0, "Document must be uploaded first");

        // Transition: pending_signature → signed
        var req = new MarkSignedAdvisoryAgreementRequest()
                .idempotencyKey("gen-s6-signed-" + System.currentTimeMillis())
                .signedDocumentId(documentId);

        advisoryAgreementActionApi.advisoryAgreementActionMarkSigned(
                String.valueOf(agreementId), req);
    }

    @Test
    @Order(15)
    void readAgreementSigned() throws ApiException {
        assertTrue(agreementId > 0, "Agreement must be created first");

        var response = advisoryAgreementApi.advisoryAgreementShow(
                String.valueOf(agreementId), null);
        assertNotNull(response);
        assertEquals("signed", response.getData().getStatus().getValue(),
                "Agreement should now be in signed status");
    }

    @Test
    @Order(16)
    void closeAdviceContext() throws ApiException {
        assertTrue(adviceContextId > 0, "Advice context must be created first");

        var req = new CloseAdviceContextRequest()
                .idempotencyKey("gen-s6-close-" + System.currentTimeMillis())
                .reason("Scenario complete");

        adviceContextActionApi.adviceContextActionClose(
                String.valueOf(adviceContextId), req);
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
