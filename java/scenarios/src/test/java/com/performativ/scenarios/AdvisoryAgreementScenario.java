package com.performativ.scenarios;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Placeholder: create an advisory agreement for a client.
 *
 * <p>Once the advisory-agreement API endpoints are available, this scenario
 * will create the prerequisite entities (Person, Client, Portfolio), then
 * create and read back an advisory agreement.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdvisoryAgreementScenario extends BaseScenario {

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
                {"first_name":"Agreement","last_name":"Test","email":"agreement@example.com","language_code":"en"}
                """);
        personId = person.get("id").asInt();
        assertTrue(personId > 0);

        var client = createEntity(token, "/api/clients",
                String.format("""
                {"name":"Agreement Test Client","type":"individual","is_active":true,"primary_person_id":%d}
                """, personId));
        clientId = client.get("id").asInt();
        assertTrue(clientId > 0);
    }

    @Test
    @Order(2)
    @Disabled("Advisory agreement endpoint not yet available — placeholder")
    void createAdvisoryAgreement() {
        // TODO: POST /api/clients/{clientId}/advisory-agreements { ... }
        //       Verify response contains agreement ID and correct client reference.
        fail("Not yet implemented");
    }

    @Test
    @Order(3)
    @Disabled("Advisory agreement endpoint not yet available — placeholder")
    void readBackAdvisoryAgreement() {
        // TODO: GET /api/clients/{clientId}/advisory-agreements/{agreementId}
        //       Verify fields match what was created.
        fail("Not yet implemented");
    }

    @AfterAll
    static void teardown() {
        if (token == null) return;
        if (clientId > 0) deleteEntity(token, "/api/clients/" + clientId);
        if (personId > 0) deleteEntity(token, "/api/persons/" + personId);
    }
}
