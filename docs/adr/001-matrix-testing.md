# ADR-001: Matrix Testing Across Client Types

**Status**: Accepted

## Context

We have three scenario implementations (curl, java-manual, java-generated) and three core scenarios (S1–S3). A single `mvn verify` job doesn't test curl, can't isolate individual scenario failures, and gives no visibility into which client/scenario combinations pass or fail.

## Decision

Adopt an N-client x M-scenario matrix where each cell runs as an independent CI job. The matrix is defined in `.matrix.json` in the public repo and expanded by a `build-matrix` job in the private `examples-ci` repo.

- **curl** is the reference implementation — simplest, easiest to read, first to implement new scenarios.
- **java-manual** verifies API behavior using raw HTTP with no abstraction beyond JDK.
- **java-generated** uses the OpenAPI-generated client with strict deserialization — catches spec drift before partners do.

Each cell is independently pass/fail (`fail-fast: false`). A summary job renders a markdown table on the workflow's Summary tab.

## Consequences

- Adding a new client or scenario requires updating `.matrix.json` — the parity validator catches gaps.
- CI cost scales linearly with cells (currently 9). Acceptable for weekly + on-demand runs.
- Each cell has its own runtime setup (bash-only for curl, JDK + Maven for Java), keeping concerns separate.
