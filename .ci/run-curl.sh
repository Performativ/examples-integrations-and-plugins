#!/usr/bin/env bash
#
# Run a curl scenario by ID.
#
# Usage: bash .ci/run-curl.sh <scenario-id>
#   e.g. bash .ci/run-curl.sh s1

set -euo pipefail

SCENARIO_ID="${1:?Usage: run-curl.sh <scenario-id>}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Map scenario IDs to curl scripts
case "$SCENARIO_ID" in
    s1) SCRIPT="curl/api-access.sh" ;;
    s2) SCRIPT="curl/client-lifecycle.sh" ;;
    s3) SCRIPT="curl/portfolio-setup.sh" ;;
    *)
        echo "Unknown scenario: ${SCENARIO_ID}"
        exit 1
        ;;
esac

echo "Running ${SCRIPT}..."
exec bash "${REPO_ROOT}/${SCRIPT}"
