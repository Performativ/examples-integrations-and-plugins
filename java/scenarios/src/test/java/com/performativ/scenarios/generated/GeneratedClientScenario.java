package com.performativ.scenarios.generated;

import com.performativ.client.core.ApiClient;
import com.performativ.scenarios.BaseScenario;

/**
 * Base class for scenarios that use the OpenAPI-generated typed client.
 *
 * <p>Provides shared setup for the generated {@link ApiClient} (base path,
 * bearer token). Subclasses instantiate the specific API classes they need.
 *
 * <p>These scenarios are <b>strict</b>: if the generated client fails
 * (deserialization error, wrong types, etc.), the test fails. There are no
 * fallbacks to raw HTTP. This surfaces spec bugs immediately.
 *
 * <p>Cleanup always uses raw HTTP via {@link #deleteEntity} to ensure
 * teardown succeeds even when the generated client has spec issues.
 */
public abstract class GeneratedClientScenario extends BaseScenario {

    /**
     * Create and configure an {@link ApiClient} with bearer token auth.
     * The generated spec paths start at {@code /clients}, {@code /persons} etc.,
     * so the base path includes {@code /api}.
     */
    protected static ApiClient createApiClient(String token) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(apiBaseUrl() + "/api");
        apiClient.setBearerToken(token);
        return apiClient;
    }
}
