# Parallel Complete-Product Tracks

This directory is the execution entry point for the two large parallel PRs defined by
[`对齐文档/19-双轨并行完全体收敛与交接计划.md`](../../对齐文档/19-双轨并行完全体收敛与交接计划.md).

| Track | Product outcome | Execution spec | Agent prompt | Mutable status |
|---|---|---|---|---|
| A | Living intelligence, memory/profile truth, capsule fidelity, psychology skills, data rights | [`TRACK-A-LIVING-INTELLIGENCE.md`](TRACK-A-LIVING-INTELLIGENCE.md) | [`track-a-codex-prompt.md`](../goal/prompts/track-a-codex-prompt.md) | [`track-a-status.yml`](../goal/tracks/track-a-status.yml) |
| B | Complete five-space experience, PWA/mobile, bilingual/a11y, local-complete and demo | [`TRACK-B-COMPLETE-EXPERIENCE.md`](TRACK-B-COMPLETE-EXPERIENCE.md) | [`track-b-codex-prompt.md`](../goal/prompts/track-b-codex-prompt.md) | [`track-b-status.yml`](../goal/tracks/track-b-status.yml) |

`97500a385a1fa1a5f0e4c3bbb6e2c1b0c895ec61` is the audited implementation baseline. Both tracks must branch from the same current `main` that contains the minimum handoff commit `7e0688308292cd9d41552c98f576d25681738be0`, then record that shared actual HEAD as `base_sha`. They must not edit the global acceptance ledger or global session cursor. Track-local discoveries and evidence go into the track-local status/contract files so the final integrator can reconcile both PRs without losing truth.

The machine-readable split, ownership rules, gates, and merge order are in
[`two-track-convergence.yml`](../goal/two-track-convergence.yml).
