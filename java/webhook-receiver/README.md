# Java Webhook Receiver Example

A Spring Boot application that demonstrates how to receive webhooks from Performativ, verify HMAC signatures, and access the API using OAuth2 `client_credentials`.

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker (optional, for containerized deployment)

## Quick Start

1. Set your environment variables:

```bash
export WEBHOOK_SIGNING_KEY="your-signing-key"
```

2. Run the application:

```bash
mvn spring-boot:run
```

The webhook endpoint will be available at `http://localhost:8080/webhook`.

## Docker

```bash
docker compose up --build
```

## What's Included

| Class | Purpose |
|-------|---------|
| `WebhookController` | POST `/webhook` endpoint with HMAC verification and idempotency |
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

JsonNode data = client.get("/api/clients/123");
```

The client handles token acquisition and caching automatically.

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
