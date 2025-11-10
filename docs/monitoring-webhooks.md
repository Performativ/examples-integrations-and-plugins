# Monitoring Webhook Deliveries

Track delivery status and replay failed webhooks.

## Overview

Every webhook sent to your plugin is tracked as a **delivery record**. You can:

- View delivery status, attempt count, and HTTP response codes
- Replay failed deliveries to re-trigger them

## Using the Admin UI

The primary way to monitor deliveries is through the Performativ admin panel:

1. Navigate to **Plugins > Custom**
2. Open your plugin
3. Click **Webhook Deliveries**

From the deliveries view, you can:

- See all deliveries with their status (`pending`, `delivered`, `failed`)
- Filter by status or entity type
- View delivery details including HTTP response codes and attempt count
- Click **Replay** on any failed delivery to re-trigger it

## Programmatic Access (Advanced)

For automated monitoring or integration into your own tooling, the delivery endpoints are also available via the API using tenant user credentials.

### Listing Deliveries

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

### Replaying a Delivery

```http
POST /api/plugins/{plugin}/instances/{instance}/webhook-deliveries/{delivery}/replay
Authorization: Bearer {user-jwt}
```

This resets the delivery for a new round of attempts.

### Java Example

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

### Delivery Statuses

| Status | Description |
|--------|-------------|
| `pending` | Queued for delivery, not yet attempted or waiting for retry |
| `delivered` | Successfully delivered (HTTP 2xx received) |
| `failed` | All retry attempts exhausted |

## Retry Behavior

Automatic retries use exponential backoff over approximately 48 hours:

- **Retryable errors**: Timeouts, HTTP 429 (rate limit), HTTP 5xx (server errors)
- **Non-retryable errors**: HTTP 4xx client errors (except 408/425/429) fail immediately

After automatic retries are exhausted, the delivery is marked as `failed`. Use the replay button in the UI (or the API endpoint) to retry manually.

## Monitoring Best Practices

1. **Check delivery status periodically** in the admin panel to catch persistent failures
2. **Replay failed deliveries** after fixing endpoint issues
3. **Monitor `attempts` count** -- high attempt counts indicate reliability issues
4. **Track `last_http_status`** to diagnose the type of failure
5. **Set up alerts** for repeated failures on the same entity type
