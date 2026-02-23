package com.performativ.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

/**
 * Polls the webhook delivery API for new events using cursor-based pagination.
 *
 * <p>This is an alternative to receiving webhook POSTs directly. Useful when
 * your development machine is behind a NAT/firewall and cannot receive
 * inbound connections.
 *
 * <p>The poller calls the dedicated poll endpoint:
 * <pre>
 * GET /api/v1/plugins/{slug}/instances/{instanceId}/webhook-deliveries/poll
 *     ?after={cursor}&amp;limit={limit}
 * </pre>
 *
 * <p>It tracks the last delivery UUID as a cursor so each poll cycle only
 * fetches new deliveries. On first start, an optional {@code since} timestamp
 * can be used to avoid replaying the entire delivery history.
 *
 * <h3>Modes</h3>
 * <ul>
 *   <li><b>Default</b> — extracts the {@code payload} from each delivery and
 *       passes it directly to {@link WebhookEventProcessor} (fast, skips
 *       signature verification).</li>
 *   <li><b>{@code include_signature=true}</b> — the API returns the full
 *       reconstructed webhook POST (URL, payload, headers including
 *       {@code x-webhook-signature}). The poller replays each delivery as a
 *       real HTTP POST to the local {@code /webhook} endpoint, exercising the
 *       full {@link WebhookController} path including HMAC verification.</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <pre>
 * POLLER_ENABLED=true
 * POLLER_INTERVAL_MS=10000         # optional, default 10s
 * POLLER_BATCH_SIZE=50             # optional, default 50
 * POLLER_SINCE=2026-02-18T00:00:00Z  # optional, only used on first poll
 * POLLER_INCLUDE_SIGNATURE=false   # optional, replay as full POST to local /webhook
 * PLUGIN_SLUG=my-plugin-id
 * PLUGIN_INSTANCE_ID=42
 * </pre>
 *
 * <p>Also requires the standard API credentials:
 * {@code PLUGIN_CLIENT_ID}, {@code PLUGIN_CLIENT_SECRET},
 * {@code TOKEN_BROKER_URL}, {@code API_BASE_URL}.
 */
@Component
@ConditionalOnProperty(name = "poller.enabled", havingValue = "true")
public class WebhookPoller {

    private static final Logger log = LoggerFactory.getLogger(WebhookPoller.class);

    private final WebhookEventProcessor processor;
    private final PluginApiClient apiClient;
    private final String pluginSlug;
    private final long instanceId;
    private final int batchSize;
    private final String since;
    private final boolean includeSignature;
    private final String localWebhookUrl;

    private final HttpClient localHttpClient;

    /**
     * Cursor for keyset pagination — the UUID of the last delivery we processed.
     * {@code null} on first poll (uses {@code since} if configured).
     */
    private String cursor;

    public WebhookPoller(
            WebhookEventProcessor processor,
            @Value("${plugin.slug:}") String pluginSlug,
            @Value("${plugin.instance-id:0}") long instanceId,
            @Value("${token.broker-url:}") String tokenBrokerUrl,
            @Value("${api.base-url:}") String apiBaseUrl,
            @Value("${plugin.client-id:}") String clientId,
            @Value("${plugin.client-secret:}") String clientSecret,
            @Value("${token.audience:backend-api}") String audience,
            @Value("${poller.batch-size:50}") int batchSize,
            @Value("${poller.since:}") String since,
            @Value("${poller.include-signature:false}") boolean includeSignature,
            @Value("${server.port:8080}") int serverPort) {

        this.processor = processor;
        this.pluginSlug = pluginSlug;
        this.instanceId = instanceId;
        this.apiClient = new PluginApiClient(tokenBrokerUrl, apiBaseUrl,
                clientId, clientSecret, audience);
        this.batchSize = batchSize;
        this.since = since.isBlank() ? null : since;
        this.includeSignature = includeSignature;
        this.localWebhookUrl = "http://localhost:" + serverPort + "/webhook";
        this.localHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        log.info("Webhook poller enabled for plugin={} instance={} batchSize={}",
                pluginSlug, instanceId, batchSize);
        if (this.since != null) {
            log.info("Poller will start from since={}", this.since);
        }
        if (includeSignature) {
            log.info("Poller will replay deliveries as local POST to {}", localWebhookUrl);
        }
    }

    @Scheduled(fixedDelayString = "${poller.interval-ms:10000}")
    public void poll() {
        try {
            String path = buildPollPath();
            JsonNode response = apiClient.get(path);

            JsonNode deliveries = response.isArray() ? response : response.path("data");
            if (!deliveries.isArray() || deliveries.isEmpty()) {
                log.debug("Poll complete, no new deliveries");
                return;
            }

            int newCount = 0;
            String lastId = null;

            for (JsonNode delivery : deliveries) {
                lastId = delivery.path("id").asText(null);

                if (includeSignature) {
                    newCount += replayLocally(delivery, lastId) ? 1 : 0;
                } else {
                    newCount += processDirectly(delivery, lastId) ? 1 : 0;
                }
            }

            // Advance the cursor to the last delivery in this batch
            if (lastId != null) {
                cursor = lastId;
            }

            if (newCount > 0) {
                log.info("Polled {} new event(s) from {} deliveries (cursor={})",
                        newCount, deliveries.size(), cursor);
            } else {
                log.debug("Poll complete, {} deliveries seen but all duplicates (cursor={})",
                        deliveries.size(), cursor);
            }

        } catch (Exception e) {
            log.error("Polling failed, will retry on next interval", e);
        }
    }

    /**
     * Default mode: extract the payload and pass directly to the processor.
     */
    private boolean processDirectly(JsonNode delivery, String deliveryId) {
        JsonNode payload = delivery.path("payload");
        if (payload.isMissingNode() || payload.isNull()) {
            log.debug("Delivery {} has no embedded payload, skipping", deliveryId);
            return false;
        }
        return processor.processIfNew(payload);
    }

    /**
     * Signature mode: replay the delivery as a real HTTP POST to the local
     * webhook endpoint, with the exact payload and headers the platform would
     * have sent. This exercises the full {@link WebhookController} path
     * including HMAC signature verification.
     */
    private boolean replayLocally(JsonNode delivery, String deliveryId) {
        JsonNode payload = delivery.path("payload");
        JsonNode headers = delivery.path("headers");

        if (payload.isMissingNode() || payload.isNull()) {
            log.debug("Delivery {} has no embedded payload, skipping", deliveryId);
            return false;
        }

        try {
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(localWebhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .timeout(Duration.ofSeconds(10));

            // Forward all headers from the delivery (x-webhook-signature, x-tenant, etc.)
            if (headers.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = headers.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    reqBuilder.header(field.getKey(), field.getValue().asText());
                }
            }

            HttpResponse<String> resp = localHttpClient.send(
                    reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                log.debug("Replayed delivery {} -> local /webhook (200 OK)", deliveryId);
                return true;
            } else {
                log.warn("Replayed delivery {} -> local /webhook returned HTTP {}",
                        deliveryId, resp.statusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to replay delivery {} to local /webhook", deliveryId, e);
            return false;
        }
    }

    private String buildPollPath() {
        StringBuilder path = new StringBuilder();
        path.append(String.format("/api/v1/plugins/%s/instances/%d/webhook-deliveries/poll",
                pluginSlug, instanceId));

        path.append("?limit=").append(batchSize);

        if (cursor != null) {
            path.append("&after=").append(cursor);
        } else if (since != null) {
            path.append("&since=").append(since);
        }

        if (includeSignature) {
            path.append("&include_signature=1");
        }

        return path.toString();
    }
}
