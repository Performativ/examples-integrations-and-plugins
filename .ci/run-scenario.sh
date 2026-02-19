#!/usr/bin/env bash
#
# Top-level CI dispatcher — delegates to the appropriate client runner.
#
# Required environment variables:
#   SCENARIO_CLIENT  — one of: curl, java-manual, java-generated
#   SCENARIO_ID      — one of: s1, s2, s3
#
# Called by the CI matrix workflow (examples-ci repo).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

: "${SCENARIO_CLIENT:?SCENARIO_CLIENT is required}"
: "${SCENARIO_ID:?SCENARIO_ID is required}"

echo "=== Running ${SCENARIO_CLIENT} / ${SCENARIO_ID} ==="

case "$SCENARIO_CLIENT" in
    curl)
        exec bash "${SCRIPT_DIR}/run-curl.sh" "$SCENARIO_ID"
        ;;
    java-manual)
        exec bash "${SCRIPT_DIR}/run-java.sh" manual "$SCENARIO_ID"
        ;;
    java-generated)
        exec bash "${SCRIPT_DIR}/run-java.sh" generated "$SCENARIO_ID"
        ;;
    *)
        echo "Unknown client: ${SCENARIO_CLIENT}"
        exit 1
        ;;
esac
