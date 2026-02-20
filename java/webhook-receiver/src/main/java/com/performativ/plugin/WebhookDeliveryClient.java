package com.performativ.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client for monitoring and replaying Performativ webhook deliveries.
 *
 * <p>Provides access to the webhook delivery management API:
 * <ul>
 *   <li>List deliveries for a plugin instance (with status, attempt count, HTTP status)</li>
 *   <li>Replay a failed delivery to re-trigger the webhook</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * WebhookDeliveryClient client = new WebhookDeliveryClient(
 *     "https://api.example.com",
 *     "your-bearer-token",
 *     "my-plugin",
 *     42
 * );
 *
 * // List recent deliveries
 * JsonNode deliveries = client.listDeliveries();
 *
 * // Replay a failed delivery
 * JsonNode result = client.replayDelivery(123);
 * }</pre>
 *
 * <p>Note: This API is accessed by tenant users (via the Performativ UI or user JWT),
 * not by plugin credentials. Plugins receive webhooks passively and do not need to
 * call this API themselves.
 */
public final class WebhookDeliveryClient {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryClient.class);

    private final String apiBaseUrl;
    private final String bearerToken;
    private final String pluginSlug;
    private final int instanceId;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * @param apiBaseUrl  base URL of the Performativ API
     * @param bearerToken a valid JWT Bearer token (user or plugin)
     * @param pluginSlug  the plugin slug (e.g. "my-plugin")
     * @param instanceId  the plugin instance ID
     */
    public WebhookDeliveryClient(String apiBaseUrl, String bearerToken,
                                 String pluginSlug, int instanceId) {
        this.apiBaseUrl = apiBaseUrl.replaceAll("/+$", "");
        this.bearerToken = bearerToken;
        this.pluginSlug = pluginSlug;
        this.instanceId = instanceId;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * List webhook deliveries for this plugin instance.
     *
     * <p>Returns a paginated list of deliveries with fields:
     * <ul>
     *   <li>{@code id} - Delivery ID</li>
     *   <li>{@code event_id} - The webhook event ID</li>
     *   <li>{@code status} - "pending", "delivered", or "failed"</li>
     *   <li>{@code attempts} - Number of delivery attempts</li>
     *   <li>{@code last_http_status} - HTTP status code of last attempt</li>
     *   <li>{@code created_at} - When the delivery was created</li>
     * </ul>
     *
     * <p>API endpoint:
     * {@code GET /api/v1/plugins/{pluginId}/instances/{instanceId}/webhook-deliveries}
     *
     * @return parsed JSON response with delivery data
     */
    public JsonNode listDeliveries() throws IOException, InterruptedException {
        String path = String.format("/api/v1/plugins/%s/instances/%d/webhook-deliveries",
                pluginSlug, instanceId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + path))
                .header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new IOException("Failed to list deliveries: HTTP " + response.statusCode()
                    + " - " + response.body());
        }

        log.info("Listed webhook deliveries for plugin={} instance={}", pluginSlug, instanceId);
        return objectMapper.readTree(response.body());
    }

    /**
     * Replay a failed webhook delivery.
     *
     * <p>Re-queues the delivery for another attempt. Useful when your endpoint
     * was temporarily unavailable and the delivery exhausted its automatic retries.
     *
     * <p>API endpoint:
     * {@code POST /api/v1/plugins/{pluginId}/instances/{instanceId}/webhook-deliveries/{deliveryId}/replay}
     *
     * @param deliveryId the ID of the delivery to replay
     * @return parsed JSON response
     */
    public JsonNode replayDelivery(int deliveryId) throws IOException, InterruptedException {
        String path = String.format(
                "/api/v1/plugins/%s/instances/%d/webhook-deliveries/%d/replay",
                pluginSlug, instanceId, deliveryId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + path))
                .header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new IOException("Failed to replay delivery " + deliveryId
                    + ": HTTP " + response.statusCode() + " - " + response.body());
        }

        log.info("Replayed webhook delivery {} for plugin={} instance={}",
                deliveryId, pluginSlug, instanceId);
        return objectMapper.readTree(response.body());
    }
}
