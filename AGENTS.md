# Agent Rules

Mandatory rules for AI agents working on this repository. These are non-negotiable.

## Strictness Rules

### Generated OpenAPI client — NEVER relax

The `java-generated` scenarios use `FAIL_ON_UNKNOWN_PROPERTIES = true` and `FAIL_ON_NULL_FOR_PRIMITIVES = true` on the Jackson ObjectMapper. This is intentional and must never be changed.

- **NEVER** disable `FAIL_ON_UNKNOWN_PROPERTIES`
- **NEVER** add `@JsonIgnoreProperties(ignoreUnknown = true)` to generated models
- **NEVER** suggest relaxing strict deserialization as a fix for test failures
- **NEVER** catch or swallow `UnrecognizedPropertyException`

When strict mode causes a test failure, the fix is **always upstream** — either the API behaviour is corrected or the spec is regenerated from the API gateway. A new `openapi.json` may then be committed to this repo, but it is never edited locally or manually.

The generated client is a **canary** (see [ADR-002](docs/adr/002-openapi-strict-generation.md)). When it breaks, it means partners using code generators will hit the same issue. The correct response is to fix things upstream, not hide the problem.

### openapi.json — NEVER modify

`openapi.json` is an upstream artifact. It is generated from the API gateway and copied into this repo as-is.

- **NEVER** edit, patch, or programmatically modify `openapi.json`
- **NEVER** add fields, fix schemas, or "help" the spec pass tests
- **NEVER** suggest modifying the spec as a workaround

When the spec is wrong, the fix happens **upstream in the API gateway** that generates it. This repo consumes the spec read-only. If the generated client fails because the spec is incomplete, that failure is the signal to fix the spec upstream — not to patch it here.

### Spec validation — NEVER skip

The OpenAPI generator's `skipValidateSpec` must remain `false`. When the spec has validation errors (duplicate operationIds, invalid schemas, etc.), the generation must **fail** — not silently produce a broken client.

- **NEVER** set `skipValidateSpec` to `true`
- **NEVER** add `--skip-validate-spec` to CLI invocations
- **NEVER** work around validation errors by suppressing them

Validation failures are upstream spec bugs that need fixing in the API gateway. The generation failure is the signal — same principle as strict deserialization (see [ADR-002](docs/adr/002-openapi-strict-generation.md)).

### Credentials — NEVER skip

All scenarios require credentials. If credentials are missing:
- Tests must **fail immediately** (`assertNotNull` / `set -euo pipefail`)
- NEVER use `@Disabled`, `assumeTrue`, or conditional skipping
- NEVER commit credentials or `.env` files

### Public-repo hygiene — NEVER leak tenant or customer data

This repository is **public** — everything committed is world-readable, including in history.

- **NEVER** commit real tenant, customer, partner, bank, or vendor names. Use neutral placeholders: `acme`, `example`, `your-tenant`.
- **NEVER** commit real hostnames or tenant subdomains, PII (real people's names / emails / IDs), access tokens, bearer JWTs, request IDs, or any payload captured from a live tenant. Example data must be synthetic (`example.com` emails, approach-prefixed fake names like `Manual-S2 Client`).
- **`openapi.json` must be clean at the source.** It is generated from the API gateway against a demo/placeholder tenant and copied here as-is. If a refreshed spec contains a real tenant/customer/partner name (commonly in a `description` derived from a source-code annotation), that is an **upstream bug** — fix the backend's spec generation / the annotation, regenerate, and re-pull. That is the durable fix; **importing a spec that contains real names is blocked until the source is clean.**
- **Mandatory pre-push scan (fail closed).** Before pushing ANY spec refresh, scan it against the current known-names denylist (maintained out-of-repo so this public file never names anyone): `grep -niE "$(known_names_regex)" openapi.json` must return nothing. Never push a spec to this public repo without this gate passing.
- **If a leak already reached this public repo, treat it as an incident:** scrub the names from `openapi.json` on the branch immediately (a sanctioned public-safety exception to "openapi.json — NEVER modify"), purge them from branch history (rewrite + force-push the unmerged branch), and open the upstream fix so the next refresh stays clean.

## Scenario Implementation Rules

### Parity is mandatory

Every scenario in `.matrix.json` must be implemented in every client. No exceptions, no partial implementations. Run `bash .ci/validate-parity.sh` before every commit.

### Cleanup is mandatory

Every scenario that creates entities must delete them, even on failure:
- **curl**: `trap cleanup EXIT`
- **Java**: `@AfterAll` with raw HTTP `deleteEntity()` (never generated client for cleanup)

### Generated scenarios use generated types for assertions

The point of the generated scenarios is to exercise the typed models. Assertions must use typed getters (`.getName()`, `.getId()`, `.getFirstName()`) — not raw JSON parsing. The only exception is cleanup (`@AfterAll`), which uses raw HTTP to ensure teardown succeeds even when the generated client has spec issues.

### Follow SCENARIOS.md exactly

[SCENARIOS.md](SCENARIOS.md) defines the canonical steps. All implementations must match it. Don't add extra steps, don't skip steps, don't change the order.

## Code Rules

### No fake tests

Every test must make real API calls and assert real behavior. No mocks, no stubs, no commented-out assertions, no `assertTrue(true)`.

### No duplicated flows

Each scenario tests a distinct scope. Don't duplicate logic between scenarios. If you find yourself copying code between S2 and S3, extract it to `BaseScenario`.

### Pin dependencies

All GitHub Actions must be pinned to commit SHAs with a version comment:
```yaml
uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4.3.1
```

Never use floating tags like `@v4` or `@main`.
