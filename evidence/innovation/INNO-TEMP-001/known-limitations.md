# Known limitations

- `wake-intent.v1` evaluates current time window, quiet/focus/sleep/todo boundaries,
  deterministic content risk, autonomous ALIVE opt-out, same-purpose supersession and
  explicit newer user messages that resolve/cancel the anchored topic. A provider-backed
  general semantic relevance evaluator and calibrated false-positive corpus remain.
- Delivery is durable in-app plus best-effort live SSE. Feedback records "matched",
  "later" and per-purpose "stop similar" and changes subsequent scheduling/delivery.
  APNs/FCM/Web Push providers, device receipts and learned fatigue calibration remain.
- The browser journey proves the user control path, while worker delivery decisions are
  covered at service/job and PostgreSQL migration levels. A time-compressed browser
  journey that waits through an actual due transition remains.
- The original independent review was `CHANGES_REQUIRED`; its two P1s are builder-fixed
  but not independently re-reviewed, so `AURORA-TEMPORAL` is not PASS.
