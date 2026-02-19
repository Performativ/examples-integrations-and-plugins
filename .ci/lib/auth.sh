#!/usr/bin/env bash
#
# Shared credential loading and token acquisition for curl scenarios.
#
# Source this in scenario scripts:
#   SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
#   source "${SCRIPT_DIR}/../.ci/lib/auth.sh"
#   load_env
#   acquire_token

# Load .env from repo root — exits on failure.
load_env() {
    local script_dir="${1:?Usage: load_env <script_dir>}"
    local env_file="${script_dir}/../.env"

    if [ ! -f "$env_file" ]; then
        echo "Error: .env file not found at $env_file"
        echo "Copy .env.example to .env and fill in your credentials."
        exit 1
    fi

    set -a
    # shellcheck source=/dev/null
    source <(grep -v '^\s*#' "$env_file" | grep -v '^\s*$')
    set +a

    for var in API_BASE_URL TOKEN_BROKER_URL PLUGIN_CLIENT_ID PLUGIN_CLIENT_SECRET; do
        if [ -z "${!var:-}" ]; then
            echo "Error: $var is not set in .env"
            exit 1
        fi
    done

    API="${API_BASE_URL%/}"
    AUDIENCE="${TOKEN_AUDIENCE:-backend-api}"
}

# Acquire an OAuth2 token — sets TOKEN variable. Exits on failure.
acquire_token() {
    echo "=== Acquire access token ==="
    local response
    response=$(curl -s -X POST "${TOKEN_BROKER_URL}/oauth/token" \
        -u "${PLUGIN_CLIENT_ID}:${PLUGIN_CLIENT_SECRET}" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=client_credentials&audience=${AUDIENCE}")

    TOKEN=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])" 2>/dev/null) || {
        echo "Error: Failed to extract access_token from token response" >&2
        echo "$response" >&2
        exit 1
    }
    echo "Token acquired (${#TOKEN} chars)"
}
