# Webhook Event Types and Payloads

Reference for all webhook event types, supported entities, and payload formats.

## Event Types

### Entity Events

| Event | Description |
|-------|-------------|
| `Created` | A new entity was created. `updated_at` equals `created_at`. |
| `Updated` | An entity was modified. Multiple rapid updates may coalesce into a single event. |
| `Deleted` | An entity was deleted. `updated_at` is the last timestamp before deletion. |

### Plugin Lifecycle Events

| Event | Entity | Description |
|-------|--------|-------------|
| `Activated` | `Plugin` | The plugin was activated for this tenant. Sent once on activation. |
| `Deactivated` | `Plugin` | The plugin was deactivated. Sent once on deactivation. |
| `DailyHeartBeat` | `Overall` | Daily heartbeat to verify the endpoint is alive. Sent once per day. |

## Supported Entities

You can subscribe to events for any of these entity types:

| Entity | API URL Pattern | Description |
|--------|----------------|-------------|
| `Client` | `/api/v1/clients/{id}` | Client records |
| `Portfolio` | `/api/v1/portfolios/{id}` | Investment portfolios |
| `Person` | `/api/v1/persons/{id}` | Person records |
| `Business` | `/api/v1/businesses/{id}` | Business entities |
| `Group` | `/api/v1/groups/{id}` | Client groups |
| `User` | `/api/v1/users/{id}` | User accounts |
| `CashAccount` | `/api/v1/cash-accounts/{id}` | Cash accounts |
| `CashAccountMovement` | null | Cash account movements (no direct URL) |
| `Transaction` | `/api/v1/transactions/{id}` | Financial transactions |
| `Instrument` | `/api/v1/instruments/{id}` | Financial instruments |
| `Order` | `/api/v1/orders/{id}` | Trading orders |
| `OrderBatch` | `/api/v1/order-batches/{id}` | Order batches |
| `RebalancingProposal` | `/api/v1/rebalancing-proposals/{id}` | Rebalancing proposals |
| `ModelPortfolio` | `/api/v1/model-portfolios/{id}` | Model portfolios |
| `Document` | null | Documents (no direct URL) |
| `Report` | null | Reports (no direct URL) |
| `OnboardingLink` | `/api/v1/onboarding-links/{id}` | Onboarding links |
| `CustomFieldValue` | null | Custom field values (no direct URL) |

Entities with `null` URL do not have a direct show endpoint. Use the index endpoint with filters to query them.

## Payload Schema

### Standard Event

```json
{
  "event_id": "550e8400-e29b-41d4-a716-446655440000",
  "entity": "Client",
  "entity_id": 12345,
  "event": "Created",
  "updated_at": "2024-01-15T10:30:00.000000Z",
  "url": "https://api.example.com/api/v1/clients/12345",
  "custom_config": {
    "tenant_reference_id": "ref-123",
    "custom_setting": "value"
  }
}
```

### Plugin Lifecycle Event

```json
{
  "event_id": "7ba7b812-9dad-11d1-80b4-00c04fd430c8",
  "entity": "Plugin",
  "entity_id": 0,
  "event": "Activated",
  "updated_at": "2024-01-15T12:00:00.000000Z",
  "url": null
}
```

### DailyHeartBeat Event

```json
{
  "event_id": "8ca8c913-aeae-22e2-91c5-11d05ge541d9",
  "entity": "Overall",
  "entity_id": 0,
  "event": "DailyHeartBeat",
  "updated_at": "2024-01-15T00:00:00.000000Z",
  "url": null
}
```

## Field Reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `event_id` | UUID | Yes | Stable identifier for idempotency checks |
| `entity` | string | Yes | Entity type (see table above) |
| `entity_id` | integer | Yes | Entity ID (0 for lifecycle events) |
| `event` | string | Yes | Event type (see table above) |
| `updated_at` | ISO 8601 | Yes | Timestamp of the change |
| `url` | string or null | Yes | API URL to fetch the entity, null for some types |
| `custom_config` | object | No | Instance config (includes `tenant_reference_id` if set) |

## Event Coalescing

Multiple rapid `Updated` events for the same entity are coalesced into a single webhook. Only the latest state is delivered.

`Created` and `Deleted` events are **never coalesced** and are always delivered individually.

## Event Ordering

Delivery order is **not guaranteed**. Use `updated_at` timestamps for staleness detection. See [Webhook Setup](webhook-setup.md) for handling strategies.
