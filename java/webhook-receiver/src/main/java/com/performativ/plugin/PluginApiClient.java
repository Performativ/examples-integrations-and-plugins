package com.performativ.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * OAuth2 client_credentials API client for Performativ plugins.
 *
 * <p>Uses {@code client_secret_basic} authentication (RFC 6749 Section 2.3.1):
 * the client_id and client_secret are sent as HTTP Basic auth in the
 * Authorization header when requesting tokens.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PluginApiClient client = new PluginApiClient(
 *     "https://api.acme.sandbox.onperformativ.com/token-broker",
 *     "https://api.acme.sandbox.onperformativ.com",
 *     "your-client-id",
 *     "your-client-secret",
 *     "backend-api"
 * );
 *
 * // Fetch a client by ID
 * JsonNode clientData = client.get("/api/v1/clients/123");
 * }</pre>
 *
 * <p>The client automatically requests and caches JWT access tokens,
 * refreshing them before expiry.
 */
public final class PluginApiClient {

    private static final Logger log = LoggerFactory.getLogger(PluginApiClient.class);

    /**
     * Refresh the token when it has less than this many seconds remaining.
     */
    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 60;

    private final String tokenEndpoint;
    private final String apiBaseUrl;
    private final String clientId;
    private final String clientSecret;
    private final String audience;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    /**
     * @param tokenBrokerUrl base URL of the token broker (e.g. "https://api.acme.sandbox.onperformativ.com/token-broker")
     * @param apiBaseUrl     base URL of the Performativ API (e.g. "https://api.example.com")
     * @param clientId       OAuth2 client_id
     * @param clientSecret   OAuth2 client_secret
     * @param audience       token audience (typically "backend-api")
     */
    public PluginApiClient(String tokenBrokerUrl, String apiBaseUrl,
                           String clientId, String clientSecret, String audience) {
        this.tokenEndpoint = tokenBrokerUrl + "/oauth/token";
        this.apiBaseUrl = apiBaseUrl.replaceAll("/+$", "");
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.audience = audience;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Perform a GET request to the Performativ API.
     *
     * @param path API path (e.g. "/api/v1/clients/123")
     * @return parsed JSON response body
     * @throws IOException          if the request fails
     * @throws InterruptedException if the thread is interrupted
     */
    public JsonNode get(String path) throws IOException, InterruptedException {
        String token = getAccessToken();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new IOException("API request failed: HTTP " + response.statusCode()
                    + " - " + response.body());
        }

        return objectMapper.readTree(response.body());
    }

    /**
     * Get a valid access token, requesting a new one if needed.
     *
     * <p>Uses client_secret_basic: the Authorization header contains
     * {@code Basic base64(client_id:client_secret)}.
     */
    private synchronized String getAccessToken() throws IOException, InterruptedException {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken;
        }

        log.info("Requesting new access token from {}", tokenEndpoint);

        // Build form body: grant_type=client_credentials&audience=backend-api
        String formBody = "grant_type=" + URLEncoder.encode("client_credentials", StandardCharsets.UTF_8)
                + "&audience=" + URLEncoder.encode(audience, StandardCharsets.UTF_8);

        // client_secret_basic: Base64(client_id:client_secret)
        String credentials = clientId + ":" + clientSecret;
        String basicAuth = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
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
        this.accessToken = tokenResponse.get("access_token").asText();
        long expiresIn = tokenResponse.get("expires_in").asLong();

        // Refresh the token before it actually expires
        this.tokenExpiresAt = Instant.now().plusSeconds(expiresIn - TOKEN_REFRESH_BUFFER_SECONDS);

        log.info("Access token acquired, expires in {}s", expiresIn);
        return accessToken;
    }
}
