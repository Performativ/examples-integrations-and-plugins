#!/usr/bin/env bash
#
# S6: Advisory Agreement — Signing Provider Plugin Perspective
#
# Tells the advisory agreement signing story from the viewpoint of a
# signing-provider plugin. The plugin:
#   1. Receives an AdviceContext.Created webhook
#   2. Queries members to discover who needs to sign
#   3. Prepares documents and a signing envelope
#   4. Creates and walks the agreement through signing states
#   5. Posts signing progress at each stage
#
# Flow (22 steps):
#   Setup (1-4) → Create advice context (5) → Query members (6) →
#   Prepare envelope (7-11) → Create agreement (12-13) →
#   Submit signing (14) → Progress: waiting (15) →
#   Signed doc + mark party (16-18) → Progress: complete (19) →
#   Mark agreement signed (20) → Verify (21) → Close (22)
#
# Cleanup: Client and Person are NOT deleted. Once an advice context
# references these entities, they cannot be removed via the API
# (FK constraint, backend #6183). Prefixed names (Curl-S6) make
# orphans identifiable.
#
# Usage:
#   cp .env.example .env   # fill in credentials
#   bash curl/advisory-agreement.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../.ci/lib/auth.sh"

load_env "$SCRIPT_DIR"
acquire_token

# ─── Setup ───────────────────────────────────────────────────────────

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
    -d '{"first_name":"Curl","last_name":"S6-AdvisoryAgreement","email":"curl-s6@example.com","language_code":"en"}')

PERSON_ID=$(echo "$PERSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Person ID: ${PERSON_ID}"

echo ""
echo "=== 3. Create Client ==="
CLIENT=$(curl -s -X POST "${API}/api/v1/clients" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"name":"Curl-S6 Client","type":"individual","is_active":true,"currency_id":47}')

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

# ─── Plugin receives AdviceContext.Created webhook ───────────────────

echo ""
echo "=== 5. Create Advice Context ==="
# Plugin webhook: AdviceContext.Created
#   The signing-provider plugin listens for this event to begin the
#   signing flow. The webhook payload includes the advice context ID
#   and the member list.
CONTEXT=$(curl -s -X POST "${API}/api/v1/advice-contexts" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"advice_policy_id\":${POLICY_ID},\"type\":\"individual\",\"name\":\"Curl-S6 Advice Context\",\"reference_person_id\":${PERSON_ID},\"members\":[{\"person_id\":${PERSON_ID},\"client_id\":${CLIENT_ID},\"power_of_attorney\":false}]}")

CONTEXT_ID=$(echo "$CONTEXT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
CONTEXT_STATUS=$(echo "$CONTEXT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Created Advice Context ID: ${CONTEXT_ID}, status: ${CONTEXT_STATUS}"

echo ""
echo "=== 6. Query Advice Context Members ==="
# Plugin discovers who needs to sign by querying the member list.
# Each member has a person_id (the signer) and client_id (the entity
# being advised). The plugin uses person_id when adding signer parties
# to the signing envelope.
MEMBERS=$(curl -s "${API}/api/v1/advice-contexts/${CONTEXT_ID}/clients" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")

MEMBER_PERSON_ID=$(echo "$MEMBERS" | python3 -c "import sys,json; print(json.load(sys.stdin)['data'][0]['person_id'])")
MEMBER_COUNT=$(echo "$MEMBERS" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['data']))")
echo "Found ${MEMBER_COUNT} member(s), signer person_id: ${MEMBER_PERSON_ID}"

# ─── Plugin prepares the signing envelope ────────────────────────────

echo ""
echo "=== 7. Upload Source Document ==="
# Plugin generates the advisory agreement document (e.g., from a
# template engine) and uploads it via multipart POST.
TMPFILE=$(mktemp /tmp/curl-s6-agreement-XXXXXX.txt)
echo "Hello World - Curl S6 Advisory Agreement" > "$TMPFILE"

DOC_RESPONSE=$(curl -s -X POST "${API}/api/v1/documents" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -F "file=@${TMPFILE}" \
    -F "type=advisory_agreement")

rm -f "$TMPFILE"

DOC_ID=$(echo "$DOC_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Uploaded Document ID: ${DOC_ID}"

echo ""
echo "=== 8. Create Signing Envelope ==="
ENVELOPE=$(curl -s -X POST "${API}/api/v1/signing-envelopes" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"title":"Curl-S6 Agreement Envelope"}')

ENVELOPE_ID=$(echo "$ENVELOPE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
ENVELOPE_STATUS=$(echo "$ENVELOPE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Created Envelope ID: ${ENVELOPE_ID}, status: ${ENVELOPE_STATUS}"

echo ""
echo "=== 9. Add Document to Envelope ==="
ADD_DOC_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/documents" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"document_id\":${DOC_ID},\"role\":\"source\"}")
echo "HTTP ${ADD_DOC_STATUS}"
if [ "$ADD_DOC_STATUS" != "201" ]; then echo "ERROR: Expected 201, got ${ADD_DOC_STATUS}"; exit 1; fi

echo ""
echo "=== 10. Add Signer Party ==="
# Uses person_id discovered from the member query (step 6), not the
# hard-coded person_id from setup. In production, the plugin would
# iterate all members and add each as a signer party.
ADD_PARTY_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/parties" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"person_id\":${MEMBER_PERSON_ID},\"role\":\"signer\"}")
echo "HTTP ${ADD_PARTY_STATUS}"
if [ "$ADD_PARTY_STATUS" != "201" ]; then echo "ERROR: Expected 201, got ${ADD_PARTY_STATUS}"; exit 1; fi

echo ""
echo "=== 11. Send Envelope ==="
SEND_RESPONSE=$(curl -s -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/send" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{}')

SEND_STATUS=$(echo "$SEND_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Envelope status after send: ${SEND_STATUS}"

# ─── Create and walk the agreement through signing ───────────────────

echo ""
echo "=== 12. Create Advisory Agreement ==="
AGREEMENT=$(curl -s -X POST "${API}/api/v1/advice-contexts/${CONTEXT_ID}/agreements" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"version\":\"1.0\",\"signing_envelope_id\":${ENVELOPE_ID}}")

AGREEMENT_ID=$(echo "$AGREEMENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
AGREEMENT_STATUS=$(echo "$AGREEMENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Created Agreement ID: ${AGREEMENT_ID}, status: ${AGREEMENT_STATUS}"

echo ""
echo "=== 13. Read Advisory Agreement ==="
READ_AGREEMENT=$(curl -s "${API}/api/v1/advice-agreements/${AGREEMENT_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")
echo "Agreement status: $(echo "$READ_AGREEMENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")"

echo ""
echo "=== 14. Submit Signing (draft → pending_signature) ==="
# Idempotency: a replayed submit-signing request returns the cached
# response with an Idempotent-Replayed header. Safe to retry on
# network timeout.
#
# Plugin webhook: AdvisoryAgreement.Updated
#   { "state": { "current": "pending_signature", "previous": "draft" } }
SUBMIT_RESPONSE=$(curl -s -X POST \
    "${API}/api/v1/advice-agreements/${AGREEMENT_ID}/submit-signing" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{}')

echo "Submit-signing status: $(echo "$SUBMIT_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")"

echo ""
echo "=== 15. Post Signing Progress (initial) ==="
# Idempotency: safe to retry if plugin crashes mid-flight. The platform
# stores the latest progress snapshot; retries overwrite with the same data.
#
# The plugin posts a progress update showing 0 of 1 signers have signed.
# This surfaces in the advisor UI as a substatus on the agreement.
PROGRESS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/advice-agreements/${AGREEMENT_ID}/report-signing-progress" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"substatus\":\"Waiting for signers\",\"signing_progress\":{\"provider\":\"example-signing-provider\",\"status\":\"in_progress\",\"signed_count\":0,\"total_signers\":1,\"signers\":[{\"name\":\"Curl S6-AdvisoryAgreement\",\"email\":\"curl-s6@example.com\",\"status\":\"pending\"}]}}")
echo "HTTP ${PROGRESS_STATUS}"
if [ "$PROGRESS_STATUS" != "200" ]; then echo "ERROR: Expected 200, got ${PROGRESS_STATUS}"; exit 1; fi

# ─── Signing ceremony ────────────────────────────────────────────────
# Order matters: upload signed doc → add to envelope → mark party signed.
# Marking the last party auto-completes the envelope, which locks it —
# no documents can be added after that.

echo ""
echo "=== 16. Upload Signed Document ==="
# The signing provider has collected all signatures. The plugin
# downloads the signed copy from the provider and uploads it.
SIGNED_TMPFILE=$(mktemp /tmp/curl-s6-signed-XXXXXX.txt)
echo "Signed Advisory Agreement - Curl S6" > "$SIGNED_TMPFILE"

SIGNED_DOC_RESPONSE=$(curl -s -X POST "${API}/api/v1/documents" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -F "file=@${SIGNED_TMPFILE}" \
    -F "type=advisory_agreement")

rm -f "$SIGNED_TMPFILE"

SIGNED_DOC_ID=$(echo "$SIGNED_DOC_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Uploaded Signed Document ID: ${SIGNED_DOC_ID}"

echo ""
echo "=== 17. Add Signed Document to Envelope ==="
# IMPORTANT: add the signed document BEFORE marking the signer party
# as signed (step 18). Marking the last party auto-completes the
# envelope, which locks it — no more documents can be added after that.
ADD_SIGNED_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/documents" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"document_id\":${SIGNED_DOC_ID},\"role\":\"signed\"}")
echo "HTTP ${ADD_SIGNED_STATUS}"
if [ "$ADD_SIGNED_STATUS" != "201" ]; then echo "ERROR: Expected 201, got ${ADD_SIGNED_STATUS}"; exit 1; fi

echo ""
echo "=== 18. Mark Signer Party Signed ==="
# Idempotency: prevents double-signing if the signing provider's
# callback fires twice. The platform returns the same response.
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
echo "=== 19. Post Signing Progress (complete) ==="
# All parties have signed. The plugin posts a final progress update
# so the advisor UI reflects completion.
SIGNED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
PROGRESS2_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/advice-agreements/${AGREEMENT_ID}/report-signing-progress" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"substatus\":\"All parties signed\",\"signing_progress\":{\"provider\":\"example-signing-provider\",\"status\":\"completed\",\"signed_count\":1,\"total_signers\":1,\"signers\":[{\"name\":\"Curl S6-AdvisoryAgreement\",\"email\":\"curl-s6@example.com\",\"status\":\"signed\",\"signed_at\":\"${SIGNED_AT}\"}]}}")
echo "HTTP ${PROGRESS2_STATUS}"
if [ "$PROGRESS2_STATUS" != "200" ]; then echo "ERROR: Expected 200, got ${PROGRESS2_STATUS}"; exit 1; fi

echo ""
echo "=== 20. Mark Agreement Signed (pending_signature → signed) ==="
# Idempotency: prevents double-signing if callback fires twice.
#
# Plugin webhook: AdvisoryAgreement.Updated
#   { "state": { "current": "signed", "previous": "pending_signature" } }
MARK_RESPONSE=$(curl -s -X POST \
    "${API}/api/v1/advice-agreements/${AGREEMENT_ID}/mark-signed" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"signed_document_id\":${SIGNED_DOC_ID}}")

echo "Mark-signed status: $(echo "$MARK_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")"

echo ""
echo "=== 21. Read Agreement (final) ==="
FINAL=$(curl -s "${API}/api/v1/advice-agreements/${AGREEMENT_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")
FINAL_STATUS=$(echo "$FINAL" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Agreement status: ${FINAL_STATUS}"

echo ""
echo "=== 22. Close Advice Context ==="
CLOSE_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/advice-contexts/${CONTEXT_ID}/close" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"reason":"Scenario complete"}')
echo "HTTP ${CLOSE_STATUS}"
if [ "$CLOSE_STATUS" != "200" ]; then echo "ERROR: Expected 200, got ${CLOSE_STATUS}"; exit 1; fi

echo ""
echo "Done. Advisory agreement signing journey complete (plugin perspective)."
# Cleanup note: Client and Person are intentionally NOT deleted.
# Once an advice context references these entities, they cannot be
# removed via the API (FK constraint, backend #6183).
