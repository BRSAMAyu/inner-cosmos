from __future__ import annotations

import csv
import hashlib
from pathlib import Path

from evals.models import HumanAnnotation, SystemRun


RATING_FIELDS = [
    "naturalness", "felt_understanding", "timing", "self_continuity",
    "capsule_identity_fidelity", "capsule_style_fidelity", "capsule_behavior_fidelity",
]


def export_blind_pairs(left: list[SystemRun], right: list[SystemRun], output: Path, seed: int) -> None:
    right_by_id = {run.scenario_id: run for run in right}
    rows = []
    for left_run in left:
        right_run = right_by_id[left_run.scenario_id]
        swap = int(hashlib.sha256(f"{seed}:{left_run.scenario_id}".encode()).hexdigest(), 16) % 2 == 1
        first, second = (right_run, left_run) if swap else (left_run, right_run)
        rows.append({
            "blind_pair_id": f"pair-{left_run.scenario_id}-{seed}", "scenario_id": left_run.scenario_id,
            "response_a": "\n".join(first.responses), "response_b": "\n".join(second.responses),
            **{field: "" for field in RATING_FIELDS}, "preference": "", "reason": "",
        })
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def import_human_annotations(path: Path, annotator_id: str) -> list[HumanAnnotation]:
    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    annotations = []
    for row in rows:
        ratings = {field: int(row[field]) if row.get(field) else None for field in RATING_FIELDS}
        annotations.append(HumanAnnotation(
            scenario_id=row["scenario_id"], blind_pair_id=row["blind_pair_id"], annotator_id=annotator_id,
            preference=row.get("preference") or None, reason=row.get("reason") or None, **ratings,
        ))
    return annotations

