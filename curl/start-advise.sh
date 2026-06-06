#!/usr/bin/env bash
#
# S8: Start Advise — curl example
#
# Demonstrates: full advice session lifecycle.
# Person → Client → Portfolio → Advice Context → Agreement →
# Document upload → Signing envelope (attached to the agreement) →
# submit-signing → ceremony → mark-signed →
# Session → data-ready → activate → ready-to-sign → signed → cleanup.
#
# v1 signing model: the advisory agreement is created first, then the signing
# envelope attaches to it via signable_type=advisory_agreement + signable_id.
# Envelope documents carry a ceremony_role (input = to-be-signed,
# output = signed result). A signed agreement is required before a session.
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
SESSION_ID=""
AGREEMENT_ID=""
ENVELOPE_ID=""
CONTEXT_ID=""

cleanup() {
    echo ""
    echo "=== Cleanup ==="
    # Delete session (signed status is deletable)
    if [ -n "$SESSION_ID" ]; then
        echo "Deleting Session ${SESSION_ID}..."
        curl -s -X DELETE "${API}/api/v1/advice-sessions/${SESSION_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
    # Expire then delete agreement (signed → expired → deletable)
    if [ -n "$AGREEMENT_ID" ]; then
        echo "Expiring Agreement ${AGREEMENT_ID}..."
        curl -s -X POST "${API}/api/v1/advice-agreements/${AGREEMENT_ID}/expire" \
            -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
            -H "Idempotency-Key: $(uuidgen)" -d '{}' -o /dev/null -w "HTTP %{http_code}\n" || true
        echo "Deleting Agreement ${AGREEMENT_ID}..."
        curl -s -X DELETE "${API}/api/v1/advice-agreements/${AGREEMENT_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
    # Delete advice context (agreement delete cascade-deletes the envelope)
    if [ -n "$CONTEXT_ID" ]; then
        echo "Deleting Advice Context ${CONTEXT_ID}..."
        curl -s -X DELETE "${API}/api/v1/advice-contexts/${CONTEXT_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
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
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"first_name":"Curl","last_name":"S8-StartAdvise","email":"curl-s8@example.com","language_code":"en"}')

PERSON_ID=$(echo "$PERSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Person ID: ${PERSON_ID}"

echo ""
echo "=== 3. Create Client ==="
CLIENT=$(curl -s -X POST "${API}/api/v1/clients" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"name":"Curl-S8 Client","type":"individual","is_active":true,"currency_id":47}')

CLIENT_ID=$(echo "$CLIENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Client ID: ${CLIENT_ID}"

echo ""
echo "=== 4. Link Person to Client ==="
LINK_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/client-persons" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"client_id\":${CLIENT_ID},\"person_id\":${PERSON_ID},\"is_primary\":true}")
echo "HTTP ${LINK_STATUS}"
if [ "$LINK_STATUS" != "201" ]; then echo "ERROR: Expected 201, got ${LINK_STATUS}"; exit 1; fi

echo ""
echo "=== 5. Create Portfolio ==="
PORTFOLIO=$(curl -s -X POST "${API}/api/v1/portfolios" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"name\":\"Curl-S8 Portfolio\",\"client_ids\":[${CLIENT_ID}],\"currency_id\":47}")

PORTFOLIO_ID=$(echo "$PORTFOLIO" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Portfolio ID: ${PORTFOLIO_ID}"

# --- Advice context + agreement (prerequisites for the signing envelope) ---
# v1: the agreement is created before the signing envelope, since the
# envelope references the agreement as its signable.

echo ""
echo "=== 6. Create Advice Context ==="
CONTEXT=$(curl -s -X POST "${API}/api/v1/advice-contexts" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"advice_policy_id\":${POLICY_ID},\"type\":\"individual\",\"name\":\"Curl-S8 Advice Context\",\"reference_person_id\":${PERSON_ID},\"members\":[{\"person_id\":${PERSON_ID},\"client_id\":${CLIENT_ID},\"power_of_attorney\":false}]}")

CONTEXT_ID=$(echo "$CONTEXT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Advice Context ID: ${CONTEXT_ID}"

echo ""
echo "=== 7. Create Advisory Agreement ==="
AGREEMENT=$(curl -s -X POST "${API}/api/v1/advice-contexts/${CONTEXT_ID}/agreements" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"version":"1.0","external_reference":"curl-s8-agreement"}')

AGREEMENT_ID=$(echo "$AGREEMENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Agreement ID: ${AGREEMENT_ID}, status: draft"

# --- Document upload and signing envelope (attached to the agreement) ---

echo ""
echo "=== 8. Upload Document (multipart) ==="
TMPFILE=$(mktemp /tmp/curl-s8-agreement-XXXXXX.txt)
echo "Hello World - Curl S8 Advisory Agreement" > "$TMPFILE"

DOC=$(curl -s -X POST "${API}/api/v1/documents" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -F "file=@${TMPFILE}" \
    -F "type=advisory_agreement")

rm -f "$TMPFILE"

DOCUMENT_ID=$(echo "$DOC" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Uploaded Document ID: ${DOCUMENT_ID}"

echo ""
echo "=== 9. Create Signing Envelope ==="
# v1: the envelope attaches to a polymorphic signable —
# signable_type=advisory_agreement + signable_id.
ENVELOPE=$(curl -s -X POST "${API}/api/v1/signing-envelopes" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"title\":\"Curl-S8 Agreement Envelope\",\"signable_type\":\"advisory_agreement\",\"signable_id\":${AGREEMENT_ID}}")

ENVELOPE_ID=$(echo "$ENVELOPE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Envelope ID: ${ENVELOPE_ID}"

echo ""
echo "=== 10. Add Document to Envelope ==="
# ceremony_role=input marks the document that will be signed.
ADD_DOC_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/documents" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"document_id\":${DOCUMENT_ID},\"ceremony_role\":\"input\"}")
echo "HTTP ${ADD_DOC_STATUS}"
if [ "$ADD_DOC_STATUS" != "201" ]; then echo "ERROR: Expected 201, got ${ADD_DOC_STATUS}"; exit 1; fi

echo ""
echo "=== 11. Add Signer Party to Envelope ==="
ADD_PARTY_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/parties" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"person_id\":${PERSON_ID},\"role\":\"signer\"}")
echo "HTTP ${ADD_PARTY_STATUS}"
if [ "$ADD_PARTY_STATUS" != "201" ]; then echo "ERROR: Expected 201, got ${ADD_PARTY_STATUS}"; exit 1; fi

echo ""
echo "=== 12. Send Envelope ==="
SEND_RESULT=$(curl -s -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/send" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)")

ENVELOPE_STATUS=$(echo "$SEND_RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Envelope status after send: ${ENVELOPE_STATUS}"

# --- Walk the agreement through signing ---

echo ""
echo "=== 13. Submit Signing (draft → pending_signature) ==="
# v1: submit-signing accepts no body fields; idempotency via header.
SUBMIT_RESULT=$(curl -s -X POST "${API}/api/v1/advice-agreements/${AGREEMENT_ID}/submit-signing" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{}')
echo "Submit-signing status: $(echo "$SUBMIT_RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('status','ERROR: '+str(d)))" 2>/dev/null || echo "error")"

echo ""
echo "=== 14. Upload signed document ==="
SIGNED_TMPFILE=$(mktemp /tmp/curl-s8-signed-XXXXXX.txt)
echo "Signed Advisory Agreement - Curl S8" > "$SIGNED_TMPFILE"

SIGNED_DOC=$(curl -s -X POST "${API}/api/v1/documents" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -F "file=@${SIGNED_TMPFILE}" \
    -F "type=advisory_agreement")

rm -f "$SIGNED_TMPFILE"

SIGNED_DOC_ID=$(echo "$SIGNED_DOC" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Uploaded Signed Document ID: ${SIGNED_DOC_ID}"

echo ""
echo "=== 15. Add signed document to envelope ==="
# ceremony_role=output marks the resulting signed document. Added before
# marking the party signed — the last party auto-completes (and locks)
# the envelope.
ADD_SIGNED_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/documents" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"document_id\":${SIGNED_DOC_ID},\"ceremony_role\":\"output\"}")
echo "HTTP ${ADD_SIGNED_STATUS}"
if [ "$ADD_SIGNED_STATUS" != "201" ]; then echo "ERROR: Expected 201, got ${ADD_SIGNED_STATUS}"; exit 1; fi

echo ""
echo "=== 16. Mark signer party as signed on envelope ==="
PARTY_ID=$(curl -s "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['parties'][0]['id'])")

MARK_PARTY_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/parties/${PARTY_ID}/mark-signed" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"signed_at\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}")
echo "HTTP ${MARK_PARTY_STATUS}"
if [ "$MARK_PARTY_STATUS" != "200" ]; then echo "ERROR: Expected 200, got ${MARK_PARTY_STATUS}"; exit 1; fi

echo ""
echo "=== 17. Mark Agreement Signed (pending_signature → signed) ==="
SIGNED_RESULT=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST \
    "${API}/api/v1/advice-agreements/${AGREEMENT_ID}/mark-signed" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"signed_document_id\":${SIGNED_DOC_ID}}")
MARK_STATUS=$(echo "$SIGNED_RESULT" | grep "HTTP_STATUS:" | cut -d: -f2)
echo "Mark-signed HTTP status: ${MARK_STATUS}"
if [ "$MARK_STATUS" != "200" ] && [ "$MARK_STATUS" != "201" ]; then
    echo "ERROR: mark-signed returned ${MARK_STATUS}"; echo "$SIGNED_RESULT"; exit 1
fi

# --- Advice Session lifecycle ---

echo ""
echo "=== 18. Create Advice Session ==="
SESSION=$(curl -s -X POST "${API}/api/v1/advice-contexts/${CONTEXT_ID}/sessions" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"external_reference":"curl-s8-session"}')

SESSION_ID=$(echo "$SESSION" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
SESSION_STATUS=$(echo "$SESSION" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Created Session ID: ${SESSION_ID}, status: ${SESSION_STATUS}"

echo ""
echo "=== 19. Read Advice Session ==="
curl -s "${API}/api/v1/advice-sessions/${SESSION_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(f\"  id={d['id']} status={d['status']}\")"

echo ""
echo "=== 20. Mark Data Ready ==="
RESULT=$(curl -s -X POST "${API}/api/v1/advice-sessions/${SESSION_ID}/mark-data-ready" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{}')
echo "  status: $(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")"

echo ""
echo "=== 21. Activate Session ==="
# v1: activate no longer takes a redirect_url in the request body; the
# advisor-UI redirect is returned by the API on the activated session.
RESULT=$(curl -s -X POST "${API}/api/v1/advice-sessions/${SESSION_ID}/activate" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{}')
echo "  status: $(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")"

echo ""
echo "=== 22. Mark Ready to Sign ==="
RESULT=$(curl -s -X POST "${API}/api/v1/advice-sessions/${SESSION_ID}/mark-ready-to-sign" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{}')
echo "  status: $(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")"

echo ""
echo "=== 23. Mark Session Signed ==="
RESULT=$(curl -s -X POST "${API}/api/v1/advice-sessions/${SESSION_ID}/mark-signed" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{}')
echo "  status: $(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")"

echo ""
echo "=== 24. Read Session (final) ==="
FINAL=$(curl -s "${API}/api/v1/advice-sessions/${SESSION_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")
echo "  status: $(echo "$FINAL" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(f\"{d['status']} (signed_at={d.get('signed_at','N/A')})\")")"

echo ""
echo "=== 25-31. Delete (handled by cleanup trap) ==="
echo "Done. Full advice session lifecycle complete: created → data_ready → active → ready_to_sign → signed."
