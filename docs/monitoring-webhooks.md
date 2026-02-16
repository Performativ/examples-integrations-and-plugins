# Monitoring Webhook Deliveries

Track delivery status and replay failed webhooks using the Performativ API.

## Overview

Every webhook sent to your plugin is tracked as a **delivery record**. You can:

- List deliveries to see their status, attempt count, and HTTP response codes
- Replay failed deliveries to re-trigger them

These endpoints are accessed by tenant users (via the Performativ UI or user JWT), not by plugin credentials.

## Listing Deliveries

```http
GET /api/plugins/{plugin}/instances/{instance}/webhook-deliveries
Authorization: Bearer {user-jwt}
Accept: application/json
```

### Response

```json
{
  "data": [
    {
      "id": 1,
      "event_id": "550e8400-e29b-41d4-a716-446655440000",
      "entity": "Client",
      "entity_id": 12345,
      "event": "Created",
      "status": "delivered",
      "attempts": 1,
      "last_http_status": 200,
      "created_at": "2024-01-15T10:30:05.000000Z",
      "delivered_at": "2024-01-15T10:30:06.000000Z"
    },
    {
      "id": 2,
      "event_id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
      "entity": "Portfolio",
      "entity_id": 67890,
      "event": "Updated",
      "status": "failed",
      "attempts": 5,
      "last_http_status": 503,
      "created_at": "2024-01-15T14:22:20.000000Z",
      "delivered_at": null
    }
  ]
}
```

### Delivery Statuses

| Status | Description |
|--------|-------------|
| `pending` | Queued for delivery, not yet attempted or waiting for retry |
| `delivered` | Successfully delivered (HTTP 2xx received) |
| `failed` | All retry attempts exhausted |

## Replaying a Delivery

Re-queue a failed delivery for another attempt:

```http
POST /api/plugins/{plugin}/instances/{instance}/webhook-deliveries/{delivery}/replay
Authorization: Bearer {user-jwt}
```

This resets the delivery for a new round of attempts.

## Java Example

```java
WebhookDeliveryClient client = new WebhookDeliveryClient(
    "https://api.example.com",
    bearerToken,
    "my-plugin",
    42
);

// List all deliveries
JsonNode deliveries = client.listDeliveries();

// Find failed deliveries and replay them
for (JsonNode delivery : deliveries.path("data")) {
    if ("failed".equals(delivery.path("status").asText())) {
        int deliveryId = delivery.path("id").asInt();
        client.replayDelivery(deliveryId);
        System.out.println("Replayed delivery " + deliveryId);
    }
}
```

See [WebhookDeliveryClient.java](../java/webhook-receiver/src/main/java/com/performativ/plugin/WebhookDeliveryClient.java) for the full implementation.

## Retry Behavior

Automatic retries use exponential backoff over approximately 48 hours:

- **Retryable errors**: Timeouts, HTTP 429 (rate limit), HTTP 5xx (server errors)
- **Non-retryable errors**: HTTP 4xx client errors (except 408/425/429) fail immediately

After automatic retries are exhausted, the delivery is marked as `failed`. Use the replay endpoint to retry manually.

## Monitoring Best Practices

1. **Check delivery status periodically** to catch persistent failures
2. **Replay failed deliveries** after fixing endpoint issues
3. **Monitor `attempts` count** -- high attempt counts indicate reliability issues
4. **Track `last_http_status`** to diagnose the type of failure
5. **Set up alerts** for repeated failures on the same entity type
