package com.performativ.scenarios;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Checks that webhook events were delivered after API mutations.
 *
 * <p>Supports two modes, selected by environment variable:
 * <ul>
 *   <li><b>Push mode</b> ({@code WEBHOOK_RECEIVER_URL} set, e.g. {@code http://localhost:8080}):
 *       queries the webhook-receiver's {@code GET /events} endpoint.</li>
 *   <li><b>Poll mode</b> ({@code POLL_ENDPOINT_URL} set): queries the Performativ
 *       delivery API directly. Currently a placeholder — the endpoint path
 *       is not yet confirmed.</li>
 * </ul>
 *
 * <p>When neither variable is set, all checks are no-ops (webhook verification
 * is skipped silently). This keeps scenarios runnable without a running
 * webhook-receiver.
 */
public final class WebhookChecker {

    private final String webhookReceiverUrl;  // push mode
    private final String pollEndpointUrl;     // poll mode (future)
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebhookChecker(String webhookReceiverUrl, String pollEndpointUrl) {
        this.webhookReceiverUrl = webhookReceiverUrl;
        this.pollEndpointUrl = pollEndpointUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /** Returns true if webhook checking is configured in either mode. */
    public boolean isEnabled() {
        return (webhookReceiverUrl != null && !webhookReceiverUrl.isBlank())
                || (pollEndpointUrl != null && !pollEndpointUrl.isBlank());
    }

    /**
     * Clear received events in the webhook-receiver (call before mutations).
     * No-op in poll mode or when disabled.
     */
    public void clearEvents() {
        if (webhookReceiverUrl == null || webhookReceiverUrl.isBlank()) return;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookReceiverUrl + "/events"))
                    .DELETE()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            System.out.println("WebhookChecker: could not clear events — " + e.getMessage());
        }
    }

    /**
     * Wait for a specific webhook event to appear.
     *
     * @param entity   expected entity type (e.g. "Client")
     * @param event    expected event type (e.g. "Created")
     * @param entityId expected entity ID as a string
     * @param timeoutMs maximum time to wait
     * @return true if the event was found, false if timed out or checking is disabled
     */
    public boolean waitForEvent(String entity, String event, String entityId, long timeoutMs) {
        if (!isEnabled()) return false;

        if (webhookReceiverUrl != null && !webhookReceiverUrl.isBlank()) {
            return waitForPushEvent(entity, event, entityId, timeoutMs);
        }

        // Poll mode placeholder
        System.out.println("WebhookChecker: poll mode not yet implemented");
        return false;
    }

    private boolean waitForPushEvent(String entity, String event, String entityId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String url = webhookReceiverUrl + "/events?entity=" + entity + "&event=" + event
                + "&entity_id=" + entityId;

        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json")
                        .GET()
                        .timeout(Duration.ofSeconds(5))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    List<Map<String, Object>> events = objectMapper.readValue(
                            response.body(), new TypeReference<>() {});
                    if (!events.isEmpty()) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // Receiver might not be up — that's fine, just return false at timeout
            }

            try { Thread.sleep(500); } catch (InterruptedException e) { break; }
        }

        System.out.println("WebhookChecker: timed out waiting for " + entity + "." + event
                + " entity_id=" + entityId);
        return false;
    }
}
