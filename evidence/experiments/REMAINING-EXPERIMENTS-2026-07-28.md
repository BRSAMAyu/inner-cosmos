# Remaining experiment closure — 2026-07-28

This index separates reproducible evidence from claims that still require
human review.

## Verified now

| Experiment | Result | Primary evidence |
|---|---|---|
| H2 KEDA | PASS: worker desired `1 -> 3 -> 6`; available `3` at `39.222s`; `1200/1200` receipts; duplicates `0`; cleanup PASS | `evidence/w3/CN-THREE-HERO-SHOWCASE-001/fresh-rerun-2026-07-28.md` |
| H3 OTel trace | PASS: trace `34f2a2dcf9b0c39fcdebbc68c93c3324`; 2 services, 21 application spans, Provider `13.9736s`, forbidden tags `0` | same fresh-rerun file |
| Public 30-user burst | Two clean PASS runs: 30/30 Gemini Aurora, HTTP 429 `0`, 50/50 isolated sandboxes, discovery/ring 30/30, cleanup complete | `PUBLIC-BURST-30X50-001/run-07-report.json`, `run-08-report.json` |
| Memory retrieval | In-memory p95 `204.23ms`; PostgreSQL+Redis p95 `315.95ms`; timeout/budget/leak/miss all `0` | `P1-MECHANISM-RERUN-2026-07-28/summary.md` |
| Memory authority | correction/withdrawal 2/2; naive baseline exposed stale/withdrawn memory as expected | same P1 summary |
| Proactive policy | quiet-hours, long-gap and preference-change 3/3 | same P1 summary |
| Capsule mechanisms | dynamic mechanism 5/5; groundedness leak 0; runtime selection accuracy 1.0; live Gemini replies 6/6 | same P1 summary |
| Bilingual reliability | Final frozen run: Direct 48/48; Full Aurora 48/48; fallback/business failure 0; CN/EN visible-language match 48/48 | `evidence/innovation/INNO-EVAL-GEMINI-BILINGUAL-006/RESULTS.md` |

## Failure-inclusive repair trail

- Public burst run-01/run-02: TLS/sandbox infrastructure failures.
- run-03: Quick Tunnel reached the stale WSL port-8080 runtime.
- run-04/run-05: the Demo unlimited flag was dead configuration; login-IP
  buckets caused 429. The final filter now bypasses only application quotas in
  non-prod Demo mode. Authentication, CSRF, privacy and crisis gates remain;
  prod forces rate limiting.
- Bilingual `-002`: English Full Aurora replies were 0/24 language matches.
  The output-language contract now follows the latest user-authored language.
- `-003`: invalidated due concurrent load contamination and excluded.
- `-004`: 3/48 Full Aurora business failures exposed valid replies erased by
  near-duplicate filtering and falsely labelled as Provider recovery.
- `-005`: the original three cases recovered, while 4/48 honestly labelled
  structured fallbacks exposed an exception-only retry loop that never retried
  fallback values.
- `-006`: after both repairs, Full Aurora completed 48/48 with zero fallback.
- Final-code burst run-09: 30/30 Gemini, HTTP 429 `0`, Aurora p95
  `7.186s`, 50/50 isolated sandboxes, discovery/ring 30/30, cleanup complete,
  and zero critic fallback, stage failure or business failure. This final smoke
  confirms the retry repair did not regress capacity/isolation; run-07/run-08
  remain the two-run capacity evidence.

No failed run was deleted or removed from its original denominator.

## Human-gated conclusions

Do not claim that Full Aurora is semantically better than Direct Gemini yet.
The three blinded reviewer sheets exist, but all three bilingual reviewers
must score them before unblinding and applying the preregistered threshold.

Do not claim capsule persona fidelity/distinctiveness from the 6/6 live
Provider smoke; that result only establishes real-Provider execution and
structured variation.

## Current Demo

- Local: `http://127.0.0.1:8082/app/aurora/`
- Public: `https://participating-beverly-susan-saint.trycloudflare.com/app/aurora/`
- Runtime self-test: PASS, Gemini 3.6 Flash, no fallback
- Verified third-party short link: none. A generated CleanURI link returned
  404 and is deliberately excluded; is.gd/TinyURL rejected the temporary
  Cloudflare hostname.

For a stable short address, configure a Cloudflare Named Tunnel with an owned
short domain. Until then, use a QR code generated after final preflight.
