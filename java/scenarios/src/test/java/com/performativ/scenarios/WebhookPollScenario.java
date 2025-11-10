package com.performativ.scenarios;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acquires a token and polls the webhook delivery endpoint to verify structure.
 *
 * <p>Currently disabled: the poll endpoint path is not yet confirmed for the
 * sandbox environment. Enable this once the delivery-poll API is deployed.
 */
class WebhookPollScenario extends BaseScenario {

    @BeforeAll
    static void checkCredentials() {
        requireEnv("PLUGIN_CLIENT_ID", "PLUGIN_CLIENT_SECRET", "TOKEN_BROKER_URL",
                "API_BASE_URL", "PLUGIN_INSTANCE_ID");
    }

    @Test
    @Disabled("Poll endpoint path not yet confirmed — enable when delivery-poll API is deployed")
    void pollWebhookDeliveries() throws Exception {
        String token = acquireToken();
        String instanceId = dotenv.get("PLUGIN_INSTANCE_ID", "0");

        // TODO: confirm the correct poll endpoint path
        String pollPath = "/api/plugin-instances/" + instanceId + "/webhook-deliveries/poll?limit=5";

        HttpResponse<String> response = apiGet(token, pollPath);

        assertEquals(200, response.statusCode(),
                "Expected 200 from webhook poll endpoint");

        JsonNode body = objectMapper.readTree(response.body());
        assertNotNull(body);
        assertTrue(body.isObject() || body.isArray(),
                "Expected JSON object or array from poll endpoint");
    }
}
