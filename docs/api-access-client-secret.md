# API Access with Client Secret

Standard OAuth2 `client_credentials` flow using `client_secret_basic` authentication. This is the simplest way for a plugin to access the Performativ API.

## Overview

1. Enable API access for your plugin instance to receive `client_id` and `client_secret`
2. Exchange credentials for a JWT access token at the token broker
3. Use the JWT to call the Performativ API

## Step 1: Enable API Access

Enable client_secret API access for a plugin instance via the UI or API:

```http
POST /api/plugins/{plugin}/instances/{instance}/enable-api-access
Content-Type: application/json
Authorization: Bearer {user-jwt}

{
  "auth_method": "client_secret_basic"
}
```

The response contains your credentials (shown **once** -- store securely):

```json
{
  "client_id": "plg:my-plugin-42.acme-corp",
  "client_secret": "generated-secret-value",
  "token_endpoint": "https://token-broker.example.com/oauth/token",
  "audience": "backend-api",
  "auth_method": "client_secret_basic",
  "grant_type": "client_credentials"
}
```

## Step 2: Request an Access Token

Use `client_secret_basic` (RFC 6749 Section 2.3.1): send `client_id` and `client_secret` as HTTP Basic auth.

```http
POST {token_endpoint}/oauth/token
Authorization: Basic base64({client_id}:{client_secret})
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&audience=backend-api
```

### curl Example

```bash
CLIENT_ID="plg:my-plugin-42.acme-corp"
CLIENT_SECRET="generated-secret-value"
TOKEN_URL="https://token-broker.example.com/oauth/token"

TOKEN=$(curl -s -X POST "$TOKEN_URL" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&audience=backend-api" \
  | jq -r '.access_token')
```

### Response

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 600
}
```

The token is a JWT valid for **10 minutes** (600 seconds).

## Step 3: Call the API

Use the JWT as a Bearer token:

```http
GET /api/clients/123
Authorization: Bearer {access_token}
Accept: application/json
```

### curl Example

```bash
curl -s "https://api.example.com/api/clients/123" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json"
```

## Java Example

The `PluginApiClient` class handles token acquisition and caching:

```java
PluginApiClient client = new PluginApiClient(
    "https://token-broker.example.com",  // token broker URL
    "https://api.example.com",           // API base URL
    "plg:my-plugin-42.acme-corp",        // client_id
    "generated-secret-value",            // client_secret
    "backend-api"                        // audience
);

// Tokens are acquired and cached automatically
JsonNode clientData = client.get("/api/clients/123");
```

See [PluginApiClient.java](../java/webhook-receiver/src/main/java/com/performativ/plugin/PluginApiClient.java) for the full implementation.

## Token Caching

- Tokens are valid for 10 minutes (600s)
- Cache the token and refresh it before expiry (e.g. at the 9-minute mark)
- Do **not** request a new token for every API call
- The `PluginApiClient` example handles this automatically

## Secret Rotation

To rotate your client secret:

```http
POST /api/plugins/{plugin}/instances/{instance}/rotate-client-secret
Authorization: Bearer {user-jwt}
```

This returns a new `client_secret`. The old secret is immediately invalidated. Update your application configuration with the new secret.

## Scope

Plugin API credentials are scoped to `data:read`. This grants read access to the tenant's data through the API.

## Comparison with mTLS

| | client_secret | mTLS |
|---|---|---|
| Setup | Simple -- just client_id + secret | Requires certificate management |
| Security | Standard OAuth2 | FAPI 2.0 grade -- mutual TLS |
| Rotation | Rotate secret via API | Rotate certificate via API |
| Use case | Standard integrations | High-security / regulated environments |

For mTLS-based API access, see [API Access with mTLS](api-access-mtls.md).
