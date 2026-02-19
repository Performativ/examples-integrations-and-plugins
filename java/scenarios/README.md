# Integration Scenarios

End-to-end integration tests that exercise the Performativ API. Credentials are **required** — tests fail immediately when `.env` is missing.

## Running

```bash
# From the repo root
cp .env.example .env
# Fill in your credentials

cd java/scenarios
mvn verify
```

Run a specific package:

```bash
mvn verify -Dit.test="com.performativ.scenarios.manual.*"
mvn verify -Dit.test="com.performativ.scenarios.generated.*"
```

## Packages

| Package | Purpose |
|---------|---------|
| `manual/` | Raw HTTP (`java.net.http`) — verifies API behavior with no abstractions |
| `generated/` | OpenAPI-generated typed client — strict deserialization catches spec drift |

Both packages implement the same scenarios. When a `generated/` test fails but the `manual/` equivalent passes, it means the OpenAPI spec has diverged from the API — the fix is upstream, not here. See [ADR-002](../../docs/adr/002-openapi-strict-generation.md).

## Scenarios

| Scenario | manual | generated | Auth | Needs credentials |
|----------|:------:|:---------:|------|:-----------------:|
| S1: API Access | `ApiAccessScenario` | `ApiAccessScenario` | OAuth2 | Yes |
| S2: Client Lifecycle | `ClientLifecycleScenario` | `ClientLifecycleScenario` | OAuth2 | Yes |
| S3: Portfolio Setup | `PortfolioSetupScenario` | `PortfolioSetupScenario` | OAuth2 | Yes |
| S4: Webhook Delivery | `WebhookDeliveryScenario` | `WebhookDeliveryScenario` | OAuth2 | Yes |
| S5: Bulk Ingestion | `BulkIngestionScenario` | `BulkIngestionScenario` | OAuth2 | Yes |

See [SCENARIOS.md](../../SCENARIOS.md) for canonical step definitions.

## Unit tests

| Class | What it tests | Needs credentials |
|-------|---------------|:-----------------:|
| `SignatureVerificationTest` | HMAC-SHA256 compute + verify round-trip | No |
