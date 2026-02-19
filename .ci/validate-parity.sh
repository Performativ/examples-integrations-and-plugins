#!/usr/bin/env bash
#
# Validate scenario parity: every (client, scenario) cell in .matrix.json
# has a corresponding implementation file.
#
# Runs without credentials — purely structural check.
# Exit code 0 = all cells covered, 1 = missing implementations.
#
# Usage: bash .ci/validate-parity.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MATRIX_FILE="${REPO_ROOT}/.matrix.json"

if [ ! -f "$MATRIX_FILE" ]; then
    echo "FAIL: .matrix.json not found at ${MATRIX_FILE}"
    exit 1
fi

MISSING=0

# Map scenario IDs to expected file patterns per client
check_cell() {
    local client="$1" scenario="$2"
    local found=false

    case "$client" in
        curl)
            case "$scenario" in
                s1) [ -f "${REPO_ROOT}/curl/api-access.sh" ] && found=true ;;
                s2) [ -f "${REPO_ROOT}/curl/client-lifecycle.sh" ] && found=true ;;
                s3) [ -f "${REPO_ROOT}/curl/portfolio-setup.sh" ] && found=true ;;
            esac
            ;;
        java-manual)
            case "$scenario" in
                s1) [ -f "${REPO_ROOT}/java/scenarios/src/test/java/com/performativ/scenarios/manual/ApiAccessScenario.java" ] && found=true ;;
                s2) [ -f "${REPO_ROOT}/java/scenarios/src/test/java/com/performativ/scenarios/manual/ClientLifecycleScenario.java" ] && found=true ;;
                s3) [ -f "${REPO_ROOT}/java/scenarios/src/test/java/com/performativ/scenarios/manual/PortfolioSetupScenario.java" ] && found=true ;;
            esac
            ;;
        java-generated)
            case "$scenario" in
                s1) [ -f "${REPO_ROOT}/java/scenarios/src/test/java/com/performativ/scenarios/generated/ApiAccessScenario.java" ] && found=true ;;
                s2) [ -f "${REPO_ROOT}/java/scenarios/src/test/java/com/performativ/scenarios/generated/ClientLifecycleScenario.java" ] && found=true ;;
                s3) [ -f "${REPO_ROOT}/java/scenarios/src/test/java/com/performativ/scenarios/generated/PortfolioSetupScenario.java" ] && found=true ;;
            esac
            ;;
        *)
            echo "  WARN: Unknown client '${client}' — skipping"
            return
            ;;
    esac

    if $found; then
        echo "  OK: ${client} / ${scenario}"
    else
        echo "  MISSING: ${client} / ${scenario}"
        MISSING=$((MISSING + 1))
    fi
}

echo "Validating scenario parity against .matrix.json..."
echo ""

# Parse .matrix.json with python3 (available in CI and most dev environments)
CELLS=$(python3 -c "
import json
with open('${MATRIX_FILE}') as f:
    matrix = json.load(f)
for client, info in matrix['clients'].items():
    for scenario in info['scenarios']:
        print(f'{client} {scenario}')
")

while IFS=' ' read -r client scenario; do
    check_cell "$client" "$scenario"
done <<< "$CELLS"

echo ""
if [ "$MISSING" -gt 0 ]; then
    echo "FAIL: ${MISSING} missing implementation(s). See above."
    exit 1
else
    echo "PASS: All scenario cells have implementations."
    exit 0
fi
