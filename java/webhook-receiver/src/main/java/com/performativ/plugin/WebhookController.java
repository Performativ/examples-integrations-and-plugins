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

/**
 * Receives and processes webhooks from Performativ.
 *
 * <p>This controller handles HTTP-specific concerns (signature verification,
 * JSON parsing, response codes) and delegates event processing to
 * {@link WebhookEventProcessor}.
 *
 * <p>This controller demonstrates:
 * <ul>
 *   <li>HMAC-SHA256 signature verification</li>
 *   <li>Idempotency via {@code event_id} tracking (shared with {@link WebhookPoller})</li>
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
    private final WebhookEventProcessor processor;

    public WebhookController(
            @Value("${webhook.signing-key:}") String signingKey,
            ObjectMapper objectMapper,
            WebhookEventProcessor processor) {
        this.verifier = signingKey.isBlank() ? null : new SignatureVerifier(signingKey);
        this.objectMapper = objectMapper;
        this.processor = processor;

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

        log.info("Webhook received: entity={} event={} entity_id={} event_id={} tenant={}",
                payload.path("entity").asText(""),
                payload.path("event").asText(""),
                payload.path("entity_id").asText(""),
                payload.path("event_id").asText(""),
                tenant);

        // Step 3: Idempotency check + processing (shared with poller)
        if (!processor.processIfNew(payload)) {
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Already processed"));
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
