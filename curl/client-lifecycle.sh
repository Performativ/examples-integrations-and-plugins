#!/usr/bin/env bash
#
# S2: Client Lifecycle — curl example
#
# Demonstrates: acquire token, create Person + Client, read, update, delete both.
#
# Usage:
#   cp .env.example .env   # fill in credentials
#   bash curl/client-lifecycle.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../.ci/lib/auth.sh"

load_env "$SCRIPT_DIR"

# Track IDs for cleanup on failure
PERSON_ID=""
CLIENT_ID=""

cleanup() {
    echo ""
    echo "=== Cleanup ==="
    if [ -n "$CLIENT_ID" ]; then
        echo "Deleting Client ${CLIENT_ID}..."
        curl -s -X DELETE "${API}/api/clients/${CLIENT_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
    if [ -n "$PERSON_ID" ]; then
        echo "Deleting Person ${PERSON_ID}..."
        curl -s -X DELETE "${API}/api/persons/${PERSON_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
}
trap cleanup EXIT

acquire_token

echo ""
echo "=== 2. Create Person ==="
PERSON=$(curl -s -X POST "${API}/api/persons" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"first_name":"Curl","last_name":"ClientLifecycle","email":"curl-s2@example.com","language_code":"en"}')

PERSON_ID=$(echo "$PERSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Person ID: ${PERSON_ID}"

echo ""
echo "=== 3. Create Client ==="
CLIENT=$(curl -s -X POST "${API}/api/clients" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"name\":\"Curl-S2 Client\",\"type\":\"individual\",\"is_active\":true,\"primary_person_id\":${PERSON_ID}}")

CLIENT_ID=$(echo "$CLIENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Client ID: ${CLIENT_ID}"

echo ""
echo "=== 4. Read back Client ==="
curl -s "${API}/api/clients/${CLIENT_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json" | python3 -m json.tool | head -20

echo ""
echo "=== 5. Update Client ==="
curl -s -X PUT "${API}/api/clients/${CLIENT_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"name":"Curl-S2 Client Updated"}' -o /dev/null -w "HTTP %{http_code}\n"

echo ""
echo "=== 6. Read back Person ==="
curl -s "${API}/api/persons/${PERSON_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json" | python3 -m json.tool | head -20

echo ""
echo "=== 7-8. Delete (handled by cleanup trap) ==="
echo "Done. All entities created, read, updated, and will be cleaned up."
