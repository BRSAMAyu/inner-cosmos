---
name: workflow-state
description: Master workflow document tracking all phases, tasks, progress, decisions, and agent assignments
---

# Inner Cosmos - Master Workflow State

> Last updated: 2026-06-02 Session Start
> Status: **EXPLORATION PHASE**

---

## 0. Execution Principles

1. Git: regular commits per subtask, format `[STAGE-Xn] description`
2. Workflow doc updated after each significant change
3. Stage review agents dispatched in background after each stage completes
4. All changes require full understanding of affected code before execution
5. Max 3 parallel agents; core work done by main session
6. Exploration results in `docs/exploration/`; reviews in `docs/review/`

## 1. Phase Overview

| Phase | Scope | Owner | Status | Agent |
|-------|-------|-------|--------|-------|
| A | Agent kernel (MockLlmClient, ContentLibrary, fallbacks, safety, PromptBuilder) | Main | PENDING | - |
| B | Psychology APIs (theme clustering, beliefs, relations, emotion timeline) | Agent-B | PENDING | worktree |
| C | Frontend UIUX (dynamic theme, animations, page redesigns, dashboard) | Agent-C | PENDING | worktree |
| D | Engineering depth (agent upgrades, multi-agent chain, strategies) | Main | PENDING | - |

## 2. File Ownership Boundaries

### Main Session (Phase A + D)
- `src/main/java/com/innercosmos/ai/` (all files - agents, clients, prompts, strategies, structured)
- `src/main/java/com/innercosmos/safety/` (all files)
- `src/main/java/com/innercosmos/service/impl/` (modifications to existing files)
- `src/main/java/com/innercosmos/service/` (interface changes)
- `src/test/` (test updates)

### Agent-B Worktree (Phase B - new files primarily)
- New: `src/main/java/com/innercosmos/service/impl/BeliefExtractServiceImpl.java`
- New: `src/main/java/com/innercosmos/service/impl/RelationNetworkServiceImpl.java`
- New: `src/main/java/com/innercosmos/service/impl/EmotionTimelineServiceImpl.java`
- New: `src/main/java/com/innercosmos/service/BeliefExtractService.java`
- New: `src/main/java/com/innercosmos/service/RelationNetworkService.java`
- New: `src/main/java/com/innercosmos/service/EmotionTimelineService.java`
- New: `src/main/java/com/innercosmos/controller/PsychologyController.java`
- New: `src/main/java/com/innercosmos/entity/` (new entity files for beliefs, relations)
- New: `src/main/java/com/innercosmos/mapper/` (new mapper files)
- Modify: `src/main/java/com/innercosmos/service/impl/ThemeAggregationServiceImpl.java`
- Modify: `src/main/resources/schema.sql` (add new tables)

### Agent-C Worktree (Phase C - frontend only)
- `src/main/resources/static/css/` (all CSS)
- `src/main/resources/static/js/` (all JS)
- `src/main/resources/static/pages/` (all HTML pages)
- `src/main/resources/static/index.html`

## 3. Task Progress

### Phase A Tasks
| # | Task | Status | Commit |
|---|------|--------|--------|
| A1 | Create sentiment lexicon + PseudoSemanticAnalyzer | PENDING | - |
| A2 | Refactor MockLlmClient + AuroraContentLibrary | PENDING | - |
| A3 | Upgrade all service fallbacks | PENDING | - |
| A4 | Upgrade SafetyBoundaryFilter | PENDING | - |
| A5 | PromptBuilder Chinese localization | PENDING | - |

### Phase B Tasks
| # | Task | Status | Commit |
|---|------|--------|--------|
| B1 | Theme clustering with LLM | PENDING | - |
| B2 | Belief extraction | PENDING | - |
| B3 | Relation network | PENDING | - |
| B4 | Emotion timeline API | PENDING | - |

### Phase C Tasks
| # | Task | Status | Commit |
|---|------|--------|--------|
| C1 | Dynamic day/night theme | PENDING | - |
| C2 | Motion/animation system | PENDING | - |
| C3 | Aurora chat redesign | PENDING | - |
| C4 | Memory starfield clustering | PENDING | - |
| C5 | Capsule personality | PENDING | - |
| C6 | Slow letter ceremony | PENDING | - |
| C7 | Dashboard upgrade | PENDING | - |

### Phase D Tasks
| # | Task | Status | Commit |
|---|------|--------|--------|
| D1 | Agent upgrades (Memory/Capsule/Aurora) | PENDING | - |
| D2 | Multi-agent chain + Strategy completion | PENDING | - |
| D3 | Prompt version management | PENDING | - |
| D4 | Token estimation + cost control | PENDING | - |

## 4. Key Decisions Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-06-02 | Day=Morandi, Night=warm-dark (auto sunset switch) | User preference: comfortable dark, not cold tech |
| 2026-06-02 | "Flowing" visual theme with dynamic time/weather | User preference for organic, fluid aesthetics |
| 2026-06-02 | 3-agent parallel: Main(A+D), Agent-B(B), Agent-C(C) | File ownership isolation prevents conflicts |

## 5. Review Results

| Stage | Reviewer | Status | Report |
|-------|----------|--------|--------|
| - | - | - | - |

## 6. Risk Register

| Risk | Mitigation |
|------|-----------|
| Agent worktree merge conflicts | Strict file ownership boundaries |
| Context compression losing state | This document + exploration docs |
| Breaking existing tests | Review tests before changing fallbacks |
| Mock mode must stay functional | All changes preserve mock fallback |
