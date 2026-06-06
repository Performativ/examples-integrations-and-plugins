# Scenarios

Nine core scenarios exercise the Performativ API v1 endpoints at increasing complexity. Each scenario is implemented in three ways — [curl](curl/), [java/manual](java/scenarios/) (raw HTTP), and [java/generated](java/scenarios/) (OpenAPI-generated typed client) — so the same operations are verified across approaches.

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

## S4: External Holdings

Creates two persons with a relationship (married couple), links both to a client, adds a portfolio with a cash account, then creates external positions and balances.

| Step | Method | Path | Expected |
|------|--------|------|----------|
| Create Relationship Type | POST | `/api/v1/person-relationship-types` | 201 |
| Create Person A | POST | `/api/v1/persons` | 201 |
| Create Person B | POST | `/api/v1/persons` | 201 |
| Create Relationship (married) | POST | `/api/v1/person-relationships` | 201 |
| Create Client | POST | `/api/v1/clients` | 201 |
| Link Person A (primary) | POST | `/api/v1/client-persons` | 201 |
| Link Person B (secondary) | POST | `/api/v1/client-persons` | 201 |
| Create Portfolio | POST | `/api/v1/portfolios` | 201 |
| Create Cash Account | POST | `/api/v1/cash-accounts` | 201 |
| Link Cash Account to Portfolio | POST | `/api/v1/portfolio-cash-accounts` | 201 |
| Reference a pre-registered Instrument | GET | `/api/v1/instruments?per_page=1` | 200, `data[0].id` (the `instrument_id` to reference) |
| Create External Position | POST | `/api/v1/external-positions` | 201 (references a pre-registered `instrument_id`) |
| Create External Balance | POST | `/api/v1/external-balances` | 201 |
| Read External Position | GET | `/api/v1/external-positions/{id}` | 200 |
| Read External Balance | GET | `/api/v1/external-balances/{id}` | 200 |
| List Person Relationships | GET | `/api/v1/persons/{id}/relationships` | 200 |
| Update External Position | PUT | `/api/v1/external-positions/{id}` | 200 (quantity changed) |
| Cleanup (reverse order) | DELETE | all entities | 200/204 |

Entity relationships: Person Relationship Type defines the relationship kind (e.g., "married"). Person Relationship links two Persons via a type. Both persons are linked to the same Client via `client-persons`. Portfolio belongs to Client. Cash Account belongs to Client and is linked to Portfolio via `portfolio-cash-accounts`. External Position references a Portfolio and a **pre-registered Instrument** by `instrument_id` (instruments are pre-registered, typically via bulk file; `instrument_isin`/`instrument_name` are optional audit fields). External Balance references Client and a Cash Account. Delete in reverse creation order.

## S5: Webhook Delivery

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

## S6: Bulk Ingestion

Create an async bulk ingestion batch and obtain a presigned upload URL via v1 endpoints.

| Step | Method | Path | Expected |
|------|--------|------|----------|
| Acquire token | POST | `{TOKEN_BROKER_URL}/oauth/token` | 200, `access_token` present |
| Create batch | POST | `/api/v1/bulk/async/batches` | 201, `data.batch_id` present |
| Get presigned URL | POST | `/api/v1/bulk/async/batches/{batchId}/presigned-url` | 200, `data.upload_url` present |

No entities are created or deleted. The batch is not started (no CSV uploaded).

## S7: Advisory Agreement

Full advisory agreement signing journey told from the perspective of a **signing-provider plugin**. The plugin reacts to webhook events, discovers the advised clients via the client query API, attaches a signing envelope to the agreement, and walks the agreement through the state machine (`draft` → `pending_signature` → `signed`).

| Step | Method | Path | Expected |
|------|--------|------|----------|
| List advice policies | GET | `/api/v1/advice-policies` | 200, `data` array with at least one policy |
| Create Person | POST | `/api/v1/persons` | 201, `data.id` > 0 |
| Create Client | POST | `/api/v1/clients` | 201, `data.id` > 0 |
| Link Person to Client | POST | `/api/v1/client-persons` | 201, links person as primary |
| Create Advice Context | POST | `/api/v1/advice-contexts` | 201, status `active` _(webhook: AdviceContext.Created)_ |
| Query advice context clients | GET | `/api/v1/advice-contexts/{id}/clients` | 200, `data` array with client IDs (client-centric members) |
| Create Advisory Agreement | POST | `/api/v1/advice-contexts/{contextId}/agreements` | 201, status `draft` (snapshots context members) |
| Read Advisory Agreement | GET | `/api/v1/advice-agreements/{id}` | 200, status `draft` |
| Upload source document | POST | `/api/v1/documents` | 201, `data.id` > 0 (multipart file upload) |
| Create signing envelope | POST | `/api/v1/signing-envelopes` | 201, status `draft`, attached via `signable_type=advisory_agreement` + `signable_id` |
| Add document to envelope | POST | `/api/v1/signing-envelopes/{id}/documents` | 201, `ceremony_role` `input` |
| Add signer party | POST | `/api/v1/signing-envelopes/{id}/parties` | 201, role `signer` (person from `member_snapshot`) |
| Send envelope | POST | `/api/v1/signing-envelopes/{id}/send` | 200, status becomes `sent` |
| Submit signing | POST | `/api/v1/advice-agreements/{id}/submit-signing` | 200, status becomes `pending_signature` (no body) |
| Upload signed document | POST | `/api/v1/documents` | 201, `data.id` > 0 (multipart file upload) |
| Add signed document to envelope | POST | `/api/v1/signing-envelopes/{id}/documents` | 201, `ceremony_role` `output` |
| Mark signer party signed | POST | `/api/v1/signing-envelopes/{id}/parties/{partyId}/mark-signed` | 200, auto-completes envelope |
| Mark agreement signed | POST | `/api/v1/advice-agreements/{id}/mark-signed` | 200, status becomes `signed` |
| Read agreement (final) | GET | `/api/v1/advice-agreements/{id}` | 200, status `signed` |

Plugin perspective: the advice context is created **before** the agreement and envelope so the plugin can query `/advice-contexts/{id}/clients` to discover the advised clients. In v1 that member resource is client-centric — it exposes the `client_id` being advised but **no `person_id`**. The signer person is instead resolved from the agreement's `member_snapshot[]`, which the platform freezes at agreement creation with `{client_id, person_id, person_name, …}` for every member (creation fails if any member can't resolve to a person). In production, the plugin would receive an `AdviceContext.Created` webhook and read `member_snapshot` to populate signer parties dynamically.

Ordering: the advisory agreement is created **before** the signing envelope, because the envelope attaches to the agreement as its signable (`signable_type=advisory_agreement` + `signable_id`). The signed document must then be added to the envelope **before** marking the signer party as signed. Marking the last party auto-completes the envelope, which locks it — no documents can be added after that. (`cancel-signing` is the negative path, returning a `pending_signature` agreement to `draft` and voiding the envelope.)

Agreement state machine: `draft` → `pending_signature` (via `submit-signing`, no body) → `signed` (via `mark-signed`, optionally with `signed_document_id`).

Cleanup: expire agreement → delete agreement (cascade-deletes envelope) → delete advice context → delete client → delete person. Person may already be cascade-deleted with the client (404 is expected).

Signing envelope: the envelope groups documents and signer parties and attaches to a polymorphic signable. To-be-signed documents are uploaded via multipart POST to `/api/v1/documents`, then added to the envelope with `ceremony_role` `input`. The resulting signed documents are added with `ceremony_role` `output`. Parties reference a person with role `signer`. The envelope must be sent before the agreement can transition.

Entity relationships: Advice Context requires an `advice_policy_id` (looked up from the tenant's configured policies) and a `type` (`individual`). Clients are added inline via the `members` array. The Advisory Agreement belongs to an Advice Context; the Signing Envelope attaches to the agreement via `signable_type=advisory_agreement` + `signable_id`, so the agreement must exist first.

## S8: Start Advise

Full advice session lifecycle: create all prerequisites, prepare a signed advisory agreement (via document upload and signing envelope), start an advice session, and walk it through the state machine (created → data_ready → active → ready_to_sign → signed). This is the most complex scenario and models the end-to-end journey of giving investment advice to a client.

A signed advisory agreement is **required** before a session can be created. This scenario therefore includes the full S7 signing flow as a prerequisite.

| Step | Method | Path | Expected |
|------|--------|------|----------|
| List advice policies | GET | `/api/v1/advice-policies` | 200, `data` array with at least one policy |
| Create Person | POST | `/api/v1/persons` | 201, `data.id` > 0 |
| Create Client | POST | `/api/v1/clients` | 201, `data.id` > 0 |
| Link Person to Client | POST | `/api/v1/client-persons` | 201, links person as primary |
| Create Portfolio | POST | `/api/v1/portfolios` | 201, `data.id` > 0 |
| Create Advice Context | POST | `/api/v1/advice-contexts` | 201, status `active` |
| Create Advisory Agreement | POST | `/api/v1/advice-contexts/{contextId}/agreements` | 201, status `draft` (snapshots context members) |
| Upload document | POST | `/api/v1/documents` | 201, `data.id` > 0 (multipart file upload) |
| Create signing envelope | POST | `/api/v1/signing-envelopes` | 201, status `draft`, attached via `signable_type=advisory_agreement` + `signable_id` |
| Add document to envelope | POST | `/api/v1/signing-envelopes/{id}/documents` | 201, `ceremony_role` `input` |
| Add signer party | POST | `/api/v1/signing-envelopes/{id}/parties` | 201, role `signer` |
| Send envelope | POST | `/api/v1/signing-envelopes/{id}/send` | 200, status becomes `sent` |
| Submit signing | POST | `/api/v1/advice-agreements/{id}/submit-signing` | 200, status becomes `pending_signature` (no body) |
| Add signed document to envelope | POST | `/api/v1/signing-envelopes/{id}/documents` | 201, `ceremony_role` `output` |
| Mark signer party signed | POST | `/api/v1/signing-envelopes/{id}/parties/{partyId}/mark-signed` | 200, auto-completes envelope |
| Mark Agreement Signed | POST | `/api/v1/advice-agreements/{id}/mark-signed` | 200, status becomes `signed` |
| Create Advice Session | POST | `/api/v1/advice-contexts/{contextId}/sessions` | 201, status `created` |
| Read Advice Session | GET | `/api/v1/advice-sessions/{id}` | 200, status `created` |
| Mark Data Ready | POST | `/api/v1/advice-sessions/{id}/mark-data-ready` | 200, status becomes `data_ready` |
| Activate Session | POST | `/api/v1/advice-sessions/{id}/activate` | 200, status becomes `active` |
| Mark Ready to Sign | POST | `/api/v1/advice-sessions/{id}/mark-ready-to-sign` | 200, status becomes `ready_to_sign` |
| Mark Session Signed | POST | `/api/v1/advice-sessions/{id}/mark-signed` | 200, status becomes `signed` |
| Read Session (final) | GET | `/api/v1/advice-sessions/{id}` | 200, status `signed` |
| Delete Session | DELETE | `/api/v1/advice-sessions/{id}` | 200 or 204 |
| Expire Agreement | POST | `/api/v1/advice-agreements/{id}/expire` | 200, status becomes `expired` |
| Delete Agreement | DELETE | `/api/v1/advice-agreements/{id}` | 200 or 204 (cascade-deletes envelope) |
| Delete Advice Context | DELETE | `/api/v1/advice-contexts/{id}` | 200 or 204 |
| Delete Portfolio | DELETE | `/api/v1/portfolios/{id}` | 200 or 204 |
| Delete Client | DELETE | `/api/v1/clients/{id}` | 200 or 204 |
| Delete Person | DELETE | `/api/v1/persons/{id}` | 200 or 204 (may 404 if cascade-deleted with client) |

Advisory agreement prerequisite: a signed agreement is required before creating a session. The agreement follows the same signing flow as S7 — agreement created first, signing envelope attached to it via `signable_type`/`signable_id`, then submit-signing → ceremony → mark-signed.

Advice session state machine: `created` → `data_ready` (plugin signals external data is loaded) → `active` (session activated; the advisor-UI redirect is returned by the API on the activated session) → `ready_to_sign` (advice proposal is ready) → `signed` (client has signed). A session can be `abandoned` from any pre-signed state.

Cleanup: delete session → expire agreement → delete agreement (cascade-deletes envelope) → delete advice context → delete portfolio → delete client → delete person. Person may already be cascade-deleted with the client (404 is expected).

## S9: Error Responses

Verify that the API returns [RFC 7807 Problem Details](https://datatracker.ietf.org/doc/html/rfc7807) (`application/problem+json`) for error responses. Exercises validation errors and not-found errors without creating any entities.

| Step | Method | Path | Expected |
|------|--------|------|----------|
| Create Client (invalid) | POST | `/api/v1/clients` | 422, RFC 7807 with `type`, `title`, `status`, `detail`, `errors` |
| Read Client (non-existent) | GET | `/api/v1/clients/0` | 404, RFC 7807 with `type`, `title`, `status`, `detail` |

No entities are created or deleted — no cleanup needed.

### Error response format

The API uses RFC 7807 Problem Details for all error responses. Required fields: `type`, `title`, `status`, `detail`. Validation errors (422) include an `errors` object with field-level messages.

**Validation error (422)**:

```json
{
  "type": "https://tools.ietf.org/html/rfc2616#section-10",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "The given data was invalid.",
  "errors": {
    "name": ["The name field is required."],
    "type": ["The type field is required."],
    "currency_id": ["The currency id field is required."]
  }
}
```

**Not found (404)**:

```json
{
  "type": "https://tools.ietf.org/html/rfc2616#section-10",
  "title": "Not Found",
  "status": 404,
  "detail": "The requested resource was not found."
}
```

Optional fields that may appear: `request_id`, `code`, `meta`.

## Test data conventions

Each implementation uses a distinct prefix to avoid collisions when running in parallel:

| Approach | Prefix | Example Person | Example Client |
|----------|--------|----------------|----------------|
| curl | `Curl` | `Curl S2-ClientLifecycle` | `Curl-S2 Client` |
| java-manual | `Manual` | `Manual S2-ClientLifecycle` | `Manual-S2 Client` |
| java-generated | `Gen` | `Gen S2-ClientLifecycle` | `Gen-S2 Client` |

Email addresses follow the pattern `{prefix}-s{n}@example.com` (e.g., `curl-s2@example.com`, `manual-s5@example.com`).

## Unit tests (Java only)

| Test | Description |
|------|-------------|
| `SignatureVerificationTest` | HMAC-SHA256 compute + verify round-trip. No credentials needed. Runs with `mvn test`. |
