# INNO-EVAL-001 sample report

- Report: `inno-eval-001-75877171bbd8`
- System: `current-production-contract`
- Provider/model: offline fixture / none
- Human pairwise: NOT_RUN
- Real provider: BLOCKED_BY_CREDENTIAL_GATE

## Aggregate metrics

- `schema_validity`: 1.000000
- `expected_bubble_count_accuracy`: 1.000000
- `stale_bubble_after_cancel_rate`: 0.000000
- `interruption_success_rate`: 1.000000
- `duplicate_committed_bubble_rate`: 0.000000
- `wake_duplicate_rate`: 0.000000
- `unauthorized_memory_recall_rate`: 0.000000
- `privacy_leakage_rate`: 0.000000
- `evidence_traceability`: 1.000000
- `held_out_leakage`: 0.000000
- `role_confusion_marker_rate`: 0.000000
- `visitor_echoing_indicator_rate`: 0.000000
- `inter_capsule_lexical_distinctiveness`: 0.395137
- `style_feature_distance`: 0.020000
- `response_length_mean`: 28.479167
- `punctuation_emoji_discourse_similarity`: 0.980000
- `latency_ms_mean`: 34.020833
- `token_usage_mean`: 6.479167
- `model_call_count_mean`: 0.000000
- `fallback_rate`: 0.000000

## Latency and cost

- Runs: 48
- Model calls: 0
- Estimated provider cost: USD 0.0000

## Privacy and safety

No real user data or Provider call was used. Hard gates: `{"evidence_traceability": true, "held_out_leakage": true, "interruption_success_rate": true, "privacy_leakage_rate": true, "schema_validity": true, "stale_bubble_after_cancel_rate": true, "unauthorized_memory_recall_rate": true, "wake_duplicate_rate": true}`.

## Ablations, failures, human review

Future candidates and human blind review are NOT_RUN. This contract fixture proves harness reproducibility, not model quality or system superiority.

## Reproducibility

`python -m evals.cli.main run --output <directory>` at Git `75877171bbd82a32980233b17860b457d02dc97e`.
