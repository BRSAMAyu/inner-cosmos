# Invalid run: concurrent load contamination

This run was interrupted and is excluded from all formal bilingual quality,
latency and fallback conclusions.

Reason: it overlapped with the public `30 users x 50 sandbox sessions` burst
against the same Gemini-backed Demo runtime and Provider quota. Responses and
latencies therefore cannot be attributed to the bilingual treatment alone.

The frozen dataset and partial failure-inclusive ledger are retained for
auditability. They must not be deleted, resampled into another run, or merged
into the formal scoring denominator.
