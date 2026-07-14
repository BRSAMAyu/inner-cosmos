from __future__ import annotations

import hashlib
from pathlib import Path

from evals.adapters.base import SystemAdapter
from evals.models import (
    ConversationEvent,
    CostRecord,
    EvaluationScenario,
    SystemRun,
    SystemUnderTest,
    utc_now,
)


class ContractDriftError(RuntimeError):
    pass


class CurrentProductionContractAdapter(SystemAdapter):
    """Offline contract/fixture adapter. It never starts Spring or calls a Provider."""

    CONTRACTS = {
        "prompt_segments": ("src/main/java/com/innercosmos/ai/prompt/PromptBuilder.java", "segments"),
        "multi_message_policy": ("src/main/java/com/innercosmos/service/impl/AuroraAgentServiceImpl.java", "multiMessageAllowed"),
        "multi_message_context": ("src/main/java/com/innercosmos/ai/context/AgentContextAssembler.java", "multiMessageAllowed"),
        "proactive_push_wait_schedule": ("src/main/java/com/innercosmos/ai/proactive/AliveDecisionEngine.java", 'case "push"'),
        "private_timer": ("src/main/java/com/innercosmos/entity/PrivateTimer.java", "fireAt"),
        "aurora_constitution": ("src/main/java/com/innercosmos/service/AuroraConstitutionService.java", "AuroraConstitutionService"),
        "aurora_reflection": ("src/main/java/com/innercosmos/entity/AuroraSelfReflection.java", "class AuroraSelfReflection"),
        "aurora_self_model": ("src/main/java/com/innercosmos/entity/AuroraSelfModel.java", "class AuroraSelfModel"),
        "aurora_emergence_trigger": ("src/main/java/com/innercosmos/ai/self/UserTriggeredSelfReflection.java", "UserTriggeredSelfReflection"),
        "user_portrait": ("src/main/java/com/innercosmos/entity/UserPortrait.java", "class UserPortrait"),
        "capsule_agent": ("src/main/java/com/innercosmos/ai/agent/CapsuleAgent.java", "class CapsuleAgent"),
    }

    def __init__(self, repository_root: Path):
        self.repository_root = repository_root

    @property
    def system(self) -> SystemUnderTest:
        return SystemUnderTest(
            id="current-production-contract",
            version="source-contract-v1",
            availability="AVAILABLE_CONTRACT_FIXTURE",
            provider="offline-fixture",
            model="none",
            prompt_version="production-contract-observed",
            policy_version="production-contract-observed",
        )

    def verify_contracts(self) -> dict[str, str]:
        result: dict[str, str] = {}
        for name, (relative, marker) in self.CONTRACTS.items():
            path = self.repository_root / relative
            if not path.exists() or marker not in path.read_text(encoding="utf-8"):
                raise ContractDriftError(f"production contract missing: {name}")
            result[name] = relative
        return result

    def run(self, scenario: EvaluationScenario, git_sha: str, seed: int) -> SystemRun:
        self.verify_contracts()
        expected = scenario.expected
        bubble_count = int(expected.get("bubble_count", 1))
        turn_id = f"turn-{scenario.id.lower()}"
        events: list[ConversationEvent] = [
            ConversationEvent(f"{turn_id}-accepted", "turn.accepted", turn_id, evidence_refs=(f"scenario:{scenario.id}",))
        ]
        responses: list[str] = []
        for index in range(bubble_count):
            bubble_id = f"b{index + 1}"
            events.append(ConversationEvent(
                f"{turn_id}-{bubble_id}", "bubble.completed", turn_id, bubble_id,
                committed=True, evidence_refs=(f"scenario:{scenario.id}",),
                payload={"decision": expected.get("decision", "stop")},
            ))
            responses.append(f"offline fixture response {scenario.id} {bubble_id}")
        if expected.get("interruption_success"):
            events.append(ConversationEvent(f"{turn_id}-interrupt", "user.interrupted", turn_id, evidence_refs=(f"scenario:{scenario.id}",)))
        if expected.get("discard_attempt"):
            events.append(ConversationEvent(f"{turn_id}-discard", "attempt.discarded", turn_id, discarded=True, evidence_refs=(f"scenario:{scenario.id}",)))
        if expected.get("decision") in {"push", "wait", "schedule", "reschedule", "silence"}:
            events.append(ConversationEvent(
                f"{turn_id}-wake", f"wake.{expected['decision']}", turn_id,
                evidence_refs=(f"scenario:{scenario.id}",),
            ))
        memory_refs = tuple(
            {"id": item, "authorized": True, "evidence_ref": f"scenario:{scenario.id}"}
            for item in expected.get("authorized_memory_refs", [])
        )
        stable = int(hashlib.sha256(f"{scenario.id}:{seed}".encode()).hexdigest()[:8], 16)
        latency = 20 + stable % 30
        tokens = sum(len(text.split()) for text in responses)
        return SystemRun(
            run_id=f"contract-{scenario.id}-{seed}", scenario_id=scenario.id, git_sha=git_sha,
            system=self.system, temperature=0.0, seed=seed, started_at=utc_now(),
            input_split=scenario.split, events=tuple(events), responses=tuple(responses),
            memory_refs=memory_refs, context_artifacts=("contract-fixture-v1",),
            cost=CostRecord(tokens // 2, tokens, 0, 0.0, latency),
        )
