# Aurora bilingual direct-vs-full real-Gemini evaluation

Status: **HUMAN_REVIEW_PENDING**. `effectiveness_claim=false`.

- Frozen cases: 48 (24 semantic prompts x 2 languages)
- Model on both arms: `gemini-3.6-flash`
- DIRECT: native Gemini call with a neutral assistant system prompt.
- FULL_AURORA: current product `/api/v1/aurora/message-rich` path.
- Seed: `20260728`
- Every success/failure is retained in `failure-inclusive-ledger.jsonl`.
- Automated language/boundary checks are diagnostics only.

Give exactly one `blind-review-R0x.csv` to each independent reviewer. Reviewers must
not see `unblinding-key.json`. Freeze all three complete files before scoring.
Do not claim Aurora or bilingual superiority from `summary.json`.
