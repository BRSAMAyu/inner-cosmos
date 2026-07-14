# Known limitations

- `INNO-CONV-001` records completed choreography. Mid-stream cancellation, discarded
  attempts, partial-delivery recovery and replanning are intentionally deferred to
  `INNO-CONV-002`.
- The schema is added to the repository's current `schema.sql` authority. PostgreSQL
  and Flyway production truth belong to G2 and are not claimed by this package.
- Database uniqueness provides the cross-replica commit invariant. A losing concurrent
  caller receives a constraint failure rather than transparent winner-result recovery;
  transparent race recovery can be added with the G2 idempotency contract.
- No real-provider quality or production latency benchmark was run because this adapter
  changes no prompt/model behavior and the external credential gate remains blocked.
- Builder evaluation is not independent verification. A different reviewer must inspect
  the evidence and implementation before the package can become `VERIFIED`.
