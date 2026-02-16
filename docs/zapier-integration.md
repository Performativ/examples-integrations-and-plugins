# Zapier Integration

Connect Performativ to 7,000+ apps using Zapier without writing code.

## Overview

Zapier supports two approaches for integrating with Performativ:

1. **Webhooks by Zapier** (trigger) -- receive webhook events from Performativ
2. **Custom Request** (action) -- call the Performativ API using OAuth2 client_secret

These can be combined: trigger on a webhook event, then fetch details or push data to another app.

## Trigger: Receive Webhooks

Use the **Webhooks by Zapier** trigger to receive events from Performativ.

### Setup

1. Create a new Zap
2. Choose **Webhooks by Zapier** as the trigger
3. Select **Catch Hook** as the event
4. Copy the webhook URL (e.g. `https://hooks.zapier.com/hooks/catch/12345/abcdef/`)
5. In Performativ, activate your plugin with this URL as the webhook URL
6. Send a test webhook (activate the plugin or wait for a DailyHeartBeat)
7. Test the trigger in Zapier to see the payload

### Available Fields

After testing, Zapier will detect these fields from the webhook payload:

- `event_id` -- Unique event identifier
- `entity` -- Entity type (Client, Portfolio, etc.)
- `entity_id` -- Entity ID
- `event` -- Event type (Created, Updated, Deleted)
- `updated_at` -- Timestamp of the change
- `url` -- API URL to fetch the entity

### Filtering

Use Zapier's **Filter** step to process only specific events:

- Only `Client` events: `entity` exactly matches `Client`
- Only `Created` events: `event` exactly matches `Created`
- Exclude heartbeats: `event` does not match `DailyHeartBeat`

## Action: Call the API

Use **Code by Zapier** (JavaScript) or **Webhooks by Zapier** (Custom Request) to call the Performativ API with OAuth2 credentials.

### Option A: Custom Request Action

1. Add a **Webhooks by Zapier** action
2. Select **Custom Request**
3. Configure:
   - **Method**: GET
   - **URL**: The `url` field from the trigger (e.g. `https://api.example.com/api/clients/123`)
   - **Headers**:
     ```
     Authorization: Bearer {your-access-token}
     Accept: application/json
     ```

Note: Zapier does not natively support OAuth2 `client_credentials`. You will need to obtain a token first (see Option B).

### Option B: JavaScript Code Action

Use a **Code by Zapier** (JavaScript) step to handle the full OAuth2 flow:

```javascript
// Input Data: configure these in the Code step
// token_url, client_id, client_secret, audience, api_url

const tokenResponse = await fetch(inputData.token_url, {
  method: 'POST',
  headers: {
    'Authorization': 'Basic ' + btoa(inputData.client_id + ':' + inputData.client_secret),
    'Content-Type': 'application/x-www-form-urlencoded'
  },
  body: 'grant_type=client_credentials&audience=' + encodeURIComponent(inputData.audience)
});

const tokenData = await tokenResponse.json();
const accessToken = tokenData.access_token;

const apiResponse = await fetch(inputData.api_url, {
  headers: {
    'Authorization': 'Bearer ' + accessToken,
    'Accept': 'application/json'
  }
});

const data = await apiResponse.json();
output = [{data: data}];
```

Configure the **Input Data** in Zapier:

| Key | Value |
|-----|-------|
| `token_url` | `https://token-broker.example.com/oauth/token` |
| `client_id` | Your plugin client_id |
| `client_secret` | Your plugin client_secret |
| `audience` | `backend-api` |
| `api_url` | The `url` from the webhook trigger step |

## Example Zap: Client Created -> Google Sheets

1. **Trigger**: Webhooks by Zapier > Catch Hook
2. **Filter**: `entity` equals `Client` AND `event` equals `Created`
3. **Code**: JavaScript step to fetch client details from API (using the `url` field)
4. **Action**: Google Sheets > Create Spreadsheet Row

## Example Zap: Portfolio Updated -> Slack Notification

1. **Trigger**: Webhooks by Zapier > Catch Hook
2. **Filter**: `entity` equals `Portfolio` AND `event` equals `Updated`
3. **Action**: Slack > Send Channel Message
   - Channel: `#portfolio-updates`
   - Message: `Portfolio {{entity_id}} was updated at {{updated_at}}`

## Security Notes

- Store `client_secret` in Zapier's **Secret** field type when available
- Use HTTPS webhook URLs (Zapier provides these by default)
- Zapier does not support HMAC signature verification natively; the Custom Request approach relies on Zapier's webhook URL being secret
- Consider IP allowlisting if your security policy requires it
