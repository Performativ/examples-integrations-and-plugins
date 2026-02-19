package com.performativ.scenarios.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.scenarios.BaseScenario;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S5: Bulk Ingestion — create a batch and obtain a presigned upload URL
 * via raw HTTP (v1 endpoints).
 *
 * <p>The batch is not started — this scenario only verifies that the
 * multi-step setup flow works (create batch → get presigned URL).
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BulkIngestionScenario extends BaseScenario {

    private static String token;
    private static String batchId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();
    }

    @Test
    @Order(1)
    void createBatch() throws Exception {
        HttpResponse<String> response = apiPost(token, "/api/v1/bulk/async/batches",
                """
                {"upload_mode":"presigned"}
                """);

        assertEquals(201, response.statusCode(),
                "Expected 201 from batch create, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        batchId = data.get("batch_id").asText();
        assertNotNull(batchId, "Batch ID should not be null");
        assertFalse(batchId.isBlank(), "Batch ID should not be blank");
    }

    @Test
    @Order(2)
    void getPresignedUrl() throws Exception {
        assertNotNull(batchId, "Batch must be created first");

        HttpResponse<String> response = apiPost(token,
                "/api/v1/bulk/async/batches/" + batchId + "/presigned-url",
                """
                {"file_name":"clients.csv","resource_type":"clients"}
                """);

        assertEquals(200, response.statusCode(),
                "Expected 200 from presigned URL, got: " + response.statusCode() + " " + response.body());

        JsonNode data = objectMapper.readTree(response.body()).path("data");
        String uploadUrl = data.get("upload_url").asText();
        assertNotNull(uploadUrl, "Upload URL should not be null");
        assertFalse(uploadUrl.isBlank(), "Upload URL should not be blank");
    }
}
