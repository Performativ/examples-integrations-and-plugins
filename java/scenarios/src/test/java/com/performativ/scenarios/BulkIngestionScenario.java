package com.performativ.scenarios;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sets up an async bulk ingestion task and verifies the response contains
 * a taskId and presigned upload URL.
 */
class BulkIngestionScenario extends BaseScenario {

    @BeforeAll
    static void checkCredentials() {
        requireEnv("API_BASE_URL", "API_KEY");
    }

    @Test
    void setupIngestionTask() throws Exception {
        String apiBaseUrl = dotenv.get("API_BASE_URL").replaceAll("/+$", "");
        String apiKey = dotenv.get("API_KEY");

        String body = "{\"entity\":\"clients\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/api/ingestion/async/setup-task"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
                "Expected 200 from ingestion setup-task");

        JsonNode json = objectMapper.readTree(response.body());
        assertTrue(json.has("taskId"), "Response should contain taskId");
        assertTrue(json.has("presignedUploadUrl"), "Response should contain presignedUploadUrl");
        assertFalse(json.get("taskId").asText().isBlank());
        assertFalse(json.get("presignedUploadUrl").asText().isBlank());
    }
}
