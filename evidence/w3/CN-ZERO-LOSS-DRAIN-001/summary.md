# CN-ZERO-LOSS-DRAIN-001 - real pod-delete fault injection during a live multi-bubble Aurora SSE turn

> Date: 2026-07-23 - Cluster: kind `kubedeploy`, namespace `inner-cosmos-w3`
> (`deploy/k8s/overlays/kind-full`, real Postgres/Redis, dev+Mock-AI profile, image
> `inner-cosmos:w3-dev`). Two API replicas behind `svc/inner-cosmos-api`, Redis-backed
> HTTP sessions (`REDIS_SESSION_ENABLED=true`) so a killed pod's session is still valid
> from the surviving replica. This is a genuinely two-sided result: the primary claim
> (deleting a pod during a live turn loses nothing) is proven; a real, disclosed boundary
> for an abrupt process crash is also proven and left open rather than hidden.

## Setup

- Registered a real user via `POST /api/auth/register` (CSRF token fetched first via
  `GET /api/auth/csrf`, session cookie jar maintained across requests).
- Created dialog sessions via `POST /api/dialog/session/create`.
- Drove the live SSE turn via `GET /api/aurora/stream?sessionId=..&message=..&mode=COMPANION`
  with a message containing "shi le hen duo" (slow-down trigger words) to get a
  2-bubble Mock-AI reply (verified via `AuroraContentLibrary.buildReply`), each bubble
  dripped token-by-token with a 30ms/2-char delay plus a 220ms inter-bubble pause -
  enough real wall-clock window to land a fault injection mid-stream.
- Port-forwarded directly to individual pods (`kubectl port-forward pod/<name> <port>:8080`)
  so the exact pod serving a given SSE connection is known and can be targeted
  deterministically, and separately to `svc/inner-cosmos-api` to prove reconnect through
  the normal Service path (as a real client would).

## Test 1 - the actual hero-gate scenario: delete the API pod during a live multi-bubble turn

```
$ date -u                                                    T0: 2026-07-23T03:46:51.076Z   (curl SSE GET started)
... 0.35s later, curl still running ...
$ kubectl -n inner-cosmos-w3 delete pod inner-cosmos-api-...-nwvls --grace-period=0 --force
T1: 2026-07-23T03:46:51.799Z   (delete issued, curl still streaming)
pod "inner-cosmos-api-...-nwvls" force deleted
T2: 2026-07-23T03:46:52.337Z   (delete call returned)
T3: 2026-07-23T03:46:53.256Z   (curl exited on its own - the response actually completed)
```

Result: despite the pod being deleted (even with `--force --grace-period=0`), Kubernetes'
own kubelet-driven graceful termination still ran the container through its normal
`terminationGracePeriodSeconds: 45` / `preStop: sleep 15` / `server.shutdown: graceful`
sequence rather than an instant SIGKILL (a well-known `kubectl delete --force` quirk: it
removes the API object immediately but does not itself bypass the container's own grace
period) - so the in-flight SSE generation was allowed to finish normally. Checked the
durable state after completion:

```sql
select id, session_id, status from tb_conversation_turn where session_id=2;
 id | session_id |  status
----+------------+-----------
  2 |          2 | COMPLETED

select id,turn_id,bubble_order,status,delivered_chars,dialog_message_id from tb_message_bubble where turn_id=2;
 id | turn_id | bubble_order |  status   | delivered_chars | dialog_message_id
----+---------+--------------+-----------+------------------+-------------------
  3 |       2 |            1 | COMMITTED |               33 |                 5
  4 |       2 |            2 | COMMITTED |               34 |                 6

select id,session_id,speaker,length(text_content) as len from tb_dialog_message where session_id=2 order by id;
 id | session_id | speaker | len
----+------------+---------+-----
  4 |          2 | USER    |  20
  5 |          2 | AURORA  |  33
  6 |          2 | AURORA  |  34
```

Exactly 3 dialog messages (1 user + 2 Aurora bubbles), both bubbles COMMITTED exactly
once with correct character counts, turn status COMPLETED. Zero duplication, zero loss,
despite the serving pod being deleted mid-turn. The Deployment controller's self-healing
also worked as expected - a replacement pod was rescheduled to restore the desired
replica count of 2 within seconds:

```
$ kubectl -n inner-cosmos-w3 get pods -l app.kubernetes.io/component=api
inner-cosmos-api-...-cznjj   0/1   Running   0     17s   (new replacement pod, still starting)
inner-cosmos-api-...-wbc5v   1/1   Running   0     7m58s (surviving original pod)
```

**Recovery time**: effectively zero from the client's perspective - the same request
that was in flight when the pod was deleted completed normally without any visible
interruption, thanks to readiness-based drain (the deleted pod stops receiving NEW
Service traffic immediately) + graceful shutdown (the terminating pod's already-open
connection is allowed to finish) + PDB (`minAvailable: 1`, never violated since only one
pod was removed at a time) + the Deployment's own replacement scheduling.

## Test 2 - the harder, disclosed boundary: an abrupt process kill mid-stream

A normal `kubectl delete pod` respects the container's grace period, which is the
*intended* production behavior for rolling updates and routine pod churn. To find the
edge of what "zero loss" actually covers, I also simulated a true abrupt failure (e.g.
what an OOM-kill or a hard node crash would look like) by sending `SIGKILL` directly to
the JVM's host PID via `docker exec` into the kind node - bypassing Kubernetes'
termination sequence entirely (no SIGTERM, no preStop, no grace period):

```
$ date -u                                                     T0: 2026-07-23T03:49:11.710520Z (curl SSE GET started)
... 0.08s later ...
$ docker exec kubedeploy-control-plane kill -9 5265
T1: 2026-07-23T03:49:11.976596Z  (kill issued)
T2: 2026-07-23T03:49:12.535229Z  (kill call returned)
T3: 2026-07-23T03:49:13.478836Z  (curl exited - connection actually dropped)
```

This time the SSE stream WAS genuinely cut mid-generation - the client's log stops at
sequence 13 (`token: "xian zai"`), inside bubble 1, well before `bubble.completed`,
`meta`, or `turn.completed`. Checked the durable state:

```sql
select id, session_id, status from tb_conversation_turn where id=4;
 id | session_id |  status
----+------------+---------
  4 |          4 | PLANNED

select turn_id, event_sequence, event_type from tb_conversation_event where turn_id=4 order by event_sequence;
 turn_id | event_sequence |     event_type
---------+----------------+--------------------
       4 |              1 | TURN_CREATED
       4 |              2 | GENERATION_STARTED
       4 |              3 | PLAN_COMMITTED
       4 |              4 | BUBBLE_PLANNED
       4 |              5 | BUBBLE_PLANNED

select id,turn_id,bubble_order,status,delivered_chars from tb_message_bubble where turn_id=4;
 id | turn_id | bubble_order |  status  | delivered_chars
----+---------+--------------+----------+------------------
  7 |       4 |            1 | PLANNED  |               0
  8 |       4 |            2 | PLANNED  |               0
```

Good news first: the full text of BOTH bubbles was already durably written to
`tb_message_bubble.content` as part of upfront planning (the Mock/real-provider reply is
fully computed before the "streaming" drip even starts), so no CONTENT is actually lost -
it is just not yet marked delivered when the process died.

The real, disclosed gap: the turn is left at status `PLANNED` (not a terminal status)
forever, with no process left to drive it forward, and nothing currently reconciles
that. Reconnecting exactly like a real client would (via the durable replay endpoint,
through the Service so it can land on the surviving pod) demonstrates the actual user-
facing consequence:

```
$ curl -sN -b cookies.txt http://localhost:18081/api/aurora/turns/4/events
id:4:1
event:turn.plan
...
id:4:13
event:token
data:{"content":"xian zai"}
<no further output - the request hangs>
```
(exit code 124 - i.e., curl's own 8s watchdog timeout; the request never completes or
errors on its own. The `SseEmitter` itself has a 120s server-side timeout, so a real
client would sit for up to two full minutes with a spinner and then just silently drop,
with zero explanation to the user that the turn failed.)

Also tried reconnecting with the live stream's own `Last-Event-ID: 4:13` header (as the
frontend actually would): that hangs from the very first byte, with NOTHING replayed at
all - because the live stream's raw per-token sequence numbers (0..42+) and the durable
timeline's coarse per-milestone sequence numbers (1..5) are two different numbering
spaces, so a live-stream cursor of 13 skips past every durable event (all <= 5) and the
endpoint just waits for new events that will never arrive.

**This is a genuine remaining gap, not covered up**: zero-loss-drain is proven for the
scenario the hero gate is actually about (pod churn during rolling updates / scale-down /
routine `kubectl delete pod`, which is what readiness-drain + graceful shutdown + PDB +
topology spread are built for). It is NOT yet proven - and in fact is disproven - for a
true abrupt crash (hard OOM-kill, node failure, `kill -9`) that bypasses Kubernetes'
graceful termination protocol entirely. Recommended follow-up (not implemented this
session): a scheduler-driven reconciliation pass that marks a turn `INTERRUPTED` once it
has sat in a non-terminal status with no active generation attempt heartbeat past some
threshold, so a client reconnecting after a true crash gets a fast, explicit failure
signal (and can retry) instead of a silent multi-minute hang; and unifying the live-
stream and durable-timeline sequence numbering (or translating between them) so a
`Last-Event-ID` from the live stream is meaningful to the durable replay endpoint.

## What this required from the k8s manifests (all in `deploy/k8s/overlays/kind-full`)

- `readinessProbe`/`livenessProbe` split (see CN-CREDIBILITY-001, item 6.1-1) so the
  deleted pod stops receiving new Service traffic immediately without a false liveness
  restart racing the graceful shutdown.
- `terminationGracePeriodSeconds: 45` + `preStop: sleep 15` + `server.shutdown: graceful`
  (already present in `application.yml`) so an in-flight SSE response gets to finish.
- `PodDisruptionBudget minAvailable: 1` (present, never triggered/violated since this
  test only ever removed one pod at a time).
- `topologySpreadConstraints` (present, but this kind cluster has exactly one node, so it
  is a no-op here; a genuine multi-node topology-spread proof needs a multi-node cluster
  or real academy-eks, neither exercised this session - disclosed boundary).
- Real Postgres-backed durable timeline (`tb_conversation_turn`/`tb_conversation_event`/
  `tb_message_bubble`) and real Redis-backed HTTP session + live-event buffering - this is
  exactly why `kind-full` (not `kind-dev`'s per-pod H2 file) was built for this test: with
  per-pod H2, killing the pod would have destroyed the ONLY copy of the turn's data,
  making it impossible to distinguish "the mechanism preserved state" from "there was
  never anything to lose in the first place."
