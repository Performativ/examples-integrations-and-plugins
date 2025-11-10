# Webhook Setup

This guide covers how to receive webhooks from Performativ and verify their HMAC signatures.

## Overview

When entities change in Performativ, your plugin receives HTTP POST requests at the configured webhook URL. Each request contains:

- A JSON payload describing the event
- HTTP headers with tenant metadata
- An HMAC-SHA256 signature for verification (when a signing key is configured)

## Endpoint Requirements

Your webhook endpoint must:

1. Accept **POST** requests with **JSON** bodies
2. Return **HTTP 200-299** to acknowledge receipt
3. Respond within **5 seconds** (process asynchronously for longer tasks)
4. Be accessible over **HTTPS**

## HTTP Headers

Every webhook request includes these headers:

| Header | Required | Description |
|--------|----------|-------------|
| `Content-Type` | Yes | Always `application/json` |
| `x-tenant` | Yes | Tenant identifier (lowercase) |
| `x-api-domain` | Yes | API domain for the tenant |
| `x-tenant-reference-id` | No | Your reference ID (if configured) |
| `x-webhook-signature` | No | HMAC-SHA256 signature (if signing key is configured) |

## Payload Structure

All webhook payloads follow this schema:

```json
{
  "event_id": "550e8400-e29b-41d4-a716-446655440000",
  "entity": "Client",
  "entity_id": 12345,
  "event": "Created",
  "updated_at": "2024-01-15T10:30:00.000000Z",
  "url": "https://api.example.com/api/clients/12345",
  "custom_config": {
    "tenant_reference_id": "ref-123"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `event_id` | UUID | Stable event identifier -- use for idempotency |
| `entity` | string | Entity type (e.g. "Client", "Portfolio") |
| `entity_id` | integer | Entity ID |
| `event` | string | "Created", "Updated", "Deleted", "Activated", "Deactivated", or "DailyHeartBeat" |
| `updated_at` | ISO 8601 | Timestamp of the change |
| `url` | string or null | API URL to fetch the full entity (null for some entity types) |
| `custom_config` | object | Instance-specific configuration |

## HMAC Signature Verification

### How It Works

1. Performativ computes HMAC-SHA256 of the JSON request body using your signing key
2. The hex-encoded signature is sent in the `x-webhook-signature` header
3. Your endpoint recomputes the HMAC and compares it to the header value

### Java Example

```java
import com.performativ.plugin.SignatureVerifier;

SignatureVerifier verifier = new SignatureVerifier("your-signing-key");

// In your controller:
boolean valid = verifier.verify(rawRequestBodyBytes, signatureHeader);
if (!valid) {
    return ResponseEntity.status(401).body("Invalid signature");
}
```

### Manual Verification (Any Language)

```
expected = HMAC-SHA256(signing_key, raw_json_body)
actual   = request.headers["x-webhook-signature"]
valid    = constant_time_equals(hex(expected), actual)
```

Important:
- Compute the HMAC on the **raw request body bytes**, not on a re-serialized JSON object
- Use **constant-time comparison** to prevent timing attacks
- If the `x-webhook-signature` header is absent, the webhook is unsigned (this is valid when no signing key is configured)

## Idempotency

Webhooks use at-least-once delivery. The same event may be delivered multiple times. Always check `event_id`:

```java
Set<String> processedEvents = ConcurrentHashMap.newKeySet();

if (!processedEvents.add(eventId)) {
    // Already processed - return 200 and skip
    return ResponseEntity.ok(Map.of("status", "ok"));
}
```

In production, store processed event IDs in a database or cache with a TTL.

## Event Ordering

Webhook delivery order is **not guaranteed**. Events for the same entity may arrive out of order. To handle this:

1. Compare `updated_at` timestamps with your last known state
2. Skip events with an older timestamp
3. For critical data, fetch the latest state from the API using the `url` field

## Best Practices

- **Return 200 immediately**, then process asynchronously
- **Verify signatures** when a signing key is configured
- **Implement idempotency** using `event_id`
- **Handle out-of-order events** using `updated_at`
- **Use HTTPS** for your webhook endpoint
- **Log everything** including `event_id` for debugging

## Next Steps

- See the [Java webhook receiver example](../java/webhook-receiver/) for a complete implementation
- Learn about [API access with client_secret](api-access-client-secret.md)
- Review the [webhook event types](webhook-events.md)
