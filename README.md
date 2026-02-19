# Performativ Examples, Integrations & Plugins

Code examples and documentation for building integrations with the Performativ platform.

> **Live API docs**: Your tenant has interactive API documentation under **Menu > Documentation**. The examples here complement — not replace — those docs.

## Documentation

| Guide | Description |
|-------|-------------|
| [Getting Started](docs/getting-started.md) | Create a custom plugin and collect credentials |
| [Webhook Setup](docs/webhook-setup.md) | Receive webhooks and verify HMAC signatures |
| [API Access (Client Secret)](docs/api-access-client-secret.md) | OAuth2 `client_credentials` with `client_secret_basic` |
| [API Access (mTLS)](docs/api-access-mtls.md) | FAPI 2.0 mutual TLS authentication (UAT/Production only) |
| [Webhook Events](docs/webhook-events.md) | Event types, payloads, and supported entities |
| [Monitoring Webhooks](docs/monitoring-webhooks.md) | List deliveries and replay failed webhooks |
| [Testing Webhooks Locally](docs/testing-webhooks-locally.md) | Cloudflare Tunnel and delivery-polling approaches for local development |

## Code Examples

Five core scenarios ([S1–S5](SCENARIOS.md)) are implemented across three approaches:

| Approach | Description | Scenarios |
|----------|-------------|-----------|
| [curl/](curl/) | Shell scripts using curl -- simplest, no build tools | S1–S5 |
| [java/manual](java/scenarios/) | Raw HTTP with `java.net.http` -- no dependencies beyond JDK | S1–S5 |
| [java/generated](java/scenarios/) | OpenAPI-generated typed client -- strict spec conformance | S1–S5 |

The manual scenarios verify API behavior using raw HTTP. The generated scenarios use the OpenAPI-generated client with **no fallbacks** -- if the generated client fails (wrong types, deserialization errors), the test fails, immediately surfacing spec bugs.

See [SCENARIOS.md](SCENARIOS.md) for the canonical scenario definitions.

### Additional examples

| Example | Description |
|---------|-------------|
| [Webhook Receiver](java/webhook-receiver/) | Spring Boot app: POST `/webhook` with HMAC verification, delivery polling, OAuth2 API client |
| [Bulk Ingestion Library](java/bulk-ingestion/) | OpenAPI-generated client for async bulk data ingestion |

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
| `API_BASE_URL` | Your tenant's web URL, prefixed with `api.` -- e.g., if your tenant is at `https://acme.sandbox.onperformativ.com`, then `API_BASE_URL=https://api.acme.sandbox.onperformativ.com` |
| `TOKEN_BROKER_URL` | Same as `API_BASE_URL` with `/token-broker` appended -- e.g., `https://api.acme.sandbox.onperformativ.com/token-broker`. The full token endpoint is `{TOKEN_BROKER_URL}/oauth/token`. |
| `PLUGIN_CLIENT_ID` | From the credential bundle |
| `PLUGIN_CLIENT_SECRET` | From the credential bundle |
| `WEBHOOK_SIGNING_KEY` | Shown when creating the plugin (displayed once) |
| `PLUGIN_INSTANCE_ID` | From the credential bundle (the `instance_id` field) |
| `PLUGIN_SLUG` | Your plugin's slug (visible in the plugin URL) |

### 3. Receive Webhooks (Java)

```bash
cd java/webhook-receiver
mvn spring-boot:run
```

Your endpoint is at `http://localhost:8080/webhook`.

### 4. Run Integration Scenarios

```bash
cd java/scenarios
mvn verify
```

Scenarios require a valid `.env` with credentials -- tests **fail** (not skip) when credentials are missing. Unit tests (like signature verification) run with `mvn test` and need no credentials.

## Authentication Methods

| Method | Use Case | Environments | Setup |
|--------|----------|-------------|-------|
| `client_secret_basic` | Standard integrations | All (Sandbox, UAT, Production) | Client ID + Secret, simple OAuth2 flow |
| `tls_client_auth` | Regulated environments | UAT and Production only | mTLS with client certificates, FAPI 2.0 grade |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for how to add new clients, scenarios, or improvements.

## Architecture Decisions

Key design choices are documented in [docs/adr/](docs/adr/):

| ADR | Title |
|-----|-------|
| [001](docs/adr/001-matrix-testing.md) | Matrix testing across client types |
| [002](docs/adr/002-openapi-strict-generation.md) | Strict OpenAPI generation as compatibility check |
| [003](docs/adr/003-scenario-definitions.md) | Scenario definitions as single source of truth |
| [004](docs/adr/004-auth-environment-strategy.md) | Auth and environment strategy |
| [005](docs/adr/005-secret-management-ci-security.md) | Secret management and CI security |
| [006](docs/adr/006-contribution-review-policy.md) | Contribution and review policy |

