#!/usr/bin/env bash
#
# S5: Webhook Delivery — curl example
#
# Demonstrates: acquire token, create a Person (to trigger webhook),
# poll the v1 delivery endpoint to verify the webhook was delivered, then clean up.
#
# Usage:
#   cp .env.example .env   # fill in credentials
#   bash curl/webhook-delivery.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../.ci/lib/auth.sh"
source "${SCRIPT_DIR}/../.ci/lib/helpers.sh"

load_env "$SCRIPT_DIR"

for var in PLUGIN_SLUG PLUGIN_INSTANCE_ID; do
    if [ -z "${!var:-}" ]; then
        echo "Error: $var is not set in .env"
        exit 1
    fi
done

PERSON_ID=""

cleanup() {
    echo ""
    echo "=== Cleanup ==="
    if [ -n "$PERSON_ID" ]; then
        echo "Deleting Person ${PERSON_ID}..."
        curl -s -X DELETE "${API}/api/v1/persons/${PERSON_ID}" \
            -H "Authorization: Bearer ${TOKEN}" -o /dev/null -w "HTTP %{http_code}\n" || true
    fi
}
trap cleanup EXIT

acquire_token

# Record timestamp before creation for ?since= polling parameter
CREATED_BEFORE=$(date -u +%Y-%m-%dT%H:%M:%SZ)

step "Create Person (to trigger webhook)"
PERSON=$(curl -s -X POST "${API}/api/v1/persons" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Idempotency-Key: $(uuidgen)" \
    -d '{"first_name":"Curl","last_name":"S5-WebhookDelivery","email":"curl-s5@example.com","language_code":"en"}')

PERSON_ID=$(extract_id "$PERSON")
echo "Created Person ID: ${PERSON_ID}"

step "Wait for delivery processing"
sleep 5

step "Poll webhook deliveries"
# This uses the delivery-polling API as a CI-friendly verification approach.
# In production, your plugin receives webhooks as real-time HTTPS POSTs.
# See java/webhook-receiver/ for a complete Spring Boot implementation,
# and docs/webhook-setup.md + docs/testing-webhooks-locally.md for the full push-based flow.
# Use ?since= to only scan deliveries created after our person (the poll
# endpoint is a cursor-based forward stream, oldest-first).
DELIVERIES=$(curl -s "${API}/api/v1/plugins/${PLUGIN_SLUG}/instances/${PLUGIN_INSTANCE_ID}/webhook-deliveries/poll?limit=50&since=${CREATED_BEFORE}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json")

echo "Poll response (first 500 chars): ${DELIVERIES:0:500}"

step "Find matching delivery"
MATCH=$(echo "$DELIVERIES" | python3 -c "
import sys, json
data = json.load(sys.stdin)
deliveries = data if isinstance(data, list) else data.get('data', [])
person_id = int(sys.argv[1])
for d in deliveries:
    payload = d.get('payload', {})
    if isinstance(payload, str):
        payload = json.loads(payload)
    if payload.get('entity') == 'Person' and payload.get('event') == 'Created' and payload.get('entity_id') == person_id:
        print('FOUND')
        sys.exit(0)
print('NOT_FOUND')
" "$PERSON_ID")

if [ "$MATCH" = "FOUND" ]; then
    echo "Webhook delivery verified: Person.Created for entity_id=${PERSON_ID}"
else
    echo "FAIL: No Person.Created delivery found for entity_id=${PERSON_ID}"
    exit 1
fi

step "Delete (handled by cleanup trap)"
echo "Done. Person created, webhook delivery verified, cleanup follows."
