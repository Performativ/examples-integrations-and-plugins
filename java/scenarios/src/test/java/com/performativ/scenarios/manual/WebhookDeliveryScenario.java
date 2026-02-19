package com.performativ.scenarios.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.scenarios.BaseScenario;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S4: Webhook Delivery — create a Person, poll the v1 delivery endpoint,
 * verify that a Person.Created delivery appears, then delete.
 *
 * <p>Uses the delivery-polling API as a CI-friendly verification approach.
 * In production, your plugin receives webhooks as real-time HTTPS POSTs.
 * See {@code java/webhook-receiver/} for a complete Spring Boot implementation,
 * and {@code docs/webhook-setup.md} + {@code docs/testing-webhooks-locally.md}
 * for the full push-based flow.
 *
 * <p>Requires {@code PLUGIN_SLUG} and {@code PLUGIN_INSTANCE_ID} in {@code .env}.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebhookDeliveryScenario extends BaseScenario {

    private static String token;
    private static int personId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL",
                "API_BASE_URL", "PLUGIN_SLUG", "PLUGIN_INSTANCE_ID");
        token = acquireToken();
    }

    @Test
    @Order(1)
    void createPerson() throws Exception {
        JsonNode person = createEntity(token, "/api/v1/persons",
                """
                {"first_name":"Manual","last_name":"S4-WebhookDelivery","email":"manual-s4@example.com","language_code":"en"}
                """);

        personId = person.get("id").asInt();
        assertTrue(personId > 0, "Person ID should be positive");
    }

    @Test
    @Order(2)
    void waitForDeliveryProcessing() throws Exception {
        assertTrue(personId > 0, "Person must be created first");
        Thread.sleep(5_000);
    }

    @Test
    @Order(3)
    void pollAndVerifyDelivery() throws Exception {
        assertTrue(personId > 0, "Person must be created first");

        String pluginSlug = dotenv.get("PLUGIN_SLUG");
        String instanceId = dotenv.get("PLUGIN_INSTANCE_ID");
        String pollPath = String.format("/api/v1/plugins/%s/instances/%s/webhook-deliveries/poll?limit=50",
                pluginSlug, instanceId);

        HttpResponse<String> response = apiGet(token, pollPath);
        assertEquals(200, response.statusCode(),
                "Poll endpoint should return 200, got: " + response.statusCode());

        JsonNode body = objectMapper.readTree(response.body());
        JsonNode deliveries = body.isArray() ? body : body.path("data");
        assertTrue(deliveries.isArray(), "Response should contain a deliveries array");

        boolean found = false;
        for (JsonNode delivery : deliveries) {
            JsonNode payload = delivery.path("payload");
            if (payload.isMissingNode()) continue;

            // payload may be embedded object or string
            JsonNode event;
            if (payload.isTextual()) {
                event = objectMapper.readTree(payload.asText());
            } else {
                event = payload;
            }

            if ("Person".equals(event.path("entity").asText())
                    && "Created".equals(event.path("event").asText())
                    && event.path("entity_id").asInt() == personId) {
                found = true;
                break;
            }
        }

        assertTrue(found,
                "Expected Person.Created delivery for entity_id=" + personId
                        + " in " + deliveries.size() + " deliveries");
    }

    @Test
    @Order(4)
    void deletePerson() throws Exception {
        assertTrue(personId > 0, "Person must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/persons/" + personId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        personId = 0;
    }

    @AfterAll
    static void teardown() {
        if (token == null) return;
        if (personId > 0) deleteEntity(token, "/api/v1/persons/" + personId);
    }
}
