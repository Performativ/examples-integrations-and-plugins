#!/usr/bin/env bash
#
# Run all curl scenarios sequentially.
#
# Usage: bash .ci/run-curl.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

SCRIPTS=(
    "curl/api-access.sh"
    "curl/client-lifecycle.sh"
    "curl/portfolio-setup.sh"
    "curl/webhook-delivery.sh"
    "curl/bulk-ingestion.sh"
    "curl/advisory-agreement.sh"
    "curl/start-advise.sh"
    "curl/error-responses.sh"
)

FAILED=0

for script in "${SCRIPTS[@]}"; do
    echo ""
    echo "========================================="
    echo "  ${script}"
    echo "========================================="
    if bash "${REPO_ROOT}/${script}"; then
        echo "PASS: ${script}"
    else
        echo "FAIL: ${script}"
        FAILED=$((FAILED + 1))
    fi
done

echo ""
if [ "$FAILED" -gt 0 ]; then
    echo "FAIL: ${FAILED} curl scenario(s) failed."
    exit 1
else
    echo "PASS: All curl scenarios passed."
fi
