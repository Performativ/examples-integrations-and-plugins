#!/usr/bin/env bash
#
# S7: Start Advise — curl example
#
# Demonstrates: full advice session lifecycle.
# Person → Client → Portfolio → Document upload → Signing envelope →
# Advice Context → Agreement (linked to envelope) → submit-signing →
# mark-signed → Session → data-ready → activate → ready-to-sign → signed →
# close context → cleanup.
#
# The advisory agreement must be signed before a session can be created.
#
# Usage:
#   cp .env.example .env   # fill in credentials
#   bash curl/start-advise.sh

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
echo "=== 1. List Advice Policies ==="
POLICIES=$(curl -s "${API}/api/v1/advice-policies" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")

POLICY_ID=$(echo "$POLICIES" | python3 -c "import sys,json; print(json.load(sys.stdin)['data'][0]['id'])")
echo "Using Advice Policy ID: ${POLICY_ID}"

echo ""
echo "=== 2. Create Person ==="
PERSON=$(curl -s -X POST "${API}/api/v1/persons" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"first_name":"Curl","last_name":"S7-StartAdvise","email":"curl-s7@example.com","language_code":"en"}')

PERSON_ID=$(echo "$PERSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Person ID: ${PERSON_ID}"

echo ""
echo "=== 3. Create Client ==="
CLIENT=$(curl -s -X POST "${API}/api/v1/clients" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"name":"Curl-S7 Client","type":"individual","is_active":true,"currency_id":47}')

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
    -d "{\"name\":\"Curl-S7 Portfolio\",\"client_id\":${CLIENT_ID},\"currency_id\":47}")

PORTFOLIO_ID=$(echo "$PORTFOLIO" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Portfolio ID: ${PORTFOLIO_ID}"

# --- Document upload and signing envelope (required for signed agreement) ---

echo ""
echo "=== 5. Upload Document (multipart) ==="
TMPFILE=$(mktemp /tmp/curl-s7-agreement-XXXXXX.txt)
echo "Hello World - Curl S7 Advisory Agreement" > "$TMPFILE"

DOC=$(curl -s -X POST "${API}/api/v1/documents" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json" \
    -F "file=@${TMPFILE}" \
    -F "type=advisory_agreement")

rm -f "$TMPFILE"

DOCUMENT_ID=$(echo "$DOC" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Uploaded Document ID: ${DOCUMENT_ID}"

echo ""
echo "=== 5b. Create Signing Envelope ==="
ENVELOPE=$(curl -s -X POST "${API}/api/v1/signing-envelopes" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"title":"Curl-S7 Agreement Envelope"}')

ENVELOPE_ID=$(echo "$ENVELOPE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Envelope ID: ${ENVELOPE_ID}"

echo ""
echo "=== 5c. Add Document to Envelope ==="
curl -s -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/documents" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"document_id\":${DOCUMENT_ID},\"role\":\"source\"}" \
    -o /dev/null -w "HTTP %{http_code}\n"

echo ""
echo "=== 5d. Add Signer Party to Envelope ==="
curl -s -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/parties" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"person_id\":${PERSON_ID},\"role\":\"signer\"}" \
    -o /dev/null -w "HTTP %{http_code}\n"

echo ""
echo "=== 5e. Send Envelope ==="
SEND_RESULT=$(curl -s -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/send" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json")

ENVELOPE_STATUS=$(echo "$SEND_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Envelope status after send: ${ENVELOPE_STATUS}"

# --- Advice context, agreement, and signing ---

echo ""
echo "=== 6. Create Advice Context ==="
CONTEXT=$(curl -s -X POST "${API}/api/v1/advice-contexts" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"advice_policy_id\":${POLICY_ID},\"type\":\"individual\",\"name\":\"Curl-S7 Advice Context\",\"reference_person_id\":${PERSON_ID},\"members\":[{\"person_id\":${PERSON_ID},\"power_of_attorney\":false}]}")

CONTEXT_ID=$(echo "$CONTEXT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Advice Context ID: ${CONTEXT_ID}"

echo ""
echo "=== 7. Create Advisory Agreement (linked to envelope) ==="
AGREEMENT=$(curl -s -X POST "${API}/api/v1/advice-contexts/${CONTEXT_ID}/agreements" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"version\":\"1.0\",\"signing_envelope_id\":${ENVELOPE_ID},\"external_reference\":\"curl-s7-agreement\"}")

AGREEMENT_ID=$(echo "$AGREEMENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Agreement ID: ${AGREEMENT_ID}, status: draft"

echo ""
echo "=== 7b. Submit Signing (draft → pending_signature) ==="
SUBMIT_RESULT=$(curl -s -X POST "${API}/api/v1/advisory-agreements/${AGREEMENT_ID}/submit-signing" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"idempotency_key\":\"curl-s7-submit-$(date +%s)\",\"document_id\":${DOCUMENT_ID}}")
echo "Submit-signing HTTP status: $(echo "$SUBMIT_RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('status','ERROR: '+str(d)))" 2>/dev/null || echo "error")"

echo ""
echo "=== 7c. Mark Agreement Signed (pending_signature → signed) ==="
SIGNED_RESULT=$(curl -s -X POST "${API}/api/v1/advisory-agreements/${AGREEMENT_ID}/mark-signed" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"idempotency_key\":\"curl-s7-signed-$(date +%s)\",\"signed_document_id\":${DOCUMENT_ID}}")
echo "Mark-signed HTTP status: $(echo "$SIGNED_RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('status','ERROR: '+str(d)))" 2>/dev/null || echo "error")"

# --- Advice Session lifecycle ---

echo ""
echo "=== 8. Create Advice Session ==="
SESSION=$(curl -s -X POST "${API}/api/v1/advice-contexts/${CONTEXT_ID}/sessions" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"external_session_id":"curl-s7-session","external_reference":"curl-s7"}')

SESSION_ID=$(echo "$SESSION" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
SESSION_STATUS=$(echo "$SESSION" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Created Session ID: ${SESSION_ID}, status: ${SESSION_STATUS}"

echo ""
echo "=== 9. Read Advice Session ==="
curl -s "${API}/api/v1/advice-sessions/${SESSION_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(f\"  id={d['id']} status={d['status']}\")"

echo ""
echo "=== 10. Mark Data Ready ==="
RESULT=$(curl -s -X POST "${API}/api/v1/advice-sessions/${SESSION_ID}/data-ready" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"idempotency_key\":\"curl-s7-data-ready-$(date +%s)\"}")
echo "  status: $(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")"

echo ""
echo "=== 11. Activate Session ==="
RESULT=$(curl -s -X POST "${API}/api/v1/advice-sessions/${SESSION_ID}/activate" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"idempotency_key\":\"curl-s7-activate-$(date +%s)\",\"redirect_url\":\"https://example.com/advisor-ui/session\"}")
echo "  status: $(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")"

echo ""
echo "=== 12. Mark Ready to Sign ==="
RESULT=$(curl -s -X POST "${API}/api/v1/advice-sessions/${SESSION_ID}/ready-to-sign" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"idempotency_key\":\"curl-s7-ready-to-sign-$(date +%s)\"}")
echo "  status: $(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")"

echo ""
echo "=== 13. Mark Session Signed ==="
RESULT=$(curl -s -X POST "${API}/api/v1/advice-sessions/${SESSION_ID}/signed" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"idempotency_key\":\"curl-s7-signed-$(date +%s)\"}")
echo "  status: $(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")"

echo ""
echo "=== 14. Read Session (final) ==="
FINAL=$(curl -s "${API}/api/v1/advice-sessions/${SESSION_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")
echo "  status: $(echo "$FINAL" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(f\"{d['status']} (signed_at={d.get('signed_at','N/A')})\")")"

echo ""
echo "=== 15. Close Advice Context ==="
curl -s -X POST "${API}/api/v1/advice-contexts/${CONTEXT_ID}/close" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"idempotency_key\":\"curl-s7-close-$(date +%s)\",\"reason\":\"Scenario complete\"}" -o /dev/null -w "HTTP %{http_code}\n"

echo ""
echo "=== 16-18. Delete (handled by cleanup trap) ==="
echo "Done. Full advice session lifecycle complete: created → data_ready → active → ready_to_sign → signed."
