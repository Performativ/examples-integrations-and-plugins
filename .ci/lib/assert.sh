#!/usr/bin/env bash
#
# Shared bash assertion helpers for curl CI scripts.
#
# Source this file in your scripts:
#   source "$(dirname "$0")/../.ci/lib/assert.sh"

# Assert HTTP status code matches expected.
# Usage: assert_status <actual> <expected> <context>
assert_status() {
    local actual="$1" expected="$2" context="${3:-}"
    if [ "$actual" != "$expected" ]; then
        echo "FAIL: Expected HTTP ${expected}, got ${actual}. ${context}" >&2
        return 1
    fi
    echo "OK: HTTP ${actual} ${context}"
}

# Assert that a JSON field exists and is non-null using python3.
# Usage: assert_json_field <json> <field_path> <context>
# Field path uses Python dict notation, e.g. "['data']['id']"
assert_json_field() {
    local json="$1" field_path="$2" context="${3:-}"
    local value
    value=$(echo "$json" | python3 -c "
import sys, json
data = json.load(sys.stdin)
try:
    val = data${field_path}
    if val is None:
        print('__null__')
    else:
        print(val)
except (KeyError, IndexError, TypeError):
    print('__missing__')
")
    if [ "$value" = "__missing__" ] || [ "$value" = "__null__" ]; then
        echo "FAIL: Field ${field_path} is missing or null. ${context}" >&2
        return 1
    fi
    echo "OK: ${field_path} = ${value} ${context}"
}

# Assert that a value is a positive integer.
# Usage: assert_positive_int <value> <context>
assert_positive_int() {
    local value="$1" context="${2:-}"
    if ! [[ "$value" =~ ^[1-9][0-9]*$ ]]; then
        echo "FAIL: Expected positive integer, got '${value}'. ${context}" >&2
        return 1
    fi
    echo "OK: ${value} is a positive integer. ${context}"
}
