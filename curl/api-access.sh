#!/usr/bin/env bash
#
# S1: API Access — curl example
#
# Demonstrates: acquire OAuth2 token, list clients via v1 API.
#
# Usage:
#   cp .env.example .env   # fill in credentials
#   bash curl/api-access.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../.ci/lib/auth.sh"

load_env "$SCRIPT_DIR"
acquire_token

echo ""
echo "=== List clients ==="
CLIENTS=$(curl -s "${API}/api/v1/clients" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")

COUNT=$(echo "$CLIENTS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])))")
echo "Got ${COUNT} clients"
echo "$CLIENTS" | python3 -m json.tool | head -20

echo ""
echo "Done. API access verified."
