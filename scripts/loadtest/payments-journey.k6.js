// CP-50B codable precursor: the payments/entitlement journey load harness.
//
// Journey mix (mirrors the CP-45/46 pipeline): login (CSRF-first) → create a server-side
// order → POST a sandbox-signed WeChat Pay callback → read the unified entitlement
// snapshot → read the transparent quota display. This exercises exactly the surfaces the
// CP-50B gate cares about under load: anonymous callback ingest, ledger idempotency and
// the entitlement/quota read path.
//
// Honesty switch: with no operator channel configuration the callback MUST fail closed
// (403 + the channel's FAIL ack) — the harness treats that as the expected, honest
// outcome and counts a 200 on an unconfigured channel as a FAILURE (someone faked a
// gate). Set SANDBOX_CONFIGURED=true (with PAYMENTS_SECRET/PAYMENTS_MCHID) only against
// a deployment whose operator actually injected the sandbox credentials.
//
// Usage:
//   k6 run -e BASE_URL=http://localhost:8080 -e USERNAME=demo -e PASSWORD=demo123 \
//         -e VUS=2 -e DURATION=30s scripts/loadtest/payments-journey.k6.js
//   k6 run -e SANDBOX_CONFIGURED=true -e PAYMENTS_SECRET=... -e PAYMENTS_MCHID=... ...
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import crypto from 'k6/crypto';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERNAME = __ENV.USERNAME || 'demo';
const PASSWORD = __ENV.PASSWORD || 'demo123';
const VUS = parseInt(__ENV.VUS || '2', 10);
const DURATION = __ENV.DURATION || '30s';
const SANDBOX_CONFIGURED = String(__ENV.SANDBOX_CONFIGURED || 'false') === 'true';
const PAYMENTS_SECRET = __ENV.PAYMENTS_SECRET || '';
const PAYMENTS_MCHID = __ENV.PAYMENTS_MCHID || '';

const journeyFailures = new Counter('payments_journey_failures');
const callbackLatency = new Trend('payments_callback_latency', true);
const failClosedViolations = new Counter('payments_fail_closed_violations');

export const options = {
  scenarios: {
    payments_journey: {
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
    http_req_failed: ['rate<0.01'],
    payments_callback_latency: ['p(95)<2000'],
    payments_journey_failures: ['count<10'],
    // A fail-closed violation is a zero-tolerance event: an unconfigured channel
    // accepting anything means the verification pipeline is not actually guarding.
    payments_fail_closed_violations: ['count==0'],
  },
};

function login() {
  const csrfRes = http.get(`${BASE_URL}/api/v1/auth/csrf`);
  const token = csrfRes.json('data.token') || csrfRes.json('token');
  const res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token || '' } });
  check(res, { 'login ok': r => r.status === 200 });
  return { token, cookies: res.cookies };
}

function sandboxCallback(order, amountCents) {
  const timestamp = Math.floor(Date.now() / 1000).toString();
  const eventId = `k6-${timestamp}-${__VU}-${__ITER}`;
  const body = JSON.stringify({
    id: eventId,
    event_type: 'TRANSACTION.SUCCESS',
    resource: {
      mchid: PAYMENTS_MCHID,
      out_trade_no: order.orderId,
      transaction_id: `k6-txn-${eventId}`,
      amount: { total: amountCents, currency: 'CNY' },
      success_time: '2026-09-12T12:00:01+08:00',
    },
  });
  const signature = crypto.hmac('sha256', PAYMENTS_SECRET, `${timestamp}.${body}`, 'hex');
  const res = http.post(`${BASE_URL}/api/payments/callbacks/wechatpay`, body, {
    headers: {
      'Content-Type': 'application/json',
      'Wechatpay-Timestamp': timestamp,
      'Wechatpay-Nonce': `k6-${eventId}`,
      'Wechatpay-Signature': signature,
    },
  });
  callbackLatency.add(res.timings.duration);
  if (!SANDBOX_CONFIGURED) {
    // Unconfigured deployment: the honest outcome is a fail-closed rejection.
    const failClosed = res.status === 403 || res.status === 401;
    if (!failClosed) {
      failClosedViolations.add(1);
      check(res, { 'unconfigured channel failed closed': () => false });
    } else {
      check(res, { 'unconfigured channel failed closed': r => r.status === 403 || r.status === 401 });
    }
  } else {
    check(res, { 'configured sandbox accepted': r => r.status === 200 });
  }
}

export default function () {
  const auth = login();
  const headers = { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': auth.token || '' };

  const orderRes = http.post(`${BASE_URL}/api/payments/orders`,
    JSON.stringify({ productId: 'pro.monthly', channel: 'wechatpay' }),
    { headers, cookies: auth.cookies });
  if (!check(orderRes, { 'order created': r => r.status === 200 })) {
    journeyFailures.add(1);
    sleep(1);
    return;
  }
  const order = orderRes.json('data');

  sandboxCallback(order, order.expectedAmountCents);

  const entitlementRes = http.get(`${BASE_URL}/api/me/entitlements`,
    { headers, cookies: auth.cookies });
  if (!check(entitlementRes, { 'entitlement snapshot ok': r => r.status === 200 })) {
    journeyFailures.add(1);
  }

  const quotaRes = http.get(`${BASE_URL}/api/me/quotas`, { headers, cookies: auth.cookies });
  if (!check(quotaRes, {
    'quota display ok': r => r.status === 200,
    'quota carries reset time': r => (r.json('data.quotas[0].resetsAt') || '') !== '',
  })) {
    journeyFailures.add(1);
  }
  sleep(1);
}
