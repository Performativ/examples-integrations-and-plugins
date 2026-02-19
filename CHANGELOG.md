# Changelog

All notable changes to this repository are documented here.

## 2026-02-19 — Symmetric scenario matrix

### Added
- `SCENARIOS.md` — canonical definitions for the three core scenarios (S1: API Access, S2: Client Lifecycle, S3: Portfolio Setup)
- `manual/` package — raw HTTP implementations of S1, S2, S3
- `generated/` package — OpenAPI-generated client implementations of S1, S2, S3 (strict, no fallbacks)
- `curl/api-access.sh` — S1 curl implementation
- `curl/portfolio-setup.sh` — S3 curl implementation
- `CHANGELOG.md` — this file

### Changed
- `BaseScenario.java` — credentials are now **required**: `requireEnv()` uses hard assertions instead of `assumeTrue`. Tests fail (not skip) without `.env`.
- `WebhookChecker.java` — methods made public for cross-package access
- `curl/client-lifecycle.sh` — added update step to match S2 spec
- `.env.example` — `TOKEN_AUDIENCE` marked as internal
- `README.md` — updated with scenario matrix table, removed `TOKEN_AUDIENCE` from credential table, FAPI noted as UAT/Production only
- `docs/getting-started.md` — removed `audience` from credential bundle table
- `docs/api-access-client-secret.md` — removed `audience` from user-facing examples (handled internally)
- `docs/api-access-mtls.md` — clarified mTLS is UAT/Production only (not Sandbox), removed `audience` from examples

### Removed
- `PriorityModelScenario.java` — replaced by `generated/` scenarios
- `OpenApiClientScenario.java` — replaced by `generated/` scenarios
- `AdvisorySetupScenario.java` — replaced by `manual/PortfolioSetupScenario`
- `AdvisoryAgreementScenario.java` — disabled placeholder, never implemented
- `StartAdviseScenario.java` — disabled placeholder, never implemented
- `WebhookPollScenario.java` — disabled placeholder, never implemented
- Root-level `ApiAccessScenario.java` — moved to `manual/`
- Root-level `ClientLifecycleScenario.java` — moved to `manual/`
