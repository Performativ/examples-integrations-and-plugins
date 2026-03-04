#!/usr/bin/env bash
#
# Shared helpers for curl scenarios.
#
# Source this in scenario scripts:
#   source "${SCRIPT_DIR}/../.ci/lib/helpers.sh"

# Auto-incrementing step counter — replaces hard-coded echo "=== N. ..."
STEP=0
step() { STEP=$((STEP + 1)); echo ""; echo "=== ${STEP}. $1 ==="; }

# Extract .data.id from JSON response (replaces inline python3 -c pattern)
extract_id() { echo "$1" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])"; }

# Extract arbitrary dot-delimited field from JSON (e.g. extract_field "$JSON" "data.status")
extract_field() {
    local json="$1" path="$2"
    echo "$json" | python3 -c "
import sys, json
d = json.load(sys.stdin)
for key in sys.argv[1].split('.'):
    d = d[key] if not key.isdigit() else d[int(key)]
print(d)
" "$path"
}

# Assert HTTP status code
assert_status() {
    local actual="$1" expected="$2" context="${3:-}"
    if [ "$actual" != "$expected" ]; then
        echo "ERROR: Expected HTTP ${expected}, got ${actual}. ${context}" >&2
        exit 1
    fi
}
