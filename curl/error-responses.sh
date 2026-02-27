#!/usr/bin/env bash
#
# S8: Error Responses — curl example
#
# Demonstrates: API error handling with RFC 7807 Problem Details responses.
# Exercises validation errors (422) and not-found errors (404).
#
# No entities are created or deleted — no cleanup needed.
#
# Usage:
#   cp .env.example .env   # fill in credentials
#   bash curl/error-responses.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../.ci/lib/auth.sh"

load_env "$SCRIPT_DIR"
acquire_token

echo ""
echo "=== 1. Create Client with invalid payload (expect 422) ==="
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API}/api/v1/clients" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{}')

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo "HTTP ${HTTP_CODE}"

if [ "$HTTP_CODE" != "422" ]; then
    echo "ERROR: Expected 422 for empty client payload, got ${HTTP_CODE}"
    echo "$BODY"
    exit 1
fi

python3 -c "
import json, sys
body = json.loads(sys.argv[1])
required = ['type', 'title', 'status', 'detail']
for field in required:
    assert field in body, f'Missing RFC 7807 field: {field}'
assert body['status'] == 422, f'Expected status 422, got {body[\"status\"]}'
assert 'errors' in body, 'Validation error should include errors field'
print('RFC 7807 fields present: ' + ', '.join(required + ['errors']))
print('Validation errors:')
for field, messages in body['errors'].items():
    print(f'  {field}: {messages}')
" "$BODY"

echo ""
echo "=== 2. Read non-existent Client (expect 404) ==="
RESPONSE=$(curl -s -w "\n%{http_code}" "${API}/api/v1/clients/0" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo "HTTP ${HTTP_CODE}"

if [ "$HTTP_CODE" != "404" ]; then
    echo "ERROR: Expected 404 for non-existent client, got ${HTTP_CODE}"
    echo "$BODY"
    exit 1
fi

python3 -c "
import json, sys
body = json.loads(sys.argv[1])
required = ['type', 'title', 'status', 'detail']
for field in required:
    assert field in body, f'Missing RFC 7807 field: {field}'
assert body['status'] == 404, f'Expected status 404, got {body[\"status\"]}'
print('RFC 7807 fields present: ' + ', '.join(required))
print(f'Detail: {body[\"detail\"]}')
" "$BODY"

echo ""
echo "Done. Error responses verified."
