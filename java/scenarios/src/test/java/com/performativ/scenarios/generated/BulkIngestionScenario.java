package com.performativ.scenarios.generated;

import com.performativ.client.api.AsyncBatchApi;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import com.performativ.client.model.CreateBatchRequest;
import com.performativ.client.model.GetPresignedUrlRequest;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S5: Bulk Ingestion — create a batch and obtain a presigned upload URL
 * using the generated OpenAPI client (v1 endpoints).
 *
 * <p>Strict: if the generated {@link AsyncBatchApi} fails, the test fails.
 *
 * <p>The batch is not started — this scenario only verifies that the
 * multi-step setup flow works (create batch → get presigned URL).
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BulkIngestionScenario extends GeneratedClientScenario {

    private static AsyncBatchApi batchApi;
    private static String batchId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        String token = acquireToken();

        ApiClient apiClient = createApiClient(token);
        batchApi = new AsyncBatchApi(apiClient);
    }

    @Test
    @Order(1)
    void createBatch() throws ApiException {
        var req = new CreateBatchRequest()
                .uploadMode(CreateBatchRequest.UploadModeEnum.PRESIGNED);

        var response = batchApi.bulkAsyncBatchesCreate(req);
        assertNotNull(response, "Batch create response should not be null");
        assertNotNull(response.getData(), "Batch data should not be null");

        batchId = response.getData().getBatchId();
        assertNotNull(batchId, "Batch ID should not be null");
        assertFalse(batchId.isBlank(), "Batch ID should not be blank");
    }

    @Test
    @Order(2)
    void getPresignedUrl() throws ApiException {
        assertNotNull(batchId, "Batch must be created first");

        var req = new GetPresignedUrlRequest()
                .fileName("clients.csv")
                .resourceType(GetPresignedUrlRequest.ResourceTypeEnum.CLIENTS);

        var response = batchApi.bulkAsyncBatchesPresigned(batchId, req);
        assertNotNull(response, "Presigned URL response should not be null");
        assertNotNull(response.getData(), "Presigned URL data should not be null");

        String uploadUrl = response.getData().getUploadUrl();
        assertNotNull(uploadUrl, "Upload URL should not be null");
        assertFalse(uploadUrl.isBlank(), "Upload URL should not be blank");
    }
}
