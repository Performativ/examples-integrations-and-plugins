#!/usr/bin/env bash
#
# S5: Bulk Ingestion — curl example
#
# Demonstrates: set up an async bulk ingestion task using API key auth.
#
# Usage:
#   cp .env.example .env   # fill in credentials
#   bash curl/bulk-ingestion.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../.ci/lib/auth.sh"

load_env "$SCRIPT_DIR"

if [ -z "${API_KEY:-}" ]; then
    echo "Error: API_KEY is not set in .env"
    exit 1
fi

echo "=== S5: Bulk Ingestion ==="
echo ""
echo "=== 1. Setup ingestion task ==="
RESPONSE=$(curl -s -X POST "${API}/api/ingestion/async/setup-task" \
    -H "Authorization: Bearer ${API_KEY}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"entity":"clients"}')

echo "Response: ${RESPONSE}"

echo ""
echo "=== 2. Verify response ==="
python3 -c "
import sys, json
data = json.loads(sys.argv[1])
assert 'taskId' in data, 'Response missing taskId'
assert 'presignedUploadUrl' in data, 'Response missing presignedUploadUrl'
assert data['taskId'], 'taskId should not be empty'
assert data['presignedUploadUrl'], 'presignedUploadUrl should not be empty'
print(f\"taskId: {data['taskId']}\")
print(f\"presignedUploadUrl: {data['presignedUploadUrl'][:80]}...\")
print('Bulk ingestion setup verified.')
" "$RESPONSE"
