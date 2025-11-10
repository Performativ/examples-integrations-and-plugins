package com.performativ.scenarios;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Creates a Person and Client, reads them back, verifies webhooks if configured,
 * then deletes both.
 *
 * <p>Set {@code WEBHOOK_RECEIVER_URL=http://localhost:8080} to enable webhook
 * verification (requires the webhook-receiver to be running).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClientLifecycleScenario extends BaseScenario {

    private static String token;
    private static int personId;
    private static int clientId;

    @BeforeAll
    static void setup() throws Exception {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL", "API_BASE_URL");
        token = acquireToken();
        webhookChecker.clearEvents();
    }

    @Test
    @Order(1)
    void createPerson() throws Exception {
        JsonNode person = createEntity(token, "/api/persons",
                """
                {"first_name":"Scenario","last_name":"ClientLifecycle","email":"lifecycle@example.com","language_code":"en"}
                """);

        personId = person.get("id").asInt();
        assertTrue(personId > 0, "Person ID should be positive");
        assertEquals("Scenario", person.get("first_name").asText());
    }

    @Test
    @Order(2)
    void createClient() throws Exception {
        assertTrue(personId > 0, "Person must be created first");

        JsonNode client = createEntity(token, "/api/clients",
                String.format("""
                {"name":"Scenario ClientLifecycle","type":"individual","is_active":true,"primary_person_id":%d}
                """, personId));

        clientId = client.get("id").asInt();
        assertTrue(clientId > 0, "Client ID should be positive");
        assertEquals("Scenario ClientLifecycle", client.get("name").asText());
    }

    @Test
    @Order(3)
    void readBackClient() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        HttpResponse<String> response = apiGet(token, "/api/clients/" + clientId);
        assertEquals(200, response.statusCode());

        JsonNode client = objectMapper.readTree(response.body()).path("data");
        assertEquals(clientId, client.get("id").asInt());
        assertEquals("Scenario ClientLifecycle", client.get("name").asText());
        assertEquals("individual", client.get("type").asText());
    }

    @Test
    @Order(4)
    void readBackPerson() throws Exception {
        assertTrue(personId > 0, "Person must be created first");

        HttpResponse<String> response = apiGet(token, "/api/persons/" + personId);
        assertEquals(200, response.statusCode());

        JsonNode person = objectMapper.readTree(response.body()).path("data");
        assertEquals(personId, person.get("id").asInt());
        assertEquals("Scenario", person.get("first_name").asText());
        assertEquals("ClientLifecycle", person.get("last_name").asText());
    }

    @Test
    @Order(5)
    void verifyWebhooksIfEnabled() {
        if (!webhookChecker.isEnabled()) {
            System.out.println("Webhook checking not configured (set WEBHOOK_RECEIVER_URL to enable)");
            return;
        }

        assertTrue(webhookChecker.waitForEvent("Person", "Created",
                        String.valueOf(personId), 10_000),
                "Expected Person.Created webhook for person " + personId);

        assertTrue(webhookChecker.waitForEvent("Client", "Created",
                        String.valueOf(clientId), 10_000),
                "Expected Client.Created webhook for client " + clientId);

        System.out.println("Webhooks verified for ClientLifecycle");
    }

    @AfterAll
    static void teardown() {
        if (token == null) return;
        if (clientId > 0) deleteEntity(token, "/api/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/persons/" + personId);
    }
}
