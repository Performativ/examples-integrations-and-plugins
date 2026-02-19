# New Client Checklist

Use this checklist when adding a new client implementation (e.g., Python, TypeScript).

## Prerequisites

- [ ] Read [SCENARIOS.md](../../SCENARIOS.md) — understand all scenario steps and expected behavior
- [ ] Read [CONTRIBUTING.md](../../CONTRIBUTING.md) — understand the PR process

## Implementation

- [ ] Create directory: `<client>/scenarios/` (e.g., `python/scenarios/`)
- [ ] Implement S1: API Access
- [ ] Implement S2: Client Lifecycle (create, read, update, delete; cleanup on failure)
- [ ] Implement S3: Portfolio Setup (full chain with reverse-order cleanup)
- [ ] All scenarios load credentials from repo-root `.env`
- [ ] Credentials are **required** — fail immediately if missing, don't skip
- [ ] Cleanup runs on failure (trap in bash, try/finally in other languages)

## CI Integration

- [ ] Create `.ci/run-<client>.sh` runner script
- [ ] Add entry to `.matrix.json` under `clients` with all scenario IDs
- [ ] Verify parity passes: `bash .ci/validate-parity.sh`

## Documentation

- [ ] Update `README.md` to list the new client in the approaches table
- [ ] Follow language-community conventions for code style
- [ ] Add a brief setup note if the client requires additional tooling (e.g., `pip install`)

## PR

- [ ] All status checks pass (parity check, security scan)
- [ ] No credentials in the diff
- [ ] Request review from `@Performativ/api-guardians`
