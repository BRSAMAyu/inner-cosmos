# H2/H3 fresh rerun — 2026-07-28

This is a failure-inclusive supplemental rerun on the live classroom kind
environment. It does not replace `final-results-2026-07-28.json`; it records
the later rehearsal executed after the public Demo runtime was separated from
the stale WSL port-8080 service.

## Environment gate

- Kubernetes context: `kind-kubedeploy`
- Namespace: `inner-cosmos-w3`
- API, worker, scheduler, Prometheus, Grafana, Jaeger, OTel collector and KEDA
  ScaledObject: Ready
- Durable outbox baseline: `0`
- Preflight result: `PREFLIGHT_READY`

## H2 KEDA

- Result: `KEDA_SCALE_OUT_PASS`
- Workload: `1200` synthetic durable outbox events
- Initial worker: desired/available `1/1`
- Observed desired replicas: `1 -> 3 -> 6`
- Gate result: desired `6`, available `3` at `39,222 ms`
- Final publication receipts: `1200/1200`
- Duplicate receipts: `0`
- Cleanup: PASS
- Worker after cleanup: `1`
- Synthetic rows after cleanup: `0`

Bounded claim: on this single-node local kind environment, the durable
business backlog caused KEDA scale-out to at least three available workers
inside the 40-second machine gate and drained without duplicate inbox
receipts. This is not an AWS production-capacity or cost-optimality claim.

## H3 OpenTelemetry

- Result: `HERO_3_PASS`
- Trace ID: `34f2a2dcf9b0c39fcdebbc68c93c3324`
- Scene duration: `36,041 ms`
- Client end-to-end: `19,605 ms`
- Traced request: `19,567.7 ms`
- Memory retrieval: `6.5 ms`
- Gemini Provider: `13,973.6 ms` (`71.4%`)
- Platform overhead: `5,587.6 ms`
- Worker consume: `8,867.7 ms`
- Memory projection: `4,551.3 ms`
- Profile projection: `4,301.6 ms`
- Services: `inner-cosmos-api`, `inner-cosmos-worker`
- Application spans at pass: `21`
- Forbidden privacy tags: `0`
- Test-account cleanup: PASS

Bounded claim: one controlled W3C trace linked a real Gemini-backed Aurora
request to memory retrieval and the asynchronous outbox/worker projections,
with latency attribution and zero tags forbidden by the checked privacy
contract. This does not prove semantic answer superiority or durable
production trace retention.
