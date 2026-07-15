# Campaign A — Living Aurora executable Experience Contract checkpoint

Status: `BUILDER_VERIFIED / IN_PROGRESS`

Date: 2026-07-15

## User-visible closure

The React Aurora space now consumes the backend's authenticated proactive SSE channel. A live hint
never becomes the source of truth: it causes the client to reload durable notifications, removes the
fulfilled agreement from the active list, and displays the returned Aurora card without a refresh.
Malformed hints are ignored. EventSource reconnect remains best-effort while the notification row
survives disconnects and restarts.

The dedicated packaged-JAR browser contract proves this real path:

```text
natural-language agreement
  -> persisted WakeIntent with dialog anchor
  -> shortened test window (no direct delivery or DB shortcut)
  -> real scheduled lease claim
  -> relevance + quiet-window + safety decision
  -> transactional durable notification
  -> authenticated proactive SSE hint
  -> React notification refresh
  -> owner-scoped deep link restores original dialog
  -> MATCHED feedback persists and dismisses the arrival
```

`scripts/run-living-aurora-experience.ps1` packages the application, starts a clean H2 instance with
scheduling enabled and runs the contract on port 8081. The first builder run passed `1/1` in 8.8s
(`28.3s` including application startup).

## Longitudinal quality contract

`living-aurora-pairwise.v3.1` keeps the existing eight interruption/boundary trajectories and adds a
cumulative three-day report-anxiety trajectory: day 1 accepts an interruption and negotiates a
return; day 2 recognizes that a new event resolved the old intent; day 3 explains a candidate Self
change, evidence and reversibility. It compares the same model and sampling settings across the
single-pass baseline and planner→speaker runtime.

The source-blind sheet independently scores both sides on felt understanding, interruption
acceptance, continuity/boundary, proactive appropriateness and Self continuity. The scorer requires
two named independent reviewers, complete pair coverage, response integrity, at least 60% dual-core
preference excluding ties, no dimension regression and all three longitudinal days. Invalid or
incomplete input raises an error rather than producing a winner.

## Honest boundary

- GLM-5.2 and MiMo v2.5 each completed 11 observations / 33 real calls without fallback. The blind
  review is not complete, deterministic checks show meaningful failures, and no AI quality advantage
  is claimed.
- The scheduler E2E proves durable in-app delivery and authenticated live fan-out on a packaged local
  system. APNs/FCM receipts and real-device behavior remain external credential/device gates.
- Builder verification is not non-author blind acceptance. `AURORA-QUALITY`, `AURORA-TEMPORAL`,
  `AURORA-SELF` and the Campaign A gate remain `IN_PROGRESS` until those reviews are complete.

## Non-author review handoff

Give each reviewer a fresh copy of `non-author-review-template.csv` and the five user-visible
scenarios in `docs/campaigns/living-aurora/experience-contract.md`; do not provide implementation
notes. Combine the completed files only after each reviewer freezes their answers, then run:

```powershell
cd ai-lab
python -m evals.cli.main experience-score `
  --ratings reviewer-01.csv reviewer-02.csv `
  --output ../evidence/campaign-a/LIVING-AURORA-001/non-author-review-report.json
```

The gate requires two independent complete reviews, every scenario completed without help, means
of at least 4/5 for continuity, proactive appropriateness and recovery confidence, an articulated
unique value in every row, and zero safety blockers. The blank template is evidence of readiness,
not evidence of a completed human review.
