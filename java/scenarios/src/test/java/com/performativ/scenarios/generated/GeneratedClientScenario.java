package com.performativ.scenarios.generated;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.performativ.client.core.ApiClient;
import com.performativ.scenarios.BaseScenario;

/**
 * Base class for scenarios that use the OpenAPI-generated typed client.
 *
 * <p>Provides shared setup for the generated {@link ApiClient} (base path,
 * bearer token, <b>strict</b> Jackson ObjectMapper). Subclasses instantiate
 * the specific API classes they need.
 *
 * <p>These scenarios are <b>strict</b>: the ObjectMapper is configured to
 * fail on unknown properties, null primitives, and missing creator properties.
 * If the API returns fields not in the spec (or omits required fields), the
 * test fails. This surfaces spec drift immediately.
 *
 * <p>Cleanup always uses raw HTTP via {@link #deleteEntity} to ensure
 * teardown succeeds even when the generated client has spec issues.
 */
public abstract class GeneratedClientScenario extends BaseScenario {

    /**
     * Create and configure an {@link ApiClient} with bearer token auth
     * and strict deserialization.
     *
     * <p>The generated spec paths start at {@code /clients}, {@code /persons} etc.,
     * so the base path includes {@code /api}.
     *
     * <p>Strictness settings:
     * <ul>
     *   <li>{@code FAIL_ON_UNKNOWN_PROPERTIES} — reject fields not in the model
     *       (catches spec drift: API returns new fields the spec doesn't declare)</li>
     *   <li>{@code FAIL_ON_NULL_FOR_PRIMITIVES} — reject null for int/boolean fields
     *       (catches missing required fields that map to primitives)</li>
     * </ul>
     */
    protected static ApiClient createApiClient(String token) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(apiBaseUrl() + "/api");
        apiClient.setBearerToken(token);

        ObjectMapper om = apiClient.getObjectMapper();
        om.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        om.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

        return apiClient;
    }
}
