package com.performativ.plugin;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Exposes received webhook events for integration testing.
 *
 * <p>{@code GET /events} returns all events received since startup.
 * {@code DELETE /events} clears the store (useful between test runs).
 *
 * <p>This endpoint is intended for local development and testing only.
 */
@RestController
@RequestMapping("/events")
public class EventStoreController {

    private final WebhookEventProcessor processor;

    public EventStoreController(WebhookEventProcessor processor) {
        this.processor = processor;
    }

    @GetMapping
    public List<Map<String, String>> getEvents(
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String event,
            @RequestParam(name = "entity_id", required = false) String entityId) {
        return processor.getEvents().stream()
                .filter(e -> entity == null || entity.equals(e.get("entity")))
                .filter(e -> event == null || event.equals(e.get("event")))
                .filter(e -> entityId == null || entityId.equals(e.get("entity_id")))
                .toList();
    }

    @DeleteMapping
    public Map<String, String> clearEvents() {
        processor.clearEvents();
        return Map.of("status", "ok");
    }
}
