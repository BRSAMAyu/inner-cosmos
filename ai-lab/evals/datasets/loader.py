from __future__ import annotations

import json
from pathlib import Path

from evals.models import DatasetManifest, EvaluationScenario

REQUIRED_SPLITS = {"compiler_train", "development", "held_out_trajectory", "adversarial"}
ALLOWED_SENSITIVITY = {"PUBLIC_DEMO", "SYNTHETIC", "CONSENTED_REDACTED"}
FORBIDDEN_MARKERS = {"P0_RAW", "REAL_USER_PRIVATE", "SECRET", "UNCONSENTED"}


class DatasetPolicyError(ValueError):
    pass


def load_manifest(path: Path) -> DatasetManifest:
    data = json.loads(path.read_text(encoding="utf-8"))
    return DatasetManifest(**data)


def load_scenarios(path: Path) -> list[EvaluationScenario]:
    return [EvaluationScenario(**json.loads(line)) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def validate_dataset(manifest: DatasetManifest, scenarios: list[EvaluationScenario]) -> list[str]:
    errors: list[str] = []
    if set(manifest.splits) != REQUIRED_SPLITS:
        errors.append(f"required splits mismatch: {sorted(set(manifest.splits))}")
    ids = [scenario.id for scenario in scenarios]
    if len(ids) != len(set(ids)):
        errors.append("scenario ids must be unique")
    by_id = {scenario.id: scenario for scenario in scenarios}
    listed = [item for split in manifest.splits.values() for item in split]
    if sorted(listed) != sorted(ids):
        errors.append("manifest scenario membership differs from dataset")
    for split, split_ids in manifest.splits.items():
        for scenario_id in split_ids:
            scenario = by_id.get(scenario_id)
            if scenario and scenario.split != split:
                errors.append(f"{scenario_id} split mismatch")
    for scenario in scenarios:
        if scenario.sensitivity not in ALLOWED_SENSITIVITY:
            errors.append(f"{scenario.id} has disallowed sensitivity")
        if not all((scenario.provenance, scenario.purpose, scenario.license)):
            errors.append(f"{scenario.id} missing provenance/purpose/license")
        serialized = json.dumps(scenario.input, ensure_ascii=False).upper()
        if any(marker in serialized for marker in FORBIDDEN_MARKERS):
            errors.append(f"{scenario.id} contains forbidden data marker")
    return errors


def assert_no_split_leakage(
    scenarios: list[EvaluationScenario], compiler_inputs: list[str]
) -> None:
    protected = [s for s in scenarios if s.split in {"held_out_trajectory", "adversarial"}]
    haystack = "\n".join(compiler_inputs).casefold()
    leaks = [s.id for s in protected if s.id.casefold() in haystack or s.title.casefold() in haystack]
    if leaks:
        raise DatasetPolicyError(f"protected split leakage: {','.join(sorted(leaks))}")

