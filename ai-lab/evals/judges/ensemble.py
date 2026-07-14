from __future__ import annotations

from evals.judges.base import Judge
from evals.models import JudgeAnnotation, SystemRun


class JudgeEnsemble:
    def __init__(self, judges: list[Judge]):
        self.judges = judges

    def evaluate(self, run: SystemRun) -> list[JudgeAnnotation]:
        return [judge.evaluate(run) for judge in self.judges]

    @staticmethod
    def aggregate(annotations: list[JudgeAnnotation]) -> dict[str, object]:
        completed = [item for item in annotations if item.status == "COMPLETED"]
        judge_types = {item.judge_type for item in completed}
        scores: dict[str, list[float]] = {}
        for item in completed:
            for name, value in item.scores.items():
                scores.setdefault(name, []).append(value)
        return {
            "status": "INSUFFICIENT_INDEPENDENT_JUDGES" if len(judge_types) < 2 else "COMPLETED",
            "judge_types": sorted(judge_types),
            "scores": {name: sum(values) / len(values) for name, values in scores.items()},
            "decision_allowed": len(judge_types) >= 2,
        }

