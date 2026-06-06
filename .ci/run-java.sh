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

# Failsafe's -Dit.test does not support fully-qualified dot-notation globs.
# Instead, exclude the other package so only the target package runs.
if [ "$PACKAGE" = "generated" ]; then
  EXCLUDE="**/manual/**"
elif [ "$PACKAGE" = "manual" ]; then
  EXCLUDE="**/generated/**"
else
  echo "Unknown package: ${PACKAGE}" >&2
  exit 1
fi

exec mvn verify -Dfailsafe.excludes="$EXCLUDE" -DskipTests=false -Dskip.unit.tests=true
