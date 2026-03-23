#!/usr/bin/env bash
#
# S4: External Holdings — curl example
#
# Demonstrates: person relationships, cash accounts, external positions and
# balances via the v1 API.
#
# Creates two persons with a relationship (married couple), links both to a
# client, adds a portfolio with a cash account, then creates external
# positions and balances.
#
# Usage:
#   cp .env.example .env   # fill in credentials
#   bash curl/external-holdings.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../.ci/lib/auth.sh"
source "${SCRIPT_DIR}/../.ci/lib/helpers.sh"

load_env "$SCRIPT_DIR"

# Track IDs for cleanup on failure
RELATIONSHIP_TYPE_ID=""
PERSON_A_ID=""
PERSON_B_ID=""
RELATIONSHIP_ID=""
CLIENT_ID=""
PORTFOLIO_ID=""
CASH_ACCOUNT_ID=""
PORTFOLIO_CASH_ACCOUNT_ID=""
EXTERNAL_POSITION_ID=""
EXTERNAL_BALANCE_ID=""

cleanup() {
    echo ""
    echo "=== Cleanup ==="
    if [ -n "$EXTERNAL_BALANCE_ID" ]; then
        echo "Deleting External Balance ${EXTERNAL_BALANCE_ID}..."
        curl -s -X DELETE "${API}/api/v1/external-balances/${EXTERNAL_BALANCE_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
    if [ -n "$EXTERNAL_POSITION_ID" ]; then
        echo "Deleting External Position ${EXTERNAL_POSITION_ID}..."
        curl -s -X DELETE "${API}/api/v1/external-positions/${EXTERNAL_POSITION_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
    if [ -n "$PORTFOLIO_CASH_ACCOUNT_ID" ]; then
        echo "Deleting Portfolio Cash Account link ${PORTFOLIO_CASH_ACCOUNT_ID}..."
        curl -s -X DELETE "${API}/api/v1/portfolio-cash-accounts/${PORTFOLIO_CASH_ACCOUNT_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
    if [ -n "$CASH_ACCOUNT_ID" ]; then
        echo "Deleting Cash Account ${CASH_ACCOUNT_ID}..."
        curl -s -X DELETE "${API}/api/v1/cash-accounts/${CASH_ACCOUNT_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
    if [ -n "$PORTFOLIO_ID" ]; then
        echo "Deleting Portfolio ${PORTFOLIO_ID}..."
        curl -s -X DELETE "${API}/api/v1/portfolios/${PORTFOLIO_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
    if [ -n "$RELATIONSHIP_ID" ]; then
        echo "Deleting Person Relationship ${RELATIONSHIP_ID}..."
        curl -s -X DELETE "${API}/api/v1/person-relationships/${RELATIONSHIP_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
    if [ -n "$PERSON_B_ID" ]; then
        echo "Deleting Person B ${PERSON_B_ID}..."
        curl -s -X DELETE "${API}/api/v1/persons/${PERSON_B_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
    if [ -n "$PERSON_A_ID" ]; then
        echo "Deleting Person A ${PERSON_A_ID}..."
        curl -s -X DELETE "${API}/api/v1/persons/${PERSON_A_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
    if [ -n "$CLIENT_ID" ]; then
        echo "Deleting Client ${CLIENT_ID}..."
        curl -s -X DELETE "${API}/api/v1/clients/${CLIENT_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
    if [ -n "$RELATIONSHIP_TYPE_ID" ]; then
        echo "Deleting Relationship Type ${RELATIONSHIP_TYPE_ID}..."
        curl -s -X DELETE "${API}/api/v1/person-relationship-types/${RELATIONSHIP_TYPE_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
}
trap cleanup EXIT

acquire_token

step "Create Relationship Type"
REL_TYPE=$(curl -s -X POST "${API}/api/v1/person-relationship-types" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"key":"curl-s4-married","label":"Married","is_symmetric":true}')

RELATIONSHIP_TYPE_ID=$(extract_id "$REL_TYPE")
echo "Created Relationship Type ID: ${RELATIONSHIP_TYPE_ID}"

step "Create Person A (John)"
PERSON_A=$(curl -s -X POST "${API}/api/v1/persons" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"first_name":"Curl","last_name":"S4-ExternalHoldings-John","email":"curl-s4-john@example.com","language_code":"en"}')

PERSON_A_ID=$(extract_id "$PERSON_A")
echo "Created Person A ID: ${PERSON_A_ID}"

step "Create Person B (Jane)"
PERSON_B=$(curl -s -X POST "${API}/api/v1/persons" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"first_name":"Curl","last_name":"S4-ExternalHoldings-Jane","email":"curl-s4-jane@example.com","language_code":"en"}')

PERSON_B_ID=$(extract_id "$PERSON_B")
echo "Created Person B ID: ${PERSON_B_ID}"

step "Create Relationship (married)"
RELATIONSHIP=$(curl -s -X POST "${API}/api/v1/person-relationships" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"person_id\":${PERSON_A_ID},\"related_person_id\":${PERSON_B_ID},\"person_relationship_type_id\":${RELATIONSHIP_TYPE_ID}}")

RELATIONSHIP_ID=$(extract_id "$RELATIONSHIP")
echo "Created Relationship ID: ${RELATIONSHIP_ID}"

step "Create Client"
CLIENT=$(curl -s -X POST "${API}/api/v1/clients" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"name":"Curl-S4 Client","type":"individual","is_active":true,"currency_id":47}')

CLIENT_ID=$(extract_id "$CLIENT")
echo "Created Client ID: ${CLIENT_ID}"

step "Link Person A (primary)"
LINK_A_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/client-persons" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"client_id\":${CLIENT_ID},\"person_id\":${PERSON_A_ID},\"is_primary\":true}")
echo "HTTP ${LINK_A_STATUS}"
assert_status "$LINK_A_STATUS" "201" "Link Person A to Client"

step "Link Person B (secondary)"
LINK_B_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/client-persons" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"client_id\":${CLIENT_ID},\"person_id\":${PERSON_B_ID},\"is_primary\":false}")
echo "HTTP ${LINK_B_STATUS}"
assert_status "$LINK_B_STATUS" "201" "Link Person B to Client"

step "Create Portfolio"
PORTFOLIO=$(curl -s -X POST "${API}/api/v1/portfolios" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"name\":\"Curl-S4 Portfolio\",\"client_ids\":[${CLIENT_ID}],\"currency_id\":47}")

PORTFOLIO_ID=$(extract_id "$PORTFOLIO")
echo "Created Portfolio ID: ${PORTFOLIO_ID}"

step "Create Cash Account"
CASH_ACCOUNT=$(curl -s -X POST "${API}/api/v1/cash-accounts" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"name\":\"Curl-S4 Cash Account\",\"client_ids\":[${CLIENT_ID}],\"currency_id\":47}")

CASH_ACCOUNT_ID=$(extract_id "$CASH_ACCOUNT")
echo "Created Cash Account ID: ${CASH_ACCOUNT_ID}"

step "Link Cash Account to Portfolio"
PCA=$(curl -s -X POST "${API}/api/v1/portfolio-cash-accounts" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"portfolio_id\":${PORTFOLIO_ID},\"cash_account_id\":${CASH_ACCOUNT_ID}}")

PORTFOLIO_CASH_ACCOUNT_ID=$(extract_id "$PCA")
echo "Created Portfolio Cash Account link ID: ${PORTFOLIO_CASH_ACCOUNT_ID}"

step "Create External Position (Apple stock via inline ISIN)"
EXT_POS=$(curl -s -X POST "${API}/api/v1/external-positions" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"portfolio_id\":${PORTFOLIO_ID},\"instrument_isin\":\"US0378331005\",\"instrument_name\":\"Apple Inc.\",\"quantity\":100,\"currency_id\":47,\"date\":\"$(date -u +%Y-%m-%d)\"}")

EXTERNAL_POSITION_ID=$(extract_id "$EXT_POS")
echo "Created External Position ID: ${EXTERNAL_POSITION_ID}"

step "Create External Balance"
EXT_BAL=$(curl -s -X POST "${API}/api/v1/external-balances" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"cash_account_id\":${CASH_ACCOUNT_ID},\"balance\":50000.00,\"currency_id\":47,\"date\":\"$(date -u +%Y-%m-%d)\"}")

EXTERNAL_BALANCE_ID=$(extract_id "$EXT_BAL")
echo "Created External Balance ID: ${EXTERNAL_BALANCE_ID}"

step "Read External Position"
EXT_POS_READ=$(curl -s "${API}/api/v1/external-positions/${EXTERNAL_POSITION_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")
echo "External Position: $(echo "$EXT_POS_READ" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(f\"id={d['id']} isin={d.get('instrument_isin','N/A')} qty={d.get('quantity','N/A')}\")")"

step "Read External Balance"
EXT_BAL_READ=$(curl -s "${API}/api/v1/external-balances/${EXTERNAL_BALANCE_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")
echo "External Balance: $(echo "$EXT_BAL_READ" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(f\"id={d['id']} balance={d.get('balance','N/A')}\")")"

step "List Person Relationships"
RELS=$(curl -s "${API}/api/v1/persons/${PERSON_A_ID}/relationships" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")
REL_COUNT=$(echo "$RELS" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['data']))")
echo "Person A has ${REL_COUNT} relationship(s)"

step "Update External Position (quantity changed)"
UPDATE_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "${API}/api/v1/external-positions/${EXTERNAL_POSITION_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"portfolio_id\":${PORTFOLIO_ID},\"instrument_isin\":\"US0378331005\",\"instrument_name\":\"Apple Inc.\",\"quantity\":200,\"currency_id\":47,\"date\":\"$(date -u +%Y-%m-%d)\"}")
echo "HTTP ${UPDATE_STATUS}"
assert_status "$UPDATE_STATUS" "200" "Update External Position"

step "Delete (handled by cleanup trap)"
echo "Done. External holdings scenario complete — all entities created, verified, and will be cleaned up."
