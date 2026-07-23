# CN-ZERO-LOSS-DRAIN-002 — live re-proof of the SIGKILL-recovery fix

> Date: 2026-07-23 · Cluster: kind `kubedeploy` (context `kind-kubedeploy`), namespace
> `inner-cosmos-w3`, reusing the `deploy/k8s/overlays/kind-full` deployment left running by the
> prior session (real Postgres + real Redis, dev+Mock-AI, image `inner-cosmos:ec948b7` — already
> built from the exact commit containing both fixes under test, confirmed via
> `git merge-base --is-ancestor 6832043 2b8ce6b` / `... ec948b7 2b8ce6b` → both ancestors of this
> branch's base commit). Branch `codex/w3-cloud-2` off `codex/w0-integration` @ `2b8ce6b`, worktree
> `D:/code/inner-cosmos-w3-cloud-2`. This purely re-proves already-merged commits `6832043`
> (`ConversationTurnRecoveryJob` + `ConversationTimelineController` reconnect fix) and does not
> modify any product code.

## What this closes

`evidence/w3/CN-ZERO-LOSS-DRAIN-001/summary.md`'s Test 2 found: a direct `docker exec ... kill -9
<jvm-pid>` (bypassing Kubernetes' graceful termination entirely) left a conversation turn orphaned
at non-terminal `PLANNED` forever, and reconnecting via the durable replay endpoint — with either a
fresh cursor or the live stream's own `Last-Event-ID` — hung until curl's own watchdog/the 120s
`SseEmitter` timeout. Commit `6832043` added a scheduler job to settle such orphans and made the
reconnect controller return an explicit terminal event. That fix had only unit/component test
coverage until this session.

## Test setup

- Threshold override for a fast test cycle (config only, no code change — per this task's own
  instruction that shortening `stale-after` via config is expected and preferred over touching the
  job's logic):
  ```
  $ kubectl -n inner-cosmos-w3 set env deployment/inner-cosmos-scheduler \
      INNER_COSMOS_AURORA_TURN_RECOVERY_STALE_AFTER=PT20S \
      INNER_COSMOS_AURORA_TURN_RECOVERY_POLL_DELAY_MS=5000
  deployment.apps/inner-cosmos-scheduler env updated
  $ kubectl -n inner-cosmos-w3 rollout status deployment/inner-cosmos-scheduler --timeout=90s
  deployment "inner-cosmos-scheduler" successfully rolled out
  ```
  (`inner-cosmos.aurora.turn-recovery.stale-after` / `.poll-delay-ms` bind from these env vars via
  Spring Boot's standard relaxed binding; the scheduler-role pod is the only one where
  `ConversationTurnRecoveryJob`'s `@ConditionalOnExpression` is active, so this could not affect the
  API/worker pods.) **Reverted immediately after the test** (see end of this file) — the cluster is
  left exactly as the prior session configured it.
- Registered a real user via `POST /api/auth/register` (CSRF token from `GET /api/auth/csrf`,
  cookie jar maintained across requests, same flow as CN-ZERO-LOSS-DRAIN-001).
- Created dialog session id `5` (`userId=2`) via `POST /api/dialog/session/create`.
- Port-forwarded directly to the target API pod (`inner-cosmos-api-7469ffbfc7-dh2mv`, so the exact
  JVM host PID killed is known and deterministic) on `localhost:28080`, and separately to
  `svc/inner-cosmos-api` on `localhost:28081` to prove reconnect through the normal Service path
  (as a real client would, potentially landing on the surviving/replacement pod).
- Found the JVM's real host PID via `docker exec kubedeploy-control-plane crictl inspect
  <containerId>` (this kind node runs containers directly in the node's own process namespace, so
  `docker exec` on the node can signal them by host PID exactly as the original
  CN-ZERO-LOSS-DRAIN-001 test did).

## The fault injection: real SIGKILL mid-stream

```
$ date -u                                                     T0: 2026-07-23T05:34:54.947503500Z (curl SSE GET started)
... 0.12s later, curl still streaming tokens ...
$ docker exec kubedeploy-control-plane kill -9 52256
T1(before kill): 2026-07-23T05:34:55.234593800Z
T2(kill call returned): 2026-07-23T05:34:55.681567500Z
T3(curl exited): 2026-07-23T05:34:55.787016300Z
```

The SSE stream was genuinely cut mid-generation — the client's log stops at live sequence 8
(`token: "很漂"`), inside bubble 1, well before `bubble.completed`/`meta`/`turn.completed`:

```
id:7:live:0  event:turn.started      data:{"turnId":7}
id:7:live:1  event:turn.plan         data:{"turnId":7,"planId":7}
id:7:live:2  event:bubble.started    data:{"order":1}
id:7:live:3  event:token             data:{"content":"我在"}
id:7:live:4  event:token             data:{"content":"。"}
id:7:live:5  event:token             data:{"content":"你不"}
id:7:live:6  event:token             data:{"content":"用组"}
id:7:live:7  event:token             data:{"content":"织得"}
id:7:live:8  event:token             data:{"content":"很漂"}
<connection dropped, no further output>
```

Confirmed the kill actually landed on the JVM (not just a network blip):

```
$ kubectl -n inner-cosmos-w3 get pod inner-cosmos-api-7469ffbfc7-dh2mv \
    -o jsonpath='{.status.containerStatuses[0].restartCount}'
2
$ kubectl -n inner-cosmos-w3 get pod inner-cosmos-api-7469ffbfc7-dh2mv \
    -o jsonpath='{.status.containerStatuses[0].lastState}'
{"terminated":{"containerID":"...","exitCode":137,"finishedAt":"2026-07-23T05:34:55Z","reason":"Error", ...}}
```
(exit code 137 = killed by signal 9; `finishedAt` matches T1/T2 above to the second.)

## Durable state immediately after the crash (turn genuinely orphaned)

```sql
select id, session_id, status, started_at, updated_at, completed_at from tb_conversation_turn where id=7;
 id | session_id |  status  |         started_at         |         updated_at         | completed_at
----+------------+----------+----------------------------+----------------------------+--------------
  7 |          5 | PLANNED  | 2026-07-23 05:34:54.916652 | 2026-07-23 05:34:54.916826 |
```
(query run at 2026-07-23T05:35:15.37Z, ~20s after the crash — still non-terminal at this point,
consistent with the 20s stale-after threshold not having elapsed quite yet at poll time)

## Recovery: the scheduler job settles the orphan

Polled again shortly after:

```sql
select id, session_id, status, started_at, updated_at, completed_at from tb_conversation_turn where id=7;
 id | session_id |    status   |         started_at         |         updated_at         |        completed_at
----+------------+-------------+----------------------------+----------------------------+----------------------------
  7 |          5 | INTERRUPTED | 2026-07-23 05:34:54.916652 | 2026-07-23 05:34:54.916826 | 2026-07-23 05:35:16.874562
```

`completed_at` = 2026-07-23T05:35:16.87Z, i.e. ~21.96s after the turn's `updated_at`
(05:34:54.9168) — matching the configured 20s `stale-after` plus one ~5s poll cycle. The full event
trail confirms the exact mechanism (`ConversationTurnRecoveryJob` → `interruptIfStale` →
`cancelTurnLocked`):

```sql
select turn_id, event_sequence, event_type, created_at from tb_conversation_event where turn_id=7 order by event_sequence;
 turn_id | event_sequence |     event_type      |         created_at
---------+----------------+----------------------+----------------------------
       7 |              1 | TURN_CREATED         | 2026-07-23 05:34:54.918343
       7 |              2 | GENERATION_STARTED   | 2026-07-23 05:34:54.920291
       7 |              3 | PLAN_COMMITTED       | 2026-07-23 05:34:55.044092
       7 |              4 | BUBBLE_PLANNED       | 2026-07-23 05:34:55.12112
       7 |              5 | BUBBLE_CANCELLED     | 2026-07-23 05:35:16.890516
       7 |              6 | TURN_INTERRUPTED     | 2026-07-23 05:35:16.895995

select id,turn_id,bubble_order,status,delivered_chars,cancelled_at from tb_message_bubble where turn_id=7;
 id | turn_id | bubble_order |  status   | delivered_chars |        cancelled_at
----+---------+--------------+-----------+------------------+----------------------------
 12 |       7 |            1 | CANCELLED |               0 | 2026-07-23 05:35:16.874562
```

The REST timeline endpoint (`GET /api/aurora/turns/7/timeline`, a plain JSON call, not SSE — so it
cannot itself hang) confirms `BUBBLE_CANCELLED`/`TURN_INTERRUPTED` both carry
`"reason":"STREAM_ORPHANED_BY_RUNTIME_FAILURE"` — the exact literal the recovery job passes.

## Reconnect: explicit terminal event instead of a hang (both cursor styles)

**A. With the live stream's own `Last-Event-ID` header** (`7:8`, i.e. the exact live-numbering
cursor that CN-ZERO-LOSS-DRAIN-001 proved used to hang from the very first byte, through
`svc/inner-cosmos-api` — the normal client path):

```
$ date -u                                                      T0: 2026-07-23T05:35:41.274560200Z
$ timeout 10 curl -sN -b cookies.txt -H "Last-Event-ID: 7:8" \
    http://localhost:28081/api/aurora/turns/7/events
event:replay.completed
data:{"turnId":7,"lastSequence":8,"turnStatus":"INTERRUPTED"}
$ echo exit=$?                                                 exit=0
                                                                T_end: 2026-07-23T05:35:42.894235600Z
```

Completed in **1.62s**, well inside curl's 10s test timeout (previously: hung for the full 8s
watchdog with zero output at all). The response is an explicit, machine-readable terminal signal
(`turnStatus: INTERRUPTED`) instead of a silent multi-minute hang.

**B. Fresh reconnect, no stale header** (as a client would do on first reconnect after a dropped
connection):

```
$ date -u                                                      T0: 2026-07-23T05:36:24.935539300Z
$ timeout 10 curl -sN -b cookies.txt http://localhost:28081/api/aurora/turns/7/events
id:7:live:0 .. id:7:live:8   (replays the 9 buffered live events still cached in Redis, tokens 0-8)
event:replay.completed
data:{"turnId":7,"lastSequence":8,"turnStatus":"INTERRUPTED"}
                                                                T_end: 2026-07-23T05:36:26.116531700Z
```

Completed in 1.18s. Both reconnect styles now terminate immediately with the same explicit
`INTERRUPTED` signal; no content was fabricated or lost — the already-buffered live tokens are
replayed verbatim, then the stream honestly reports the turn's real terminal status.

## Result

Both parts of the gap this item was `PARTIAL`/`IN_PROGRESS` for are now confirmed live, not just
unit-tested:

1. A turn orphaned by a true abrupt process kill (SIGKILL bypassing Kubernetes' graceful
   termination) now reaches a terminal `INTERRUPTED` status on its own, settled by
   `ConversationTurnRecoveryJob` within (`stale-after` + one poll cycle) — proven at the mechanism
   level (a config-only threshold override, not a code change, was used to make the test
   practical; the mechanism exercised is identical code at any threshold).
2. Reconnecting — via either the exact `Last-Event-ID` cursor style that used to hang, or a fresh
   cursor — now gets an explicit `replay.completed` / `INTERRUPTED` event within ~1.2-1.6s instead
   of hanging for up to 120s with no explanation.

## What is still NOT proven / left open

- This test used a single-node kind cluster and a single killed API pod; it does not re-exercise
  multi-node topology spread (already a disclosed boundary in CN-ZERO-LOSS-DRAIN-001, unchanged).
- The default production `stale-after` (`PT5M`) itself was not run end-to-end at full length in
  this session (that would need a 5+ minute idle wait with no other change in mechanism) — only the
  shortened-threshold *mechanism* was proven; since the job's logic is identical regardless of the
  configured duration and the duration itself is straightforward config, this is a reasonable,
  disclosed scope reduction, not a hidden gap.
- The recovery job's own resilience under its *own* failure (e.g. scheduler pod itself crashing
  before it settles a turn) was not fault-injected this session — only the JVM-hosting-the-turn
  crash was tested, per the task's exact scenario.

## Cleanup

The threshold override was reverted immediately after the test so the cluster matches the prior
session's baseline configuration for whoever inspects it next:

```
$ kubectl -n inner-cosmos-w3 set env deployment/inner-cosmos-scheduler \
    INNER_COSMOS_AURORA_TURN_RECOVERY_STALE_AFTER- INNER_COSMOS_AURORA_TURN_RECOVERY_POLL_DELAY_MS-
deployment.apps/inner-cosmos-scheduler env updated
$ kubectl -n inner-cosmos-w3 rollout status deployment/inner-cosmos-scheduler --timeout=90s
deployment "inner-cosmos-scheduler" successfully rolled out
$ kubectl -n inner-cosmos-w3 get deploy inner-cosmos-scheduler -o jsonpath='{.spec.template.spec.containers[0].env}'
[{"name":"INNER_COSMOS_RUNTIME_ROLE","value":"scheduler"},{"name":"SERVER_PORT","value":"8082"}]
```
