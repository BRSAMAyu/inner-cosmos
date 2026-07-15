# Campaign A system-role and pairwise-v2 checkpoint

Date: 2026-07-15

## Product effect

Aurora's identity, safety, non-diagnosis, anti-injection and memory authorization boundary is no
longer embedded in a user message. `PromptBuilder` exposes immutable system and dynamic user parts;
`StructuredAiService` removes the system boundary from serialized context and passes it via
`LlmRequest.systemPrompt`; MiniMax, GLM, DeepSeek and OpenAI-compatible clients use that value for
their provider `role=system` message. Legacy non-Aurora calls retain their existing fallback system
instruction.

The real-provider pairwise harness now covers eight Living Aurora trajectories and produces prompt
and trajectory hashes, per-response deterministic constraint checks, aggregate pass rates, source-
blind human review rows, model identity, latency, tokens and request ids. It remains fail-closed when
the three ephemeral credential values are absent and never substitutes Mock.

## Reproducible verification

- Java focused runtime/prompt gate: `76 tests, 0 failures, 0 errors`.
- Full Java regression: `777 tests, 0 failures, 0 errors, 0 skipped`.
- React Vitest: `3/3 PASS`; TypeScript/Vite production build: `PASS`.
- Full Playwright Experience Contract regression: `9/9 PASS` in `38.3s`.
- `python -m unittest discover -s tests -p "test_*.py"`: `39 tests, PASS`.
- `python -m evals.cli.main validate`: `48 scenarios, PASS`.
- `python -m evals.cli.main run --output ../target/evaluation/innovation-system-role --seed 20260715`:
  `192 runs`, `80 metrics`, `hard_gates=PASS`.

## Honest gate

`REAL_PROVIDER_BASE_URL`, `REAL_PROVIDER_API_KEY` and `REAL_PROVIDER_MODEL` are not configured in
this process. No provider was called, no live output exists, and no runtime-quality winner is
claimed. A completed source-blind human review and independent re-review remain required.
