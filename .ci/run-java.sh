#!/usr/bin/env bash
#
# Run a Java scenario by package and scenario ID.
#
# Usage: bash .ci/run-java.sh <package> <scenario-id>
#   e.g. bash .ci/run-java.sh manual s1
#        bash .ci/run-java.sh generated s2

set -euo pipefail

PACKAGE="${1:?Usage: run-java.sh <manual|generated> <scenario-id>}"
SCENARIO_ID="${2:?Usage: run-java.sh <manual|generated> <scenario-id>}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Map scenario IDs to test class names
case "$SCENARIO_ID" in
    s1) CLASS="ApiAccessScenario" ;;
    s2) CLASS="ClientLifecycleScenario" ;;
    s3) CLASS="PortfolioSetupScenario" ;;
    *)
        echo "Unknown scenario: ${SCENARIO_ID}"
        exit 1
        ;;
esac

FQN="com.performativ.scenarios.${PACKAGE}.${CLASS}"

echo "Running ${FQN} via maven-failsafe-plugin..."
cd "${REPO_ROOT}/java/scenarios"
exec mvn verify -Dit.test="${FQN}" -Dsurefire.skip=true
