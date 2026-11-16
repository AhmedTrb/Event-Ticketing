// End-to-end suite for the running docker stack.
//   node --test --test-timeout=600000 tests/e2e
//
// Covers the documented flows: auth, catalog + RBAC, seat selection/locking,
// conflicts, rollback, lock expiry, async queue registration, worker
// confirmation, WebSocket push notifications, failure notifications,
// overbooking races and a burst load absorbed by the queue.
import { describe, it, before, after } from 'node:test';
import assert from 'node:assert/strict';
import {
  API_WS, NOTIFICATION_WS, StompClient, api, compose, createUser, jwtSubject, login, percentile, pool,
  psql, queueStats, rabbit, redis, sleep, waitUntil,
} from './helpers.mjs';

const RUN = Date.now().toString(36);
const lockKey = (eventId, seat) => `seat_lock:${eventId}:${seat}`;
const register = (kind, user, eventId, seats) =>
  api('POST', `/api/register/${kind}`, { token: user.token, body: { eventId, seatIdentifiers: seats } });
const ticketCount = (where) => Number(psql(`select count(*) from tickets where ${where}`));

describe('ticketing platform end to end', () => {
  let admin;
  let eventId;
  const clients = [];

  before(async () => {
    const health = await api('GET', '/actuator/health');
    assert.equal(health.status, 200, 'ticketing-api must be up (docker compose up -d)');
    admin = await login('admin@ticketing.local', 'Admin123!');

    // Warm-up: push one booking through the whole pipeline (API -> RabbitMQ ->
    // worker -> Postgres) so the first real test does not pay JVM cold-start costs.
    const warm = await createUser('warmup');
    const warmEvent = crypto.randomUUID();
    const res = await register('queue', warm, warmEvent, ['WARM-1']);
    assert.equal(res.status, 202, res.text);
    await waitUntil('warm-up booking processed',
      () => ticketCount(`booking_id='${res.json.bookingId}'`) === 1, { timeoutMs: 120_000, intervalMs: 1000 });
  });

  after(() => clients.forEach((c) => c.close()));

  // ------------------------------------------------------------------ auth
  describe('authentication', () => {
    it('public health endpoint is reachable without a token', async () => {
      const res = await api('GET', '/api/auth/public/health');
      assert.equal(res.status, 200);
    });

    it('registers, logs in, syncs and returns the profile', async () => {
      const user = await createUser('profile');
      const sync = await api('POST', '/api/auth/sync', { token: user.token });
      assert.equal(sync.status, 200);
      assert.equal(sync.json.email, user.email);
      assert.equal(sync.json.role, 'USER');
      const me = await api('GET', '/api/auth/me', { token: user.token });
      assert.equal(me.status, 200);
      assert.equal(me.json.email, user.email);
    });

    it('rejects duplicate registration and bad credentials with 400', async () => {
      const user = await createUser('dup');
      const dup = await api('POST', '/api/auth/register', {
        body: { email: user.email, password: 'Passw0rd!', firstName: 'a', lastName: 'b' },
      });
      assert.equal(dup.status, 400);
      const bad = await api('POST', '/api/auth/login', { body: { email: user.email, password: 'wrong-password' } });
      assert.equal(bad.status, 400);
    });

    it('protects authenticated endpoints (401 without a token)', async () => {
      assert.equal((await api('GET', '/api/auth/me')).status, 401);
      const res = await api('POST', '/api/register/queue', {
        body: { eventId: crypto.randomUUID(), seatIdentifiers: ['A1'] },
      });
      assert.equal(res.status, 401);
    });
  });

  // ------------------------------------------------------- catalog + RBAC
  describe('catalog and role based access', () => {
    it('admin creates venue, movie, performer and event; reads are public', async () => {
      const venue = await api('POST', '/api/venues', {
        token: admin,
        body: { name: `Cinema ${RUN}`, location: 'Tunis', type: 'MOVIE_THEATRE', capacity: 50, rowCount: 5, colCount: 10 },
      });
      assert.equal(venue.status, 201, venue.text);
      const movie = await api('POST', '/api/movies', {
        token: admin,
        body: { title: `Movie ${RUN}`, description: 'd', director: 'x', releaseDate: '2025-01-01', durationMinutes: 120 },
      });
      assert.equal(movie.status, 201, movie.text);
      const performer = await api('POST', '/api/performers', {
        token: admin, body: { name: `Band ${RUN}`, description: 'rock' },
      });
      assert.equal(performer.status, 201, performer.text);

      const start = new Date(Date.now() + 7 * 864e5).toISOString();
      const end = new Date(Date.now() + 7 * 864e5 + 2 * 36e5).toISOString();
      const event = await api('POST', '/api/events', {
        token: admin,
        body: {
          name: `Event ${RUN}`, description: 'e2e', startDate: start, endDate: end, eventType: 'MOVIE',
          venueId: venue.json.id, movieId: movie.json.id,
        },
      });
      assert.equal(event.status, 201, event.text);
      assert.equal(event.json.maxCapacity, 50, 'capacity defaults to venue capacity');
      assert.equal(event.json.status, 'UPCOMING');
      eventId = event.json.id;

      assert.equal((await api('GET', `/api/events/${eventId}`)).json.name, `Event ${RUN}`);
      const list = await api('GET', '/api/events?size=100');
      assert.equal(list.status, 200);
      assert.ok(list.json.content.some((e) => e.id === eventId));
      assert.ok((await api('GET', '/api/venues')).json.some((v) => v.id === venue.json.id));
      assert.ok((await api('GET', '/api/movies')).json.some((m) => m.id === movie.json.id));
      assert.ok((await api('GET', '/api/performers')).json.some((p) => p.id === performer.json.id));
    });

    it('rejects invalid input and unknown ids with the right status codes', async () => {
      const unknown = await api('GET', `/api/events/${crypto.randomUUID()}`);
      assert.equal(unknown.status, 404);
      const invalid = await api('POST', '/api/events', { token: admin, body: { name: '' } });
      assert.equal(invalid.status, 400);
      const dupVenue = await api('POST', '/api/venues', {
        token: admin, body: { name: `Cinema ${RUN}`, location: 'x', type: 'OTHER', capacity: 1 },
      });
      assert.equal(dupVenue.status, 409);
    });

    it('enforces ADMIN-only writes: 401 anonymous, 403 for normal users', async () => {
      const user = await createUser('rbac');
      const body = { name: `Nope ${RUN}`, location: 'x', type: 'OTHER', capacity: 1 };
      assert.equal((await api('POST', '/api/venues', { body })).status, 401);
      assert.equal((await api('POST', '/api/venues', { token: user.token, body })).status, 403);
    });
  });

  // -------------------------------------------- seat selection and locking
  describe('seat selection, locking and unlocking (simple flow)', () => {
    let alice;
    let bob;
    let seatFeed;

    before(async () => {
      [alice, bob] = await Promise.all([createUser('alice'), createUser('bob')]);
      seatFeed = await StompClient.connect(API_WS);
      clients.push(seatFeed);
      await seatFeed.subscribe(`/topic/events/${eventId}/seats`);
    });

    it('locks the selected seats for the user with a 10 minute TTL and broadcasts LOCKED', async () => {
      const res = await register('simple', alice, eventId, ['S1', 'S2']);
      assert.equal(res.status, 200, res.text);
      assert.ok(res.json.bookingId);

      assert.equal(redis('GET', lockKey(eventId, 'S1')), `locked_by_${alice.userId}`);
      assert.equal(redis('GET', lockKey(eventId, 'S2')), `locked_by_${alice.userId}`);
      const ttl = Number(redis('TTL', lockKey(eventId, 'S1')));
      assert.ok(ttl > 500 && ttl <= 600, `ttl should be ~600s, was ${ttl}`);

      const evt = await seatFeed.waitFor(`/topic/events/${eventId}/seats`, (b) => b.seatId === 'S1' && b.status === 'LOCKED');
      assert.equal(evt.userId, alice.userId);
    });

    it('returns 409 Conflict when a seat is already locked and keeps the original owner', async () => {
      const res = await register('simple', bob, eventId, ['S1']);
      assert.equal(res.status, 409);
      assert.equal(res.json.error, 'SEAT_ALREADY_BOOKED');
      assert.equal(redis('GET', lockKey(eventId, 'S1')), `locked_by_${alice.userId}`);
    });

    it('is all-or-nothing: a partial conflict releases only the seats it grabbed and broadcasts AVAILABLE', async () => {
      const res = await register('simple', bob, eventId, ['S3', 'S1']);
      assert.equal(res.status, 409);
      assert.equal(redis('EXISTS', lockKey(eventId, 'S3')), '0', 'S3 must be rolled back');
      assert.equal(redis('GET', lockKey(eventId, 'S1')), `locked_by_${alice.userId}`, "alice's lock untouched");
      await seatFeed.waitFor(`/topic/events/${eventId}/seats`, (b) => b.seatId === 'S3' && b.status === 'AVAILABLE');
    });

    it('frees a seat for others once its lock expires', async () => {
      redis('EXPIRE', lockKey(eventId, 'S2'), '1');
      await sleep(2000);
      assert.equal(redis('EXISTS', lockKey(eventId, 'S2')), '0');
      const res = await register('simple', bob, eventId, ['S2']);
      assert.equal(res.status, 200);
      assert.equal(redis('GET', lockKey(eventId, 'S2')), `locked_by_${bob.userId}`);
    });

    it('validates the request body', async () => {
      assert.equal((await register('simple', alice, eventId, [])).status, 400);
      const noEvent = await api('POST', '/api/register/simple', { token: alice.token, body: { seatIdentifiers: ['A'] } });
      assert.equal(noEvent.status, 400);
    });
  });

  // ---------------------------- async queue registration + confirmation
  describe('queue registration, worker confirmation and push notification', () => {
    it('accepts instantly (202), persists tickets, makes the booking permanent and pushes BOOKING_CONFIRMED', async () => {
      const carol = await createUser('carol');
      const feed = await StompClient.connect(NOTIFICATION_WS);
      clients.push(feed);
      const topic = `/topic/user/${carol.userId}/notifications`;
      await feed.subscribe(topic);

      const res = await register('queue', carol, eventId, ['Q1', 'Q2']);
      assert.equal(res.status, 202, res.text);
      const bookingId = res.json.bookingId;
      assert.ok(bookingId, 'tracking id returned');

      // the seats are locked synchronously, before any worker involvement
      assert.ok(['locked_by_', 'booked_by_'].some((p) => redis('GET', lockKey(eventId, 'Q1')).startsWith(p)));

      const note = await feed.waitFor(topic, (b) => b.bookingId === bookingId);
      assert.equal(note.type, 'BOOKING_CONFIRMED');
      assert.equal(note.userId, carol.userId);
      assert.equal(note.read, false);

      assert.equal(ticketCount(`booking_id='${bookingId}' and status='CONFIRMED'`), 2);
      assert.equal(redis('GET', lockKey(eventId, 'Q1')), `booked_by_${carol.userId}`);
      assert.equal(redis('TTL', lockKey(eventId, 'Q1')), '-1', 'confirmed seats must never expire');
      assert.equal(
        Number(psql(`select count(*) from notifications where user_id='${carol.userId}' and booking_id='${bookingId}' and type='BOOKING_CONFIRMED'`)),
        1,
        'notification persisted',
      );
    });

    it('a confirmed seat can never be booked again (both flows answer 409)', async () => {
      const dave = await createUser('dave');
      assert.equal((await register('queue', dave, eventId, ['Q1'])).status, 409);
      assert.equal((await register('simple', dave, eventId, ['Q2'])).status, 409);
    });

    it('pushes BOOKING_FAILED when the lock is missing/expired and creates no ticket', async () => {
      const erin = await createUser('erin');
      const feed = await StompClient.connect(NOTIFICATION_WS);
      clients.push(feed);
      const topic = `/topic/user/${erin.userId}/notifications`;
      await feed.subscribe(topic);

      const bookingId = crypto.randomUUID();
      const publish = await publishCommand({ bookingId, userId: erin.userId, eventId, seats: ['NOLOCK-1'] });
      assert.equal(publish.routed, true);

      const note = await feed.waitFor(topic, (b) => b.bookingId === bookingId);
      assert.equal(note.type, 'BOOKING_FAILED');
      assert.match(note.message, /Locks expired/);
      assert.equal(ticketCount(`booking_id='${bookingId}'`), 0);
    });

    it("refuses to confirm seats locked by someone else (lock ownership is verified)", async () => {
      const owner = await createUser('owner');
      const thief = await createUser('thief');
      assert.equal((await register('simple', owner, eventId, ['OWN-1'])).status, 200);

      const feed = await StompClient.connect(NOTIFICATION_WS);
      clients.push(feed);
      const topic = `/topic/user/${thief.userId}/notifications`;
      await feed.subscribe(topic);

      const bookingId = crypto.randomUUID();
      await publishCommand({ bookingId, userId: thief.userId, eventId, seats: ['OWN-1'] });
      const note = await feed.waitFor(topic, (b) => b.bookingId === bookingId);
      assert.equal(note.type, 'BOOKING_FAILED');
      assert.equal(ticketCount(`booking_id='${bookingId}'`), 0);
      assert.equal(redis('GET', lockKey(eventId, 'OWN-1')), `locked_by_${owner.userId}`);
    });

    it('is idempotent when RabbitMQ redelivers the same command', async () => {
      const frank = await createUser('frank');
      const res = await register('queue', frank, eventId, ['IDEM-1']);
      const bookingId = res.json.bookingId;
      await waitUntil('first delivery processed', () => ticketCount(`booking_id='${bookingId}'`) === 1);
      await publishCommand({ bookingId, userId: frank.userId, eventId, seats: ['IDEM-1'] });
      await sleep(2000);
      assert.equal(ticketCount(`booking_id='${bookingId}'`), 1);
    });
  });

  // --------------------------------------------------- overbooking race
  describe('concurrency', () => {
    it('never overbooks: 25 users race for one seat, exactly one wins', async () => {
      const users = await pool(Array.from({ length: 25 }, (_, i) => () => createUser(`race${i}`)), 8);
      const results = await Promise.all(users.map((u) => register('queue', u, eventId, ['RACE-1'])));
      const accepted = results.filter((r) => r.status === 202);
      const conflicts = results.filter((r) => r.status === 409);
      assert.equal(accepted.length, 1, `statuses: ${results.map((r) => r.status)}`);
      assert.equal(conflicts.length, 24);

      await waitUntil('winner confirmed', () => ticketCount(`event_id='${eventId}' and seat_id='RACE-1'`) === 1);
      await sleep(1500);
      assert.equal(ticketCount(`event_id='${eventId}' and seat_id='RACE-1'`), 1);
    });
  });

  // ------------------------------------------------------------ load
  describe('load absorption', () => {
    const TOTAL = Number(process.env.LOAD_REQUESTS ?? 600);
    const LOAD_USERS = Number(process.env.LOAD_USERS ?? 8);
    const CONCURRENCY = Number(process.env.LOAD_CONCURRENCY ?? 64);
    const P95_LIMIT_MS = Number(process.env.LOAD_P95_MS ?? 3000);

    it(`accepts a burst of ${TOTAL} registrations while the worker is down, then drains the queue with zero loss`, async () => {
      const loadEvent = crypto.randomUUID();
      const users = await pool(Array.from({ length: LOAD_USERS }, (_, i) => () => createUser(`load${i}`)), 8);
      const userIds = users.map((u) => `'${u.userId}'`).join(',');

      // Worker down => nothing can be processed: the API must still answer fast.
      compose('stop', 'registration-worker');
      try {
        const tasks = Array.from({ length: TOTAL }, (_, i) => () =>
          register('queue', users[i % users.length], loadEvent, [`L-${i}`]));
        const started = performance.now();
        const results = await pool(tasks, CONCURRENCY);
        const burstMs = performance.now() - started;

        const statuses = results.reduce((acc, r) => ({ ...acc, [r.status]: (acc[r.status] ?? 0) + 1 }), {});
        const latencies = results.map((r) => r.ms);
        console.log(`  burst: ${TOTAL} requests in ${(burstMs / 1000).toFixed(1)}s (${Math.round(TOTAL / (burstMs / 1000))} req/s), `
          + `p50=${percentile(latencies, 50).toFixed(0)}ms p95=${percentile(latencies, 95).toFixed(0)}ms `
          + `p99=${percentile(latencies, 99).toFixed(0)}ms statuses=${JSON.stringify(statuses)}`);
        assert.deepEqual(statuses, { 202: TOTAL }, 'every request must be accepted');
        assert.ok(percentile(latencies, 95) < P95_LIMIT_MS,
          `p95 acceptance latency must stay under ${P95_LIMIT_MS}ms (set LOAD_P95_MS to tune for slow/shared machines)`);

        const stats = await waitUntil('queue depth visible', async () => {
          const s = await queueStats();
          return s.messages_ready >= TOTAL ? s : null;
        }, { timeoutMs: 30_000 });
        console.log(`  queue backlog while worker down: ${stats.messages_ready} messages`);
        assert.equal(ticketCount(`event_id='${loadEvent}'`), 0, 'nothing processed yet');
      } finally {
        compose('up', '-d', 'registration-worker');
      }

      const drainStart = performance.now();
      await waitUntil('all tickets persisted',
        () => ticketCount(`event_id='${loadEvent}'`) === TOTAL, { timeoutMs: 240_000, intervalMs: 1000 });
      console.log(`  worker drained ${TOTAL} bookings in ${((performance.now() - drainStart) / 1000).toFixed(1)}s`);

      assert.equal(Number(psql(`select count(*) - count(distinct seat_id) from tickets where event_id='${loadEvent}'`)), 0, 'no duplicate seats');
      await waitUntil('all notifications persisted', () =>
        Number(psql(`select count(*) from notifications where user_id in (${userIds}) and type='BOOKING_CONFIRMED'`)) === TOTAL,
      { timeoutMs: 120_000, intervalMs: 1000 });
      const failed = Number(psql(`select count(*) from notifications where user_id in (${userIds}) and type='BOOKING_FAILED'`));
      assert.equal(failed, 0, 'no booking may fail');
      // RabbitMQ's management stats are sampled every 5s, so wait for them to settle
      await waitUntil('queue fully drained', async () => (await queueStats()).messages === 0, { timeoutMs: 30_000 });
    });
  });
});

async function publishCommand({ bookingId, userId, eventId, seats }) {
  const res = await rabbit('POST', '/api/exchanges/%2F/registration-exchange/publish', {
    properties: { content_type: 'application/json' },
    routing_key: 'registration-routing-key',
    payload: JSON.stringify({ bookingId, userId, eventId, selectedSeats: seats, fromQueue: true }),
    payload_encoding: 'string',
  });
  assert.equal(res.status, 200);
  return res.json;
}
