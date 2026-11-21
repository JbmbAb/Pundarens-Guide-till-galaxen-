// Körs med:  node --test relay/convoy-relay.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { createRelay } from './convoy-relay.mjs';

const ID = 'a'.repeat(32);
const A = '1'.repeat(16);
const B = '2'.repeat(16);
const SECRET = 'c'.repeat(32);
const HASH = createHash('sha256').update(Buffer.from(SECRET, 'hex')).digest('hex');
const HOUR = 3600_000;

async function relay(options = {}) {
  const clock = { t: 1_000_000 };
  const server = createRelay({ now: () => clock.t, ...options });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}/v1/sessions`;
  const call = async (method, path, body, token) => {
    const res = await fetch(base + path, { method, body, headers: token ? { authorization: `Bearer ${token}` } : {} });
    return { status: res.status, text: await res.text() };
  };
  return { clock, call, close: () => new Promise((r) => server.close(r)) };
}

async function pair(r) {
  const a = await r.call('POST', `/${ID}`, `${r.clock.t + HOUR} ${r.clock.t + 600_000} ${HASH} ${A}`);
  const b = await r.call('POST', `/${ID}/join`, `${SECRET} ${B}`);
  return { a: a.text, b: b.text.split(' ')[0], joined: b };
}

test('två deltagare byter kuvert och ser aldrig sina egna', async () => {
  const r = await relay();
  const { a, b, joined } = await pair(r);
  assert.equal(joined.status, 200);
  assert.equal(joined.text.split(' ')[1], String(r.clock.t + HOUR));
  assert.equal((await r.call('POST', `/${ID}/envelopes`, '7 999 POSITION bm9uY2U Y2lwaGVy', a)).status, 204);
  const got = await r.call('GET', `/${ID}/envelopes?since=0`, undefined, b);
  assert.equal(got.text, `${r.clock.t} 1\n${r.clock.t} ${A} 7 999 POSITION bm9uY2U Y2lwaGVy`);
  assert.equal((await r.call('GET', `/${ID}/envelopes?since=0`, undefined, a)).text, `${r.clock.t} 1`);
  assert.equal((await r.call('GET', `/${ID}/envelopes?since=1`, undefined, b)).text, `${r.clock.t} 1`);
  await r.close();
});

test('bara det senaste kuvertet per deltagare och sort sparas', async () => {
  const r = await relay();
  const { a, b } = await pair(r);
  await r.call('POST', `/${ID}/envelopes`, '1 10 POSITION n1 c1', a);
  await r.call('POST', `/${ID}/envelopes`, '2 20 EMERGENCY n2 c2', a);
  await r.call('POST', `/${ID}/envelopes`, '3 30 POSITION n3 c3', a);
  const lines = (await r.call('GET', `/${ID}/envelopes?since=0`, undefined, b)).text.split('\n').slice(1);
  assert.deepEqual(lines.map((l) => l.split(' ').slice(2).join(' ')), ['2 20 EMERGENCY n2 c2', '3 30 POSITION n3 c3']);
  await r.close();
});

test('inbjudan löses in en gång, och fel hemlighet eller för sen inlösen nekas', async () => {
  const r = await relay();
  await r.call('POST', `/${ID}`, `${r.clock.t + HOUR} ${r.clock.t + 600_000} ${HASH} ${A}`);
  assert.equal((await r.call('POST', `/${ID}/join`, `${'d'.repeat(32)} ${B}`)).status, 403);
  assert.equal((await r.call('POST', `/${ID}/join`, `${SECRET} ${A}`)).status, 409);
  assert.equal((await r.call('POST', `/${ID}/join`, `${SECRET} ${B}`)).status, 200);
  assert.equal((await r.call('POST', `/${ID}/join`, `${SECRET} ${'3'.repeat(16)}`)).status, 409);

  const other = 'b'.repeat(32);
  await r.call('POST', `/${other}`, `${r.clock.t + HOUR} ${r.clock.t + 99 * HOUR} ${HASH} ${A}`);
  r.clock.t += 600_000;
  assert.equal((await r.call('POST', `/${other}/join`, `${SECRET} ${B}`)).status, 410);
  await r.close();
});

test('fem felaktiga hemligheter spärrar inbjudan', async () => {
  const r = await relay();
  await r.call('POST', `/${ID}`, `${r.clock.t + HOUR} ${r.clock.t + 600_000} ${HASH} ${A}`);
  for (let i = 0; i < 5; i++) await r.call('POST', `/${ID}/join`, `${'d'.repeat(32)} ${B}`);
  assert.equal((await r.call('POST', `/${ID}/join`, `${SECRET} ${B}`)).status, 409);
  await r.close();
});

test('utan giltig token kommer inget in eller ut', async () => {
  const r = await relay();
  await pair(r);
  assert.equal((await r.call('GET', `/${ID}/envelopes?since=0`)).status, 401);
  assert.equal((await r.call('GET', `/${ID}/envelopes?since=0`, undefined, 'f'.repeat(64))).status, 401);
  assert.equal((await r.call('POST', `/${ID}/envelopes`, '1 10 POSITION n c', 'f'.repeat(64))).status, 401);
  assert.equal((await r.call('GET', `/${'e'.repeat(32)}/envelopes?since=0`, undefined, 'f'.repeat(64))).status, 404);
  await r.close();
});

test('vid sluttiden är sessionen och allt i den borta', async () => {
  const r = await relay();
  const { a, b } = await pair(r);
  await r.call('POST', `/${ID}/envelopes`, '1 10 POSITION n c', a);
  r.clock.t += HOUR;
  assert.equal((await r.call('GET', `/${ID}/envelopes?since=0`, undefined, b)).status, 404);
  assert.equal((await r.call('POST', `/${ID}/envelopes`, '2 20 POSITION n c', a)).status, 404);
  await r.close();
});

test('den som slutar dela spärras, och positionerna tas bort hos servern', async () => {
  const r = await relay();
  const { a, b } = await pair(r);
  await r.call('POST', `/${ID}/envelopes`, '1 10 POSITION n c', a);
  assert.equal((await r.call('POST', `/${ID}/envelopes`, '2 20 REVOKE n2 c2', a)).status, 204);
  assert.equal((await r.call('POST', `/${ID}/envelopes`, '3 30 POSITION n c', a)).status, 403);
  const lines = (await r.call('GET', `/${ID}/envelopes?since=0`, undefined, b)).text.split('\n').slice(1);
  assert.equal(lines.length, 1);
  assert.match(lines[0], / 2 20 REVOKE n2 c2$/);

  await r.call('POST', `/${ID}/envelopes`, '1 10 POSITION n c', b);
  assert.equal((await r.call('DELETE', `/${ID}/token`, undefined, b)).status, 204);
  assert.equal((await r.call('GET', `/${ID}/envelopes?since=0`, undefined, b)).status, 403);
  await r.close();
});

test('felaktiga, för stora och för många anrop nekas', async () => {
  const r = await relay({ maxRequestsPerMinute: 8 });
  const { a } = await pair(r);
  assert.equal((await r.call('POST', `/${ID}/envelopes`, '1 10 HEJ n c', a)).status, 400);
  assert.equal((await r.call('POST', `/${ID}/envelopes`, `1 10 POSITION n ${'c'.repeat(2000)}`, a)).status, 413);
  assert.equal((await r.call('POST', `/${'b'.repeat(32)}`, `${r.clock.t + 25 * HOUR} ${r.clock.t} ${HASH} ${A}`)).status, 400);
  assert.equal((await r.call('POST', `/${ID}`, `${r.clock.t + HOUR} ${r.clock.t} ${HASH} ${A}`)).status, 409);
  assert.equal((await r.call('GET', '/nej')).status, 404);
  assert.equal((await r.call('GET', `/${ID}/envelopes?since=0`, undefined, a)).status, 200);
  assert.equal((await r.call('GET', `/${ID}/envelopes?since=0`, undefined, a)).status, 429);
  await r.close();
});
