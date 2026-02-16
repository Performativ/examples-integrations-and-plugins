package com.performativ.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Receives and processes webhooks from Performativ.
 *
 * <p>This controller demonstrates:
 * <ul>
 *   <li>HMAC-SHA256 signature verification</li>
 *   <li>Idempotency via {@code event_id} tracking</li>
 *   <li>Quick HTTP 200 response (process asynchronously in production)</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * Set the following environment variable (or application.properties):
 * <pre>
 * WEBHOOK_SIGNING_KEY=your-signing-key
 * </pre>
 */
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final SignatureVerifier verifier;
    private final ObjectMapper objectMapper;

    /**
     * Tracks processed event IDs for idempotency.
     * In production, use a database or Redis instead.
     */
    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();

    public WebhookController(
            @Value("${webhook.signing-key:}") String signingKey,
            ObjectMapper objectMapper) {
        this.verifier = signingKey.isBlank() ? null : new SignatureVerifier(signingKey);
        this.objectMapper = objectMapper;

        if (this.verifier == null) {
            log.warn("No WEBHOOK_SIGNING_KEY configured - signature verification is disabled");
        }
    }

    /**
     * POST /webhook
     *
     * <p>Receives webhook events from Performativ. Returns 200 immediately and
     * processes the event. In production, queue the event for async processing.
     *
     * <h4>Headers</h4>
     * <ul>
     *   <li>{@code x-webhook-signature} - HMAC-SHA256 hex signature (optional)</li>
     *   <li>{@code x-tenant} - Tenant identifier</li>
     *   <li>{@code x-api-domain} - API domain for the tenant</li>
     *   <li>{@code x-tenant-reference-id} - Your reference ID (optional)</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> receiveWebhook(
            @RequestBody byte[] body,
            @RequestHeader(value = "x-webhook-signature", required = false) String signature,
            @RequestHeader(value = "x-tenant", required = false) String tenant,
            @RequestHeader(value = "x-api-domain", required = false) String apiDomain) {

        // Step 1: Verify signature
        if (verifier != null && !verifier.verify(body, signature)) {
            log.warn("Invalid webhook signature from tenant={}", tenant);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid signature"));
        }

        // Step 2: Parse payload
        JsonNode payload;
        try {
            payload = objectMapper.readTree(body);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid JSON"));
        }

        String eventId = payload.path("event_id").asText("");
        String entity = payload.path("entity").asText("");
        String event = payload.path("event").asText("");
        String entityId = payload.path("entity_id").asText("");

        // Step 3: Idempotency check
        if (!eventId.isEmpty() && !processedEvents.add(eventId)) {
            log.debug("Duplicate event skipped: event_id={}", eventId);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Already processed"));
        }

        // Step 4: Process the event
        // In production, queue this for async processing and return 200 immediately.
        log.info("Webhook received: entity={} event={} entity_id={} event_id={} tenant={}",
                entity, event, entityId, eventId, tenant);

        processEvent(payload, apiDomain);

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * Process a webhook event.
     *
     * <p>Replace this with your business logic. In production, you would typically:
     * <ol>
     *   <li>Queue the event for async processing</li>
     *   <li>Fetch the full entity from the API using the {@code url} field</li>
     *   <li>Update your local state</li>
     * </ol>
     */
    private void processEvent(JsonNode payload, String apiDomain) {
        String event = payload.path("event").asText();
        String entity = payload.path("entity").asText();
        String url = payload.path("url").asText(null);

        switch (event) {
            case "Created", "Updated" -> {
                if (url != null) {
                    log.info("Fetch latest state from: {}", url);
                    // Use PluginApiClient to fetch the entity via API
                }
            }
            case "Deleted" -> log.info("Entity deleted: {} {}", entity, payload.path("entity_id"));
            case "Activated" -> log.info("Plugin activated");
            case "Deactivated" -> log.info("Plugin deactivated");
            case "DailyHeartBeat" -> log.info("Heartbeat received");
            default -> log.warn("Unknown event type: {}", event);
        }
    }
}
