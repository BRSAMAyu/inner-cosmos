# Living Aurora real-Provider quality analysis

Status: `REAL_PROVIDER_EXECUTED / HUMAN_PAIRWISE_PENDING`

Two same-model pairwise runs completed with real Provider calls and no fallback. Inputs are synthetic
contract trajectories; this evidence does not contain credentials or raw endpoints. Endpoint origins
are represented only by hashes in the machine reports.

| Model | Prompt contract | Observations / calls | Single deterministic pass | Dual deterministic pass | Single latency total / median | Dual latency total / median | Input tokens A / B | Output tokens A / B |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| GLM-5.2 | v3 | 11 / 33 | 5/11 | 5/11 | 202649 / 16125 ms | 482502 / 42987 ms | 802 / 4924 | 7617 / 18124 |
| MiMo v2.5 | v3.1 | 11 / 33 | 6/11 | 5/11 | 104580 / 9286 ms | 293633 / 28529 ms | 831 / 4636 | 3636 / 14274 |

The deterministic checker is a constraint diagnostic, not an empathy or preference judge. The
blind sheets remain source-blind and require two independent reviewers before any measured-benefit
claim. Neither run currently establishes dual-kernel superiority.

## Failure analysis and repair

The GLM v3 dual Self response included language equivalent to Aurora waiting for the user. That can
create dependency pressure and violates the intended non-anthropomorphic relationship boundary.
Prompt contract v3.1 now explicitly forbids claims that Aurora waits for, misses, needs, or depends
on the user. A post-hoc deterministic rescore of the unchanged GLM responses is retained separately
as `real-provider-runs-rescored-v3.1.json`; it reports 6/11 versus 10/11, is marked
`post_hoc_calibration=true`, and explicitly sets `effectiveness_claim=false`. It is rubric calibration,
not a replacement model run and not evidence of improvement.

MiMo v2.5 was then executed with v3.1. Its failures cluster in relationship repair, proactive fatigue,
memory correction and the three-day continuity trajectory. Those outputs must remain visible to blind
reviewers; they are not converted to known limitations or removed from the sheet.

## Reproduction and human gate

- Machine reports and blind sheets: `glm-5.2-v3/` and `mimo-v2.5-v3.1/`.
- The report asserts `provider_called=true` and `fallback_used=false`.
- Provider identity, prompt hash, trajectory hash, request metadata, latency and token counts are
  retained; credentials and raw endpoint origins are not.
- Run `real-pairwise-score` only after two reviewers independently freeze complete rating sheets.
