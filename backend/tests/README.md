# Tests

| Layer | What | How to run |
|-------|------|------------|
| Unit / slice (JUnit 5, Mockito, MockMvc) | seat locking, queue + simple registration, worker booking logic, notification persistence/push, RBAC rules, exception mapping, message contracts | `./tests/run-unit.sh` |
| End to end (Node `node:test`, real containers) | auth, catalog + RBAC, lock / conflict / rollback / expiry, async queue confirmation, WebSocket push, failure notifications, overbooking race, burst load | `./tests/run-e2e.sh` |

The e2e suite needs Node 22+ and Docker. It starts the stack from
`infrastructure/docker/docker-compose.yml`, runs
`infrastructure/docker/keycloak/bootstrap.mjs` (idempotent: public client,
roles, `admin@ticketing.local` / `Admin123!`) and then executes
`tests/e2e/ticketing.e2e.test.mjs`.

Tune the load test with `LOAD_REQUESTS` (default 600), `LOAD_USERS` (8) and
`LOAD_CONCURRENCY` (64). Scale throughput with
`docker compose up -d --scale registration-worker=3`.
