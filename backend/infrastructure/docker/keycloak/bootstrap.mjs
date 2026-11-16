// Idempotent Keycloak bootstrap for the ticketing platform.
//
// Works against a brand-new Keycloak AND against an existing volume (it never
// deletes anything): ensures realm, roles, a *public* password-grant client
// (the API logs users in without a client secret) and an admin test user.
//
//   node bootstrap.mjs            (KC_URL, KC_ADMIN, KC_ADMIN_PASSWORD optional)

const KC_URL = process.env.KC_URL ?? 'http://localhost:9090';
const KC_ADMIN = process.env.KC_ADMIN ?? 'admin';
const KC_ADMIN_PASSWORD = process.env.KC_ADMIN_PASSWORD ?? 'admin';
const REALM = 'ticketing-platform';
const CLIENT_ID = 'ticketing-api-client';
const PLATFORM_ADMIN = {
  email: process.env.PLATFORM_ADMIN_EMAIL ?? 'admin@ticketing.local',
  password: process.env.PLATFORM_ADMIN_PASSWORD ?? 'Admin123!',
};

async function adminToken() {
  const res = await fetch(`${KC_URL}/realms/master/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: 'admin-cli',
      grant_type: 'password',
      username: KC_ADMIN,
      password: KC_ADMIN_PASSWORD,
    }),
  });
  if (!res.ok) throw new Error(`admin login failed: ${res.status} ${await res.text()}`);
  return (await res.json()).access_token;
}

async function waitForKeycloak(timeoutMs = 180_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(`${KC_URL}/realms/master/.well-known/openid-configuration`);
      if (res.ok) return;
    } catch { /* not up yet */ }
    await new Promise((r) => setTimeout(r, 2000));
  }
  throw new Error('Keycloak did not become ready in time');
}

async function main() {
  await waitForKeycloak();
  const token = await adminToken();
  const call = async (method, path, body) => {
    const res = await fetch(`${KC_URL}/admin${path}`, {
      method,
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (res.status === 404 && method === 'GET') return null;
    if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${await res.text()}`);
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  };

  // 1. realm
  const realm = await call('GET', `/realms/${REALM}`);
  if (!realm) {
    await call('POST', '/realms', {
      realm: REALM, enabled: true, sslRequired: 'none', loginWithEmailAllowed: true,
      accessTokenLifespan: 3600,
    });
    console.log(`created realm ${REALM}`);
  } else {
    // long-lived tokens so load tests are not interrupted by expiry
    await call('PUT', `/realms/${REALM}`, { ...realm, accessTokenLifespan: 3600, sslRequired: 'none' });
    console.log(`realm ${REALM} present (access token lifespan = 1h)`);
  }

  // 2. roles
  for (const name of ['USER', 'ADMIN']) {
    if (!(await call('GET', `/realms/${REALM}/roles/${name}`))) {
      await call('POST', `/realms/${REALM}/roles`, { name });
      console.log(`created role ${name}`);
    }
  }

  // 3. public client with direct access grants (password login without secret)
  const clientRep = {
    clientId: CLIENT_ID, enabled: true, publicClient: true,
    directAccessGrantsEnabled: true, standardFlowEnabled: true,
    redirectUris: ['*'], webOrigins: ['*'],
  };
  const [existing] = (await call('GET', `/realms/${REALM}/clients?clientId=${CLIENT_ID}`)) ?? [];
  if (existing) {
    await call('PUT', `/realms/${REALM}/clients/${existing.id}`, { ...existing, ...clientRep });
    console.log(`client ${CLIENT_ID} set to public + direct access grants`);
  } else {
    await call('POST', `/realms/${REALM}/clients`, clientRep);
    console.log(`created client ${CLIENT_ID}`);
  }

  // 4. platform admin user
  let [user] = (await call('GET', `/realms/${REALM}/users?username=${encodeURIComponent(PLATFORM_ADMIN.email)}&exact=true`)) ?? [];
  if (!user) {
    await call('POST', `/realms/${REALM}/users`, {
      username: PLATFORM_ADMIN.email, email: PLATFORM_ADMIN.email, firstName: 'Platform', lastName: 'Admin',
      enabled: true, emailVerified: true,
      credentials: [{ type: 'password', value: PLATFORM_ADMIN.password, temporary: false }],
    });
    [user] = await call('GET', `/realms/${REALM}/users?username=${encodeURIComponent(PLATFORM_ADMIN.email)}&exact=true`);
    console.log(`created user ${PLATFORM_ADMIN.email}`);
  }
  const roles = await Promise.all(['ADMIN', 'USER'].map((n) => call('GET', `/realms/${REALM}/roles/${n}`)));
  await call('POST', `/realms/${REALM}/users/${user.id}/role-mappings/realm`, roles);
  console.log(`user ${PLATFORM_ADMIN.email} has roles ADMIN, USER`);
}

main().catch((e) => { console.error(e.message); process.exit(1); });
