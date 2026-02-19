# Agent Rules

Mandatory rules for AI agents working on this repository. These are non-negotiable.

## Strictness Rules

### Generated OpenAPI client — NEVER relax

The `java-generated` scenarios use `FAIL_ON_UNKNOWN_PROPERTIES = true` and `FAIL_ON_NULL_FOR_PRIMITIVES = true` on the Jackson ObjectMapper. This is intentional and must never be changed.

- **NEVER** disable `FAIL_ON_UNKNOWN_PROPERTIES`
- **NEVER** add `@JsonIgnoreProperties(ignoreUnknown = true)` to generated models
- **NEVER** suggest relaxing strict deserialization as a fix for test failures
- **NEVER** catch or swallow `UnrecognizedPropertyException`

When strict mode causes a test failure, the fix is **always upstream in `openapi.json`** — the spec must be updated to match what the API actually returns. The tests are correct; the spec is wrong.

The generated client is a **canary** (see [ADR-002](docs/adr/002-openapi-strict-generation.md)). When it breaks, it means partners using code generators will hit the same issue. The correct response is to fix the spec, not hide the problem.

### Credentials — NEVER skip

All scenarios require credentials. If credentials are missing:
- Tests must **fail immediately** (`assertNotNull` / `set -euo pipefail`)
- NEVER use `@Disabled`, `assumeTrue`, or conditional skipping
- NEVER commit credentials or `.env` files

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
