# ADR-002: Strict OpenAPI Generation as Compatibility Check

**Status**: Accepted

## Context

The Performativ API publishes an OpenAPI 3.0 specification (`openapi.json`). Partners may use code generators to build typed clients. If the spec drifts from the actual API behavior (wrong types, missing fields, changed enums), generated clients break silently or with confusing errors.

## Decision

The `java-generated` scenario package uses an OpenAPI-generated client with **strict Jackson deserialization and no fallbacks**:

- `FAIL_ON_UNKNOWN_PROPERTIES = true` — if the API returns a field not declared in the spec, the test fails. This catches the most common form of spec drift: the API evolves but the spec doesn't get updated.
- `FAIL_ON_NULL_FOR_PRIMITIVES = true` — if a field mapped to `int` or `boolean` comes back null, the test fails instead of silently defaulting.
- No manual workarounds, no `@JsonIgnoreProperties`, no catch-and-swallow.

The generated client acts as a **canary**. When it breaks, it means the spec and the API have diverged — and partners using code generators will hit the same issue.

### `openapi.json` is an upstream artifact

`openapi.json` is generated from the API gateway and consumed read-only by this repo. When strict mode catches a spec gap:

1. The **manual** scenario for the same operation confirms the API itself works correctly.
2. The **generated** scenario failure pinpoints exactly which response field is missing from the spec.
3. The fix happens **upstream in the API gateway** that generates the spec — never by patching `openapi.json` in this repo.

This is the intended feedback loop: generated tests fail → spec issue is identified → API gateway spec is updated → updated `openapi.json` is copied here → generated tests pass.

## Consequences

- `java-generated` failures are **not bugs in the test** — they indicate spec drift that needs fixing upstream.
- The `java-manual` implementation of the same scenario proves the API itself works; the `java-generated` failure pinpoints the spec issue.
- Maintaining two Java implementations per scenario is extra work, justified by the early-warning value.
- Strict mode may cause failures when a new `openapi.json` is first imported, if the upstream spec has existing gaps. These failures are surfacing real issues that partners would encounter.
