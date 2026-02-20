#!/usr/bin/env bash
#
# S3: Portfolio Setup — curl example
#
# Demonstrates: acquire token, create Person + Client + Portfolio via v1 API,
# read, update, delete all.
#
# Usage:
#   cp .env.example .env   # fill in credentials
#   bash curl/portfolio-setup.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../.ci/lib/auth.sh"

load_env "$SCRIPT_DIR"

# Track IDs for cleanup on failure
PERSON_ID=""
CLIENT_ID=""
PORTFOLIO_ID=""

cleanup() {
    echo ""
    echo "=== Cleanup ==="
    if [ -n "$PORTFOLIO_ID" ]; then
        echo "Deleting Portfolio ${PORTFOLIO_ID}..."
        curl -s -X DELETE "${API}/api/v1/portfolios/${PORTFOLIO_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
    if [ -n "$CLIENT_ID" ]; then
        echo "Deleting Client ${CLIENT_ID}..."
        curl -s -X DELETE "${API}/api/v1/clients/${CLIENT_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
    if [ -n "$PERSON_ID" ]; then
        echo "Deleting Person ${PERSON_ID}..."
        curl -s -X DELETE "${API}/api/v1/persons/${PERSON_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
}
trap cleanup EXIT

acquire_token

echo ""
echo "=== 2. Create Person ==="
PERSON=$(curl -s -X POST "${API}/api/v1/persons" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"first_name":"Curl","last_name":"PortfolioSetup","email":"curl-s3@example.com","language_code":"en"}')

PERSON_ID=$(echo "$PERSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Person ID: ${PERSON_ID}"

echo ""
echo "=== 3. Create Client ==="
CLIENT=$(curl -s -X POST "${API}/api/v1/clients" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"name":"Curl-S3 Client","type":"individual","is_active":true,"currency_id":47}')

CLIENT_ID=$(echo "$CLIENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Client ID: ${CLIENT_ID}"

echo ""
echo "=== 3b. Link Person to Client ==="
curl -s -X POST "${API}/api/v1/client-persons" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"client_id\":${CLIENT_ID},\"person_id\":${PERSON_ID},\"is_primary\":true}" \
    -o /dev/null -w "HTTP %{http_code}\n"

echo ""
echo "=== 4. Create Portfolio ==="
PORTFOLIO=$(curl -s -X POST "${API}/api/v1/portfolios" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"name\":\"Curl-S3 Portfolio\",\"client_id\":${CLIENT_ID},\"currency_id\":47}")

PORTFOLIO_ID=$(echo "$PORTFOLIO" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Portfolio ID: ${PORTFOLIO_ID}"

echo ""
echo "=== 5. Read back Portfolio ==="
curl -s "${API}/api/v1/portfolios/${PORTFOLIO_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json" | python3 -m json.tool | head -20

echo ""
echo "=== 6. Update Portfolio ==="
curl -s -X PUT "${API}/api/v1/portfolios/${PORTFOLIO_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"name":"Curl-S3 Portfolio Updated","currency_id":47}' -o /dev/null -w "HTTP %{http_code}\n"

echo ""
echo "=== 7-9. Delete (handled by cleanup trap) ==="
echo "Done. All entities created, read, updated, and will be cleaned up."
