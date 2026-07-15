# Known limitations

- `wake-intent.v1` evaluates current time window, quiet/focus/sleep/todo boundaries,
  deterministic content risk and autonomous ALIVE opt-out. The persisted structured
  precondition/cancel-condition fields are not yet backed by a general evidence evaluator.
- Contextual relevance is currently bounded by the delivery window and latest user
  preference; semantic "already discussed" or superseded-event detection remains.
- Delivery is durable in-app plus best-effort live SSE. APNs/FCM/Web Push providers,
  receipts and fatigue-feedback learning are not part of this increment.
- The browser journey proves the user control path, while worker delivery decisions are
  covered at service/job and PostgreSQL migration levels. A time-compressed browser
  journey that waits through an actual due transition remains.
- Independent product/safety review is unassigned, so AURORA-TEMPORAL is not PASS.
