#!/usr/bin/env bash
#
# S6: Advisory Agreement — curl example
#
# Demonstrates the full advisory agreement signing journey:
#   1. List advice policies
#   2. Create Person + Client (prerequisites)
#   3. Upload document via multipart POST
#   4. Create signing envelope, add document + signer party, send
#   5. Create advice context with member
#   6. Create advisory agreement linked to signing envelope
#   7. Submit-signing (draft → pending_signature)
#   8. Mark-signed (pending_signature → signed)
#   9. Close advice context, clean up
#
# Usage:
#   cp .env.example .env   # fill in credentials
#   bash curl/advisory-agreement.sh

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
    -d '{"first_name":"Curl","last_name":"S6-AdvisoryAgreement","email":"curl-s6@example.com","language_code":"en"}')

PERSON_ID=$(echo "$PERSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Person ID: ${PERSON_ID}"

echo ""
echo "=== 3. Create Client ==="
CLIENT=$(curl -s -X POST "${API}/api/v1/clients" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"name":"Curl-S6 Client","type":"individual","is_active":true,"currency_id":47}')

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

# --- Document upload and signing envelope ---

echo ""
echo "=== 4. Upload Document (multipart) ==="
echo "Hello World - Curl S6 Advisory Agreement" > /tmp/curl-s6-agreement.txt
DOC_RESPONSE=$(curl -s -X POST "${API}/api/v1/documents" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json" \
    -F "file=@/tmp/curl-s6-agreement.txt" \
    -F "type=advisory_agreement")

DOC_ID=$(echo "$DOC_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Uploaded Document ID: ${DOC_ID}"

echo ""
echo "=== 5. Create Signing Envelope ==="
ENVELOPE=$(curl -s -X POST "${API}/api/v1/signing-envelopes" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{"title":"Curl-S6 Agreement Envelope"}')

ENVELOPE_ID=$(echo "$ENVELOPE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
ENVELOPE_STATUS=$(echo "$ENVELOPE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Created Envelope ID: ${ENVELOPE_ID}, status: ${ENVELOPE_STATUS}"

echo ""
echo "=== 5b. Add Document to Envelope ==="
curl -s -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/documents" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"document_id\":${DOC_ID},\"role\":\"source\"}" \
    -o /dev/null -w "HTTP %{http_code}\n"

echo ""
echo "=== 5c. Add Signer Party to Envelope ==="
curl -s -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/parties" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"person_id\":${PERSON_ID},\"role\":\"signer\"}" \
    -o /dev/null -w "HTTP %{http_code}\n"

echo ""
echo "=== 5d. Send Envelope ==="
SEND_RESPONSE=$(curl -s -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/send" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d '{}')

SEND_STATUS=$(echo "$SEND_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Envelope status after send: ${SEND_STATUS}"

# --- Advice context and agreement ---

echo ""
echo "=== 6. Create Advice Context ==="
CONTEXT=$(curl -s -X POST "${API}/api/v1/advice-contexts" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"advice_policy_id\":${POLICY_ID},\"type\":\"individual\",\"name\":\"Curl-S6 Advice Context\",\"reference_person_id\":${PERSON_ID},\"members\":[{\"person_id\":${PERSON_ID},\"power_of_attorney\":false}]}")

CONTEXT_ID=$(echo "$CONTEXT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
CONTEXT_STATUS=$(echo "$CONTEXT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Created Advice Context ID: ${CONTEXT_ID}, status: ${CONTEXT_STATUS}"

echo ""
echo "=== 7. Create Advisory Agreement (linked to envelope) ==="
AGREEMENT=$(curl -s -X POST "${API}/api/v1/advice-contexts/${CONTEXT_ID}/agreements" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"version\":\"1.0\",\"signing_envelope_id\":${ENVELOPE_ID}}")

AGREEMENT_ID=$(echo "$AGREEMENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
AGREEMENT_STATUS=$(echo "$AGREEMENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Created Agreement ID: ${AGREEMENT_ID}, status: ${AGREEMENT_STATUS}"

echo ""
echo "=== 8. Submit Signing (draft → pending_signature) ==="
SUBMIT_RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST \
    "${API}/api/v1/advisory-agreements/${AGREEMENT_ID}/submit-signing" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"idempotency_key\":\"curl-s6-submit-$(date +%s)\",\"document_id\":${DOC_ID}}")

SUBMIT_STATUS=$(echo "$SUBMIT_RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
echo "Submit-signing HTTP status: ${SUBMIT_STATUS}"
if [ "$SUBMIT_STATUS" != "200" ] && [ "$SUBMIT_STATUS" != "201" ]; then
    echo "WARNING: submit-signing failed — see repro-submit-signing-500.sh for known bug details"
    echo "Response: $(echo "$SUBMIT_RESPONSE" | head -1)"
fi

echo ""
echo "=== 9. Mark Agreement Signed (pending_signature → signed) ==="
MARK_RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST \
    "${API}/api/v1/advisory-agreements/${AGREEMENT_ID}/mark-signed" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"idempotency_key\":\"curl-s6-signed-$(date +%s)\",\"signed_document_id\":${DOC_ID}}")

MARK_STATUS=$(echo "$MARK_RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
echo "Mark-signed HTTP status: ${MARK_STATUS}"

echo ""
echo "=== 10. Verify Agreement Status ==="
SIGNED=$(curl -s "${API}/api/v1/advisory-agreements/${AGREEMENT_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")
SIGNED_STATUS=$(echo "$SIGNED" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Agreement status: ${SIGNED_STATUS}"

echo ""
echo "=== 11. Close Advice Context ==="
curl -s -X POST "${API}/api/v1/advice-contexts/${CONTEXT_ID}/close" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "{\"idempotency_key\":\"curl-s6-close-$(date +%s)\",\"reason\":\"Scenario complete\"}" -o /dev/null -w "HTTP %{http_code}\n"

echo ""
echo "=== 12-13. Delete (handled by cleanup trap) ==="
echo "Done. Advisory agreement signing journey complete."
