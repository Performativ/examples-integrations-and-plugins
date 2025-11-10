# Integration Scenarios

End-to-end integration tests that exercise the Performativ API. Tests that require credentials are skipped gracefully when no `.env` is present, so `mvn verify` always succeeds (with skipped tests) even in forks or PRs.

## Running

```bash
# From the repo root
cp .env.example .env
# Fill in your credentials

cd java/scenarios
mvn verify
```

## Scenarios

| Class | What it tests | Needs credentials |
|-------|---------------|-------------------|
| `SignatureVerificationTest` | HMAC-SHA256 compute + verify round-trip | No |
| `ApiAccessScenario` | Acquire token, `GET /api/clients`, verify 200 | Yes |
| `WebhookPollScenario` | Token, `GET .../webhook-deliveries/poll?limit=5`, verify structure | Yes |
| `BulkIngestionScenario` | Token, setup ingestion task, verify taskId + uploadUrl | Yes |
