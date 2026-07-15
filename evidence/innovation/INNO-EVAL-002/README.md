# INNO-EVAL-002 — real Provider interrupt pairwise

The executable harness is implemented and unit-tested. The live run is truthfully
`BLOCKED_BY_CREDENTIAL_GATE`: no approved real Provider endpoint/key/model pair is
present in this process, so no model was called and no Mock result was substituted.

When the three ephemeral environment values in `gate-status.json` are supplied, the
harness runs four synthetic interrupt/replan trajectories against one configured model,
holding model and sampling settings constant while comparing single-pass with the real
planner→speaker runtime. It emits `real-provider-runs.json` plus a source-blind review CSV.
It never stores the key or raw endpoint and it cannot claim a winner before human
pairwise review is completed.
