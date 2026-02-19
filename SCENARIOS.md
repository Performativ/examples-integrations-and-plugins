# Scenarios

Three core scenarios exercise the Performativ API at increasing complexity. Each scenario is implemented in three ways — [curl](curl/), [java/manual](java/scenarios/) (raw HTTP), and [java/generated](java/scenarios/) (OpenAPI-generated typed client) — so the same operations are verified across approaches.

Every scenario acquires an OAuth2 token via `client_credentials` before calling the API.

## S1: API Access

Verify that credentials work and the API is reachable.

| Step | Method | Path | Expected |
|------|--------|------|----------|
| Acquire token | POST | `{TOKEN_BROKER_URL}/oauth/token` | 200, `access_token` present |
| List clients | GET | `/api/clients` | 200, JSON with `data` array |

No entities are created or deleted.

## S2: Client Lifecycle

Create a Person and Client, read them back, update the Client, then delete both.

| Step | Method | Path | Expected |
|------|--------|------|----------|
| Create Person | POST | `/api/persons` | 201 or 200, `data.id` > 0 |
| Create Client | POST | `/api/clients` | 201 or 200, `data.id` > 0 |
| Read Client | GET | `/api/clients/{id}` | 200, matches created |
| Update Client | PUT | `/api/clients/{id}` | 200, name updated |
| Read Person | GET | `/api/persons/{id}` | 200, matches created |
| Delete Client | DELETE | `/api/clients/{id}` | 200 or 204 |
| Delete Person | DELETE | `/api/persons/{id}` | 200 or 204 |

Entity relationships: Client references Person via `primary_person_id`. Delete Client before Person (FK dependency).

Webhook verification is optional — when `WEBHOOK_RECEIVER_URL` is configured, the scenario verifies that `Person.Created` and `Client.Created` events were delivered.

## S3: Portfolio Setup

Create the full prerequisite chain (Person, Client, Portfolio), read them back, update the Portfolio, then delete all in reverse order.

| Step | Method | Path | Expected |
|------|--------|------|----------|
| Create Person | POST | `/api/persons` | 201 or 200, `data.id` > 0 |
| Create Client | POST | `/api/clients` | 201 or 200, `data.id` > 0 |
| Create Portfolio | POST | `/api/clients/{clientId}/portfolios` | 201 or 200, `data.id` > 0 |
| Read Portfolio | GET | `/api/portfolios/{id}` | 200, matches created |
| Update Portfolio | PUT | `/api/portfolios/{id}` | 200, name updated |
| Delete Portfolio | DELETE | `/api/portfolios/{id}` | 200 or 204 |
| Delete Client | DELETE | `/api/clients/{id}` | 200 or 204 |
| Delete Person | DELETE | `/api/persons/{id}` | 200 or 204 |

Entity relationships: Portfolio belongs to Client (`currency_id` = 47 / EUR). Client references Person. Delete in reverse order.

## Test data conventions

Each scenario uses distinctive entity names to aid debugging when cleanup fails:

| Scenario | Person name | Client name |
|----------|-------------|-------------|
| S1 | — | — |
| S2 | `Scenario` `ClientLifecycle` | `Scenario-S2 Client` |
| S3 | `Scenario` `PortfolioSetup` | `Scenario-S3 Client` |

## Standalone scenarios (outside the matrix)

| Scenario | Description |
|----------|-------------|
| `BulkIngestionScenario` | Uses `API_KEY` auth (not OAuth2). Tests async ingestion task setup. |
| `SignatureVerificationTest` | Unit test for HMAC-SHA256 signature algorithm. No credentials needed. |
