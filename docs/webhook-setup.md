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
| `x-webhook-timestamp` | Yes (when signing is enabled) | Unix epoch seconds at which the delivery was enqueued. Required for signature verification. |
| `x-webhook-signature` | Yes (when signing is enabled) | Hex-encoded `HMAC_SHA256(signing_key, "{timestamp}.{raw_body}")` |

## Payload Structure

All webhook payloads follow this schema:

```json
{
  "event_id": "550e8400-e29b-41d4-a716-446655440000",
  "entity": "Client",
  "entity_id": 12345,
  "event": "Created",
  "updated_at": "2024-01-15T10:30:00.000000Z",
  "url": "https://api.example.com/api/v1/clients/12345",
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

Performativ signs each delivery with HMAC-SHA256 over the delivery timestamp, a literal `.` separator, and the raw JSON request body, in that exact order:

```
signature = HMAC-SHA256(signing_key, "{x-webhook-timestamp}.{raw_body}")
```

Both the timestamp and the hex-encoded signature are sent on every delivery, in the `x-webhook-timestamp` and `x-webhook-signature` headers. The receiver recomputes the HMAC using the same inputs and compares it to the header value.

Prepending the timestamp binds the signature to one delivery, so a captured `(timestamp, body, signature)` tuple cannot be re-signed onto a different body.

**On freshness windows.** `x-webhook-timestamp` is the moment the delivery was *enqueued*, not the moment it was sent, and it does not change when a delivery is retried. A failing delivery is retried eight times over roughly 24 hours, so a short window would reject every retry — exactly the deliveries that follow a failure.

The examples here use **26 hours**: long enough to cover the full retry schedule plus clock skew. That is a backstop against very old captures, not meaningful replay protection. **Your real defence against replay is idempotency** — record the delivery id and ignore one you have already processed. See [Idempotency](#idempotency) below.

### Java Example

```java
import com.performativ.plugin.SignatureVerifier;

SignatureVerifier verifier = new SignatureVerifier("your-signing-key");

// In your controller:
boolean valid = verifier.verify(rawRequestBodyBytes, timestampHeader, signatureHeader);
if (!valid) {
    return ResponseEntity.status(401).body("Invalid signature");
}
```

`SignatureVerifier` checks four things in order: the signature header is present, the timestamp header is present and parseable, the timestamp is inside the freshness window, and the HMAC of `{timestamp}.{raw_body}` matches the header value (via constant-time comparison). If any of those fails, it returns `false`.

### Manual Verification (Any Language)

```
if abs(now_epoch_seconds - int(timestamp_header)) > 93600:   # 26h, covers retries
    reject                                          # outside the freshness window
signed_content = timestamp_header + "." + raw_body  # concatenate bytes, no re-serialise
expected       = HMAC-SHA256(signing_key, signed_content)
actual         = request.headers["x-webhook-signature"]
valid          = constant_time_equals(hex(expected), actual)
```

Important:
- Read the **raw request body bytes** before any JSON parsing. Re-serialising the body produces a different hash even when semantically equivalent.
- Use **constant-time comparison** to prevent timing attacks.
- Enforce the freshness window before computing the HMAC. A delivery with a stale timestamp should be rejected even if the signature would otherwise have matched.
- Do not shorten the window below the retry horizon (~24h) unless you are certain you do not need retried deliveries. Use delivery-id idempotency for replay protection instead.
- If neither `x-webhook-timestamp` nor `x-webhook-signature` is present, the webhook is unsigned (this is valid only when no signing key is configured for the plugin).

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
