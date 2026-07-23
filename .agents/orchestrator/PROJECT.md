# Project: Inner Cosmos Deep System & Vulnerability Audit

## Audit Milestones

| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1_R1_Spec_Alignment | Architectural & Spec Alignment Audit (`对齐文档/`, `goal-objective.md`, `src/`, `web/`) | none | IN_PROGRESS |
| 2 | M2_R2_Backend_Vulnerabilities | Backend Concurrency, Transactions, PostgreSQL/pgvector, Redis, Flyway (`src/`) | none | IN_PROGRESS |
| 3 | M3_R3_AI_Safety_Privacy | AI Safety, P0-P3 Privacy Partitioning, Prompt Injection, Safety Filters (`src/main/java/com/innercosmos/ai`, `safety`, `service`) | none | IN_PROGRESS |
| 4 | M4_R4_Frontend_E2E_Fault_Tolerance | Frontend React 19 / Vite, SSE/REST, Long Connection, State Sync, UX (`web/`) | none | IN_PROGRESS |
| 5 | M5_Master_Report | Master Synthesis & Audit Report Compilation | M1, M2, M3, M4 | PLANNED |

## Detailed Milestone Scope Definitions

### Milestone 1: R1. Architectural & Spec Alignment Audit
- Compare `对齐文档/`, `goal-objective.md` vs actual implementation (`src/`, `web/`).
- Identify missing boundary checks, state machine transition flaws, or logic conflicts with ultra-fast / slow-social design principles.

### Milestone 2: R2. Backend Concurrency, Transactions & Storage Scan
- Spring Boot 3.5.x + PostgreSQL/pgvector + Redis + Flyway.
- High concurrency race conditions, DB transaction isolation / deadlock risks, pgvector search performance/degradation, memory leaks, Redis & DB dual-write consistency.

### Milestone 3: R3. AI Safety, P0-P3 Privacy & Prompt Injection Audit
- Prompts for Aurora, Capsule, ThoughtShredder.
- `DataMaskingService` effectiveness under complex scenarios (ensuring P0 private chat doesn't bleed into P2/P3 social layers).
- `SafetyBoundaryFilter` boundary failures.

### Milestone 4: R4. Frontend & E2E Fault Tolerance Audit
- React 19 / Vite + REST/SSE interface handling.
- Connection drop & retry, SSE memory leaks, unhandled exceptions, state desynchronization, hidden UX traps.
