# Performativ Examples, Integrations & Plugins

Code examples and documentation for building integrations with the Performativ platform.

## Documentation

| Guide | Description |
|-------|-------------|
| [Getting Started](docs/getting-started.md) | Register a plugin and activate it for a tenant |
| [Webhook Setup](docs/webhook-setup.md) | Receive webhooks and verify HMAC signatures |
| [API Access (Client Secret)](docs/api-access-client-secret.md) | OAuth2 `client_credentials` with `client_secret_basic` |
| [API Access (mTLS)](docs/api-access-mtls.md) | FAPI 2.0 mutual TLS authentication (advanced) |
| [Webhook Events](docs/webhook-events.md) | Event types, payloads, and supported entities |
| [Monitoring Webhooks](docs/monitoring-webhooks.md) | List deliveries and replay failed webhooks |
| [Zapier Integration](docs/zapier-integration.md) | No-code integration via Zapier |

## Code Examples

### Java

| Example | Description |
|---------|-------------|
| [Webhook Receiver](java/webhook-receiver/) | Spring Boot app: POST `/webhook` with HMAC verification, OAuth2 API client, delivery monitoring |
| [Bulk Ingestion](java/) | OpenAPI-generated client for async bulk data ingestion |

### Zapier

| Example | Description |
|---------|-------------|
| [Zapier Integration](zapier/) | Webhooks trigger + Code action for no-code workflows |

## Quick Start

### Receive Webhooks (Java)

```bash
cd java/webhook-receiver
export WEBHOOK_SIGNING_KEY="your-signing-key"
mvn spring-boot:run
```

Your endpoint is at `http://localhost:8080/webhook`.

### Call the API (Java)

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

## Authentication Methods

| Method | Use Case | Setup |
|--------|----------|-------|
| `client_secret_basic` | Standard integrations | Client ID + Secret, simple OAuth2 flow |
| `tls_client_auth` | Regulated environments | mTLS with client certificates, FAPI 2.0 grade |

## License

Proprietary. For use by Performativ customers and integration partners.
