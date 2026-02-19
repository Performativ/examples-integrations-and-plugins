# Performativ Examples — Agent Context

This is the public, partner-facing examples repo for the Performativ API.

**Read [AGENTS.md](AGENTS.md) first** — it contains mandatory rules that override any default behavior.

## Key Files

| File | Role |
|------|------|
| `.matrix.json` | Machine-readable manifest of clients, scenarios, and environments. CI reads this. |
| `SCENARIOS.md` | Canonical scenario definitions — single source of truth for what each scenario does. |
| `.ci/run-scenario.sh` | CI entry point — dispatches to client-specific runners based on `SCENARIO_CLIENT` + `SCENARIO_ID`. |
| `.ci/validate-parity.sh` | Structural parity check — verifies every (client, scenario) cell has an implementation. |
| `docs/adr/` | Architecture decision records. |

## Parity Rules

- Every scenario in `.matrix.json` must be implemented in every client.
- Run `bash .ci/validate-parity.sh` before every commit.
- PRs that break parity will fail CI.

## Adding a New Scenario

1. Define in `SCENARIOS.md` with the step table.
2. Implement in curl first (reference implementation).
3. Implement in all Java packages (manual + generated).
4. Add the scenario ID to `.matrix.json`.
5. Run `bash .ci/validate-parity.sh`.

## Adding a New Client

1. Create `<client>/scenarios/` directory.
2. Implement all scenarios from `.matrix.json`.
3. Add `.ci/run-<client>.sh` runner script.
4. Add entry to `.matrix.json` under `clients`.
5. See `templates/new-client/CHECKLIST.md`.

## CI Architecture

- **Public repo** (this repo): code, scripts, structural checks. No credentials.
- **Private repo** (`examples-ci`): workflow that runs scenarios with tenant secrets.
- Fork PRs cannot trigger CI runs.

## Conventions

- Credentials from `.env` at repo root (never committed). Tests fail immediately when missing.
- Cleanup on failure: curl scripts use `trap`, Java uses `@AfterAll` with raw HTTP.
- Test data uses distinctive names (e.g., `Scenario-S2 Client`) for debugging.
