# Gemini bilingual direct-vs-full evaluation — final reliability run

Evidence ID: `INNO-EVAL-GEMINI-BILINGUAL-006`
Dataset SHA-256: `96f12ab65ef2dae218a563e0fb32f202fe12fc3b06c4c6f9617a45433f44f3ad`
Ledger SHA-256: `27bcee03222011fedc3b76ff45afe82f5669674868b0b01a3b4bc7158117131a`

## Machine-verifiable result

- Frozen paired items: `48` (`24 zh-CN`, `24 en-US`)
- Model on both paths: `gemini-3.6-flash`
- Direct Gemini: `48/48` successful, fallback `0`
- Full Aurora: `48/48` successful, business failure `0`, fallback `0`
- Direct visible-language match: `48/48`
- Full Aurora visible-language match: `48/48`
- Full Aurora automatic advice-boundary warnings: `0`
- Full Aurora automatic meta-AI phrase warnings: `0`

Latency:

| Path | p50 | p95 | max |
|---|---:|---:|---:|
| Direct Gemini | `8.349s` | `13.880s` | `16.965s` |
| Full Aurora | `3.695s` | `9.847s` | `15.148s` |

This run followed two failure-inclusive diagnostic runs:

- `-004` exposed three false Provider-failure templates caused by response
  de-duplication erasing valid near-duplicate segments.
- `-005` confirmed that metadata was now truthful and the original three
  cases recovered, but exposed four structured fallback values that the
  exception-only retry loop could not observe.
- `-006` ran after both defects were fixed. All historical ledgers remain in
  their original denominators.

## Claim boundary

The reliability and language gates pass. The effectiveness claim remains
`false` and the status remains `HUMAN_REVIEW_PENDING`.

Three independent bilingual reviewers must complete the blinded sheets before
unblinding. The preregistered preference claim requires Full Aurora to win at
least 60% of non-tied pairs with the Wilson 95% lower bound above 0.50, without
dimension regression. These machine results do not establish semantic
superiority or human preference.

## Artifacts

- `summary.json`: machine-readable aggregate
- `failure-inclusive-ledger.jsonl`: all 48 paired records
- `blind-review-R01.csv`, `R02.csv`, `R03.csv`: blinded human-review sheets
- `unblinding-key.json`: do not open before review completion
- `dataset.freeze.json`: exact frozen dataset
