from evals.judges.base import Judge
from evals.models import JudgeAnnotation, SystemRun


class DeterministicOfflineJudge(Judge):
    def evaluate(self, run: SystemRun) -> JudgeAnnotation:
        committed = sum(1 for event in run.events if event.committed)
        traced = all(event.evidence_refs for event in run.events if event.committed)
        return JudgeAnnotation(
            scenario_id=run.scenario_id,
            judge_type="deterministic-offline",
            status="COMPLETED",
            judge_model=None,
            judge_version="offline-rules-v1",
            prompt_version=None,
            scores={"has_committed_output_or_silence": float(committed > 0 or not run.responses), "evidence_traceability": float(traced)},
            rationale="Rule-based structural checks only; not a quality preference judgment.",
        )

