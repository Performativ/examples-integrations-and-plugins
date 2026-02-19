package com.performativ.scenarios.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.scenarios.BaseScenario;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S2: Client Lifecycle — create Person and Client, read back, update, delete.
 *
 * <p>Uses raw HTTP throughout.
 *
 * @see <a href="../../../../../../../../../SCENARIOS.md">SCENARIOS.md</a>
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
    }

    @Test
    @Order(1)
    void createPerson() throws Exception {
        JsonNode person = createEntity(token, "/api/persons",
                """
                {"first_name":"Manual","last_name":"S2-ClientLifecycle","email":"manual-s2@example.com","language_code":"en"}
                """);

        personId = person.get("id").asInt();
        assertTrue(personId > 0, "Person ID should be positive");
        assertEquals("Manual", person.get("first_name").asText());
    }

    @Test
    @Order(2)
    void createClient() throws Exception {
        assertTrue(personId > 0, "Person must be created first");

        JsonNode client = createEntity(token, "/api/clients",
                String.format("""
                {"name":"Manual-S2 Client","type":"individual","is_active":true,"primary_person_id":%d}
                """, personId));

        clientId = client.get("id").asInt();
        assertTrue(clientId > 0, "Client ID should be positive");
        assertEquals("Manual-S2 Client", client.get("name").asText());
    }

    @Test
    @Order(3)
    void readBackClient() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        HttpResponse<String> response = apiGet(token, "/api/clients/" + clientId);
        assertEquals(200, response.statusCode());

        JsonNode client = objectMapper.readTree(response.body()).path("data");
        assertEquals(clientId, client.get("id").asInt());
        assertEquals("Manual-S2 Client", client.get("name").asText());
        assertEquals("individual", client.get("type").asText());
    }

    @Test
    @Order(4)
    void updateClient() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        HttpResponse<String> response = apiPut(token, "/api/clients/" + clientId,
                """
                {"name":"Manual-S2 Client Updated"}
                """);
        assertTrue(response.statusCode() < 300,
                "Update should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode client = objectMapper.readTree(response.body()).path("data");
        assertEquals("Manual-S2 Client Updated", client.get("name").asText());
    }

    @Test
    @Order(5)
    void readBackPerson() throws Exception {
        assertTrue(personId > 0, "Person must be created first");

        HttpResponse<String> response = apiGet(token, "/api/persons/" + personId);
        assertEquals(200, response.statusCode());

        JsonNode person = objectMapper.readTree(response.body()).path("data");
        assertEquals(personId, person.get("id").asInt());
        assertEquals("Manual", person.get("first_name").asText());
        assertEquals("S2-ClientLifecycle", person.get("last_name").asText());
    }

    @Test
    @Order(6)
    void deleteClient() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/clients/" + clientId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        clientId = 0;
    }

    @Test
    @Order(7)
    void deletePerson() throws Exception {
        assertTrue(personId > 0, "Person must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/persons/" + personId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        personId = 0;
    }

    @AfterAll
    static void teardown() {
        if (token == null) return;
        if (clientId > 0) deleteEntity(token, "/api/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/persons/" + personId);
    }
}
