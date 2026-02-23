# Java Webhook Receiver Example

A Spring Boot application that demonstrates how to receive webhooks from Performativ, verify HMAC signatures, and access the API using OAuth2 `client_credentials`.

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker (optional, for containerized deployment)

## Running Without Docker

Docker is optional — you only need Java and Maven.

### 1. Build the JAR

```bash
mvn package -DskipTests
```

This produces `target/plugin-webhook-receiver-1.0.0-SNAPSHOT.jar`.

### 2a. Configure credentials

Copy the shared credential template and fill in your values:

```bash
cp ../../.env.example ../../.env
# Edit ../../.env with your credentials
```

Credentials are loaded automatically from the repo root `.env` file via `spring-dotenv`. See [`.env.example`](../../.env.example) for all available settings.

### 2b. Receive webhooks (push mode)

```bash
java -jar target/plugin-webhook-receiver-1.0.0-SNAPSHOT.jar
```

The webhook endpoint will be available at `http://localhost:8080/webhook`.

Or skip the JAR step and run directly with Maven:

```bash
mvn spring-boot:run
```

### 2c. Poll for events (pull mode)

If your machine can't receive inbound connections, run the poller instead:

```bash
POLLER_ENABLED=true java -jar target/plugin-webhook-receiver-1.0.0-SNAPSHOT.jar
```

The poller and the webhook endpoint run in the same process — you can enable both at once. They share event-level deduplication so nothing gets processed twice.

### Configuration

All settings are controlled via environment variables. You can also put them in `src/main/resources/application.properties` (rebuild after editing) or pass them as flags:

```bash
java -jar target/plugin-webhook-receiver-1.0.0-SNAPSHOT.jar \
  --poller.enabled=true \
  --plugin.slug=my-plugin \
  --plugin.instance-id=42
```

| Environment Variable | Property | Default | Description |
|---|---|---|---|
| `WEBHOOK_SIGNING_KEY` | `webhook.signing-key` | _(empty)_ | HMAC key for signature verification |
| `PLUGIN_CLIENT_ID` | `plugin.client-id` | _(empty)_ | OAuth2 client ID |
| `PLUGIN_CLIENT_SECRET` | `plugin.client-secret` | _(empty)_ | OAuth2 client secret |
| `TOKEN_BROKER_URL` | `token.broker-url` | _(empty)_ | Token endpoint base URL |
| `API_BASE_URL` | `api.base-url` | _(empty)_ | Performativ API base URL |
| `TOKEN_AUDIENCE` | `token.audience` | `backend-api` | Token audience |
| `POLLER_ENABLED` | `poller.enabled` | `false` | Enable the delivery poller |
| `POLLER_INTERVAL_MS` | `poller.interval-ms` | `10000` | Polling interval in milliseconds |
| `POLLER_BATCH_SIZE` | `poller.batch-size` | `50` | Max deliveries per poll request |
| `POLLER_SINCE` | `poller.since` | _(empty)_ | ISO-8601 timestamp for initial poll (e.g. `2026-02-18T00:00:00Z`) |
| `POLLER_INCLUDE_SIGNATURE` | `poller.include-signature` | `false` | Request HMAC signatures with deliveries |
| `PLUGIN_SLUG` | `plugin.slug` | _(empty)_ | Plugin identifier (poller only) |
| `PLUGIN_INSTANCE_ID` | `plugin.instance-id` | `0` | Plugin instance ID (poller only) |

### Where to add your business logic

Edit `WebhookEventProcessor.java` — the `processEvent()` method has a switch on event type. Replace the log statements with your own logic (API calls, database writes, etc.).

## Docker

Docker wraps the same JAR in a container. Useful for consistent environments or for the Cloudflare Tunnel sidecar.

```bash
docker compose up --build
```

## What's Included

| Class | Purpose |
|-------|---------|
| `WebhookController` | POST `/webhook` endpoint with HMAC verification |
| `WebhookEventProcessor` | Shared event processing and idempotency (used by controller and poller) |
| `WebhookPoller` | Polls the delivery API for new events (alternative to receiving POSTs) |
| `SignatureVerifier` | HMAC-SHA256 signature computation and constant-time verification |
| `PluginApiClient` | OAuth2 `client_credentials` token acquisition and API calls |
| `WebhookDeliveryClient` | List and replay webhook deliveries |

## Signature Verification

Performativ signs webhook payloads using HMAC-SHA256. The signature is sent in the `x-webhook-signature` header as a 64-character lowercase hex string.

```java
SignatureVerifier verifier = new SignatureVerifier("your-signing-key");
boolean valid = verifier.verify(rawRequestBody, signatureHeader);
```

## API Access with client_secret

Use `PluginApiClient` to call the Performativ API using OAuth2 `client_credentials` with `client_secret_basic` authentication:

```java
PluginApiClient client = new PluginApiClient(
    "https://token-broker.example.com",
    "https://api.example.com",
    "your-client-id",
    "your-client-secret",
    "backend-api"
);

JsonNode data = client.get("/api/v1/clients/123");
```

The client handles token acquisition and caching automatically.

## Local Testing

If your machine can't receive inbound connections, there are two options:

**Webhook Poller** (no Docker required) — polls the delivery API for events, works behind any firewall:

```bash
# With the JAR
POLLER_ENABLED=true java -jar target/plugin-webhook-receiver-1.0.0-SNAPSHOT.jar

# Or with Maven
POLLER_ENABLED=true mvn spring-boot:run
```

**Cloudflare Tunnel** (requires Docker) — a sidecar creates a temporary public URL:

```bash
docker compose --profile tunnel up --build
```

See [Testing Webhooks Locally](../../docs/testing-webhooks-locally.md) for full setup instructions.

## Webhook Delivery Monitoring

Use `WebhookDeliveryClient` to list and replay deliveries (requires a user JWT):

```java
WebhookDeliveryClient deliveries = new WebhookDeliveryClient(
    "https://api.example.com",
    bearerToken,
    "my-plugin",
    42
);

JsonNode list = deliveries.listDeliveries();
JsonNode result = deliveries.replayDelivery(123);
```
