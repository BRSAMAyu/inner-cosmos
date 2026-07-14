from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Any


@dataclass(frozen=True)
class EvaluationScenario:
    id: str
    family: str
    split: str
    title: str
    input: dict[str, Any]
    expected: dict[str, Any]
    provenance: str
    purpose: str
    sensitivity: str
    license: str


@dataclass(frozen=True)
class DatasetManifest:
    id: str
    version: str
    splits: dict[str, list[str]]
    sources: list[dict[str, str]]
    prohibited_data: list[str]


@dataclass(frozen=True)
class SystemUnderTest:
    id: str
    version: str
    availability: str
    provider: str
    model: str
    prompt_version: str
    policy_version: str
    genome_version: str | None = None
    blocked_reason: str | None = None


@dataclass(frozen=True)
class ConversationEvent:
    event_id: str
    event_type: str
    turn_id: str
    bubble_id: str | None = None
    committed: bool = False
    discarded: bool = False
    evidence_refs: tuple[str, ...] = ()
    payload: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class CostRecord:
    input_tokens: int
    output_tokens: int
    model_calls: int
    estimated_cost_usd: float
    latency_ms: int


@dataclass(frozen=True)
class SystemRun:
    run_id: str
    scenario_id: str
    git_sha: str
    system: SystemUnderTest
    temperature: float
    seed: int
    started_at: str
    input_split: str
    events: tuple[ConversationEvent, ...]
    responses: tuple[str, ...]
    memory_refs: tuple[dict[str, Any], ...]
    context_artifacts: tuple[str, ...]
    cost: CostRecord
    failure_status: str = "NONE"
    fallback_status: str = "NONE"


@dataclass(frozen=True)
class HumanAnnotation:
    scenario_id: str
    blind_pair_id: str
    annotator_id: str
    naturalness: int | None = None
    felt_understanding: int | None = None
    timing: int | None = None
    self_continuity: int | None = None
    capsule_identity_fidelity: int | None = None
    capsule_style_fidelity: int | None = None
    capsule_behavior_fidelity: int | None = None
    preference: str | None = None
    reason: str | None = None


@dataclass(frozen=True)
class JudgeAnnotation:
    scenario_id: str
    judge_type: str
    status: str
    judge_model: str | None
    judge_version: str
    prompt_version: str | None
    scores: dict[str, float]
    rationale: str | None = None


@dataclass(frozen=True)
class MetricResult:
    name: str
    value: float
    direction: str
    hard_gate: float | None
    passed: bool | None
    numerator: float
    denominator: float
    details: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class EvaluationReport:
    schema_version: int
    report_id: str
    generated_at: str
    configuration: dict[str, Any]
    dataset_manifest: dict[str, Any]
    aggregate_metrics: list[dict[str, Any]]
    pairwise_results: dict[str, Any]
    latency_and_cost: dict[str, Any]
    ablations: list[dict[str, Any]]
    failure_cases: list[dict[str, Any]]
    privacy_and_safety: dict[str, Any]
    human_review: dict[str, Any]
    reproducibility: dict[str, Any]


@dataclass(frozen=True)
class EvidenceManifest:
    package_id: str
    source_sha: str
    generated_at: str
    artifacts: tuple[dict[str, str], ...]
    status: str


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def to_dict(value: Any) -> dict[str, Any]:
    return asdict(value)

