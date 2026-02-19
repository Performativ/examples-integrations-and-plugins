# Testing Webhooks Locally

Performativ delivers webhooks via HTTPS POST to a publicly reachable URL. During development, your machine is usually behind NAT or a corporate firewall and can't receive inbound connections.

This guide covers two approaches for testing webhooks on your local machine.

## Comparison

| | Cloudflare Tunnel | Webhook Poller |
|---|---|---|
| **How it works** | Creates a public URL that tunnels traffic to your local server | Polls the delivery API for new events over outbound HTTPS |
| **Network requirements** | Outbound HTTPS (tunnel auto-connects) | Outbound HTTPS only |
| **Corporate firewalls** | May be blocked (requires WebSocket/QUIC) | Works everywhere |
| **Latency** | Real-time (push) | Polling interval (default 10s) |
| **Setup** | Docker only | Docker or bare `mvn spring-boot:run` |
| **Signature verification** | Yes (real webhook POST) | Optional (`POLLER_INCLUDE_SIGNATURE=true`) |

## Prerequisites

- Docker and Docker Compose (for either approach)
- Plugin credentials from your custom plugin setup:
  - **Client ID** and **Client Secret** (from enabling API access)
  - **Webhook Signing Key** (from plugin creation, tunnel approach only)
  - **Token Broker URL** and **API Base URL** (from your environment)
  - **Plugin Slug** and **Instance ID** (from plugin activation)

Copy the shared credential template and fill in your values:

```bash
cp .env.example .env
# Edit .env with your credentials
```

See [`.env.example`](../.env.example) in the repo root for all available settings.

## Approach 1: Cloudflare Tunnel

A Docker sidecar creates a temporary public URL using [Cloudflare Quick Tunnels](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/do-more-with-tunnels/trycloudflare/). No Cloudflare account required.

### Start

```bash
cd java/webhook-receiver
docker compose --profile tunnel up --build
```

### Grab the tunnel URL

Watch the `tunnel` container logs for a line like:

```
tunnel  | INF +----------------------------+
tunnel  | INF |  https://abc-xyz.trycloudflare.com  |
tunnel  | INF +----------------------------+
```

### Configure in admin UI

1. Go to **Plugins > Custom > Your Plugin > Configure**
2. Set the webhook URL to `https://abc-xyz.trycloudflare.com/webhook`
3. Save — webhooks will now be delivered to your local machine

### Notes

- The tunnel URL changes every time the container restarts
- Signature verification works normally (the tunnel passes through the raw POST)
- Stop with `Ctrl+C` or `docker compose --profile tunnel down`

## Approach 2: Webhook Poller

The poller uses a dedicated polling endpoint with cursor-based keyset pagination:

```
GET /api/plugins/{slug}/instances/{id}/webhook-deliveries/poll
    ?after={cursor}&limit=50
```

Each poll cycle fetches only new deliveries since the last cursor. The poller has two modes:

- **Default** — extracts the `payload` from each delivery and processes it directly (fast, skips signature verification).
- **Replay mode** (`POLLER_INCLUDE_SIGNATURE=true`) — the API returns the full reconstructed webhook POST (payload + all headers including `x-webhook-signature`). The poller replays each delivery as a real HTTP POST to your local `/webhook` endpoint, so the full controller path runs — including HMAC signature verification. This is byte-identical to what the platform would have sent.

### Start

```bash
cd java/webhook-receiver
POLLER_ENABLED=true docker compose up --build
```

Or without Docker (credentials are loaded from the root `.env` file automatically via `spring-dotenv`):

```bash
cd java/webhook-receiver
POLLER_ENABLED=true mvn spring-boot:run
```

### Start from a specific point in time

By default, the first poll fetches all available deliveries. To skip old history:

```bash
export POLLER_SINCE=2026-02-18T00:00:00Z
```

The `since` timestamp is only used on the first poll. After that, the cursor takes over.

### Replay mode (full signature verification)

Set `POLLER_INCLUDE_SIGNATURE=true` to switch to replay mode. The poller fetches the complete reconstructed webhook POST from the API — same payload, same headers (`x-webhook-signature`, `x-tenant`, `x-api-domain`, etc.) — and replays it as a real HTTP POST to your local `/webhook` endpoint.

This exercises your full `WebhookController` path including HMAC signature verification, exactly as if the platform had sent the webhook directly.

```bash
export POLLER_INCLUDE_SIGNATURE=true
export WEBHOOK_SIGNING_KEY=your-signing-key   # needed for verification
```

You need to set `WEBHOOK_SIGNING_KEY` for replay mode, since the controller will verify signatures on the replayed POSTs.

### Watch logs

Default mode:
```
WebhookPoller  : Webhook poller enabled for plugin=my-plugin instance=42 batchSize=50
WebhookPoller  : Polled 3 new event(s) from 5 deliveries (cursor=abc-123-def)
WebhookEventProcessor : Processing event: entity=Client event=Created ...
```

Replay mode:
```
WebhookPoller  : Poller will replay deliveries as local POST to http://localhost:8080/webhook
WebhookPoller  : Replayed delivery abc-123 -> local /webhook (200 OK)
WebhookController : Webhook received: entity=Client event=Created ...
```

The poller runs every 10 seconds by default. Change this with `POLLER_INTERVAL_MS`.

### Notes

- The poller tracks its position via a cursor (last delivery UUID) — no duplicate fetching
- The poller and the webhook endpoint can run simultaneously — they share event-level deduplication via `event_id`
- `POLLER_BATCH_SIZE` controls how many deliveries are fetched per request (default 50)

## Choosing an Approach

| Scenario | Recommended |
|----------|-------------|
| Quick demo, unrestricted network | Cloudflare Tunnel |
| Corporate environment, firewall restrictions | Webhook Poller |
| Testing signature verification logic | Either (poller supports `POLLER_INCLUDE_SIGNATURE`) |
| CI/integration test pipeline | Webhook Poller |
| No Docker available | Webhook Poller (bare `mvn spring-boot:run`) |

## Plain Receiver (No Local Testing)

If your machine is already publicly reachable (e.g., a cloud VM), just run the receiver directly:

```bash
cd java/webhook-receiver
docker compose up --build
```

This starts only the webhook receiver on port 8080, with no tunnel or poller.
