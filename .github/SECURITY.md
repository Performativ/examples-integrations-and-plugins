# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in this repository or in the Performativ platform, please report it responsibly.

**Do not open a public GitHub issue.**

Email **security@performativ.com** with:

1. A description of the vulnerability
2. Steps to reproduce
3. Any relevant logs or screenshots (redact credentials)

We will acknowledge receipt within 2 business days and aim to provide a fix or mitigation within 10 business days.

## Credential Leaks

If you believe credentials (client secrets, API keys, signing keys) have been exposed:

1. **Immediately** notify security@performativ.com
2. Rotate the affected credentials in your Performativ tenant (Plugins > Custom > regenerate)
3. If the leak is in a commit, we will force-push to remove it from history

## Scope

This policy covers:
- This repository (`examples-integrations-and-plugins`)
- The Performativ API and platform
- Credential material for sandbox, UAT, and production environments

## Secret Scanning

This repository has GitHub secret scanning and push protection enabled. Additionally, [gitleaks](https://github.com/gitleaks/gitleaks) runs on every push and pull request to catch Performativ-specific credential patterns.
