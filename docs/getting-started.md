# Getting Started with Performativ Plugins

This guide walks you through registering a plugin and activating it for a tenant.

## What is a Plugin?

A plugin is an external integration that connects to the Performativ platform. Plugins can:

- **Receive webhooks** when entities (clients, portfolios, transactions, etc.) are created, updated, or deleted
- **Access the API** to read data using OAuth2 credentials (mTLS or client_secret)

## Registering a Plugin (Admin)

Plugins are registered by platform administrators. The registration defines the plugin's metadata and default configuration.

### Via the UI

1. Navigate to **Settings > Plugins** in the Performativ admin panel
2. Click **Register Plugin**
3. Fill in:
   - **Name** - A unique slug for the plugin (e.g. `my-crm-sync`)
   - **Title** - Display name shown to tenants
   - **Description** - What the plugin does
   - **Default Webhook URL** - Where webhooks will be sent (can be overridden per instance)
4. Save the plugin

### Via the API

```http
POST /api/plugins
Content-Type: application/json
Authorization: Bearer {admin-jwt}

{
  "name": "my-crm-sync",
  "title": "CRM Sync",
  "description": "Syncs client data to your CRM",
  "webhook_url": "https://your-domain.com/webhook"
}
```

## Activating a Plugin (Tenant)

Once a plugin is registered, tenants can activate it to create an **instance**.

### Via the UI

1. Navigate to **Settings > Plugins**
2. Find the plugin and click **Activate**
3. Configure:
   - **Webhook URL** (optional) - Override the default URL
   - **Entities** - Select which entity types to receive events for
   - **Instance Name** (optional) - A name for this instance
   - **Tenant Reference ID** (optional) - Your reference identifier
4. Click **Activate**

### Via the API

```http
POST /api/plugins/{plugin}/activate
Content-Type: application/json
Authorization: Bearer {user-jwt}

{
  "webhook_url": "https://your-domain.com/webhook",
  "entities": ["Client", "Portfolio", "Transaction"],
  "instance_name": "Production",
  "tenant_reference_id": "ref-123"
}
```

The response includes a **signing key** for webhook signature verification.

## Next Steps

Once your plugin is activated:

1. **[Set up webhook receiving](webhook-setup.md)** - Receive and verify webhooks
2. **[Enable API access](api-access-client-secret.md)** - Call the API with client_secret credentials
3. **[Monitor deliveries](monitoring-webhooks.md)** - Track and replay webhook deliveries
