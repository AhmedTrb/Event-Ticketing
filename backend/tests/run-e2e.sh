#!/usr/bin/env bash
# Builds + starts the whole stack, bootstraps Keycloak and runs the end-to-end suite.
#   ./tests/run-e2e.sh              # default load (600 registrations)
#   LOAD_REQUESTS=3000 ./tests/run-e2e.sh
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE="docker compose -f infrastructure/docker/docker-compose.yml"

$COMPOSE up -d --build
node infrastructure/docker/keycloak/bootstrap.mjs

echo "waiting for ticketing-api ..."
until curl -fs http://localhost:8090/actuator/health >/dev/null; do sleep 3; done
echo "waiting for notification-service ..."
until curl -s -o /dev/null http://localhost:8092/ws-notification/info; do sleep 3; done

REPORTERS=(--test-reporter=spec --test-reporter-destination=stdout)
# CI sets E2E_JUNIT_FILE (absolute path) to also get a JUnit XML report
if [ -n "${E2E_JUNIT_FILE:-}" ]; then
  REPORTERS+=(--test-reporter=junit "--test-reporter-destination=${E2E_JUNIT_FILE}")
fi
node --test --test-timeout=900000 "${REPORTERS[@]}" tests/e2e/ticketing.e2e.test.mjs
