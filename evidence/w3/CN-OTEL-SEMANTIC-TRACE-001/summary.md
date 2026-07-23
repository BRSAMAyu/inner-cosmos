# CN-OTEL-SEMANTIC-TRACE-001

Status: **PASS on local kind**

Date: 2026-07-23  
Cluster: `kind-kubedeploy`, Kubernetes `v1.36.1`  
Application image: `inner-cosmos@sha256:bc3540707380be7f42f3a8ea3f53ecf8bbe6dd268d23d90a205846cbdd967f54`  
Collector: `otel/opentelemetry-collector-contrib:0.156.0`  
Backend: `cr.jaegertracing.io/jaegertracing/jaeger:2.20.0`

## Implemented trace contract

- Spring/Micrometer observations bridge to OpenTelemetry and export over OTLP/HTTP.
- runtime roles have separate service resources: `inner-cosmos-api`, `inner-cosmos-worker`,
  `inner-cosmos-scheduler`.
- the Aurora provider span measures the actual provider/runtime call.
- memory retrieval has its own span.
- transactional outbox rows propagate a W3C `traceparent` only; no baggage or private data.
- the worker starts a `CONSUMER` span from that remote parent.
- memory and profile projection spans are children of the consumer.
- a due WakeIntent creates `inner.cosmos.wake-intent.deliver`.
- the Collector deletes known sensitive attributes again as defense in depth before export.

The first live attempt found a real configuration defect: `kind-full` activates `dev,postgres`, so
aliases defined only in `application-prod.yml` did not enable export. The overlay now binds the
direct `MANAGEMENT_OTLP_*`, sampling and resource-attribute properties. A second fresh journey
proved export.

## Live Aurora-to-projection trace

An isolated account was registered through the real CSRF/session contract, then a dialog was
created, an `ACTION_SPLIT` Aurora message was sent, and the dialog was finished. Jaeger returned
services for all three runtime roles.

Selected trace:

```text
trace=a86c0453fff1b3db621614c1702ab994
inner-cosmos-api|http post /api/dialog/session/{id}/finish
  -> inner-cosmos-api|secured request
    -> inner-cosmos-worker|inner.cosmos.outbox.consume
      -> inner-cosmos-worker|inner.cosmos.projection.memory
      -> inner-cosmos-worker|inner.cosmos.projection.profile
span count: 8
forbidden attribute keys: 0
```

The separate Aurora request trace contained:

```text
http post /api/aurora/message-rich
aurora.turn
inner.cosmos.ai.provider
inner.cosmos.memory.retrieve
```

The consumer did not merely share a trace ID by coincidence: its parent span ID was the API
`secured request` span, and both projection spans named the consumer as their parent.

## Live WakeIntent trace

A due `DEMO_CHECKIN` intent was created using the API's Singapore local-wall-clock contract. The
real scheduler claimed and delivered it. Jaeger returned one matching trace containing:

```text
task wake-intent-delivery-job.run
inner.cosmos.wake-intent.deliver
forbidden attribute keys: 0
```

## Privacy and failure controls

Application span attributes are bounded enums, booleans, counts or operation names. The selected
traces were scanned for:

`user.id`, `enduser.id`, `message.content`, `gen_ai.prompt`, `gen_ai.completion`, `db.statement`,
`http.request.body`, `url.query`, `userId`, `message`, `prompt`, `content`.

Result: **0 forbidden keys**. The Collector repeats deletion of the primary semantic-convention
keys. Export is disabled by default outside an explicitly configured environment; the showcase
uses 100% sampling while production defaults to 10%.

## Honest boundaries

- Jaeger uses ephemeral in-memory storage in this local showcase; production requires durable,
  access-controlled retention and an approved retention period.
- This proves application propagation and semantic visibility on kind, not Academy add-on
  availability.
- Trace content privacy is machine-checked by key contract and Collector stripping; operational
  review must continue when new instrumentation is added.

