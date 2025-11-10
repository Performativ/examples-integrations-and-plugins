#!/usr/bin/env bash
#
# Client Lifecycle — curl example
#
# Demonstrates: acquire token → create person → create client → read back → delete both
# Loads credentials from the repo-root .env file.
#
# Usage:
#   cp .env.example .env   # fill in credentials
#   bash curl/client-lifecycle.sh
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/../.env"

if [ ! -f "$ENV_FILE" ]; then
    echo "Error: .env file not found at $ENV_FILE"
    echo "Copy .env.example to .env and fill in your credentials."
    exit 1
fi

# Load .env (skip comments and blank lines)
set -a
# shellcheck source=/dev/null
source <(grep -v '^\s*#' "$ENV_FILE" | grep -v '^\s*$')
set +a

for var in API_BASE_URL TOKEN_BROKER_URL PLUGIN_CLIENT_ID PLUGIN_CLIENT_SECRET; do
    if [ -z "${!var:-}" ]; then
        echo "Error: $var is not set in .env"
        exit 1
    fi
done

API="${API_BASE_URL%/}"
AUDIENCE="${TOKEN_AUDIENCE:-backend-api}"

echo "=== 1. Acquire access token ==="
TOKEN_RESPONSE=$(curl -s -X POST "${TOKEN_BROKER_URL}/oauth/token" \
    -u "${PLUGIN_CLIENT_ID}:${PLUGIN_CLIENT_SECRET}" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=client_credentials&audience=${AUDIENCE}")

TOKEN=$(echo "$TOKEN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
echo "Token acquired (${#TOKEN} chars)"

echo ""
echo "=== 2. Create Person ==="
PERSON=$(curl -s -X POST "${API}/api/persons" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"first_name":"Curl","last_name":"Example","email":"curl-example@example.com","language_code":"en"}')

PERSON_ID=$(echo "$PERSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Person ID: $PERSON_ID"

echo ""
echo "=== 3. Create Client ==="
CLIENT=$(curl -s -X POST "${API}/api/clients" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"name\":\"Curl Example Client\",\"type\":\"individual\",\"is_active\":true,\"primary_person_id\":${PERSON_ID}}")

CLIENT_ID=$(echo "$CLIENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Client ID: $CLIENT_ID"

echo ""
echo "=== 4. Read back Client ==="
curl -s "${API}/api/clients/${CLIENT_ID}" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Accept: application/json" | python3 -m json.tool | head -20

echo ""
echo "=== 5. Read back Person ==="
curl -s "${API}/api/persons/${PERSON_ID}" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Accept: application/json" | python3 -m json.tool | head -20

echo ""
echo "=== 6. Delete Client ==="
curl -s -X DELETE "${API}/api/clients/${CLIENT_ID}" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Accept: application/json" -o /dev/null -w "HTTP %{http_code}\n"

echo ""
echo "=== 7. Delete Person ==="
curl -s -X DELETE "${API}/api/persons/${PERSON_ID}" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Accept: application/json" -o /dev/null -w "HTTP %{http_code}\n"

echo ""
echo "Done. All entities created and cleaned up."
