package com.performativ.scenarios;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Placeholder: start the advise process for a client.
 *
 * <p>Once the advise API endpoints are available, this scenario will
 * create the full prerequisite chain (Person, Client, Portfolio, Cash Account,
 * Advisory Agreement) and then trigger the advise process.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StartAdviseScenario extends BaseScenario {

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
    void createPrerequisites() throws Exception {
        var person = createEntity(token, "/api/persons",
                """
                {"first_name":"Advise","last_name":"Test","email":"advise@example.com","language_code":"en"}
                """);
        personId = person.get("id").asInt();
        assertTrue(personId > 0);

        var client = createEntity(token, "/api/clients",
                String.format("""
                {"name":"Advise Test Client","type":"individual","is_active":true,"primary_person_id":%d}
                """, personId));
        clientId = client.get("id").asInt();
        assertTrue(clientId > 0);
    }

    @Test
    @Order(2)
    @Disabled("Start-advise endpoint not yet available — placeholder")
    void startAdvise() {
        // TODO: POST /api/clients/{clientId}/advise { ... }
        //       Prerequisites: advisory agreement, portfolio with positions/balances.
        //       Verify response contains advise run ID and status.
        fail("Not yet implemented");
    }

    @Test
    @Order(3)
    @Disabled("Start-advise endpoint not yet available — placeholder")
    void checkAdviseStatus() {
        // TODO: GET /api/clients/{clientId}/advise/{runId}
        //       Verify the advise run is in a valid state.
        fail("Not yet implemented");
    }

    @AfterAll
    static void teardown() {
        if (token == null) return;
        if (clientId > 0) deleteEntity(token, "/api/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/persons/" + personId);
    }
}
