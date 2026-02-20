# Scenarios

Five core scenarios exercise the Performativ API v1 endpoints at increasing complexity. Each scenario is implemented in three ways — [curl](curl/), [java/manual](java/scenarios/) (raw HTTP), and [java/generated](java/scenarios/) (OpenAPI-generated typed client) — so the same operations are verified across approaches.

Every scenario acquires an OAuth2 token via `client_credentials` before calling the API, except S5 which uses API key auth.

All v1 endpoints use the `/api/v1/` path prefix.

## S1: API Access

Verify that credentials work and the API is reachable.

| Step | Method | Path | Expected |
|------|--------|------|----------|
| Acquire token | POST | `{TOKEN_BROKER_URL}/oauth/token` | 200, `access_token` present |
| List clients | GET | `/api/v1/clients` | 200, JSON with `data` array |

No entities are created or deleted.

## S2: Client Lifecycle

Create a Person and Client, link them, read them back, update the Client, then delete both.

| Step | Method | Path | Expected |
|------|--------|------|----------|
| Create Person | POST | `/api/v1/persons` | 201, `data.id` > 0 |
| Create Client | POST | `/api/v1/clients` | 201, `data.id` > 0 |
| Link Person to Client | POST | `/api/v1/client-persons` | 201, links person as primary |
| Read Client | GET | `/api/v1/clients/{id}` | 200, matches created |
| Update Client | PUT | `/api/v1/clients/{id}` | 200, name updated |
| Read Person | GET | `/api/v1/persons/{id}` | 200, matches created |
| Delete Client | DELETE | `/api/v1/clients/{id}` | 200 or 204 |
| Delete Person | DELETE | `/api/v1/persons/{id}` | 200 or 204 |

Entity relationships: In v1, Person is linked to Client via the `/v1/client-persons` join resource (with `is_primary`). Client requires `currency_id`. Delete Client before Person (FK dependency; client-person link cascades with client deletion).

## S3: Portfolio Setup

Create the full prerequisite chain (Person, Client, Portfolio), read them back, update the Portfolio, then delete all in reverse order.

| Step | Method | Path | Expected |
|------|--------|------|----------|
| Create Person | POST | `/api/v1/persons` | 201, `data.id` > 0 |
| Create Client | POST | `/api/v1/clients` | 201, `data.id` > 0 |
| Link Person to Client | POST | `/api/v1/client-persons` | 201, links person as primary |
| Create Portfolio | POST | `/api/v1/portfolios` | 201, `data.id` > 0 |
| Read Portfolio | GET | `/api/v1/portfolios/{id}` | 200, matches created |
| Update Portfolio | PUT | `/api/v1/portfolios/{id}` | 200, name updated |
| Delete Portfolio | DELETE | `/api/v1/portfolios/{id}` | 200 or 204 |
| Delete Client | DELETE | `/api/v1/clients/{id}` | 200 or 204 |
| Delete Person | DELETE | `/api/v1/persons/{id}` | 200 or 204 |

Entity relationships: Portfolio belongs to Client (passed as `client_id` in the request body, `currency_id` = 47 / EUR). Person linked to Client via `client-persons`. Delete in reverse order.

## S4: Webhook Delivery

Verify that creating an entity triggers a webhook delivery. This scenario uses the **delivery-polling API** to confirm the webhook was generated — this is the CI-friendly verification approach (no inbound connectivity required).

> **Production webhook setup**: In production, your plugin receives webhooks as real-time HTTPS POSTs to a registered endpoint. See the [Webhook Receiver](java/webhook-receiver/) for a complete Spring Boot implementation with HMAC signature verification, and [Webhook Setup](docs/webhook-setup.md) + [Testing Webhooks Locally](docs/testing-webhooks-locally.md) for the full push-based flow.

| Step | Method | Path | Expected |
|------|--------|------|----------|
| Create Person | POST | `/api/v1/persons` | 201, `data.id` > 0 |
| Wait for delivery | — | — | Brief pause for async delivery processing |
| Poll deliveries | GET | `/api/v1/plugins/{pluginId}/instances/{instanceId}/webhook-deliveries/poll?limit=50` | 200, `data` array |
| Find matching delivery | — | — | Delivery with `entity=Person`, `event=Created`, matching `entity_id` |
| Delete Person | DELETE | `/api/v1/persons/{id}` | 200 or 204 |

Requires `PLUGIN_SLUG` and `PLUGIN_INSTANCE_ID` in `.env`. The poll endpoint uses cursor-based pagination; this scenario reads the latest batch and searches for the expected delivery.

## S5: Bulk Ingestion

Set up an async bulk data ingestion task and verify the response. Uses API key auth (not OAuth2).

| Step | Method | Path | Expected |
|------|--------|------|----------|
| Setup ingestion task | POST | `/api/ingestion/async/setup-task` | 200, `taskId` + `presignedUploadUrl` present |

Auth: `Authorization: Bearer {API_KEY}` — uses the `API_KEY` from `.env`, not an OAuth2 token.

No entities are created or deleted. The task setup is idempotent.

## Test data conventions

Each implementation uses a distinct prefix to avoid collisions when running in parallel:

| Approach | Prefix | Example Person | Example Client |
|----------|--------|----------------|----------------|
| curl | `Curl` | `Curl S2-ClientLifecycle` | `Curl-S2 Client` |
| java-manual | `Manual` | `Manual S2-ClientLifecycle` | `Manual-S2 Client` |
| java-generated | `Gen` | `Gen S2-ClientLifecycle` | `Gen-S2 Client` |

Email addresses follow the pattern `{prefix}-s{n}@example.com` (e.g., `curl-s2@example.com`, `manual-s4@example.com`).

## Unit tests (Java only)

| Test | Description |
|------|-------------|
| `SignatureVerificationTest` | HMAC-SHA256 compute + verify round-trip. No credentials needed. Runs with `mvn test`. |
