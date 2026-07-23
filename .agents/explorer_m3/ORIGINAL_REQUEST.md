## 2026-07-22T23:58:43Z
You are an Explorer subagent assigned to Milestone M3: R3 AI Safety, P0-P3 Privacy & Prompt Injection Audit for the Inner Cosmos project.
Your assigned working directory is `d:\code\inner cosmos\.agents\explorer_m3`.

### Task Objective:
Thoroughly audit AI agent interactions (Aurora, EchoCapsule, ThoughtShredder, etc.), Data Privacy Layer isolation (P0 raw chat, P1 structured memory, P2 capsule social, P3 public social), DataMaskingService, Prompt injection defense, and Safety Boundary Filters.

### Specific Audit Focus Areas:
1. **P0 to P2/P3 Privacy Bleed & DataMasking Deficiencies**:
   - Audit `DataMaskingService` and all data sanitization logic. Test against complex scenarios (e.g., indirect PII, embedded sensitive metadata, structured json prompts).
   - Check if P0 (raw dialog messages in `tb_dialog_session` / `tb_dialog_message`) can accidentally leak into P2 (EchoCapsule persona prompts, shared memory cards) or P3 (Slow Letters, Starfield Square).
   - Trace how EchoCapsules retrieve memory context: can an EchoCapsule created by User A leak User A's private P0 details during limited-round chat with User B?
2. **Prompt Injection & System Prompt Security**:
   - Audit all `PromptBuilder`, prompt templates, system instructions, and user input injection vectors across Aurora, Capsule, and ThoughtShredder strategies.
   - Check if malicious user inputs can bypass system instructions, leak system prompts, override safety boundaries, or hijack AI behavior.
3. **Safety Boundary Filter & Content Moderator Bypasses**:
   - Audit `SafetyBoundaryFilter`, `CrisisKeywordRule`, `AbuseKeywordRule`, and rule chain implementations.
   - Look for regex bypasses, homoglyphs, unicode evasions, multi-turn prompt context manipulation, or failure to handle streaming (SSE) chunk-level violations.

### Instructions:
- Inspect files under `src/main/java/com/innercosmos/ai/`, `safety/`, `service/`, `util/`, `prompt/`, etc.
- Document every finding with:
  1. Exact File Path and Line Numbers.
  2. Root Cause Analysis.
  3. Scenario Reproduction / Failure Deductions.
  4. Impact Assessment (High/Medium/Low).
  5. Exact Recommended Fix / Code Refactoring.
- Write your comprehensive investigation report to `d:\code\inner cosmos\.agents\explorer_m3\handoff.md`.
- Send a summary message back to the orchestrator when finished.
