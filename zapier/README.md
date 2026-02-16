# Zapier Integration

Connect Performativ to 7,000+ apps using Zapier's no-code platform.

## Approaches

### 1. Webhooks Trigger (Receive Events)

Use **Webhooks by Zapier > Catch Hook** to receive real-time events when entities change in Performativ.

1. Create a Zap with Webhooks by Zapier as the trigger
2. Copy the Zapier webhook URL
3. Activate your Performativ plugin with this URL
4. Use Zapier filters to process specific entity types or events

### 2. API Action (Fetch Data)

Use **Code by Zapier** (JavaScript) to call the Performativ API with OAuth2 `client_credentials`:

```javascript
const tokenResponse = await fetch(inputData.token_url, {
  method: 'POST',
  headers: {
    'Authorization': 'Basic ' + btoa(inputData.client_id + ':' + inputData.client_secret),
    'Content-Type': 'application/x-www-form-urlencoded'
  },
  body: 'grant_type=client_credentials&audience=' + encodeURIComponent(inputData.audience)
});

const { access_token } = await tokenResponse.json();

const apiResponse = await fetch(inputData.api_url, {
  headers: {
    'Authorization': 'Bearer ' + access_token,
    'Accept': 'application/json'
  }
});

output = [{ data: await apiResponse.json() }];
```

## Full Documentation

See [docs/zapier-integration.md](../docs/zapier-integration.md) for detailed setup instructions and example Zaps.
