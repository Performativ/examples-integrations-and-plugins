package com.performativ.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared event processor for webhook payloads.
 *
 * <p>Provides idempotency (via {@code event_id} tracking) and event routing
 * logic that is shared between the push path ({@link WebhookController})
 * and the pull path ({@link WebhookPoller}).
 *
 * <p>In production, replace the in-memory set with a database or Redis
 * for durability across restarts.
 */
@Service
public class WebhookEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventProcessor.class);

    /**
     * Tracks processed event IDs for idempotency.
     * Shared across both push (webhook POST) and pull (poller) paths.
     */
    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();

    /**
     * Stores received events for query by integration tests.
     * In production, replace with a proper event store.
     */
    private final List<Map<String, String>> eventStore = new CopyOnWriteArrayList<>();

    /**
     * Process a webhook payload if it hasn't been seen before.
     *
     * @param payload the parsed webhook JSON payload
     * @return {@code true} if the event was new and processed,
     *         {@code false} if it was a duplicate
     */
    public boolean processIfNew(JsonNode payload) {
        String eventId = payload.path("event_id").asText("");
        String entity = payload.path("entity").asText("");
        String event = payload.path("event").asText("");
        String entityId = payload.path("entity_id").asText("");

        if (!eventId.isEmpty() && !processedEvents.add(eventId)) {
            log.debug("Duplicate event skipped: event_id={}", eventId);
            return false;
        }

        log.info("Processing event: entity={} event={} entity_id={} event_id={}",
                entity, event, entityId, eventId);

        eventStore.add(Map.of(
                "entity", entity,
                "event", event,
                "entity_id", entityId,
                "event_id", eventId));

        processEvent(payload);
        return true;
    }

    /** Returns all stored events (for integration test queries). */
    public List<Map<String, String>> getEvents() {
        return List.copyOf(eventStore);
    }

    /** Clears the event store (call between test runs). */
    public void clearEvents() {
        eventStore.clear();
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
    private void processEvent(JsonNode payload) {
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
