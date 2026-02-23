package com.performativ.scenarios;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Shared utilities for integration scenarios.
 *
 * <p>Loads configuration from the repo-root {@code .env} file via dotenv-java.
 * Credentials are required — tests fail immediately when {@code .env} is missing
 * or incomplete. Copy {@code .env.example} to {@code .env} and fill in values.
 */
public abstract class BaseScenario {

    protected static final Dotenv dotenv = Dotenv.configure()
            .directory("../../")
            .ignoreIfMissing()
            .load();

    protected static final ObjectMapper objectMapper = new ObjectMapper();

    protected static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Assert that all given environment variables are present and non-blank.
     * Fails the test immediately if any are missing — no graceful skipping.
     */
    protected static void requireEnv(String... keys) {
        for (String key : keys) {
            String value = dotenv.get(key);
            assertNotNull(value, key + " must be set in .env (copy .env.example and fill in credentials)");
            assertFalse(value.isBlank(), key + " must not be blank in .env");
        }
    }

    protected static String apiBaseUrl() {
        return dotenv.get("API_BASE_URL").replaceAll("/+$", "");
    }

    /**
     * Acquire an OAuth2 access token using client_credentials with client_secret_basic.
     */
    protected static String acquireToken() throws IOException, InterruptedException {
        String tokenBrokerUrl = dotenv.get("TOKEN_BROKER_URL");
        String clientId = dotenv.get("PLUGIN_CLIENT_ID");
        String clientSecret = dotenv.get("PLUGIN_CLIENT_SECRET");
        String audience = dotenv.get("TOKEN_AUDIENCE", "backend-api");

        String formBody = "grant_type=" + URLEncoder.encode("client_credentials", StandardCharsets.UTF_8)
                + "&audience=" + URLEncoder.encode(audience, StandardCharsets.UTF_8);

        String credentials = clientId + ":" + clientSecret;
        String basicAuth = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenBrokerUrl + "/oauth/token"))
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Token request failed: HTTP " + response.statusCode()
                    + " - " + response.body());
        }

        JsonNode tokenResponse = objectMapper.readTree(response.body());
        return tokenResponse.get("access_token").asText();
    }

    /**
     * Perform an authenticated GET request.
     */
    protected static HttpResponse<String> apiGet(String token, String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl() + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Perform an authenticated POST request with a JSON body.
     */
    protected static HttpResponse<String> apiPost(String token, String path, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl() + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Perform an authenticated PUT request with a JSON body.
     */
    protected static HttpResponse<String> apiPut(String token, String path, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl() + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Perform an authenticated DELETE request.
     */
    protected static HttpResponse<String> apiDelete(String token, String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl() + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .DELETE()
                .timeout(Duration.ofSeconds(30))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Perform an authenticated multipart POST request. Uploads a file along with
     * additional form fields. Used for document upload endpoints.
     */
    protected static HttpResponse<String> apiPostMultipart(String token, String path,
            Path filePath, String fileFieldName, Map<String, String> fields)
            throws IOException, InterruptedException {

        String boundary = "----FormBoundary" + UUID.randomUUID().toString().replace("-", "");
        var body = new StringBuilder();

        // File part
        body.append("--").append(boundary).append("\r\n");
        body.append("Content-Disposition: form-data; name=\"").append(fileFieldName)
                .append("\"; filename=\"").append(filePath.getFileName()).append("\"\r\n");
        body.append("Content-Type: application/octet-stream\r\n\r\n");
        body.append(java.nio.file.Files.readString(filePath, StandardCharsets.UTF_8));
        body.append("\r\n");

        // Additional fields
        for (var entry : fields.entrySet()) {
            body.append("--").append(boundary).append("\r\n");
            body.append("Content-Disposition: form-data; name=\"")
                    .append(entry.getKey()).append("\"\r\n\r\n");
            body.append(entry.getValue()).append("\r\n");
        }

        body.append("--").append(boundary).append("--\r\n");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl() + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(Duration.ofSeconds(30))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * POST and extract the "data" object from the response. Fails if status is not 2xx.
     */
    protected static JsonNode createEntity(String token, String path, String jsonBody)
            throws IOException, InterruptedException {
        HttpResponse<String> response = apiPost(token, path, jsonBody);
        if (response.statusCode() >= 300) {
            throw new IOException("Create failed at " + path + ": HTTP " + response.statusCode()
                    + " - " + response.body());
        }
        return objectMapper.readTree(response.body()).path("data");
    }

    /**
     * DELETE an entity. Logs but does not throw on failure (for use in cleanup).
     */
    protected static void deleteEntity(String token, String path) {
        try {
            apiDelete(token, path);
        } catch (Exception e) {
            System.err.println("Cleanup failed for " + path + ": " + e.getMessage());
        }
    }
}
