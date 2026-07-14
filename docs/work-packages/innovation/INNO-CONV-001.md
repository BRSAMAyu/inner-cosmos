# INNO-CONV-001 Claim Record

```yaml
package: INNO-CONV-001
status: EVALUATED
owner: Codex Goal Mode
reviewer: unassigned
branch: feat/run006-aurora-self-understanding
worktree: D:\\code\\inner cosmos
base_sha: e903776f372070dd09cc99bbfa226bc704697cfb
started_at: 2026-07-15T00:00:00+08:00
owned_paths:
  - src/main/java/com/innercosmos/conversation/**
  - src/main/java/com/innercosmos/mapper/Conversation*.java
  - src/main/java/com/innercosmos/mapper/TurnPlanMapper.java
  - src/main/java/com/innercosmos/mapper/MessageBubbleMapper.java
  - src/main/java/com/innercosmos/mapper/GenerationAttemptMapper.java
  - src/main/java/com/innercosmos/controller/ConversationTimelineController.java
  - src/main/java/com/innercosmos/service/impl/AuroraAgentServiceImpl.java
  - src/main/java/com/innercosmos/vo/AuroraReplyVO.java
  - src/main/resources/schema.sql
  - src/test/java/com/innercosmos/conversation/**
  - src/test/java/com/innercosmos/controller/AuroraStreamControllerTest.java
  - docs/work-packages/innovation/README.md
  - docs/work-packages/innovation/INNO-CONV-001.md
  - docs/goal/complete-product-acceptance.yml
  - evidence/innovation/INNO-CONV-001/**
evidence: evidence/innovation/INNO-CONV-001/
integrated_sha: null
```

## Frozen pre-change behavior

- Aurora keeps the existing generation path, prompt inputs, Self/Constitution/Emergence,
  portrait, relationship, proactive and safety behavior.
- A normal turn emits one to three `segments`; `multiMessageAllowed=false` still limits it
  to one.
- Existing SSE content reaches the current browser through its already-supported
  explicit `token` listener; rendered behavior remains unchanged.
- This package adds persistence and observability; interruption/replanning remains owned by
  `INNO-CONV-002`.

## Builder evaluation

- Evidence: `evidence/innovation/INNO-CONV-001/`
- Builder implementation and evaluation are complete.
- Independent reviewer is still required before `VERIFIED` or `INTEGRATED`.
