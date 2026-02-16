# Getting Started with Performativ Plugins

This guide walks you through how plugins are set up and what you need to start building.

## What is a Plugin?

A plugin is an external integration that connects to the Performativ platform. Plugins can:

- **Receive webhooks** when entities (clients, portfolios, transactions, etc.) are created, updated, or deleted
- **Access the API** to read data using OAuth2 credentials (client_secret or mTLS)

## How Plugins Are Set Up

Plugin setup is a two-step process performed by administrators through the Performativ UI.

### Step 1: Registering a Plugin (Platform Admin)

A platform administrator registers the plugin definition:

1. Navigate to **Settings > Plugins** in the Performativ admin panel
2. Click **Register Plugin**
3. Fill in:
   - **Name** -- A unique slug for the plugin (e.g. `my-crm-sync`)
   - **Title** -- Display name shown to tenants
   - **Description** -- What the plugin does
   - **Default Webhook URL** -- Where webhooks will be sent (can be overridden per instance)
4. Save the plugin

### Step 2: Activating a Plugin (Tenant Admin)

Once a plugin is registered, a tenant admin activates it to create a plugin **instance** for their tenant:

1. Navigate to **Settings > Plugins**
2. Find the plugin and click **Activate**
3. Configure:
   - **Webhook URL** (optional) -- Override the default URL
   - **Entities** -- Select which entity types to receive events for
   - **Instance Name** (optional) -- A name for this instance
   - **Tenant Reference ID** (optional) -- Your reference identifier
4. Click **Activate**

After activation, the admin is shown a **signing key** for webhook signature verification. This key is displayed once -- the admin should provide it to you securely.

## What You Receive as a Developer

After your admin sets up the plugin, you will receive:

| Credential | Source | Purpose |
|---|---|---|
| **Signing key** | Shown on activation | Verify webhook HMAC signatures |
| **Client ID + Secret** | Shown when enabling client secret access | OAuth2 token requests |
| **PEM bundle** | Downloaded when enabling mTLS access | mTLS token requests (advanced) |
| **Token endpoint** | Included in credential bundle | Where to request tokens |
| **Audience** | Included in credential bundle | The `audience` parameter for token requests |

## Next Steps

Once your plugin is activated and you have your credentials:

1. **[Set up webhook receiving](webhook-setup.md)** -- Receive and verify webhooks
2. **[Enable API access](api-access-client-secret.md)** -- Call the API with client_secret credentials
3. **[Monitor deliveries](monitoring-webhooks.md)** -- Track and replay webhook deliveries
