# INNO-CONV-001 evidence

## Builder result

`EVALUATED`, pending independent review. The current Aurora response is now adapted
to a durable conversation turn, committed turn plan, ordered message bubbles,
generation attempt and append-only lifecycle events.

The implementation deliberately does not change Aurora's prompt, response selection,
Self, Constitution, Emergence, portrait, relationship, proactive policy, Capsule or
slow-social semantics. Content deltas now use the current frontend's already-supported
explicit `token` event listener, and the existing `done` event remains available;
lifecycle events are additive.

## Acceptance mapping

| Package acceptance | Evidence |
|---|---|
| Existing 1-3 bubble experience does not regress | Full 642-test baseline plus `AuroraStreamControllerTest` and production-contract validation |
| Bubble states are replayable | Ordered `TURN_CREATED`, `PLAN_COMMITTED`, `BUBBLE_PLANNED`, `BUBBLE_COMMITTED`, `TURN_COMPLETED` events and owner-scoped timeline test |
| Retry does not duplicate committed bubbles | Same `user_message_id` returns the existing turn/plan; integration test observes unchanged bubble/event counts |
| One effective plan commit across replicas | Database unique keys on user message, turn/version and `(turn_id, commit_slot)`; constraint integration test rejects a second effective commit |

## Scope boundary

This package establishes the persistent authority needed for interruption and
replanning. Stop, partial delivery, cancellation and replanning are not claimed here;
they remain `INNO-CONV-002`. Accordingly, complete-product acceptance item
`AURORA-CHOREOGRAPHY` is `IN_PROGRESS`, not `PASS`.
