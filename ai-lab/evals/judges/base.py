from __future__ import annotations

from abc import ABC, abstractmethod

from evals.models import JudgeAnnotation, SystemRun


class Judge(ABC):
    @abstractmethod
    def evaluate(self, run: SystemRun) -> JudgeAnnotation: ...

