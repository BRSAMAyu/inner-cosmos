from __future__ import annotations

from dataclasses import replace
from pathlib import Path

from evals.adapters.current_production import CurrentProductionContractAdapter
from evals.models import EvaluationScenario, SystemRun, SystemUnderTest


class OfflineBaselineAdapter(CurrentProductionContractAdapter):
    """Synthetic baseline runner sharing event semantics, never production data or a Provider."""

    SUPPORTED = {"single-prompt", "long-persona-prompt", "structured-context"}

    def __init__(self, repository_root: Path, baseline_id: str):
        if baseline_id not in self.SUPPORTED:
            raise ValueError(f"unsupported offline baseline: {baseline_id}")
        super().__init__(repository_root)
        self.baseline_id = baseline_id

    @property
    def system(self) -> SystemUnderTest:
        return SystemUnderTest(
            id=self.baseline_id, version="offline-baseline-v1", availability="AVAILABLE_OFFLINE_FIXTURE",
            provider="offline-fixture", model="none", prompt_version=f"{self.baseline_id}-v1",
            policy_version="synthetic-event-policy-v1",
        )

    def verify_contracts(self) -> dict[str, str]:
        return {}

    def run(self, scenario: EvaluationScenario, git_sha: str, seed: int) -> SystemRun:
        run = super().run(scenario, git_sha, seed)
        responses = tuple(text.replace("offline fixture", self.baseline_id) for text in run.responses)
        return replace(
            run,
            run_id=f"{self.baseline_id}-{scenario.id}-{seed}",
            system=self.system,
            responses=responses,
            context_artifacts=(f"{self.baseline_id}-fixture-v1",),
        )
