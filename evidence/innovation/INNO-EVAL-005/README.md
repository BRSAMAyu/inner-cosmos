# INNO-EVAL-005 — real-provider dual-kernel reliability: DeepSeek clean, GLM schema-drift

## What this is

A real-LLM verification (the operator's explicit "use real LLM to make sure everything works well")
of the Aurora dual-kernel against the operator's real chat providers, run on the current integrated
HEAD via `TrackARealProviderSmokeEvaluationTest` (the A1 schema-embedding regression gate).

## Method

```
# DeepSeek (clean)
DEEPSEEK_API_KEY=<from API及文档.txt> ./mvnw test -Dtest=TrackARealProviderSmokeEvaluationTest -DexcludedGroups=

# GLM (drift)
GLM_API_KEY=<from API及文档.txt>      ./mvnw test -Dtest=TrackARealProviderSmokeEvaluationTest -DexcludedGroups=
```

The test drives each provider through the REAL `LlmClient` → dual-kernel (plan → speaker → critic)
pipeline over fixed scenarios and counts `badOutputEventsInThisCall`: the number of times the real
provider's JSON did NOT parse against the expected schema on the first attempt (requiring the
structured-AI repair retry, and if that too fails, surfacing a deterministic fallback instead of the
model's own words). The A1 fix (an inline JSON schema example embedded in the plan/speaker
instructions) is the permanent gate intended to keep this at **0**.

## Result (2026-07-24, current HEAD)

| provider | model | result | badOutputEvents (first-attempt JSON drift) |
|---|---|---|---|
| DeepSeek | deepseek-chat | **PASS (3/3)** | **0** |
| GLM | glm-4-flash | **FAIL** | **3** per dual-kernel run |

- **DeepSeek** parses cleanly on the first attempt every time → the visible reply is the model's
  real words, no fallback. This confirms the prior INNO-EVAL-004 DeepSeek result still holds on the
  integrated HEAD (the voice-feature work did not regress the dual-kernel prompts).
- **GLM (glm-4-flash)** fails first-attempt JSON parsing ~3× per run despite the inline schema
  example being present in the prompts (verified: `AuroraDualKernelRuntime.planInstruction()` and
  `speakerInstruction()` still embed the `{"...":"..."}` schema example; commit 7a3e73b only added
  the additive inner-voice overload and did not touch those prompts). So this is a **provider
  model behavior, not a code regression**: GLM is materially more prone to schema drift than
  DeepSeek on the same prompts. The repair retry may still recover the visible output, but a demo
  on GLM risks periodically surfacing deterministic-fallback text instead of GLM's real words.

## Decision (demo)

The in-class demo (`scripts/demo/run-demo-server.sh`) defaults to **DeepSeek** (proven 0 drift) so
classmates always see real, model-generated replies. GLM remains selectable via
`DEMO_PROVIDER=glm`, with a startup warning about the known drift. `LLM_ALLOW_FALLBACK=false` is set
so a provider failure surfaces visibly (never a silent Mock substitution) during the demo.

## Honest scope — what is proven vs not

**Proven:** on the current HEAD, DeepSeek runs the full real dual-kernel with zero first-attempt
schema drift (3/3 scenarios); GLM exhibits ~3 first-attempt drifts per run on the same prompts
(provider-specific). The voice-feature work did not regress the dual-kernel prompts.

**Not proven / open:** the GLM drift is not root-caused to a fixable prompt change (the A1 schema
example is already present); a GLM-specific prompt tweak or a more lenient GLM JSON parser could
reduce it, but that is non-trivial provider tuning, not a defect — tracked as a follow-up, not
blocking the demo (DeepSeek is the demo provider). A larger statistical sample (more than 3
scenarios) and blind human-rated reply quality are the standing real-provider human gates.
