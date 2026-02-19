# ADR-005: Secret Management and CI Security

**Status**: Accepted

## Context

This is a public repository. Integration scenarios require real credentials (client ID, client secret, API keys) to run. We need to ensure credentials are never exposed in the public repo while still enabling CI.

## Decision

### Split-repo architecture

- **Public repo** (`examples-integrations-and-plugins`): code, scripts, documentation. No credentials, no CI runs against real APIs.
- **Private repo** (`examples-ci`): GitHub Actions workflows that check out the public repo and run scenarios using secrets stored in the private repo.

### Secret storage

Credentials are stored as GitHub Environment secrets in `examples-ci`, scoped per environment (e.g., `sandbox-client-secret`). This provides:
- Environment-level access control
- Audit logging of secret access
- Protection rules (e.g., manual approval for production)

### Fork PR safety

Fork PRs to the public repo **cannot** trigger CI runs in `examples-ci`. Only people with write access to `examples-ci` can trigger workflows via `workflow_dispatch` or schedule. This is safe by design.

### Defense in depth

1. **GitHub secret scanning + push protection**: enabled on the public repo (free for public repos)
2. **gitleaks**: runs on every push/PR in the public repo, configured with Performativ-specific patterns
3. **CODEOWNERS**: all changes require review by `@Performativ/api-guardians`
4. **`.env` in `.gitignore`**: local credentials never committed

## Consequences

- Contributors can't run integration tests in CI from fork PRs — they test locally with their own credentials.
- Adding a new environment requires creating secrets in the private repo (documented process in `examples-ci` README).
- The public repo's CI only runs structural checks (parity, security scan) that need no credentials.
