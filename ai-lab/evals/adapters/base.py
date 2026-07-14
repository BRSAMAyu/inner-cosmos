from __future__ import annotations

from abc import ABC, abstractmethod

from evals.models import EvaluationScenario, SystemRun, SystemUnderTest


class SystemAdapter(ABC):
    @property
    @abstractmethod
    def system(self) -> SystemUnderTest: ...

    @abstractmethod
    def run(self, scenario: EvaluationScenario, git_sha: str, seed: int) -> SystemRun: ...


class UnavailableSystemError(RuntimeError):
    pass

