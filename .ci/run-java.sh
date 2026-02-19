#!/usr/bin/env bash
#
# Run all Java scenarios for a given package (manual or generated).
#
# Usage: bash .ci/run-java.sh <manual|generated>

set -euo pipefail

PACKAGE="${1:?Usage: run-java.sh <manual|generated>}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "Running all scenarios in com.performativ.scenarios.${PACKAGE}..."
cd "${REPO_ROOT}/java/scenarios"
exec mvn verify -Dit.test="com.performativ.scenarios.${PACKAGE}.*" -Dsurefire.skip=true
