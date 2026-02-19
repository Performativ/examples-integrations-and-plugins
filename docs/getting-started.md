# Getting Started with Performativ Plugins

This guide walks you through setting up a custom plugin and what you need to start building.

## What is a Plugin?

A plugin is an external integration that connects to the Performativ platform. Plugins can:

- **Receive webhooks** when entities (clients, portfolios, transactions, etc.) are created, updated, or deleted
- **Access the API** to read data using OAuth2 credentials (client_secret or mTLS)

## Creating a Custom Plugin

Navigate to **Plugins > Custom** in the Performativ admin panel and click **Create Plugin**.

### Configure the plugin

| Field | Description |
|-------|-------------|
| **Name** | A display name for your integration (e.g. "CRM Sync") |
| **Webhook URL** (optional) | Where webhooks will be sent (HTTPS endpoint) |
| **Entities** | Which entity types to receive events for (e.g. Client, Portfolio) |
| **Instance Name** (optional) | A name for this plugin instance |
| **Tenant Reference ID** (optional) | Your own reference identifier |

### Save and collect credentials

After saving, you are shown a **signing key** for webhook signature verification. This key is displayed once -- copy it immediately and store it securely.

## Enabling API Access

Once your plugin exists, you can enable API access to read tenant data:

1. Open your plugin instance and click **API Credentials**
2. Click **Enable Client Secret**
3. Copy the credential bundle immediately -- **the secret is shown only once**

The credential bundle contains:

| Field | Description |
|-------|-------------|
| `client_id` | Your plugin's OAuth2 client identifier |
| `client_secret` | The client secret (shown once) |
| `token_endpoint` | Token broker URL for requesting access tokens |
| `instance_id` | Your plugin instance ID |

## What You Need as a Developer

After your plugin is set up, you should have:

| Credential | Source | Purpose |
|---|---|---|
| **Signing key** | Shown on plugin creation | Verify webhook HMAC signatures |
| **Client ID + Secret** | Shown when enabling client secret access | OAuth2 token requests |
| **PEM bundle** | Downloaded when enabling mTLS access | mTLS token requests (UAT/Production only) |
| **Token endpoint** | Included in credential bundle | Where to request tokens |

The token broker URL follows the pattern `https://api.{tenant}.{environment}.onperformativ.com/token-broker`. The full token endpoint is `{TOKEN_BROKER_URL}/oauth/token`.

## Using the Examples in This Repo

Copy the environment template and fill in your credentials:

```bash
cp .env.example .env
```

See [`.env.example`](../.env.example) for all configurable variables. All code examples in this repo load credentials from `.env` -- no hardcoded values.

To verify everything works:

```bash
# Quick smoke test -- acquires a token and lists clients
cd java/scenarios && mvn verify -Dit.test="com.performativ.scenarios.manual.ApiAccessScenario"
```

## Next Steps

Once your plugin is set up and you have your credentials:

1. **[Set up webhook receiving](webhook-setup.md)** -- Receive and verify webhooks
2. **[Enable API access](api-access-client-secret.md)** -- Call the API with client_secret credentials
3. **[Monitor deliveries](monitoring-webhooks.md)** -- Track and replay webhook deliveries
