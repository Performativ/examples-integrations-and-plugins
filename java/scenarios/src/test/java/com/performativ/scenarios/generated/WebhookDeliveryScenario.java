package com.performativ.scenarios.generated;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.client.api.PersonApi;
import com.performativ.client.core.ApiClient;
import com.performativ.client.core.ApiException;
import com.performativ.client.model.AppHttpRequestsApiV1StorePersonRequest;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S4: Webhook Delivery — create a Person via the generated client, poll the
 * v1 delivery endpoint, verify that a Person.Created delivery appears, then delete.
 *
 * <p>Uses the generated client for Person CRUD (testing typed models) and raw
 * HTTP for the delivery poll endpoint (internal plugin management API).
 *
 * <p>Delivery polling is a CI-friendly verification approach. In production,
 * your plugin receives webhooks as real-time HTTPS POSTs. See
 * {@code java/webhook-receiver/} for a complete Spring Boot implementation,
 * and {@code docs/webhook-setup.md} + {@code docs/testing-webhooks-locally.md}
 * for the full push-based flow.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebhookDeliveryScenario extends GeneratedClientScenario {

    private static String token;
    private static PersonApi personApi;
    private static int personId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL",
                "API_BASE_URL", "PLUGIN_SLUG", "PLUGIN_INSTANCE_ID");
        token = acquireToken();

        ApiClient apiClient = createApiClient(token);
        personApi = new PersonApi(apiClient);
    }

    @Test
    @Order(1)
    void createPerson() throws ApiException {
        var req = new AppHttpRequestsApiV1StorePersonRequest()
                .firstName("Gen")
                .lastName("S4-WebhookDelivery")
                .email("gen-s4@example.com")
                .languageCode("en");

        var response = personApi.personsStore(req);
        assertNotNull(response, "Person store response should not be null");
        assertNotNull(response.getData(), "Person data should not be null");

        personId = response.getData().getId();
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

        // Use raw HTTP for the delivery poll endpoint (plugin management API)
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
    void deletePerson() throws ApiException {
        assertTrue(personId > 0, "Person must be created first");
        personApi.personsDestroy(String.valueOf(personId));
        personId = 0;
    }

    @AfterAll
    static void teardown() {
        if (token == null) return;
        if (personId > 0) deleteEntity(token, "/api/v1/persons/" + personId);
    }
}
