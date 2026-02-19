# Contributing

Thank you for your interest in contributing to the Performativ examples repository. This is a partner-facing resource — contributions that improve clarity, add new client implementations, or fix bugs are welcome.

## What You Can Contribute

- **New client implementations** (e.g., Python, TypeScript) for existing scenarios
- **Bug fixes** in existing scenarios or documentation
- **Documentation improvements** — clearer explanations, better examples
- **Scenario proposals** — suggest new scenarios via an issue before implementing

## How to Add a New Client

1. Create a directory at the repo root (e.g., `python/scenarios/`)
2. Implement all scenarios listed in [SCENARIOS.md](SCENARIOS.md) — partial implementations will not be merged
3. Add a CI runner script at `.ci/run-<client>.sh`
4. Add an entry to `.matrix.json` under `clients`
5. Verify parity passes: `bash .ci/validate-parity.sh`
6. Open a PR — see [templates/new-client/CHECKLIST.md](templates/new-client/CHECKLIST.md) for the full checklist

## How to Add a New Scenario

1. Open an issue describing the scenario (what it tests, which API endpoints, expected behavior)
2. Once approved, add the definition to [SCENARIOS.md](SCENARIOS.md) with the step table
3. Implement in **all** existing clients (curl, java-manual, java-generated)
4. Add the scenario ID to `.matrix.json`
5. Open a PR

## PR Process

1. Fork the repo and create a feature branch
2. Make your changes
3. Ensure parity: every scenario must be implemented in every client
4. Push and open a PR against `main`
5. A member of `@Performativ/api-guardians` will review — expect feedback within 5 business days

### Requirements for Merge

- All status checks pass (parity check, security scan)
- At least one CODEOWNERS approval
- No credentials or secrets in the diff
- Squash merge (we maintain linear history)

## Code Style

| Language | Style |
|----------|-------|
| Shell (curl) | `set -euo pipefail`, load `.env` from repo root, clean up on exit via `trap` |
| Java | Follow existing `BaseScenario` patterns, JUnit 5 integration tests via `mvn verify` |
| Other | Follow the language's community conventions; include a brief note in your PR |

## Questions?

Open a GitHub issue or reach out to your Performativ integration contact.
