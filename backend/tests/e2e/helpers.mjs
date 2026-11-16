// Shared helpers for the end-to-end suite (Node >= 22: global fetch + WebSocket).
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

export const API = process.env.API_URL ?? 'http://localhost:8090';
export const NOTIFICATION_WS = process.env.NOTIFICATION_WS ?? 'ws://localhost:8092/ws-notification/websocket';
export const API_WS = process.env.API_WS ?? 'ws://localhost:8090/ws/websocket';
export const RABBIT = process.env.RABBIT_URL ?? 'http://localhost:15672';
export const COMPOSE_FILE = path.resolve(here, '../../infrastructure/docker/docker-compose.yml');

export const REDIS_CONTAINER = 'redis-ticketing-platform';
export const POSTGRES_CONTAINER = 'postgres-ticketing-platform';

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export function docker(...args) {
  return execFileSync('docker', args, { encoding: 'utf8' }).trim();
}
export const redis = (...args) => docker('exec', REDIS_CONTAINER, 'redis-cli', ...args);
export const psql = (sql) =>
  docker('exec', POSTGRES_CONTAINER, 'psql', '-U', 'postgres', '-d', 'ticketing_db', '-At', '-c', sql);
export const compose = (...args) => docker('compose', '-f', COMPOSE_FILE, ...args);

export async function api(method, route, { token, body } = {}) {
  const started = performance.now();
  const res = await fetch(`${API}${route}`, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* non-json body */ }
  return { status: res.status, json, text, ms: performance.now() - started };
}

export function jwtSubject(token) {
  return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString()).sub;
}

let userCounter = 0;
export async function createUser(prefix = 'user') {
  const email = `e2e-${prefix}-${Date.now()}-${userCounter++}@test.local`;
  const password = 'Passw0rd!';
  const reg = await api('POST', '/api/auth/register', {
    body: { email, password, firstName: 'E2E', lastName: prefix },
  });
  if (reg.status !== 201) throw new Error(`register failed: ${reg.status} ${reg.text}`);
  const login = await api('POST', '/api/auth/login', { body: { email, password } });
  if (login.status !== 200) throw new Error(`login failed: ${login.status} ${login.text}`);
  const token = login.json.access_token;
  return { email, password, token, userId: jwtSubject(token) };
}

export async function login(email, password) {
  const res = await api('POST', '/api/auth/login', { body: { email, password } });
  if (res.status !== 200) throw new Error(`login failed: ${res.status} ${res.text}`);
  return res.json.access_token;
}

export async function waitUntil(description, fn, { timeoutMs = 60_000, intervalMs = 500 } = {}) {
  const deadline = Date.now() + timeoutMs;
  let last;
  while (Date.now() < deadline) {
    last = await fn();
    if (last) return last;
    await sleep(intervalMs);
  }
  throw new Error(`timed out waiting for: ${description}`);
}

/** Minimal STOMP-over-WebSocket client (enough for subscribe + receive). */
export class StompClient {
  constructor(ws) {
    this.ws = ws;
    this.messages = [];
    this.waiters = [];
    this.receipts = new Set();
    this.buffer = '';
    this.subId = 0;
    ws.addEventListener('message', (ev) => this.#onData(String(ev.data)));
  }

  static async connect(url) {
    const ws = new WebSocket(url);
    await new Promise((resolve, reject) => {
      ws.addEventListener('open', resolve, { once: true });
      ws.addEventListener('error', () => reject(new Error(`websocket error: ${url}`)), { once: true });
    });
    const client = new StompClient(ws);
    const connected = new Promise((resolve) => { client.onConnected = resolve; });
    ws.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\0');
    await Promise.race([connected, sleep(5000).then(() => { throw new Error('STOMP CONNECT timeout'); })]);
    return client;
  }

  #onData(data) {
    this.buffer += data;
    let idx;
    while ((idx = this.buffer.indexOf('\0')) !== -1) {
      const raw = this.buffer.slice(0, idx).replace(/^\n+/, '');
      this.buffer = this.buffer.slice(idx + 1);
      if (raw) this.#onFrame(raw);
    }
  }

  #onFrame(raw) {
    const [head, ...bodyParts] = raw.split('\n\n');
    const [command, ...headerLines] = head.split('\n');
    const headers = Object.fromEntries(headerLines.map((l) => [l.slice(0, l.indexOf(':')), l.slice(l.indexOf(':') + 1)]));
    const body = bodyParts.join('\n\n');
    if (command === 'CONNECTED') this.onConnected?.();
    else if (command === 'RECEIPT') this.receipts.add(headers['receipt-id']);
    else if (command === 'MESSAGE') {
      let parsed = body;
      try { parsed = JSON.parse(body); } catch { /* keep raw */ }
      const msg = { destination: headers.destination, body: parsed };
      this.messages.push(msg);
      this.waiters.forEach((w) => w(msg));
    }
  }

  async subscribe(destination) {
    const id = `sub-${this.subId++}`;
    this.ws.send(`SUBSCRIBE\nid:${id}\ndestination:${destination}\n\n\0`);
    // Spring's simple broker does not send RECEIPT frames; give the subscription a moment to register.
    await sleep(500);
  }

  /** Resolve with the first (past or future) message on `destination` matching `predicate`. */
  waitFor(destination, predicate = () => true, timeoutMs = 20_000) {
    const existing = this.messages.find((m) => m.destination === destination && predicate(m.body));
    if (existing) return Promise.resolve(existing.body);
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.waiters = this.waiters.filter((w) => w !== waiter);
        reject(new Error(`no message on ${destination} within ${timeoutMs}ms`));
      }, timeoutMs);
      const waiter = (msg) => {
        if (msg.destination === destination && predicate(msg.body)) {
          clearTimeout(timer);
          this.waiters = this.waiters.filter((w) => w !== waiter);
          resolve(msg.body);
        }
      };
      this.waiters.push(waiter);
    });
  }

  close() { this.ws.close(); }
}

export async function rabbit(method, route, body) {
  const res = await fetch(`${RABBIT}${route}`, {
    method,
    headers: {
      Authorization: `Basic ${Buffer.from('guest:guest').toString('base64')}`,
      'Content-Type': 'application/json',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, json: text ? JSON.parse(text) : null };
}

export const queueStats = async () => (await rabbit('GET', '/api/queues/%2F/registration-queue')).json;

/** Run `tasks` (array of async fns) with bounded concurrency. */
export async function pool(tasks, concurrency) {
  const results = new Array(tasks.length);
  let next = 0;
  await Promise.all(Array.from({ length: concurrency }, async () => {
    while (next < tasks.length) {
      const i = next++;
      results[i] = await tasks[i]();
    }
  }));
  return results;
}

export const percentile = (values, p) => {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))];
};
