# ADR-003: Scenario Definitions as Single Source of Truth

**Status**: Accepted

## Context

With multiple implementations of each scenario across different languages, there's a risk of implementations diverging — one client testing slightly different steps, assertions, or cleanup behavior.

## Decision

[SCENARIOS.md](../../SCENARIOS.md) is the canonical definition of each scenario. It specifies:

- The exact HTTP methods, paths, and expected status codes per step
- Entity relationships and deletion order
- Test data naming conventions

All implementations (curl, java-manual, java-generated) must conform to these definitions. When adding a new scenario, the definition goes in `SCENARIOS.md` first, and all implementations must match it.

Agents and contributors diff their implementations against `SCENARIOS.md` to verify conformance.

## Consequences

- Changing a scenario's behavior requires updating `SCENARIOS.md` and all implementations together.
- Code review can verify conformance by comparing implementations against the step table.
- New contributors have a clear specification to implement against.
