# ADR-002: Strict OpenAPI Generation as Compatibility Check

**Status**: Accepted

## Context

The Performativ API publishes an OpenAPI 3.0 specification (`openapi.json`). Partners may use code generators to build typed clients. If the spec drifts from the actual API behavior (wrong types, missing fields, changed enums), generated clients break silently or with confusing errors.

## Decision

The `java-generated` scenario package uses an OpenAPI-generated client with **no fallbacks and no manual workarounds**. If the generated client fails to deserialize a response, the test fails immediately.

This is intentional: the generated client acts as a canary. When it breaks, it means the spec and the API have diverged — and partners using generators will hit the same issue.

## Consequences

- `java-generated` failures are **not bugs in the test** — they indicate spec drift that needs fixing upstream.
- The `java-manual` implementation of the same scenario proves the API itself works; the `java-generated` failure pinpoints the spec issue.
- Maintaining two Java implementations per scenario is extra work, justified by the early-warning value.
