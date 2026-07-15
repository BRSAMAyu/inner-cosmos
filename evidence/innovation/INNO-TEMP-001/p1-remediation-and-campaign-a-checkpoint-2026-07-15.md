# INNO-TEMP-001 P1 remediation and Campaign A checkpoint — 2026-07-15

Status: `BUILDER_VERIFIED / IN_PROGRESS`.

## Independent-review P1 closure

- Time is stored as a UTC instant and evaluated using `Clock.systemUTC()` plus the
  persisted `UserProfile.timezone`. Browser IANA timezone is persisted during login.
  Local DST gaps and overlaps fail explicitly; instant scheduling remains unambiguous.
- Ordinary notifications remain repeatable. Only `notifyOnce` supplies the nullable
  owner-scoped idempotency key. A real PostgreSQL V3 fixture with duplicate Capsule
  notifications migrates through V4 without loss.

## Campaign A temporal loop added

- Natural negotiation grammar accepts deterministic Chinese/English relative times and
  tomorrow/clock expressions; the React UI no longer presents a fixed one-hour contract.
- New same-purpose agreements atomically mark the old active intent `SUPERSEDED` and keep
  the replacement edge.
- Natural agreements capture the owner-checked dialog session and latest message anchor.
  Immediately before delivery, a newer explicit user resolution/cancel statement drops
  the return; quiet/safety/preference checks still apply.
- Durable notification retains the WakeIntent reference. `?wakeIntent={id}` owner-checks
  the intent and restores the anchored session. UI feedback supports `MATCHED`, `LATER`
  (new durable intent), and `STOP_SIMILAR` (future same-purpose delivery drop).

## Verification

- `WakeIntentServiceIntegrationTest`: 10/10 pass (UTC/local conversion, DST, lease race,
  recovery, cancellation, expiry, natural negotiation, supersession and feedback).
- `WakeIntentDeliveryJobTest`: 6/6 pass; `WakeIntentRelevanceEvaluatorTest`: 1/1 pass.
- `AliveDecisionEngineTimezoneTest`: 1/1 pass; `NotificationServiceTest`: 5/5 pass.
- `PostgresFlywayBaselineTest`: 3/3 pass against pinned PostgreSQL/pgvector; six migrations
  applied, V3→V4 duplicate legacy notifications preserved, V1→V6 baseline matches schema.
- Self-contained packaged-JAR Playwright: 4/4 pass for interrupt/replan, live SSE fault
  recovery, natural return control and Self evolution/rollback.
- Campaign checkpoint full Maven gate: 733/733 pass on Java 21 / Spring Boot 3.5.14.
- Repository secret scan: PASS, 0 findings; React Vitest: 3/3 pass; production bundle built.

## Honest remaining gates

- No APNs/FCM/Web Push credential or real device receipt was available; in-app persistence
  plus live SSE is the verified delivery channel.
- Relevance uses explicit post-anchor resolution evidence plus deterministic supersession,
  not a calibrated real-provider semantic judge.
- Independent re-review, real-provider pairwise and non-implementer blind experience remain.
