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

node --test --test-timeout=900000 tests/e2e/ticketing.e2e.test.mjs
