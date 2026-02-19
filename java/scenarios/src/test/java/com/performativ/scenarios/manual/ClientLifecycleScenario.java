package com.performativ.scenarios.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.performativ.scenarios.BaseScenario;
import org.junit.jupiter.api.*;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S2: Client Lifecycle — create Person and Client, link them, read back, update, delete.
 *
 * <p>Uses raw HTTP throughout. In v1, Person is linked to Client via the
 * {@code /api/v1/client-persons} join resource instead of {@code primary_person_id}.
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
        JsonNode person = createEntity(token, "/api/v1/persons",
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
        JsonNode client = createEntity(token, "/api/v1/clients",
                """
                {"name":"Manual-S2 Client","type":"individual","is_active":true,"currency_id":47}
                """);

        clientId = client.get("id").asInt();
        assertTrue(clientId > 0, "Client ID should be positive");
        assertEquals("Manual-S2 Client", client.get("name").asText());
    }

    @Test
    @Order(3)
    void linkPersonToClient() throws Exception {
        assertTrue(personId > 0, "Person must be created first");
        assertTrue(clientId > 0, "Client must be created first");

        HttpResponse<String> response = apiPost(token, "/api/v1/client-persons",
                String.format("""
                {"client_id":%d,"person_id":%d,"is_primary":true}
                """, clientId, personId));

        assertTrue(response.statusCode() < 300,
                "Link person to client should succeed, got: " + response.statusCode() + " " + response.body());
    }

    @Test
    @Order(4)
    void readBackClient() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        HttpResponse<String> response = apiGet(token, "/api/v1/clients/" + clientId);
        assertEquals(200, response.statusCode());

        JsonNode client = objectMapper.readTree(response.body()).path("data");
        assertEquals(clientId, client.get("id").asInt());
        assertEquals("Manual-S2 Client", client.get("name").asText());
    }

    @Test
    @Order(5)
    void updateClient() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");

        HttpResponse<String> response = apiPut(token, "/api/v1/clients/" + clientId,
                """
                {"name":"Manual-S2 Client Updated","type":"individual","currency_id":47}
                """);
        assertTrue(response.statusCode() < 300,
                "Update should succeed, got: " + response.statusCode() + " " + response.body());

        JsonNode client = objectMapper.readTree(response.body()).path("data");
        assertEquals("Manual-S2 Client Updated", client.get("name").asText());
    }

    @Test
    @Order(6)
    void readBackPerson() throws Exception {
        assertTrue(personId > 0, "Person must be created first");

        HttpResponse<String> response = apiGet(token, "/api/v1/persons/" + personId);
        assertEquals(200, response.statusCode());

        JsonNode person = objectMapper.readTree(response.body()).path("data");
        assertEquals(personId, person.get("id").asInt());
        assertEquals("Manual", person.get("first_name").asText());
        assertEquals("S2-ClientLifecycle", person.get("last_name").asText());
    }

    @Test
    @Order(7)
    void deleteClient() throws Exception {
        assertTrue(clientId > 0, "Client must be created first");
        HttpResponse<String> response = apiDelete(token, "/api/v1/clients/" + clientId);
        assertTrue(response.statusCode() < 300 || response.statusCode() == 404,
                "Delete should succeed, got: " + response.statusCode());
        clientId = 0;
    }

    @Test
    @Order(8)
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
        if (clientId > 0) deleteEntity(token, "/api/v1/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/v1/persons/" + personId);
    }
}
