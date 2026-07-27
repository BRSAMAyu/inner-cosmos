# Pre-fix finding — English input is answered in Chinese

Status: **VALID DEFECT EVIDENCE / NOT EFFECTIVENESS EVIDENCE**

This run completed all 48 real-output pairs without a request failure:

- 24 frozen semantic prompts in `zh-CN` and semantically matched `en-US`;
- direct native Gemini and the live full-Aurora product path ran at nearly the
  same wall-clock time;
- direct Gemini matched the requested language in 48/48 outputs;
- full Aurora matched Chinese in 24/24 outputs, but matched English in **0/24**.

The English mismatch is visible in the immutable
`failure-inclusive-ledger.jsonl`; it is not inferred from a human or LLM judge.
For example, the English `quiet-boundary` input received:

> 明天的展示和现在这份紧张已经连在一起了。最绷着你的那一处，还可以继续说。

The live container reported `LLM_PROVIDER=gemini`,
`GEMINI_MODEL=gemini-3.6-flash`, `LLM_PROMPT_LANGUAGE=auto`, and fallback was
disabled. However, this run was executed before the contemporaneous
`SessionModelRouter` cross-user preference lookup repair. The runner also had
not yet pinned the per-session provider. Therefore:

1. keep this run as the pre-fix bilingual defect baseline;
2. do not use it to claim same-model quality superiority;
3. rerun the unchanged frozen dataset after the repaired image is deployed;
4. the revised runner pins every evaluation session to `GEMINI`, eliminating
   the legacy profile-preference ambiguity.

Human preference remains unscored. `effectiveness_claim=false`.
