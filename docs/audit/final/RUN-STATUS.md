# Inner Cosmos — Final Perfection Run (RUN-FINAL)

Autonomous run kicked off 2026-06-23 via `/loop 15m keep pushing until the end`.
**Goal:** audit → master report → implement everything → verify → fix → final-verify → DONE. Make the project perfect & *complete* (no room to improve). Fully realize the vision in `inner_cosmos_愿景文档` / `工程总纲`.

## Hard facts
- **Git: NO remote configured** → `git push` is impossible. Current state was committed locally (checkpoint) on branch `feat/run006-aurora-self-understanding`. To push: add a remote (`git remote add origin <url>`) — **pending a URL from the user**.
- **Loop:** recurring job `30d563f0`, every 15 min, prompt `keep pushing until the end`. Auto-expires in 7 days. Cancel sooner: `CronDelete 30d563f0`.

## Phases
1. **[Phase 1 — IN PROGRESS]** 5-expert deep audit (2+2+1). Four independent experts (Backend/Data, AI/Prompt, Frontend/UX, Safety/Ops) each fan out subagents across their aspects; then Expert 5 synthesis critic reads all four and builds the master list. Outputs: `docs/audit/final/01..04-*.md` + `05-critic-synthesis.md`. Workflow name: `inner-cosmos-final-audit`.
2. **[Phase 2]** Supervisor writes `docs/audit/final/MASTER-POLISH-REPORT.md` — the ultimate report (drawbacks + what to improve + how). Guides the final term.
3. **[Phase 3]** Implement everything in the master report (perfect + add missing features to fully realize the vision). Sub-tasks created after the report.
4. **[Phase 4]** 3 independent experts inspect the implementation in detail.
5. **[Phase 5]** Fix all remaining problems found.
6. **[Phase 6]** 2 final agents inspect the repair work → END.

## How to resume (for /loop wake-ups & new sessions)
- Read THIS file first, then `TaskList`.
- Phase 1 outputs land in `docs/audit/final/`. If the audit workflow was interrupted, resume via `Workflow({ scriptPath, resumeFromRunId })` (journal cached) or relaunch.
- Keep tests green: `mvn test`. Memory note: M2_HOME gotcha — use `.tools/apache-maven-3.9.9` or `scripts/run-dev.ps1`. Green floor was ~606 tests; never regress.
- Commit per completed phase on the feature branch. **Do NOT deploy / publish / charge / exfiltrate** — implementation + local commits + (future) push only. Those are hard-stop boundaries.
- **Runtime verification (user requirement — project must be proven actually runnable & workable):** build + run the Spring Boot app (`mvn spring-boot:run`, H2 default; use `.tools/apache-maven-3.9.9` / `scripts/run-dev.ps1` to dodge the M2_HOME gotcha), then exercise it for real — `curl` the APIs, fetch pages via webReader, and use the `run`/`verify` skills (browser-driven) to walk core journeys (register → login → Aurora chat → memory → starfield → capsule → plaza → slow-letter). Browser-test deeply in Phase 4 & Phase 6; smoke-test when writing the master report.

## Independence contract (per user)
Experts 1–4 must NOT see each other's output. Expert 5 sees all four and critically inspects them. Enforced by the workflow's wave structure.

## Live notes
- **Audit workflow RUNNING:** Run ID `wf_91785a38-3f5` (task `w1pwjdv9f`). 21 agents across 3 waves. Outputs → `docs/audit/final/01..05-*.md`. Watch: `/workflows`. Resume if interrupted: `Workflow({ scriptPath: ".../inner-cosmos-final-audit-wf_91785a38-3f5.js", resumeFromRunId: "wf_91785a38-3f5" })`.
- **Vision brief PENDING:** agent for `docs/audit/final/00-vision-brief.md` died on a 529 capacity error before doing any work. RETRY when API capacity frees (Phase-2 prep, not critical path). Alternative: read the vision docs directly during Phase 2.
- **App booting in background** (`mvn spring-boot:run`, H2) for real-world browser testing. Health check: `curl http://localhost:8080/pages/index.html`. Default login: demo/demo123.
