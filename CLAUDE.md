# Performativ Examples — Agent Context

This is the public, partner-facing examples repo for the Performativ API. It contains integration scenarios, documentation, and CI infrastructure.

**Read [AGENTS.md](AGENTS.md) first** — it contains mandatory rules that override any default behavior.

## Key Files

| File | Role |
|------|------|
| `.matrix.json` | Machine-readable manifest of clients, scenarios, and environments. CI reads this. |
| `SCENARIOS.md` | Canonical scenario definitions — the single source of truth for what each scenario does. |
| `.ci/run-scenario.sh` | CI entry point — dispatches to client-specific runners based on `SCENARIO_CLIENT` + `SCENARIO_ID`. |
| `.ci/validate-parity.sh` | Structural parity check — verifies every (client, scenario) cell has an implementation file. |
| `docs/adr/` | Architecture decision records explaining key design choices. |

## Scenario Matrix

Three clients x three scenarios = 9 cells:

| | S1: API Access | S2: Client Lifecycle | S3: Portfolio Setup |
|---|---|---|---|
| curl | `curl/api-access.sh` | `curl/client-lifecycle.sh` | `curl/portfolio-setup.sh` |
| java-manual | `manual/ApiAccessScenario.java` | `manual/ClientLifecycleScenario.java` | `manual/PortfolioSetupScenario.java` |
| java-generated | `generated/ApiAccessScenario.java` | `generated/ClientLifecycleScenario.java` | `generated/PortfolioSetupScenario.java` |

Java files are under `java/scenarios/src/test/java/com/performativ/scenarios/`.

## Parity Rules

- Every scenario in `.matrix.json` must be implemented in every client.
- Run `bash .ci/validate-parity.sh` to check — it reads `.matrix.json` and verifies files exist.
- PRs that break parity will fail the Parity Check CI.

## Adding a New Scenario

1. Define it in `SCENARIOS.md` with the step table.
2. Implement in curl first (reference implementation).
3. Implement in all Java packages (manual + generated).
4. Add the scenario ID to `.matrix.json`.
5. Run `bash .ci/validate-parity.sh` to verify.

## Adding a New Client

1. Create `<client>/scenarios/` directory.
2. Implement all scenarios from `.matrix.json`.
3. Add `.ci/run-<client>.sh` runner script.
4. Add entry to `.matrix.json` under `clients`.
5. See `templates/new-client/CHECKLIST.md` for the full checklist.

## CI Architecture

- **Public repo** (this repo): code, scripts, structural checks (parity, security scan). No credentials.
- **Private repo** (`examples-ci`): workflow that checks out this repo and runs scenarios with tenant secrets.
- Fork PRs cannot trigger CI runs — only `examples-ci` maintainers can.

## Conventions

- Credentials loaded from `.env` at repo root (never committed).
- Credentials are **required** — tests fail immediately, never skip.
- Cleanup on failure: curl scripts use `trap`, Java uses try/finally in `@AfterAll`.
- Test data uses distinctive names (e.g., `Scenario-S2 Client`) for debugging.
