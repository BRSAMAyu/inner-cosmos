# INNO-CONV-003 — React Aurora interrupt vertical slice

Builder status: `EVALUATED`.

- React 19 + TypeScript strict + Vite production output is served from `/app/aurora/`.
- The legacy Aurora physical page remains available during progressive migration.
- The typed SSE decoder handles split chunks, CRLF, event IDs and malformed JSON.
- New messages are allowed during generation: the current turn is stopped and the
  new user message becomes the replanning input.
- Stop preserves delivered text as partial and prevents later bubbles.
- Durable owner-scoped `Last-Event-ID` replay is available at
  `/api/aurora/turns/{turnId}/events`; the React client uses it before timeline polling.
- A browser-level fault test cuts the live SSE body immediately after `turn.started`.
  Generation now continues against the durable turn even when the HTTP response detaches,
  and the client restores the committed bubbles from the owner-scoped timeline without
  duplicating the user message.
- Authentication/session bootstrap no longer renders an apparently ready composer before
  the dialog session exists.
- A fresh database now initializes Aurora's complete stable Self profile before the
  first conversation, fixing the browser-discovered NOT NULL failure without reducing
  Self/Constitution/Emergence semantics.

The acceptance ledger stays honest: G3 and AURORA-CHOREOGRAPHY remain
`IN_PROGRESS` because this is one migrated journey rather than the five-space shell,
and the choreography packages still require independent review before verification.
