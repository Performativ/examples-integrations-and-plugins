#!/usr/bin/env bash
#
# Top-level CI dispatcher — delegates to the appropriate client runner.
#
# Required environment variables:
#   SCENARIO_CLIENT  — one of: curl, java-manual, java-generated
#
# Each runner executes ALL scenarios for its client type.
# Called by the CI matrix workflow (examples-ci repo).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

: "${SCENARIO_CLIENT:?SCENARIO_CLIENT is required}"

echo "=== Running all scenarios for ${SCENARIO_CLIENT} ==="

case "$SCENARIO_CLIENT" in
    curl)
        exec bash "${SCRIPT_DIR}/run-curl.sh"
        ;;
    java-manual)
        exec bash "${SCRIPT_DIR}/run-java.sh" manual
        ;;
    java-generated)
        exec bash "${SCRIPT_DIR}/run-java.sh" generated
        ;;
    *)
        echo "Unknown client: ${SCENARIO_CLIENT}"
        exit 1
        ;;
esac
