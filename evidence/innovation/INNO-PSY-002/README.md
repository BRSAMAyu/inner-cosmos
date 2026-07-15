# INNO-PSY-002 — First Psychology Skills

Date: 2026-07-15
Status: BUILDER_VERIFIED / IN_PROGRESS

Three bounded first Skills are usable in the React product: emotion/needs clarification, values compass, and decision-conflict mapping. Each has bilingual copy, evidence references, limitations, exact inputs, no external tools, deterministic uncertainty-aware output, crisis escalation, explicit consent, retention choice, revocation, and a versioned evaluation suite.

Aurora integration is suggestion-only. A suggestion explains the explicit wording that triggered it, creates no run, and still requires the user to open Skill Studio and separately consent. Ordinary unrelated chat and crisis input do not produce Skill suggestions.

The React surface can switch between `zh-CN` and `en-SG`. Titles, limitations, questions, retention, consent, execution locale, results and Aurora continuation remain aligned; `psychology-skill-studio-en.png` is a real-browser record.

## Skill / no-Skill blind comparison

Run from `ai-lab`:

```powershell
python -m evals.cli.main psychology-compare --output ../evidence/innovation/INNO-PSY-002 --seed 20260715
```

The command creates nine reproducible pairs across ordinary, ambiguous and English scenarios. `psychology-no-skill-pairwise.csv` hides system identity and leaves preference plus five review dimensions blank. Give reviewers only that CSV; do not give them `psychology-no-skill-key.json` until ratings are frozen. The key is retained for later analysis, not for review.

After every rating cell is frozen, run:

```powershell
python -m evals.cli.main psychology-compare-score `
  --ratings ../evidence/innovation/INNO-PSY-002/psychology-no-skill-pairwise.csv `
  --key ../evidence/innovation/INNO-PSY-002/psychology-no-skill-key.json `
  --output ../evidence/innovation/INNO-PSY-002/psychology-human-score.json
```

The scorer rejects incomplete or invalid ratings, unblinds by pair, and reports preference/dimension/safety counts. It deliberately keeps `effectivenessClaim=false`.

This package compares offline reference outputs that mirror Skill v1 with a frozen ordinary-Aurora reflection baseline. It does not call a Provider or use real user data. `psychology-comparison-report.json` therefore records `PENDING_HUMAN_REVIEW` and `effectivenessClaim=false`.

## Evidence

- Runtime, release, safety and browser evidence: `../INNO-PSY-001/`.
- Offline contract report: `../INNO-PSY-001/psychology-contract-report.json`.
- Suggest-only visual: `../INNO-PSY-001/aurora-suggest-only.png`.
- Skill result visual: `../INNO-PSY-001/psychology-skill-studio.png`.
- English Skill visual: `../INNO-PSY-001/psychology-skill-studio-en.png`.
- Blind review sheet: `psychology-no-skill-pairwise.csv`.
- Blinding key and machine report: `psychology-no-skill-key.json`, `psychology-comparison-report.json`.

## Remaining acceptance

- Psychology-domain expert review and non-author blind experience are still pending.
- The bilingual UI and runtime are builder-tested, but have not yet passed a non-author language review.
- A comparative no-Skill package is ready, but no frozen human ratings exist, so product-experience uplift is not established.

This evidence therefore supports implementation progress, not `SKILL-PRODUCT = PASS`.
