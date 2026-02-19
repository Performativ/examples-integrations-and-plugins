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
readonly MATRIX_FILE="${REPO_ROOT}/.matrix.json"

if [ ! -f "$MATRIX_FILE" ]; then
    echo "FAIL: .matrix.json not found at ${MATRIX_FILE}"
    exit 1
fi

MISSING=0

# Scenario ID → file name mapping
scenario_file() {
    local scenario="$1"
    case "$scenario" in
        s1) echo "api-access" ;;
        s2) echo "client-lifecycle" ;;
        s3) echo "portfolio-setup" ;;
        *)  echo "" ;;
    esac
}

# Scenario ID → Java class name mapping
scenario_class() {
    local scenario="$1"
    case "$scenario" in
        s1) echo "ApiAccessScenario" ;;
        s2) echo "ClientLifecycleScenario" ;;
        s3) echo "PortfolioSetupScenario" ;;
        *)  echo "" ;;
    esac
}

check_cell() {
    local client="$1" scenario="$2"
    local file=""

    case "$client" in
        curl)
            local name
            name=$(scenario_file "$scenario")
            [ -n "$name" ] && file="${REPO_ROOT}/curl/${name}.sh"
            ;;
        java-manual|java-generated)
            local package="${client#java-}"
            local class
            class=$(scenario_class "$scenario")
            [ -n "$class" ] && file="${REPO_ROOT}/java/scenarios/src/test/java/com/performativ/scenarios/${package}/${class}.java"
            ;;
        *)
            echo "  WARN: Unknown client '${client}' — skipping"
            return
            ;;
    esac

    if [ -n "$file" ] && [ -f "$file" ]; then
        echo "  OK: ${client} / ${scenario}"
    else
        echo "  MISSING: ${client} / ${scenario}"
        MISSING=$((MISSING + 1))
    fi
}

echo "Validating scenario parity against .matrix.json..."
echo ""

# Parse .matrix.json — pass file path as argument, not interpolated into code
CELLS=$(python3 -c "
import json, sys
with open(sys.argv[1]) as f:
    matrix = json.load(f)
for client, info in matrix['clients'].items():
    for scenario in info['scenarios']:
        print(f'{client} {scenario}')
" "$MATRIX_FILE")

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
