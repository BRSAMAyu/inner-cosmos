# INNO-CAP-006 — Owner slow-letter consent boundary

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-15.

## Experience contract implemented

- The React Aurora surface now has an owner inbox for letters that have actually arrived, with
  read, gentle decline, report and block controls.
- `SENT` and `FLYING` letters are excluded server-side from the receiver inbox, so slow time cannot
  be bypassed by calling the list endpoint early.
- Blocking as the receiver now persists a `tb_block_relation`, not only a terminal status on one
  letter. The existing safety filter therefore rejects future letters from that sender.
- Reporting continues through the restricted moderation record and does not expose owner-private
  capsule or Aurora data.

## Verification at checkpoint

- Focused Java slow-letter and application-flow gate: `19/19 PASS`, including query-boundary
  proof that pre-arrival states are excluded.
- React Vitest protocol suite: `3/3 PASS`; production TypeScript build: `PASS`.
- Full Playwright suite: `9/9 PASS` in 41.6 seconds, including the cross-account publish, visitor
  conversation, slow-letter send, owner inbox and withdrawal journey.
- Visual QA: [`owner-letter-inbox.png`](owner-letter-inbox.png) was inspected at full-page scale.
  Two arrived letters show distinct states and readable read/decline/block/report controls; the
  inbox remains visually separated from the Resonance Network and Aurora conversation.

## Honest remaining work

Reply composition, mutual connection consent and a real-time arrival notification still need a
blind member experience pass. The social closed-loop gate therefore remains `IN_PROGRESS`.
