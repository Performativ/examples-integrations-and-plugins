# Scenarios

Seven core scenarios exercise the Performativ API v1 endpoints at increasing complexity. Each scenario is implemented in three ways — [curl](curl/), [java/manual](java/scenarios/) (raw HTTP), and [java/generated](java/scenarios/) (OpenAPI-generated typed client) — so the same operations are verified across approaches.

Every scenario acquires an OAuth2 token via `client_credentials` before calling the API.

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

Entity relationships: In v1, Person is linked to Client via the `/api/v1/client-persons` join resource (with `is_primary`). Client requires `currency_id`. Delete Client before Person (FK dependency; client-person link cascades with client deletion).

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
| Poll deliveries | GET | `/api/v1/plugins/{slug}/instances/{instanceId}/webhook-deliveries/poll?limit=50` | 200, `data` array |
| Find matching delivery | — | — | Delivery with `entity=Person`, `event=Created`, matching `entity_id` |
| Delete Person | DELETE | `/api/v1/persons/{id}` | 200 or 204 |

Requires `PLUGIN_SLUG` and `PLUGIN_INSTANCE_ID` in `.env`. The poll endpoint uses cursor-based pagination; this scenario reads the latest batch and searches for the expected delivery.

## S5: Bulk Ingestion

Create an async bulk ingestion batch and obtain a presigned upload URL via v1 endpoints.

| Step | Method | Path | Expected |
|------|--------|------|----------|
| Acquire token | POST | `{TOKEN_BROKER_URL}/oauth/token` | 200, `access_token` present |
| Create batch | POST | `/api/v1/bulk/async/batches` | 201, `data.batch_id` present |
| Get presigned URL | POST | `/api/v1/bulk/async/batches/{batchId}/presigned-url` | 200, `data.upload_url` present |

No entities are created or deleted. The batch is not started (no CSV uploaded).

## S6: Advisory Agreement

Full advisory agreement signing journey: upload a document, prepare a signing envelope, create an advice context with the client as member, create an advisory agreement linked to the envelope, walk it through the signing state machine (`draft` → `pending_signature` → `signed`), then close and clean up.

| Step | Method | Path | Expected |
|------|--------|------|----------|
| List advice policies | GET | `/api/v1/advice-policies` | 200, `data` array with at least one policy |
| Create Person | POST | `/api/v1/persons` | 201, `data.id` > 0 |
| Create Client | POST | `/api/v1/clients` | 201, `data.id` > 0 |
| Link Person to Client | POST | `/api/v1/client-persons` | 201, links person as primary |
| Upload document | POST | `/api/v1/documents` | 201, `data.id` > 0 (multipart file upload) |
| Create signing envelope | POST | `/api/v1/signing-envelopes` | 201, status `draft` |
| Add document to envelope | POST | `/api/v1/signing-envelopes/{id}/documents` | 201, role `source` |
| Add signer party | POST | `/api/v1/signing-envelopes/{id}/parties` | 201, role `signer` |
| Send envelope | POST | `/api/v1/signing-envelopes/{id}/send` | 200, status becomes `sent` |
| Create Advice Context | POST | `/api/v1/advice-contexts` | 201, `data.id` > 0, status `active` |
| Create Advisory Agreement | POST | `/api/v1/advice-contexts/{contextId}/agreements` | 201, status `draft`, linked via `signing_envelope_id` |
| Read Advisory Agreement | GET | `/api/v1/advisory-agreements/{id}` | 200, matches created |
| Submit signing | POST | `/api/v1/advisory-agreements/{id}/submit-signing` | 200, status becomes `pending_signature` |
| Mark Agreement Signed | POST | `/api/v1/advisory-agreements/{id}/mark-signed` | 200, status becomes `signed` |
| Read Agreement (signed) | GET | `/api/v1/advisory-agreements/{id}` | 200, status `signed` |
| Close Advice Context | POST | `/api/v1/advice-contexts/{id}/close` | 200 |

Agreement state machine: `draft` → `pending_signature` (via `submit-signing` with `document_id`) → `signed` (via `mark-signed` with `signed_document_id`).

Cleanup: Client and Person remain after the scenario completes. Once an advice context references these entities, they cannot be deleted via the API (FK constraint). Best-effort cleanup is attempted in `@AfterAll` / `trap EXIT`.

Signing envelope: the envelope groups documents and signer parties. Documents are uploaded via multipart POST to `/api/v1/documents`, then added to the envelope with role `source`. Parties reference a person with role `signer`. The envelope must be sent before the agreement can transition.

Entity relationships: Advice Context requires an `advice_policy_id` (looked up from the tenant's configured policies) and a `type` (`individual`). Members are added inline via the `members` array. Advisory Agreement belongs to an Advice Context and is linked to a Signing Envelope via `signing_envelope_id`.

## S7: Start Advise

Full advice session lifecycle: create all prerequisites, prepare a signed advisory agreement (via document upload and signing envelope), start an advice session, and walk it through the state machine (created → data_ready → active → ready_to_sign → signed). This is the most complex scenario and models the end-to-end journey of giving investment advice to a client.

A signed advisory agreement is **required** before a session can be created. This scenario therefore includes the full S6 signing flow as a prerequisite.

| Step | Method | Path | Expected |
|------|--------|------|----------|
| List advice policies | GET | `/api/v1/advice-policies` | 200, `data` array with at least one policy |
| Create Person | POST | `/api/v1/persons` | 201, `data.id` > 0 |
| Create Client | POST | `/api/v1/clients` | 201, `data.id` > 0 |
| Link Person to Client | POST | `/api/v1/client-persons` | 201, links person as primary |
| Create Portfolio | POST | `/api/v1/portfolios` | 201, `data.id` > 0 |
| Upload document | POST | `/api/v1/documents` | 201, `data.id` > 0 (multipart file upload) |
| Create signing envelope | POST | `/api/v1/signing-envelopes` | 201, status `draft` |
| Add document to envelope | POST | `/api/v1/signing-envelopes/{id}/documents` | 201, role `source` |
| Add signer party | POST | `/api/v1/signing-envelopes/{id}/parties` | 201, role `signer` |
| Send envelope | POST | `/api/v1/signing-envelopes/{id}/send` | 200, status becomes `sent` |
| Create Advice Context | POST | `/api/v1/advice-contexts` | 201, status `active` |
| Create Advisory Agreement | POST | `/api/v1/advice-contexts/{contextId}/agreements` | 201, status `draft`, linked via `signing_envelope_id` |
| Submit signing | POST | `/api/v1/advisory-agreements/{id}/submit-signing` | 200, status becomes `pending_signature` |
| Mark Agreement Signed | POST | `/api/v1/advisory-agreements/{id}/mark-signed` | 200, status becomes `signed` |
| Create Advice Session | POST | `/api/v1/advice-contexts/{contextId}/sessions` | 201, status `created` |
| Read Advice Session | GET | `/api/v1/advice-sessions/{id}` | 200, status `created` |
| Mark Data Ready | POST | `/api/v1/advice-sessions/{id}/data-ready` | 200, status becomes `data_ready` |
| Activate Session | POST | `/api/v1/advice-sessions/{id}/activate` | 200, status becomes `active`, `redirect_url` present |
| Mark Ready to Sign | POST | `/api/v1/advice-sessions/{id}/ready-to-sign` | 200, status becomes `ready_to_sign` |
| Mark Session Signed | POST | `/api/v1/advice-sessions/{id}/signed` | 200, status becomes `signed` |
| Read Session (final) | GET | `/api/v1/advice-sessions/{id}` | 200, status `signed` |
| Close Advice Context | POST | `/api/v1/advice-contexts/{id}/close` | 200 |
| Delete Portfolio | DELETE | `/api/v1/portfolios/{id}` | 200 or 204 |

Advisory agreement prerequisite: a signed agreement is required before creating a session. The agreement follows the same signing flow as S6 — document upload, signing envelope with signer party, submit-signing, mark-signed.

Advice session state machine: `created` → `data_ready` (plugin signals external data is loaded) → `active` (session activated with redirect URL for the advisor UI) → `ready_to_sign` (advice proposal is ready) → `signed` (client has signed). A session can be `abandoned` from any pre-signed state.

Cleanup: Portfolio can be deleted normally. Client and Person remain after the scenario completes — once an advice context references these entities, they cannot be deleted via the API (FK constraint). Best-effort cleanup is attempted in `@AfterAll` / `trap EXIT`.

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
