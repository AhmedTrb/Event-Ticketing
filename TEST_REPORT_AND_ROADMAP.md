# Event Ticketing Platform: Test Report & Roadmap

*Date: 2026-11-16 · Scope: `backend/` (ticketing-api, registration-worker, notification-service, shared) · Environment: single Windows 11 PC, Docker Desktop VM (8 vCPU / ~3.8 GB RAM), Node 22, Java 17 in containers.*

---

## 1. Executive summary

| Question | Answer |
|---|---|
| Does the platform work as the README describes? | **Not at the start. It does now.** The async path (lock → RabbitMQ → worker → Postgres → Redis pub/sub → WebSocket) was broken end-to-end in five independent places (see §3). All are fixed and covered by tests. |
| Unit tests | **60 tests, all green** (shared 4 · ticketing-api 41 · notification-service 5 · registration-worker 10). |
| End-to-end tests (real containers) | **19 tests.** All 18 functional tests pass on the final code. The 1 failure in the last run was a *performance threshold* on a cold API (§4.3), not a functional failure. |
| Does it absorb a registration burst? | **Yes.** In every burst run (600, 1,500) all requests were accepted with HTTP 202, with zero errors and zero lost bookings. Warm API: ~160 req/s, p95 < 1 s. Cold API right after a restart: much slower (§4.3). |
| Is it production ready? | **No.** It is a solid, tested foundation. §5 lists the gaps (payment, auth on WebSockets, observability, secrets, migrations). |

**What I could not verify:** code coverage was not measured (no JaCoCo configured, so the 80% target is unproven). Performance was measured on one shared laptop-class machine, with the load generator competing for the same CPU. Treat the numbers as relative, not absolute. No cloud testing was done.

---

## 2. What was built / changed

**Infrastructure (reusing your existing `docker` compose project, container names and volumes)**
- `backend/infrastructure/docker/docker-compose.yml`: your Keycloak/Redis/RabbitMQ kept as-is (volumes preserved), plus PostgreSQL and the 3 app services, healthchecks, memory limits, worker concurrency (4–8 consumers, prefetch 50).
- `backend/Dockerfile`: one parametrised multi-stage recipe for all modules: Maven build → `jlink`-trimmed JRE on Alpine, Spring Boot layered jar, non-root user. **Images: 225–278 MB (down from 372–425 MB).**
- `infrastructure/docker/keycloak/bootstrap.mjs`: idempotent; never deletes. It made your existing `ticketing-api-client` *public* (the API logs users in without a secret, which a confidential client cannot do), ensured roles `USER`/`ADMIN`, and created `admin@ticketing.local` / `Admin123!`.
- `infrastructure/docker/rabbitmq/10-guest.conf`: lets the `guest` user connect from other containers (dev only).

**Tests**
- 60 JUnit/Mockito/MockMvc tests across all four modules.
- `backend/tests/e2e/`: Node `node:test` suite against the real stack (19 tests incl. WebSocket/STOMP, Redis, Postgres and RabbitMQ assertions).
- `backend/tests/run-unit.sh`, `run-e2e.sh`, `README.md`.

---

## 3. Defects found and fixed

| # | Defect | Impact | Fix |
|---|---|---|---|
| 1 | Worker published to Redis channel `notification`; notification-service listened on `notifications` | **No push notification was ever delivered** | Channel aligned (single constant) |
| 2 | Worker's Redis template used a String serializer but was given a `Map` | `ClassCastException` on every notification | Publish a JSON string |
| 3 | Notification listener used the default JDK deserializer on a JSON string | Messages unreadable even if 1 and 2 were fixed | `StringRedisSerializer` on the adapter |
| 4 | Queue endpoint never locked seats, but the worker required a lock | **Every queued booking failed with "Locks expired"**; README promised a 409 on conflict | API locks first (409 on conflict), publishes after, unlocks if the broker is down |
| 5 | No exception handler | Seat conflict, not-found, validation → HTTP 500 | `GlobalExceptionHandler`: 409 / 404 / 400 / 410 |
| 6 | Worker: no lock-ownership check, no idempotency, no DB uniqueness, confirmed seats never marked | Overbooking, duplicate tickets on redelivery | Owner check, `existsByBookingId`, unique `(event_id, seat_id)`, permanent "booked" marker |
| 7 | Partial-conflict rollback deleted *every* requested seat | A failed request could **free another user's lock** | Rolls back only seats this request locked |
| 8 | `messaging.rabbitmq.*` only in the dev profile; worker didn't declare the queue | Prod profile couldn't start; worker crashed if started before the API | Keys in base config; worker declares the same durable queue |
| 9 | `AuthService.register` authenticated the admin against the wrong realm; duplicate email → 500 | Registration impossible; wrong status codes | `master` realm; duplicate → 400 |
| 10 | `/api/auth/me` and `/sync` were public via `/api/auth/**` | Anonymous call → `ClassCastException` (masked as 401) | Explicit `authenticated()` rule + regression test |
| 11 | Notifications carried no `bookingId`/`eventId` | Client couldn't match a push to the tracking ID returned by the 202 | Fields added end-to-end |
| 12 | Keycloak token issuer differed between host and container; `guest` blocked off-loopback | Valid tokens rejected by the API; app containers couldn't reach RabbitMQ | `KC_HOSTNAME` + backchannel-dynamic; RabbitMQ config |
| 13 | `ticketing-api` had no test dependencies; existing `contextLoads` tests needed live infra | Module didn't compile its tests | Test deps added; stubs replaced by real tests |

---

## 4. Test results

### 4.1 Unit tests (JUnit 5, Mockito, MockMvc)

| Module | Tests | Covers |
|---|---:|---|
| shared | 4 | Command JSON round-trip, envelope, error codes, ID generator |
| ticketing-api | 41 | Seat locking (TTL, atomic lock, conflict, rollback), simple + queue registration, event/venue/movie/performer services, Keycloak role mapping, error→status mapping, HTTP security rules (401/403/202/409/400) |
| notification-service | 5 | Persist + push, validation, malformed input, Redis listener |
| registration-worker | 10 | Confirm path, expired lock, foreign lock, duplicate delivery, DB conflict, DB failure, notification publisher, consumer |
| **Total** | **60** | **All green** |

### 4.2 End-to-end functional results (final code)

| Area | Checks | Result |
|---|---|---|
| Authentication | public health; register→login→sync→profile; duplicate (400) & bad credentials (400); anonymous = 401 | ✅ |
| Catalog & RBAC | admin creates venue/movie/performer/event; public reads; 404/400/409; anonymous 401; user 403 | ✅ |
| **Seat selection / locking / unlocking** | lock with ~600 s TTL and `LOCKED` broadcast; 409 on taken seat and owner unchanged; partial conflict rolls back and broadcasts `AVAILABLE`; lock expiry frees the seat; payload validation | ✅ |
| **Async confirmation + push** | 202 + tracking ID; tickets `CONFIRMED`; lock becomes permanent (TTL −1); `BOOKING_CONFIRMED` pushed over WebSocket with the `bookingId`; notification persisted; confirmed seat can never be re-booked (both flows → 409) | ✅ |
| **Failure notifications** | expired/missing lock → `BOOKING_FAILED`, no ticket; seat locked by someone else → rejected | ✅ |
| Reliability | RabbitMQ redelivery handled idempotently | ✅ |
| Overbooking | 25 users race for one seat → exactly 1 accepted, 24 conflicts, 1 ticket | ✅ |
| Burst load, worker down then drained | 100% accepted (202), backlog held in RabbitMQ, drained with zero loss, no duplicate seats, all notifications persisted | ✅ when warm (§4.3) |

### 4.3 Load results (single PC, load generator on the same machine)

| Run | Burst | Throughput | p50 / p95 / p99 | Drain by worker | Outcome |
|---|---|---:|---|---|---|
| Original images, warm API | 1,500 | 159 req/s | 359 / **896** / 1,152 ms | 100 s (≈15/s) | ✅ all 1,500 confirmed |
| After restart | 1,500 | 70 req/s | 700 / 2,467 / 3,184 ms | 165 s (≈9/s) | ✅ |
| Final code, **cold** API | 1,500 | 42 req/s | 941 / **5,460** / 6,845 ms | n/a (stopped at latency assertion) | ❌ p95 > 3 s threshold |
| Idle machine, **cold** | 600 | 31 req/s | 645 / **11,851** / 12,132 ms | n/a | ❌ p95 > 3 s threshold |
| Idle machine, **warm** | 600 | 163 req/s | 325 / **815** / 1,000 ms | 33 s (≈18/s) | ✅ |

**Reading these honestly**
- No request was ever rejected or lost. Every burst returned 100% HTTP 202.
- The same code on the same idle machine gave 31 req/s cold and 163 req/s warm. So **the first burst after a restart is the weak spot**, which is exactly the flash-sale scenario. In the cold runs the slowdown hit only the tail (p50 stayed < 1 s).
- I did **not** establish the cause. My hypothesis is JVM heap growth/JIT warm-up on a small container. I applied a larger initial heap (`InitialRAMPercentage=60`) in the compose file but **did not benchmark it**; it is marked as a hypothesis in the file.
- Worker drain is ~15–18 bookings/s with 4 consumers on this machine. Time-to-confirm for a 10,000-seat on-sale would be ~10 min at that rate unless workers are scaled or inserts batched.
- Resource use at rest: API 300–420 MB / 640, worker 170–270 / 512, notification 220–250 / 512, Keycloak ~1 GB.
- The suite never produced a single uninterrupted 19/19 on the final code. The last full run was 18/19 (the cold latency assertion). The burst test passes when the API is warm.

---

## 5. Known gaps (not fixed, so you know what "working" does and doesn't mean)

1. **No payment step.** `registerSimple` returns a placeholder `bookingId`; nothing is persisted, and the worker confirms tickets without payment. "Lock → pay → confirm" is not modelled. There is no `Booking` entity.
2. **No unlock endpoint.** Seats are released only by rollback, TTL expiry, or worker failure. A user who abandons checkout holds seats for up to 10 minutes.
3. **No validation that the event exists or the seat is valid.** Any string is accepted as a seat for any UUID; `maxCapacity` is never enforced.
4. **WebSocket topics are unauthenticated.** Anyone who knows a user ID can subscribe to `/topic/user/{id}/notifications`.
5. **Email, PDF and QR delivery are documented but not implemented.** The README says they are; the mail dependency is unused.
6. **Notification delivery is not durable.** Redis pub/sub is fire-and-forget, and persistence happens in the subscriber. If notification-service is down, those notifications are lost.
7. **Scaling notification-service beyond one replica would write each notification once per replica** (every instance receives the pub/sub message and saves it).
8. **No dead-letter queue or retry/backoff** for poison messages (they are logged and dropped).
9. **`ddl-auto: update`** instead of migrations; the `prod` profile uses `validate` with no schema to validate against.
10. **Dev defaults everywhere:** `guest/guest`, `postgres/1234`, Keycloak `start-dev` with `admin/admin`, CORS for localhost only.
11. **README drift:** it says the worker "releases the lock"; the implementation converts it to a permanent booked marker. Setup steps predate the new compose file.
12. **Code coverage unmeasured**; the CI workflow still builds with `-DskipTests`.

---

## 6. Recommended next steps (in order)

1. **Commit this work on a branch** (≈22 modified files plus new ones), review the diff, and open a PR. Nothing is committed yet.
2. **Make CI run the tests.** Update `backend-ci.yml`: `mvn verify` (+ JaCoCo, fail under 80%), then `docker compose up` and `tests/e2e`. Publish JUnit XML (Maven surefire; the Node suite already emits JUnit via `--test-reporter=junit`) to the job summary. **This is the better "visualisation of test results"**: a per-PR pass/fail table, a coverage badge, and k6/Grafana charts for load, rather than building a custom dashboard.
3. **Close the functional gaps tied to the README:** `Booking` entity + payment stub with lock-extension, `DELETE`/release endpoint with ownership check (Lua compare-and-delete), event/seat validation, STOMP auth (JWT on CONNECT).
4. **Benchmark the cold-start problem** (the one real performance risk found): test `InitialRAMPercentage`, readiness warm-up, and pre-scaling.
5. **Observability:** Micrometer + Prometheus, structured JSON logs, a trace/correlation ID carried in RabbitMQ headers, and dashboards for queue depth, consumer lag, lock-conflict rate, accept latency and time-to-confirm.
6. **Resilience:** DLQ + retry with backoff, move notification events to RabbitMQ or Redis Streams (durable), Flyway migrations, batch inserts in the worker.

---

## 7. Over-engineered or missed?

**Reasonable for the goal (a portfolio / learning project about burst absorption):** Redis locks + RabbitMQ queue + idempotent worker + DB uniqueness is the right shape. The DB unique constraint as the final safety net is exactly what you want, because Redis locks can be lost on failover.

**Arguably heavy for a real small business:** Keycloak (Cognito or Spring-managed auth would do), three services, and separate Redis/RabbitMQ. A modular monolith with Postgres constraints would handle thousands of bookings per minute. Keep the split only because absorbing a flash-sale spike is the project's stated purpose.

**Missed technical details**
- The queue protects *accept latency*, not *time-to-confirm*; that is bounded by worker throughput (§4.3).
- The README says "controlled access via queue", but this is an async command queue, not a **virtual waiting room**. Real flash-sales add a queue position/token layer in front.
- The in-memory STOMP broker and Redis pub/sub do not scale out cleanly (§5 items 6–7).
- Seat locks live in a single Redis primary. After a failover they can vanish; the DB constraint covers correctness, but the user sees a failed booking.
- `sub` from the JWT is used as the user ID in tickets and notifications; it is not linked to the local `users` table.

---

## 8. AWS deployment: suggested structure

Start simple (ECS Fargate), not Kubernetes. Three small services do not justify EKS.

```
Route 53 ─▶ CloudFront + WAF (rate limit, bot control) ─▶ ALB (HTTPS, WebSocket-capable)
                                                           ├─▶ ECS Fargate: ticketing-api     (autoscale: CPU + request count)
                                                           └─▶ ECS Fargate: notification-service (WebSocket; sticky not required for STOMP over a shared broker)
                                       ECS Fargate: registration-worker (no ALB; autoscale on queue depth)
Private subnets ─▶ RDS PostgreSQL (Multi-AZ in prod) · ElastiCache Redis (replica + failover)
                   Amazon MQ for RabbitMQ (managed; keeps the code unchanged) · Keycloak on ECS + RDS (or Cognito)
Cross-cutting: ECR · Secrets Manager · CloudWatch + AWS Distro for OpenTelemetry (or Managed Prometheus/Grafana) · S3 (ticket PDFs) · SES (email)
IaC: Terraform or CDK · Environments: dev / staging / prod (separate accounts or at least separate VPCs)
```

Key points:
- **Autoscaling the worker on queue depth** (Amazon MQ metrics → CloudWatch → target tracking) is the heart of "absorb the load". **Pre-scale API and workers on a schedule before known on-sale times**, given the cold-start finding.
- Private subnets for data stores; only the ALB is public. Budget carefully for NAT Gateway, Amazon MQ and ElastiCache. These are the usual surprise costs; check them in the AWS Pricing Calculator.
- Deploy via GitHub Actions with **OIDC to AWS** (no long-lived keys): build → test → push to ECR → `ecs deploy`, with a smoke test and automatic rollback.
- Replace the dev config with a real `prod` profile fed by Secrets Manager and environment variables.

**Testing on real hardware / cloud:** stand up a small *staging* environment, run **k6** from separate machines (not the system under test) for smoke → load → spike → soak, and add failure injection (kill a worker, fail over Redis, restart RabbitMQ). Judge by p95/p99, error rate, queue depth, time-to-confirm, and cost per 1,000 bookings.

---

## 9. Future feature ideas

- Payment integration (Stripe) with webhook confirmation and lock extension
- Virtual waiting room with queue position and a signed admission token
- PDF + QR tickets and email via SES; QR scan/validation endpoint for entry
- Ticket transfer / cancellation / refund with seat release
- Interactive seat-map UI (SVG for stadiums, procedural for cinemas) wired to the seat-status WebSocket
- Per-user purchase limits, anti-bot rules, admin analytics dashboard
- Multi-region read replicas for event discovery (the CQRS split the architecture doc describes)

---

## 10. How to reproduce

```bash
cd backend
./tests/run-unit.sh     # 60 JUnit tests inside a Maven container (no local JDK needed)
./tests/run-e2e.sh      # builds + starts the stack, bootstraps Keycloak, runs the 19 e2e tests
# knobs: LOAD_REQUESTS (default 600), LOAD_USERS (8), LOAD_CONCURRENCY (64)
# more throughput: docker compose -f infrastructure/docker/docker-compose.yml up -d --scale registration-worker=3
```

Demo credentials (local only): `admin@ticketing.local` / `Admin123!`. Services: API `:8090` (Swagger `/swagger-ui.html`), notifications `:8092`, Keycloak `:9090`, RabbitMQ UI `:15672`.
