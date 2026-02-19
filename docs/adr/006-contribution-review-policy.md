# ADR-006: Contribution and Review Policy

**Status**: Accepted

## Context

As a public, partner-facing repository, we need a clear contribution policy that maintains quality while welcoming external contributions. Partners need to know what they can contribute, how, and what to expect from the review process.

## Decision

### Ownership

The `@Performativ/api-guardians` team owns all files via CODEOWNERS. CI and security config additionally requires `@Performativ/devsecops` review.

### What partners can contribute

- New client implementations (must cover all scenarios — parity enforced)
- Bug fixes in existing scenarios or documentation
- Documentation improvements
- Scenario proposals (via issue first, then implementation)

### Merge requirements

1. All status checks pass (parity check, security scan)
2. At least one CODEOWNERS approval
3. Squash merge only (linear history on `main`)
4. No credentials in the diff

### Parity enforcement

The `.ci/validate-parity.sh` script runs on every PR. It reads `.matrix.json` and verifies that every (client, scenario) cell has a corresponding implementation file. PRs that add a new scenario without implementing it in all clients will fail this check.

## Consequences

- Partners have a clear, documented path to contribute.
- Parity is automatically enforced — no scenario can be partially implemented.
- Review bottleneck on `api-guardians` is acceptable for a partner-facing resource where correctness matters.
- The contribution guide (`CONTRIBUTING.md`) references this ADR for the "why" behind the policy.
