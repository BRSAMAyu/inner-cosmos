# INNO-TEMP-001 — Durable Aurora return intent vertical slice

Builder status: `EVALUATED`.

This package proves the first durable WakeIntent slice without replacing or reducing
the existing proactive/ALIVE capability:

- Aurora return plans persist purpose, user-facing reason, earliest/preferred/latest
  window, timezone, payload reference, policy version and lifecycle state.
- Stored schedule instants are normalized to UTC and the owner-safe API converts them
  back to the user's IANA timezone; worker lease tokens never enter the HTTP response.
- Per-row compare-and-set claim tokens prevent duplicate multi-worker delivery and an
  expired lease can be reclaimed after a worker crash or restart.
- Near delivery, the worker rechecks quiet/focus/sleep/todo boundaries, deterministic
  safety rules and the latest ALIVE opt-out. It records Delay, Drop, Send+in-app or
  Convert-to-in-app outcomes.
- Intent completion and the idempotent in-app notification commit atomically before
  best-effort SSE fan-out, so cancellation races, the Academy API/scheduler split and
  an offline browser do not create a stray or erased return.
- The owner can list, create, postpone and cancel a return from the React Aurora page.
- Existing `PrivateTimer` rows continue to be processed for compatibility; new valid
  ALIVE `schedule` decisions create WakeIntent rows and preserve their content.
- A previously hidden owner/profile ID mix-up in the proactive scheduler was fixed so
  decisions are evaluated for `UserProfile.userId`, not the profile row primary key.

The acceptance ledger remains `IN_PROGRESS`: the first policy has deterministic
boundary/risk checks, but context-evidence preconditions, semantic relevance scoring,
delivery feedback learning and independent review still remain.
