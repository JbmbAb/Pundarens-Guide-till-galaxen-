// Convoy-reläet (07): en brevlåda för förseglade kuvert mellan två telefoner.
// Reläet ser aldrig sessionsnyckeln eller någon position. Det håller bara det senaste kuvertet per
// deltagare och sort, kontrollerar åtkomsttoken och sluttid, och glömmer allt när sessionen är slut.
// Inga beroenden: starta med  node relay/convoy-relay.mjs
//
// Anropen är rena textrader, fält skilda med blanksteg:
//   POST   /v1/sessions/<id>            "<slutar> <inbjudanSlutar> <sha256 av inbjudans hemlighet> <deltagare>" -> token
//   POST   /v1/sessions/<id>/join       "<inbjudans hemlighet> <deltagare>"                               -> "<token> <slutar>"
//   POST   /v1/sessions/<id>/envelopes  "<löpnummer> <sänt> <sort> <nonce> <chiffertext>"   (Bearer-token)
//   GET    /v1/sessions/<id>/envelopes?since=<n>   -> "<serverns klocka> <n>" och sedan en rad per kuvert
//   DELETE /v1/sessions/<id>/token      slutar dela: token spärras och deltagarens positioner tas bort
import http from 'node:http';
import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';
import { pathToFileURL } from 'node:url';

const MAX_SESSION_MS = 24 * 3600_000;
const MAX_INVITE_MS = 10 * 60_000;
const MAX_BODY_BYTES = 1024;
const MAX_SESSIONS = 50;
const MAX_WRONG_INVITES = 5;
const HEX16 = /^[0-9a-f]{16}$/;
const HEX32 = /^[0-9a-f]{32}$/;
const HEX64 = /^[0-9a-f]{64}$/;
const NUMBER = /^\d{1,16}$/;
const BASE64 = /^[A-Za-z0-9_-]{1,256}$/;
const KINDS = new Set(['POSITION', 'EMERGENCY', 'REVOKE']);

function readBody(req) {
  return new Promise((resolve) => {
    let size = 0;
    const parts = [];
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size <= MAX_BODY_BYTES) parts.push(chunk);
    });
    req.on('end', () => resolve(size > MAX_BODY_BYTES ? null : Buffer.concat(parts).toString('utf8')));
    req.on('error', () => resolve(null));
  });
}

export function createRelay({ now = Date.now, maxRequestsPerMinute = 1200 } = {}) {
  const sessions = new Map();
  let windowStart = 0;
  let windowCount = 0;

  const sweep = () => {
    const t = now();
    for (const [id, s] of sessions) if (t >= s.expiresAt) sessions.delete(id);
  };
  const newToken = () => randomBytes(32).toString('hex');
  // Den som slutar dela ska inte ligga kvar på kartan hos den andra.
  const forgetPositions = (s, participant) => {
    s.latest.delete(`${participant} POSITION`);
    s.latest.delete(`${participant} EMERGENCY`);
  };

  const server = http.createServer(async (req, res) => {
    const send = (code, body = '') => {
      res.writeHead(code, { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store' });
      res.end(body);
    };
    try {
      const t = now();
      if (t - windowStart >= 60_000) { windowStart = t; windowCount = 0; }
      if (++windowCount > maxRequestsPerMinute) return send(429);
      sweep();

      const url = new URL(req.url, 'http://relay');
      const match = /^\/v1\/sessions\/([0-9a-f]{32})(\/join|\/envelopes|\/token)?$/.exec(url.pathname);
      if (!match) return send(404);
      const id = match[1];
      const tail = match[2] ?? '';
      const body = req.method === 'POST' ? await readBody(req) : '';
      if (body === null) return send(413);
      const f = body.trim().split(' ');
      const s = sessions.get(id);

      if (tail === '' && req.method === 'POST') {
        const [expires, inviteExpires, inviteHash, participant] = f;
        if (f.length !== 4 || !NUMBER.test(expires) || !NUMBER.test(inviteExpires) || !HEX64.test(inviteHash) || !HEX16.test(participant)) return send(400);
        const expiresAt = Number(expires);
        if (expiresAt <= t || expiresAt > t + MAX_SESSION_MS) return send(400);
        if (s) return send(409);
        if (sessions.size >= MAX_SESSIONS) return send(503);
        const token = newToken();
        sessions.set(id, {
          expiresAt,
          inviteHash: Buffer.from(inviteHash, 'hex'),
          inviteExpiresAt: Math.min(Number(inviteExpires), t + MAX_INVITE_MS, expiresAt),
          inviteUsed: false,
          wrongInvites: 0,
          tokens: new Map([[token, { participant, revoked: false }]]),
          latest: new Map(),
          counter: 0,
        });
        return send(201, token);
      }

      if (!s) return send(404);

      if (tail === '/join' && req.method === 'POST') {
        const [secret, participant] = f;
        if (f.length !== 2 || !HEX32.test(secret) || !HEX16.test(participant)) return send(400);
        if (s.inviteUsed) return send(409);
        if (t >= s.inviteExpiresAt) return send(410);
        if ([...s.tokens.values()].some((p) => p.participant === participant)) return send(409);
        const hash = createHash('sha256').update(Buffer.from(secret, 'hex')).digest();
        if (!timingSafeEqual(hash, s.inviteHash)) {
          if (++s.wrongInvites >= MAX_WRONG_INVITES) s.inviteUsed = true;
          return send(403);
        }
        s.inviteUsed = true;
        const token = newToken();
        s.tokens.set(token, { participant, revoked: false });
        return send(200, `${token} ${s.expiresAt}`);
      }

      const bearer = /^Bearer ([0-9a-f]{64})$/.exec(req.headers.authorization ?? '');
      const me = bearer && s.tokens.get(bearer[1]);
      if (!me) return send(401);
      if (me.revoked) return send(403);

      if (tail === '/envelopes' && req.method === 'POST') {
        const [seq, sentAt, kind, nonce, ciphertext] = f;
        if (f.length !== 5 || !NUMBER.test(seq) || !NUMBER.test(sentAt) || !KINDS.has(kind) || !BASE64.test(nonce) || !BASE64.test(ciphertext)) return send(400);
        if (kind === 'REVOKE') {
          me.revoked = true;
          forgetPositions(s, me.participant);
        }
        s.latest.set(`${me.participant} ${kind}`, {
          n: ++s.counter,
          participant: me.participant,
          line: `${t} ${me.participant} ${seq} ${sentAt} ${kind} ${nonce} ${ciphertext}`,
        });
        return send(204);
      }

      if (tail === '/envelopes' && req.method === 'GET') {
        const since = Number(url.searchParams.get('since') ?? '0');
        if (!Number.isSafeInteger(since) || since < 0) return send(400);
        const lines = [...s.latest.values()]
          .filter((e) => e.participant !== me.participant && e.n > since)
          .sort((a, b) => a.n - b.n)
          .map((e) => e.line);
        return send(200, [`${t} ${s.counter}`, ...lines].join('\n'));
      }

      if (tail === '/token' && req.method === 'DELETE') {
        me.revoked = true;
        forgetPositions(s, me.participant);
        return send(204);
      }

      return send(405);
    } catch {
      return send(500);
    }
  });

  const timer = setInterval(sweep, 60_000);
  timer.unref();
  server.on('close', () => clearInterval(timer));
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const host = process.env.HOST ?? '127.0.0.1';
  const port = Number(process.env.PORT ?? 8787);
  const server = createRelay();
  server.listen(port, host, () => console.log(`LISTENING ${server.address().port}`));
}
