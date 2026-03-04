#!/usr/bin/env bash
#
# S6: Bulk Ingestion — curl example
#
# Demonstrates: acquire token, create a bulk async batch, obtain a presigned
# upload URL via v1 API.
#
# Usage:
#   cp .env.example .env   # fill in credentials
#   bash curl/bulk-ingestion.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../.ci/lib/auth.sh"
source "${SCRIPT_DIR}/../.ci/lib/helpers.sh"

load_env "$SCRIPT_DIR"
acquire_token

echo ""
echo "=== S6: Bulk Ingestion ==="

step "Create batch"
BATCH_RESPONSE=$(curl -s -X POST "${API}/api/v1/bulk/async/batches" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"upload_mode":"presigned"}')

BATCH_ID=$(extract_field "$BATCH_RESPONSE" "data.batch_id")
echo "Created batch ID: ${BATCH_ID}"

step "Get presigned URL"
PRESIGNED_RESPONSE=$(curl -s -X POST "${API}/api/v1/bulk/async/batches/${BATCH_ID}/presigned-url" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"file_name":"clients.csv","resource_type":"clients"}')

python3 -c "
import sys, json
data = json.loads(sys.argv[1]).get('data', {})
assert data.get('upload_url'), 'upload_url should not be empty'
print(f\"Upload URL: {data['upload_url'][:80]}...\")
print(f\"File ID: {data.get('file_id', 'N/A')}\")
print('Bulk ingestion batch setup verified.')
" "$PRESIGNED_RESPONSE"

echo ""
echo "Done. Batch created and presigned URL obtained."
