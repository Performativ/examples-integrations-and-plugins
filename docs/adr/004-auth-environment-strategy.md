# ADR-004: Auth and Environment Strategy

**Status**: Accepted

## Context

The Performativ API supports two authentication methods (`client_secret_basic` and `tls_client_auth` / mTLS) across multiple environments (sandbox, UAT, production). We need a strategy that works now with client_secret on sandbox, and scales to additional auth methods and environments later.

## Decision

### Active environments

- **Auth**: `client_secret_basic`
- **Environment**: sandbox
- **CI environment**: `sandbox-client-secret` in the private `examples-ci` repo

### Planned environments

| Environment | Auth | Notes |
|-------------|------|-------|
| `uat-client-secret` | client_secret | Requires UAT tenant provisioning |
| `uat-mtls` | mTLS | Requires certificate credentials via `CertificateIssuerClient` |
| `production-client-secret` | client_secret | Brook tenant; requires approval gates |
| `production-mtls` | mTLS | Brook tenant with mTLS certs |

### Auth switching

For mTLS environments, the token acquisition layer switches based on an `AUTH_METHOD` environment variable:
- `client_secret` (default): current `client_secret_basic` flow
- `mtls`: mutual TLS with client certificate

This affects `BaseScenario.acquireToken()` in Java and the token acquisition step in curl scripts. The rest of the scenario logic is auth-agnostic.

### Production safeguards

Production environments require:
- GitHub Environment protection rules requiring manual approval before each run
- Concurrency limits (1 concurrent run per environment)
- Dedicated "smoke test" plugin instance with minimal permissions
- All scenarios must be idempotent (create, verify, delete — no side effects)

## Consequences

- `.matrix.json` distinguishes `active_environments` from `planned_environments` — CI only expands active ones.
- Adding a new environment is a configuration change (secrets + `.matrix.json`), not a code change, unless it introduces a new auth method.
- mTLS implementation is deferred but the architecture is ready for it.
