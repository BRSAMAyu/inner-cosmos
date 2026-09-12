// CP-50 codable precursor: the Aurora journey load harness.
//
// Journey mix (mirrors the product's paid path): login → create session → send an Aurora
// message (non-SSE authoritative endpoint) → fetch messages → settle the session. A small
// SSE smoke stage exercises the streaming path once per iteration.
//
// The REAL CP-50A gate (200-concurrent SSE baseline / 2x peak / >=24h soak on the domestic
// staging cluster with fault injection) is operator-gated: this file is the repeatable
// harness those runs execute, plus a local smoke mode against a dev deployment.
//
// Usage:
//   k6 run -e BASE_URL=http://localhost:8080 -e USERNAME=demo -e PASSWORD=demo123 \
//         -e VUS=2 -e DURATION=30s scripts/loadtest/aurora-journey.k6.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERNAME = __ENV.USERNAME || 'demo';
const PASSWORD = __ENV.PASSWORD || 'demo123';
const VUS = parseInt(__ENV.VUS || '2', 10);
const DURATION = __ENV.DURATION || '30s';

const journeyFailures = new Counter('aurora_journey_failures');
const turnLatency = new Trend('aurora_turn_latency', true);

export const options = {
  scenarios: {
    aurora_journey: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '30s', target: VUS },
        { duration: DURATION, target: VUS },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    // CP-50 acceptance drafts: journey failures must be provider-observable events, never
    // silent; http errors are failures. Tune per environment before the staged staging run.
    http_req_failed: ['rate<0.01'],
    aurora_turn_latency: ['p(95)<8000'],
    aurora_journey_failures: ['count<10'],
  },
};

function login() {
  // CSRF-first: the app rejects an unauthenticated POST without the token.
  const csrfRes = http.get(`${BASE_URL}/api/v1/auth/csrf`);
  const token = csrfRes.json('data.token') || csrfRes.json('token');
  const res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token || '' } });
  check(res, { 'login ok': r => r.status === 200 });
  return res.cookies;
}

export default function () {
  const cookies = login();
  const authed = { cookies, headers: { 'Content-Type': 'application/json' } };

  const created = http.post(`${BASE_URL}/api/dialog/session/create`,
    JSON.stringify({ title: 'load-journey', sessionType: 'AURORA_CHAT' }), authed);
  if (!check(created, { 'session created': r => r.status === 200 })) {
    journeyFailures.add(1);
    return;
  }
  const sessionId = created.json('data.id') || created.json('id');

  const turnStart = Date.now();
  const reply = http.post(`${BASE_URL}/api/aurora/message`,
    JSON.stringify({ sessionId, message: '压力旅程：今天想聊聊最近的一件小事。', mode: 'DAILY_TALK' }),
    authed);
  turnLatency.add(Date.now() - turnStart);
  if (!check(reply, { 'aurora turn ok': r => r.status === 200 })) {
    // The failure counter is the journey-failure SLI CP-50 requires — never silently green.
    journeyFailures.add(1);
  }

  http.get(`${BASE_URL}/api/dialog/session/${sessionId}/messages`, authed);

  http.post(`${BASE_URL}/api/aurora/settle?sessionId=${sessionId}`, null, authed);

  // One SSE stream probe per iteration keeps the streaming path represented without
  // holding long-lived connections for every VU (the staged staging run does the real
  // 200-concurrent SSE leg). 2xx/3xx/404 pass the probe; 5xx is a failure.
  const stream = http.get(`${BASE_URL}/api/aurora/stream?sessionId=${sessionId}&message=hi&mode=DAILY_TALK`,
    Object.assign({}, authed, { timeout: '10s' }));
  check(stream, {
    'sse probe responded': r => r.status < 500,
    'sse probe not hard-failed': r => r.status !== 401 && r.status !== 403
  });

  sleep(1);
}
