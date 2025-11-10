# Performativ Examples, Integrations & Plugins

Code examples and documentation for building integrations with the Performativ platform.

## Documentation

| Guide | Description |
|-------|-------------|
| [Getting Started](docs/getting-started.md) | Create a custom plugin and collect credentials |
| [Webhook Setup](docs/webhook-setup.md) | Receive webhooks and verify HMAC signatures |
| [API Access (Client Secret)](docs/api-access-client-secret.md) | OAuth2 `client_credentials` with `client_secret_basic` |
| [API Access (mTLS)](docs/api-access-mtls.md) | FAPI 2.0 mutual TLS authentication (advanced) |
| [Webhook Events](docs/webhook-events.md) | Event types, payloads, and supported entities |
| [Monitoring Webhooks](docs/monitoring-webhooks.md) | List deliveries and replay failed webhooks |
| [Testing Webhooks Locally](docs/testing-webhooks-locally.md) | Cloudflare Tunnel and delivery-polling approaches for local development |
## Code Examples

### Java

| Example | Description |
|---------|-------------|
| [Webhook Receiver](java/webhook-receiver/) | Spring Boot app: POST `/webhook` with HMAC verification, OAuth2 API client, delivery monitoring |
| [Bulk Ingestion](java/bulk-ingestion/) | OpenAPI-generated client for async bulk data ingestion |
| [Integration Scenarios](java/scenarios/) | Automated tests that exercise the API, webhooks, and ingestion |

### curl

| Example | Description |
|---------|-------------|
| [Client Lifecycle](curl/client-lifecycle.sh) | Token → create Person & Client → read back → delete (loads credentials from `.env`) |

## Quick Start

### 1. Create a Custom Plugin

In your Performativ tenant, navigate to **Plugins > Custom** and create a new plugin:

1. Give it a name, optional webhook URL, and select entity types (e.g., `Client`)
2. Save the **signing key** shown after creation (displayed once)
3. Enable **API Access** and copy the **credential bundle** (Client ID, Client Secret, Token Endpoint)

### 2. Configure credentials

```bash
cp .env.example .env
```

Fill in the values from your credential bundle:

| `.env` variable | Where to find it |
|-----------------|------------------|
| `API_BASE_URL` | Your tenant's web URL, prefixed with `api.` — e.g., if your tenant is at `https://acme.sandbox.onperformativ.com`, then `API_BASE_URL=https://api.acme.sandbox.onperformativ.com` |
| `TOKEN_BROKER_URL` | Same as `API_BASE_URL` with `/token-broker` appended — e.g., `https://api.acme.sandbox.onperformativ.com/token-broker`. The full token endpoint is `{TOKEN_BROKER_URL}/oauth/token`. |
| `PLUGIN_CLIENT_ID` | From the credential bundle |
| `PLUGIN_CLIENT_SECRET` | From the credential bundle |
| `TOKEN_AUDIENCE` | From the credential bundle (typically `backend-api`) |
| `WEBHOOK_SIGNING_KEY` | Shown when creating the plugin (displayed once) |
| `PLUGIN_INSTANCE_ID` | From the credential bundle (the `instance_id` field) |

### 3. Receive Webhooks (Java)

```bash
cd java/webhook-receiver
mvn spring-boot:run
```

Your endpoint is at `http://localhost:8080/webhook`.

### 4. Run Integration Scenarios (optional)

```bash
cd java/scenarios
mvn verify
```

Tests that require credentials are skipped gracefully when no `.env` is present. Unit tests (like signature verification) always run.

## Authentication Methods

| Method | Use Case | Setup |
|--------|----------|-------|
| `client_secret_basic` | Standard integrations | Client ID + Secret, simple OAuth2 flow |
| `tls_client_auth` | Regulated environments | mTLS with client certificates, FAPI 2.0 grade |

## License

Proprietary. For use by Performativ customers and integration partners.
