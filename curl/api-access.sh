#!/usr/bin/env bash
#
# S1: API Access — curl example
#
# Demonstrates: acquire OAuth2 token → list clients
# Loads credentials from the repo-root .env file.
#
# Usage:
#   cp .env.example .env   # fill in credentials
#   bash curl/api-access.sh
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
echo "=== 2. List clients ==="
CLIENTS=$(curl -s "${API}/api/clients" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Accept: application/json")

COUNT=$(echo "$CLIENTS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])))")
echo "Got $COUNT clients"
echo "$CLIENTS" | python3 -m json.tool | head -20

echo ""
echo "Done. API access verified."
