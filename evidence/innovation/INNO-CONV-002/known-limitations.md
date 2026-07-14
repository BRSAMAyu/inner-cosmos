# Known limitations

- Provider transport cancellation is not assumed. The network request may finish, but
  its result is discarded and cannot commit a plan after stop.
- SSE event IDs are correlated and typed, but browser reconnect does not yet replay
  missed token deltas. Complete resumable SSE remains part of G2 API contracts.
- Cancellation is database-authoritative and polled during streaming with at most
  100 ms detection latency. Redis/lease externalization belongs to G2/G8.
- A partially emitted bubble is retained in the turn timeline, but no incomplete
  DialogMessage is created in chat history; the next turn receives the exact delivered
  and unsent split through interruption context.
- Builder evaluation is not independent verification.
