package com.performativ.scenarios.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.scenarios.BaseScenario;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S5: Bulk Ingestion — set up an async ingestion task using API key auth.
 *
 * <p>Uses {@code API_KEY} (not OAuth2) for authentication.
 * No entities are created or deleted.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
class BulkIngestionScenario extends BaseScenario {

    @BeforeAll
    static void checkCredentials() {
        requireEnv("API_BASE_URL", "API_KEY");
    }

    @Test
    void setupIngestionTask() throws Exception {
        String apiKey = dotenv.get("API_KEY");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl() + "/api/ingestion/async/setup-task"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"entity\":\"clients\"}"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
                "Expected 200 from ingestion setup-task, got: " + response.statusCode());

        JsonNode json = objectMapper.readTree(response.body());
        assertTrue(json.has("taskId"), "Response should contain taskId");
        assertTrue(json.has("presignedUploadUrl"), "Response should contain presignedUploadUrl");
        assertFalse(json.get("taskId").asText().isBlank(), "taskId should not be empty");
        assertFalse(json.get("presignedUploadUrl").asText().isBlank(), "presignedUploadUrl should not be empty");
    }
}
