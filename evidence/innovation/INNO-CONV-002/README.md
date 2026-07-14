# INNO-CONV-002 evidence

## Builder result

`EVALUATED`, pending independent review.

- A durable turn now exists before the Provider call, and SSE emits `turn.started`,
  allowing the user to stop while Aurora is thinking.
- A stopped generation is recorded as `DISCARDED`; a late Provider result cannot
  create a plan, DialogMessage or outgoing bubble.
- Streaming persists each Aurora DialogMessage only after that bubble finishes.
  Stop cancels all pending bubbles and no later bubble is sent. The DialogMessage
  write and bubble commit share the locked turn transaction, closing the final
  stop-versus-persistence race.
- Partial delivery stores an exact character boundary. The next turn receives an
  owner-scoped distinction between what was actually heard and what remained unsent.
- A new user message interrupts the prior active turn and becomes a normal replanning
  input. The UI no longer tells the user to wait; it offers Stop and allows direct
  interjection.
- Cancellation ownership uses the same opaque owner-scoped turn lookup as timeline.

## Acceptance mapping

| Package acceptance | Evidence |
|---|---|
| Stop prevents later bubbles | `stopAfterFirstBubbleKeepsDeliveredContextAndCancelsEveryPendingBubble` and SSE loop cancellation gate |
| Uncancellable Provider result is discarded | `stopDuringProviderGenerationDiscardsAttemptAndCommitsNoPlanOrBubble` |
| Stop and final persistence are atomic and retry-safe | `cancelledTurnNeverInvokesAtomicMessagePersistence`, `committedBubbleRetryNeverInvokesPersistenceTwice` |
| New turn knows delivered and unsent content | partial-delivery boundary assertion and `interruptionContext` passed to structured AI context |
| Interruption is normal UX | Stop control, `turn.interrupted` event, direct interjection source contract |

Complete-product `AURORA-CHOREOGRAPHY` remains `IN_PROGRESS`, not `PASS`, because
resumable reconnect is a wider G2 API/runtime concern and has not been closed.
