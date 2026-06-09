#!/usr/bin/env bash
#
# S10: Financial Sync — curl example
#
# Syncs an advice context's financial situation: resolve the members, discover
# the portfolios and cash accounts Performativ already holds, then write
# positions and balances via the batch endpoints.
#
# Models a couple holding a JOINT portfolio and JOINT cash account plus a solo
# portfolio, and contrasts the two discovery styles:
#   - batched:   GET /portfolios?filter[client_id]=A,B  (joint returned once, with
#                client_ids + is_shared)
#   - per-member: GET /clients/{id}/portfolios           (joint returned per holder)
#
# Usage:
#   cp .env.example .env   # fill in credentials
#   bash curl/financial-sync.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../.ci/lib/auth.sh"
source "${SCRIPT_DIR}/../.ci/lib/helpers.sh"

load_env "$SCRIPT_DIR"

TODAY="$(date +%Y-%m-%d)"

PERSON_A_ID=""
PERSON_B_ID=""
CLIENT_A_ID=""
CLIENT_B_ID=""
ADVICE_CONTEXT_ID=""
JOINT_PORTFOLIO_ID=""
SOLO_PORTFOLIO_ID=""
JOINT_CASH_ACCOUNT_ID=""
PORTFOLIO_IDS=""
CASH_ACCOUNT_IDS=""

post_json() {
    curl -s -X POST "${API}$1" \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" \
        -H "Accept: application/json" \
        -H "Idempotency-Key: $(uuidgen)" \
        -d "$2"
}

get_json() {
    # -g disables curl globbing so filter[client_id] brackets pass through.
    curl -s -g "${API}$1" -H "Authorization: Bearer ${TOKEN}" -H "Accept: application/json"
}

cleanup() {
    echo ""
    echo "=== Cleanup ==="
    # Clear batch-written slices first so portfolio/account deletes aren't blocked.
    for pid in $PORTFOLIO_IDS; do
        post_json "/api/v1/external-positions/batch/replace" \
            "{\"portfolio_id\":${pid},\"date\":\"${TODAY}\",\"positions\":[]}" >/dev/null || true
    done
    for caid in $CASH_ACCOUNT_IDS; do
        BAL=$(get_json "/api/v1/external-balances?filter%5Bcash_account_id%5D=${caid}" || true)
        for bid in $(echo "$BAL" | python3 -c "import sys,json
try:
    print(' '.join(str(b['id']) for b in json.load(sys.stdin)['data']))
except Exception:
    pass" 2>/dev/null); do
            curl -s -X DELETE "${API}/api/v1/external-balances/${bid}" \
                -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "" || true
        done
    done
    for id in $JOINT_CASH_ACCOUNT_ID $SOLO_PORTFOLIO_ID $JOINT_PORTFOLIO_ID; do
        [ -n "$id" ] && curl -s -X DELETE "${API}/api/v1/portfolios/${id}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "" 2>/dev/null || true
    done
    [ -n "$JOINT_CASH_ACCOUNT_ID" ] && curl -s -X DELETE "${API}/api/v1/cash-accounts/${JOINT_CASH_ACCOUNT_ID}" \
        -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "" || true
    [ -n "$ADVICE_CONTEXT_ID" ] && curl -s -X DELETE "${API}/api/v1/advice-contexts/${ADVICE_CONTEXT_ID}" \
        -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "" || true
    for id in $CLIENT_A_ID $CLIENT_B_ID; do
        [ -n "$id" ] && curl -s -X DELETE "${API}/api/v1/clients/${id}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "" || true
    done
    for id in $PERSON_A_ID $PERSON_B_ID; do
        [ -n "$id" ] && curl -s -X DELETE "${API}/api/v1/persons/${id}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "" || true
    done
}
trap cleanup EXIT

acquire_token

step "Find an advice policy"
POLICIES=$(get_json "/api/v1/advice-policies")
ADVICE_POLICY_ID=$(echo "$POLICIES" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; assert d, 'no advice policies'; print(d[0]['id'])")

step "Create Person A"
PERSON_A_ID=$(extract_id "$(post_json /api/v1/persons '{"first_name":"Curl","last_name":"S10-John","email":"curl-s10-john@example.com","language_code":"en"}')")
step "Create Person B"
PERSON_B_ID=$(extract_id "$(post_json /api/v1/persons '{"first_name":"Curl","last_name":"S10-Jane","email":"curl-s10-jane@example.com","language_code":"en"}')")

step "Create Client A"
CLIENT_A_ID=$(extract_id "$(post_json /api/v1/clients '{"name":"Curl-S10 Client A","type":"individual","is_active":true,"currency_id":47}')")
step "Create Client B"
CLIENT_B_ID=$(extract_id "$(post_json /api/v1/clients '{"name":"Curl-S10 Client B","type":"individual","is_active":true,"currency_id":47}')")

step "Link persons to clients"
post_json /api/v1/client-persons "{\"client_id\":${CLIENT_A_ID},\"person_id\":${PERSON_A_ID},\"is_primary\":true}" >/dev/null
post_json /api/v1/client-persons "{\"client_id\":${CLIENT_B_ID},\"person_id\":${PERSON_B_ID},\"is_primary\":true}" >/dev/null

step "Create Advice Context (couple)"
# Client-centric contract: members carry client_id only.
ADVICE_CONTEXT_ID=$(extract_id "$(post_json /api/v1/advice-contexts \
    "{\"advice_policy_id\":${ADVICE_POLICY_ID},\"type\":\"couple\",\"name\":\"Curl-S10 Advice Context\",\"members\":[{\"client_id\":${CLIENT_A_ID}},{\"client_id\":${CLIENT_B_ID}}]}")")

step "Create joint Portfolio (A+B)"
JOINT_PORTFOLIO_ID=$(extract_id "$(post_json /api/v1/portfolios "{\"name\":\"Curl-S10 Joint Portfolio\",\"client_ids\":[${CLIENT_A_ID},${CLIENT_B_ID}],\"currency_id\":47}")")
step "Create solo Portfolio (A)"
SOLO_PORTFOLIO_ID=$(extract_id "$(post_json /api/v1/portfolios "{\"name\":\"Curl-S10 Solo Portfolio\",\"client_ids\":[${CLIENT_A_ID}],\"currency_id\":47}")")
step "Create joint Cash Account (A+B)"
JOINT_CASH_ACCOUNT_ID=$(extract_id "$(post_json /api/v1/cash-accounts "{\"name\":\"Curl-S10 Joint Cash Account\",\"client_ids\":[${CLIENT_A_ID},${CLIENT_B_ID}],\"currency_id\":47}")")

step "Resolve advice context members"
MEMBERS=$(get_json "/api/v1/advice-contexts/${ADVICE_CONTEXT_ID}/clients")
echo "$MEMBERS" | python3 -c "
import sys, json
ids = {m['client_id'] for m in json.load(sys.stdin)['data']}
assert ids == {${CLIENT_A_ID}, ${CLIENT_B_ID}}, f'unexpected members: {ids}'
print('OK: members resolved')
"

step "Batched discovery — portfolios (one call for both members)"
PORTFOLIOS=$(get_json "/api/v1/portfolios?filter%5Bclient_id%5D=${CLIENT_A_ID},${CLIENT_B_ID}&include=clients")
echo "$PORTFOLIOS" | python3 -c "
import sys, json
data = json.load(sys.stdin)['data']
joint = [p for p in data if p['id'] == ${JOINT_PORTFOLIO_ID}]
assert len(joint) == 1, f'joint portfolio must appear once, got {len(joint)}'
assert joint[0]['is_shared'] is True, 'joint portfolio is_shared must be true'
assert ${CLIENT_A_ID} in joint[0]['client_ids'] and ${CLIENT_B_ID} in joint[0]['client_ids']
solo = [p for p in data if p['id'] == ${SOLO_PORTFOLIO_ID}]
assert solo and solo[0]['is_shared'] is False, 'solo portfolio is_shared must be false'
print('OK: joint once, is_shared/client_ids correct')
"
PORTFOLIO_IDS=$(echo "$PORTFOLIOS" | python3 -c "import sys,json; print(' '.join(str(p['id']) for p in json.load(sys.stdin)['data']))")

step "Batched discovery — cash accounts"
CASH=$(get_json "/api/v1/cash-accounts?filter%5Bclient_id%5D=${CLIENT_A_ID},${CLIENT_B_ID}&include=clients")
echo "$CASH" | python3 -c "
import sys, json
data = json.load(sys.stdin)['data']
joint = [c for c in data if c['id'] == ${JOINT_CASH_ACCOUNT_ID}]
assert len(joint) == 1, f'joint cash account must appear once, got {len(joint)}'
assert joint[0]['is_shared'] is True, 'joint cash account is_shared must be true'
print('OK: joint cash account once, is_shared true')
"
CASH_ACCOUNT_IDS=$(echo "$CASH" | python3 -c "import sys,json; print(' '.join(str(c['id']) for c in json.load(sys.stdin)['data']))")

step "Per-member discovery — joint returned once per holder"
JOINT_SEEN=0
for CID in ${CLIENT_A_ID} ${CLIENT_B_ID}; do
    RESP=$(get_json "/api/v1/clients/${CID}/portfolios")
    CNT=$(echo "$RESP" | python3 -c "import sys,json; print(sum(1 for p in json.load(sys.stdin)['data'] if p['id']==${JOINT_PORTFOLIO_ID}))")
    JOINT_SEEN=$((JOINT_SEEN + CNT))
done
[ "$JOINT_SEEN" -eq 2 ] || { echo "ERROR: joint portfolio should appear once per holder (2 total), got ${JOINT_SEEN}" >&2; exit 1; }
echo "OK: joint portfolio returned once per holder (dedup is the caller's job)"

step "Write positions per portfolio"
for PID in $PORTFOLIO_IDS; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/external-positions/batch/replace" \
        -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -H "Accept: application/json" \
        -H "Idempotency-Key: $(uuidgen)" \
        -d "{\"portfolio_id\":${PID},\"date\":\"${TODAY}\",\"positions\":[{\"instrument_isin\":\"US0378331005\",\"instrument_name\":\"Apple Inc.\",\"quantity\":100,\"value\":15000,\"currency_id\":47}]}")
    assert_status "$STATUS" "200" "Replace positions for portfolio ${PID}"
done

step "Write balances (one call across all cash accounts)"
BALANCES=$(python3 -c "
ids = '${CASH_ACCOUNT_IDS}'.split()
import json
print(json.dumps({'balances': [{'cash_account_id': int(i), 'date': '${TODAY}', 'balance': 50000.00, 'currency_id': 47} for i in ids]}))
")
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/external-balances/batch/upsert" \
    -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" -d "$BALANCES")
assert_status "$STATUS" "200" "Upsert balances"

echo ""
echo "=== Financial sync complete ==="
