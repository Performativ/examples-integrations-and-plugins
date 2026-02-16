# API Access with mTLS (Advanced)

FAPI 2.0-grade mutual TLS authentication for high-security environments. The plugin authenticates to the token broker using a client certificate instead of a shared secret.

## Overview

mTLS (mutual TLS) provides stronger security than `client_secret_basic`:

- The private key never leaves the plugin's environment
- Authentication is bound to a specific TLS connection (sender-constrained tokens)
- Certificates can be revoked independently of the token broker
- Meets FAPI 2.0 security profile requirements

## How It Works

1. **Enable API access** for the plugin instance with mTLS auth
2. Performativ issues a **client certificate** (PEM bundle with cert + private key)
3. The plugin uses the certificate for **mTLS** when requesting tokens
4. The token broker validates the client certificate and issues a JWT
5. The JWT is used as a Bearer token to call the API

## Step 1: Enable API Access with mTLS

```http
POST /api/plugins/{plugin}/instances/{instance}/enable-api-access
Content-Type: application/json
Authorization: Bearer {user-jwt}

{
  "auth_method": "tls_client_auth"
}
```

The response contains the credential bundle (shown **once** -- store securely):

```json
{
  "client_id": "plg:my-plugin-42",
  "pem_bundle": "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----\n-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n",
  "certificate_fingerprint": "sha256:abc123...",
  "certificate_expires_at": "2024-04-15T00:00:00+00:00",
  "token_endpoint": "https://token-broker.example.com/oauth/token",
  "auth_method": "tls_client_auth"
}
```

Save the PEM bundle to files:

```bash
# Extract certificate and key from the PEM bundle
# The bundle contains both the certificate and private key

echo "$PEM_BUNDLE" > plugin-cert.pem

# Or split into separate files if needed:
# Certificate is the first PEM block, key is the second
```

## Step 2: Request an Access Token with mTLS

```bash
curl -s -X POST "https://token-broker.example.com/oauth/token" \
  --cert plugin-cert.pem \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=plg:my-plugin-42&audience=backend-api"
```

Note: With mTLS, the `client_id` is sent in the request body (not as Basic auth). The client certificate provides the authentication.

### Response

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 600
}
```

## Step 3: Call the API

Same as client_secret -- use the JWT as a Bearer token:

```bash
curl -s "https://api.example.com/api/clients/123" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json"
```

## Java Example

```java
import javax.net.ssl.*;
import java.security.*;

// Load the PEM bundle into an SSLContext
KeyStore keyStore = loadPemBundle("plugin-cert.pem");
KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
kmf.init(keyStore, new char[0]);

SSLContext sslContext = SSLContext.getInstance("TLS");
sslContext.init(kmf.getKeyManagers(), null, null);

HttpClient client = HttpClient.newBuilder()
    .sslContext(sslContext)
    .build();

// Request token with mTLS
HttpRequest tokenRequest = HttpRequest.newBuilder()
    .uri(URI.create("https://token-broker.example.com/oauth/token"))
    .header("Content-Type", "application/x-www-form-urlencoded")
    .POST(HttpRequest.BodyPublishers.ofString(
        "grant_type=client_credentials&client_id=plg:my-plugin-42&audience=backend-api"))
    .build();

HttpResponse<String> response = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
```

## Certificate Management

### Rotation

Rotate credentials before the certificate expires:

```http
POST /api/plugins/{plugin}/instances/{instance}/rotate-mtls
Authorization: Bearer {user-jwt}
```

This issues a new certificate and revokes the old one. The response contains a new PEM bundle.

### Expiry Monitoring

Certificates have a defined expiry date (`certificate_expires_at`). Monitor this and rotate before expiry. The certificate status is visible in the plugin instance details.

### Revocation

When a plugin is deactivated or API access is disabled, the certificate is automatically revoked.

## When to Use mTLS vs client_secret

Use **mTLS** when:
- Operating in a regulated environment (banking, financial services)
- FAPI 2.0 compliance is required
- You need sender-constrained tokens
- Your security policy requires certificate-based authentication

Use **client_secret** when:
- Standard OAuth2 security is sufficient
- You want simpler setup and operations
- Certificate management is not practical for your environment

See [API Access with Client Secret](api-access-client-secret.md) for the simpler alternative.
