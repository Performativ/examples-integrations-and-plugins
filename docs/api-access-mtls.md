# API Access with mTLS (Advanced)

FAPI 2.0-grade mutual TLS authentication for high-security environments. The plugin authenticates to the token broker using a client certificate instead of a shared secret.

## Overview

mTLS (mutual TLS) provides stronger security than `client_secret_basic`:

- The private key never leaves the plugin's environment
- Authentication is bound to a specific TLS connection (sender-constrained tokens)
- Certificates can be revoked independently of the token broker
- Meets FAPI 2.0 security profile requirements

## How It Works

1. Your tenant admin enables mTLS access for your plugin instance via the Performativ UI
2. You receive a **PEM bundle** containing a client certificate and private key (one-time download)
3. You use the certificate for **mTLS** when requesting tokens from the token broker
4. The token broker validates the client certificate and issues a JWT
5. You use the JWT as a Bearer token to call the API

## Step 1: Obtain Your Certificate

Your tenant admin enables mTLS access for your plugin instance through the Performativ admin panel:

1. Navigate to **Settings > Plugins**
2. Find your plugin instance and click **API Credentials**
3. Click **Enable mTLS Access**
4. Download the PEM bundle immediately -- **it is available only once**

The PEM bundle contains:

```
-----BEGIN CERTIFICATE-----
... (your client certificate) ...
-----END CERTIFICATE-----
-----BEGIN PRIVATE KEY-----
... (your private key) ...
-----END PRIVATE KEY-----
```

Along with the bundle, you will also receive:

- **client_id** -- your plugin's principal identifier (e.g. `plg:my-plugin-42`)
- **token_endpoint** -- the token broker URL
- **certificate_fingerprint** -- SHA-256 fingerprint for verification
- **certificate_expires_at** -- when the certificate expires

Save the PEM bundle to a secure file:

```bash
# Save the PEM bundle (contains both certificate and private key)
echo "$PEM_BUNDLE" > plugin-cert.pem
chmod 600 plugin-cert.pem
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

When a certificate is approaching expiry, your tenant admin rotates it through the Performativ UI:

1. Navigate to **Settings > Plugins**
2. Find your plugin instance and click **API Credentials**
3. Click **Rotate Certificate**
4. Download the new PEM bundle immediately -- **available only once**

The old certificate is revoked immediately. Update your application with the new PEM bundle.

### Expiry Monitoring

Certificates have a defined expiry date (`certificate_expires_at`). Monitor this and coordinate with your admin to rotate before expiry. The certificate status is visible in the plugin instance details in the admin panel.

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
