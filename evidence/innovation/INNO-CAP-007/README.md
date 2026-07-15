# INNO-CAP-007 — Mutual human connection consent

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-15.

## Experience contract implemented

- A human connection can be proposed from a specific arrived and read slow letter. The client sends
  only the letter id; the server resolves both parties and rejects senders, strangers, unread
  letters and blocked pairs.
- The receiver of the letter makes the first explicit decision to invite. The original sender then
  gets a separate incoming request and must independently accept before the relationship becomes
  `ACCEPTED`.
- Pending invitations disclose that no connection exists yet. Either addressee may decline, and
  either accepted party may leave, producing `WITHDRAWN` rather than an irreversible relationship.
- Replaying accept/decline against a non-pending request is rejected, closing a stale-state consent
  race in the previous generic social endpoints.
- A recipient can write a real slow-letter reply. It passes both content and relationship safety,
  joins the durable letter thread, enters `SENT`, and keeps the original conversation asynchronous.
- Letter transitions now enforce actor roles and reserve `FLYING`/`DELIVERED` for the scheduler, so
  neither party can bypass slow arrival through a public endpoint.

## Verification at checkpoint

- Focused Java social authorization, slow-letter and application-flow gate: `23/23 PASS`.
- Full Java regression gate: `775/775 PASS` (`mvn test`, 0 failures, 0 errors, 0 skipped).
- React Vitest protocol suite: `3/3 PASS`; TypeScript production build: `PASS`.
- Full Playwright suite: `9/9 PASS` in 37.1 seconds. It covers reply flight, invitation, independent
  acceptance, accepted state, safe exit, capsule withdrawal and pre-arrival body isolation.
- Visual QA: [`mutual-connection-consent.png`](mutual-connection-consent.png) was captured from the
  consent component itself and inspected at original resolution. Pending columns are empty only
  after acceptance, while the accepted column visibly identifies the connection and exit control.

## Honest remaining work

The connection intentionally provides consent state and safe exit, not a new high-pressure chat
feed. Real-member blind acceptance and policy review are still required before
`SOCIAL-CLOSED-LOOP` can be marked `PASS`.
