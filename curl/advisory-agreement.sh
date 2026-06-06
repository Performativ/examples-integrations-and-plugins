#!/usr/bin/env bash
#
# S7: Advisory Agreement — Signing Provider Plugin Perspective
#
# Tells the advisory agreement signing story from the viewpoint of a
# signing-provider plugin. The plugin:
#   1. Receives an AdviceContext.Created webhook
#   2. Queries members to discover the advised clients
#   3. Creates the advisory agreement (snapshots the members)
#   4. Attaches a signing envelope to the agreement and runs the ceremony
#   5. Walks the agreement through draft → pending_signature → signed
#
# v1 signing model: the agreement is created first, then the signing envelope
# attaches to it via signable_type=advisory_agreement + signable_id. Envelope
# documents carry a ceremony_role (input = to-be-signed, output = signed
# result). submit-signing has no body; cancel-signing is the negative path.
#
# Cleanup: expire agreement → delete agreement (cascade-deletes
# envelope) → delete advice context → delete client → delete person.
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
    -d '{"first_name":"Curl","last_name":"S7-AdvisoryAgreement","email":"curl-s7@example.com","language_code":"en"}')

PERSON_ID=$(echo "$PERSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "Created Person ID: ${PERSON_ID}"

echo ""
echo "=== 3. Create Client ==="
CLIENT=$(curl -s -X POST "${API}/api/v1/clients" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"name":"Curl-S7 Client","type":"individual","is_active":true,"currency_id":47}')

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
    -d "{\"advice_policy_id\":${POLICY_ID},\"type\":\"individual\",\"name\":\"Curl-S7 Advice Context\",\"reference_person_id\":${PERSON_ID},\"members\":[{\"person_id\":${PERSON_ID},\"client_id\":${CLIENT_ID},\"power_of_attorney\":false}]}")

CONTEXT_ID=$(echo "$CONTEXT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
CONTEXT_STATUS=$(echo "$CONTEXT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Created Advice Context ID: ${CONTEXT_ID}, status: ${CONTEXT_STATUS}"

echo ""
echo "=== 6. Query Advice Context Members ==="
# Plugin discovers the advised entities by querying the member list.
# In v1 the member resource is client-centric: each member exposes the
# client_id being advised. The signer person is resolved from the client
# (here, the person we created and linked above).
MEMBERS=$(curl -s "${API}/api/v1/advice-contexts/${CONTEXT_ID}/clients" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")

MEMBER_CLIENT_ID=$(echo "$MEMBERS" | python3 -c "import sys,json; print(json.load(sys.stdin)['data'][0]['client_id'])")
MEMBER_COUNT=$(echo "$MEMBERS" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['data']))")
echo "Found ${MEMBER_COUNT} member(s), advised client_id: ${MEMBER_CLIENT_ID}"
# The v1 member resource is client-centric and exposes no person_id; the
# signer person is resolved from the agreement's member_snapshot (step 7).

# ─── Create the agreement (snapshots members), then attach an envelope ──
# v1: the envelope references the agreement (signable), so the agreement
# must exist first — the inverse of the pre-v1 flow.

echo ""
echo "=== 7. Create Advisory Agreement ==="
AGREEMENT=$(curl -s -X POST "${API}/api/v1/advice-contexts/${CONTEXT_ID}/agreements" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"version":"1.0","external_reference":"curl-s7-agreement"}')

AGREEMENT_ID=$(echo "$AGREEMENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
AGREEMENT_STATUS=$(echo "$AGREEMENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Created Agreement ID: ${AGREEMENT_ID}, status: ${AGREEMENT_STATUS}"

# Resolve the signer from the agreement's member_snapshot. The platform freezes
# (client_id, person_id, person_name) per member at creation and rejects
# creation unless every member resolves to a person — the canonical, reliable
# signer-discovery source.
SIGNER_PERSON_ID=$(echo "$AGREEMENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['member_snapshot'][0]['person_id'])")
echo "Signer person_id (from member_snapshot): ${SIGNER_PERSON_ID}"

echo ""
echo "=== 8. Read Advisory Agreement ==="
READ_AGREEMENT=$(curl -s "${API}/api/v1/advice-agreements/${AGREEMENT_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")
echo "Agreement status: $(echo "$READ_AGREEMENT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")"

# ─── Plugin prepares the signing envelope ────────────────────────────

echo ""
echo "=== 9. Upload Source Document ==="
# Plugin generates the advisory agreement document (e.g., from a
# template engine) and uploads it via multipart POST.
TMPFILE=$(mktemp /tmp/curl-s7-agreement-XXXXXX.txt)
echo "Hello World - Curl S7 Advisory Agreement" > "$TMPFILE"

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
echo "=== 10. Create Signing Envelope ==="
# v1: the envelope attaches to a polymorphic signable —
# signable_type=advisory_agreement + signable_id.
ENVELOPE=$(curl -s -X POST "${API}/api/v1/signing-envelopes" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"title\":\"Curl-S7 Agreement Envelope\",\"signable_type\":\"advisory_agreement\",\"signable_id\":${AGREEMENT_ID}}")

ENVELOPE_ID=$(echo "$ENVELOPE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
ENVELOPE_STATUS=$(echo "$ENVELOPE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Created Envelope ID: ${ENVELOPE_ID}, status: ${ENVELOPE_STATUS}"

echo ""
echo "=== 11. Add Document to Envelope ==="
# ceremony_role=input marks the document that will be signed.
ADD_DOC_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/documents" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"document_id\":${DOC_ID},\"ceremony_role\":\"input\"}")
echo "HTTP ${ADD_DOC_STATUS}"
if [ "$ADD_DOC_STATUS" != "201" ]; then echo "ERROR: Expected 201, got ${ADD_DOC_STATUS}"; exit 1; fi

echo ""
echo "=== 12. Add Signer Party ==="
# Uses the signer person resolved from the advice context member (step 6).
# In production, the plugin would iterate all members and add each
# client's person(s) as a signer party.
ADD_PARTY_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/parties" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"person_id\":${SIGNER_PERSON_ID},\"role\":\"signer\"}")
echo "HTTP ${ADD_PARTY_STATUS}"
if [ "$ADD_PARTY_STATUS" != "201" ]; then echo "ERROR: Expected 201, got ${ADD_PARTY_STATUS}"; exit 1; fi

echo ""
echo "=== 13. Send Envelope ==="
SEND_RESPONSE=$(curl -s -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/send" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{}')

SEND_STATUS=$(echo "$SEND_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Envelope status after send: ${SEND_STATUS}"

# ─── Walk the agreement through signing ──────────────────────────────

echo ""
echo "=== 14. Submit Signing (draft → pending_signature) ==="
# v1: submit-signing is a pure state transition and accepts no body fields.
# Idempotency travels via the Idempotency-Key header; a replayed request
# returns the cached response. Safe to retry on network timeout.
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

# ─── Signing ceremony ────────────────────────────────────────────────
# Order matters: upload signed doc → add to envelope → mark party signed.
# Marking the last party auto-completes the envelope, which locks it —
# no documents can be added after that.

echo ""
echo "=== 15. Upload Signed Document ==="
# The signing provider has collected all signatures. The plugin
# downloads the signed copy from the provider and uploads it.
SIGNED_TMPFILE=$(mktemp /tmp/curl-s7-signed-XXXXXX.txt)
echo "Signed Advisory Agreement - Curl S7" > "$SIGNED_TMPFILE"

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
echo "=== 16. Add Signed Document to Envelope ==="
# IMPORTANT: add the signed document BEFORE marking the signer party
# as signed (step 17). Marking the last party auto-completes the
# envelope, which locks it — no more documents can be added after that.
# ceremony_role=output marks the resulting signed document.
ADD_SIGNED_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/signing-envelopes/${ENVELOPE_ID}/documents" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d "{\"document_id\":${SIGNED_DOC_ID},\"ceremony_role\":\"output\"}")
echo "HTTP ${ADD_SIGNED_STATUS}"
if [ "$ADD_SIGNED_STATUS" != "201" ]; then echo "ERROR: Expected 201, got ${ADD_SIGNED_STATUS}"; exit 1; fi

echo ""
echo "=== 17. Mark Signer Party Signed ==="
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
echo "=== 18. Mark Agreement Signed (pending_signature → signed) ==="
# v1: mark-signed optionally accepts signed_document_id (stored in metadata
# for audit) and external_reference. Idempotency via header.
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
echo "=== 19. Read Agreement (final) ==="
FINAL=$(curl -s "${API}/api/v1/advice-agreements/${AGREEMENT_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")
FINAL_STATUS=$(echo "$FINAL" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])")
echo "Agreement status: ${FINAL_STATUS}"

echo ""
echo "=== 20. Expire Agreement ==="
EXPIRE_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/api/v1/advice-agreements/${AGREEMENT_ID}/expire" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{}')
echo "HTTP ${EXPIRE_STATUS}"
if [ "$EXPIRE_STATUS" != "200" ]; then echo "ERROR: Expected 200, got ${EXPIRE_STATUS}"; exit 1; fi

echo ""
echo "=== 21. Delete Agreement ==="
DEL_AGREE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${API}/api/v1/advice-agreements/${AGREEMENT_ID}" \
    -H "Authorization: Bearer ${TOKEN}")
echo "HTTP ${DEL_AGREE}"

echo ""
echo "=== 22. Delete Advice Context ==="
DEL_CTX=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${API}/api/v1/advice-contexts/${CONTEXT_ID}" \
    -H "Authorization: Bearer ${TOKEN}")
echo "HTTP ${DEL_CTX}"

echo ""
echo "=== 23. Delete Client ==="
DEL_CLIENT=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${API}/api/v1/clients/${CLIENT_ID}" \
    -H "Authorization: Bearer ${TOKEN}")
echo "HTTP ${DEL_CLIENT}"

echo ""
echo "=== 24. Delete Person ==="
DEL_PERSON=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${API}/api/v1/persons/${PERSON_ID}" \
    -H "Authorization: Bearer ${TOKEN}")
echo "HTTP ${DEL_PERSON}"

echo ""
echo "Done. Advisory agreement signing journey complete (plugin perspective)."
