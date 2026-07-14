from __future__ import annotations

from evals.judges.base import Judge
from evals.models import JudgeAnnotation, SystemRun


class OptionalLlmJudge(Judge):
    """Metadata-only gate. Provider execution must be supplied by a future approved adapter."""

    def __init__(self, enabled: bool = False, model: str | None = None):
        self.enabled = enabled
        self.model = model

    def evaluate(self, run: SystemRun) -> JudgeAnnotation:
        if not self.enabled:
            return JudgeAnnotation(
                scenario_id=run.scenario_id, judge_type="optional-llm", status="NOT_RUN",
                judge_model=None, judge_version="llm-judge-interface-v1", prompt_version="judge-rubric-v1",
                scores={}, rationale="No approved provider adapter/credential; no Mock fallback was used.",
            )
        raise RuntimeError("LLM judge requires an explicitly approved provider adapter; direct key use is forbidden")

