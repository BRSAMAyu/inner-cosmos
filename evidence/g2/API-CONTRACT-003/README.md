# API-CONTRACT-003 — Cross-Pod live Aurora SSE fan-out, resume and heartbeat

Status: `BUILDER_VERIFIED / IN_PROGRESS` on 2026-07-16.

## Problem

Live Aurora streaming previously kept both the private stage token and the in-flight token/lifecycle
events in process memory. Under a multi-replica `local-complete`/EKS deployment a client that POSTs
the stage on Pod A and opens the GET stream on Pod B loses its private context, and a client that
reconnects mid-turn onto a different Pod can only receive a durable snapshot that ends immediately —
it cannot keep following a turn that is still generating elsewhere.

## Implemented

- `com.innercosmos.streaming` externalises both concerns behind interfaces with an in-memory
  dev/test backend and a Redis backend selected by `inner-cosmos.aurora.stream.redis.enabled`:
  - `AuroraStreamStageStore` — owner-scoped, single-use, TTL-bound private stage tokens
    (`RedisAuroraStreamStageStore` uses `SET … EX` + `GETDEL`, so a token is consumable exactly once
    and only by its owner, on any Pod).
  - `AuroraLiveEventStore` — per-turn live event fan-out with a resume cursor
    (`RedisAuroraLiveEventStore` uses `XADD MAXLEN` + `XRANGE`/blocking `XREAD`, owner/turn filtered).
- `AuroraAgentServiceImpl` now publishes every id-carrying lifecycle/token event to the live store
  (best-effort) before writing the locally connected emitter, so a second Pod can replay and keep
  following the same turn. Redis publish failure degrades to the durable PostgreSQL timeline and the
  still-connected client rather than aborting a safe in-flight response.
- `ConversationTimelineController#/{turnId}/events` follows the live store from the resume cursor
  (Last-Event-ID or `afterSequence`), blocks up to 1s per read (safe under a 2s prod socket timeout),
  heartbeats every 10s, completes on the terminal event or a durable-terminal turn status, and falls
  back to an owner-scoped PostgreSQL snapshot on any Redis read failure.
- `ProductionStartupGuard` refuses to start prod unless Redis-backed Aurora stream staging + fan-out
  is enabled and both namespaces are configured (process-local streaming is a multi-replica hazard).

## Recovery race fixed this checkpoint

A client that disconnected in the instant right after the first `turn.started` (sequence 0) and
resumed on another Pod was misclassified as "finished": the live-vs-durable decision probed with
`afterSequence = 0`, which excludes the sequence-0 `turn.started`, so the resume replayed the durable
snapshot and completed while the turn was still generating. The decision now treats a turn as live
when the durable timeline is still non-terminal **or** any live event (sequence ≥ 0) is buffered, so
the resume keeps blocking on the live stream and delivers the later `turn.completed`.

## Verification (Java 21, offline)

- RED: `ConversationTimelineLiveResumeRaceTest` — resume right after `turn.started` on a non-terminal
  turn originally emitted a premature `replay.completed { turnStatus: IN_PROGRESS, lastSequence: 0 }`
  and never delivered `turn.completed`. GREEN after the fix (follows live, delivers `turn.completed`).
- `ConversationTimelineRedisOutageTest` — a Redis live-probe failure falls back to the owner-scoped
  PostgreSQL timeline (`timeline.event` + `replay.completed` + `TURN_COMPLETED`).
- `AuroraLiveEventStoreRetentionTest` — resume cursor returns only strictly-later events (no
  duplication), the sequence-0 `turn.started` is reachable via the inclusive probe, streams are
  owner-scoped across turns, and `MAXLEN`-style bounded retention keeps only the most recent window
  under sustained publication.
- `AuroraStreamControllerTest` — the live stream still emits the full typed lifecycle
  (`turn.started`/`turn.plan`/`bubble.*`/`token`/`meta`/`turn.completed`) with resumable ids, and the
  reconnect path now replays the original live events (not durable envelopes) with resumable ids.
- `ProductionStartupGuardTest` — prod boot is rejected when
  `inner-cosmos.aurora.stream.redis.enabled=false`.
- Frontend: `web` Vitest 15 files / 60 tests PASS; the reconnect path forwards non-`timeline.event`
  events to the same live `handleEvent` renderer.

## Docker-gated cross-instance proof

`RedisAuroraStreamingStoreIntegrationTest` (Testcontainers `redis:7.4.2-alpine`,
`@Testcontainers(disabledWithoutDocker = true)`) proves the real Redis backends across two
independent store instances: a stage token staged on one instance is consumed once, owner-bound, on
the other; a live event published on one instance is replayed on the other and a blocking reader on
the second instance unblocks when the first publishes the terminal event.

## Honest boundary

This closes cross-Pod private staging, resumable live fan-out, the `turn.started` resume race,
heartbeat and Redis-outage degradation for the Aurora turn stream, with a Docker-gated real-Redis
cross-instance proof. It does not yet include a sustained multi-Pod soak/throughput load run with
recorded latency/heartbeat percentiles, nor Redis Stream consumer groups / claim-based redelivery.
Live cross-Pod SSE therefore moves from "open" to builder-verified; `API-CONTRACT` remains
`IN_PROGRESS` pending the load-percentile soak and independent review.
