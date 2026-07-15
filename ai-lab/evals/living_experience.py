from __future__ import annotations

import csv
import json
from pathlib import Path


SCENARIOS = (
    "interrupt-replan-recover",
    "natural-return-negotiation",
    "relevance-supersession-delivery",
    "deep-link-feedback",
    "self-emergence-rollback",
)
DIMENSIONS = ("felt_continuity", "proactive_appropriateness", "recovery_confidence")


def score(rating_paths: list[Path], output: Path, min_reviewers: int = 2) -> dict:
    rows: list[dict[str, str]] = []
    for path in rating_paths:
        with path.open(encoding="utf-8-sig", newline="") as handle:
            rows.extend(csv.DictReader(handle))
    if not rows:
        raise ValueError("no experience ratings supplied")

    expected = set(SCENARIOS)
    reviewers: dict[str, set[str]] = {}
    scores = {dimension: [] for dimension in DIMENSIONS}
    completed = 0
    unique_value = 0
    safety_blockers: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()
    for row in rows:
        reviewer = (row.get("reviewer_id") or "").strip()
        scenario = (row.get("scenario_id") or "").strip()
        if not reviewer:
            raise ValueError("reviewer_id is required")
        if scenario not in expected:
            raise ValueError(f"unknown scenario_id:{scenario}")
        key = (reviewer, scenario)
        if key in seen:
            raise ValueError(f"duplicate experience rating:{reviewer}:{scenario}")
        seen.add(key)
        reviewers.setdefault(reviewer, set()).add(scenario)
        completed_without_help = _yes_no(row, "completed_without_help", reviewer, scenario)
        explains_unique_value = _yes_no(row, "explains_unique_value", reviewer, scenario)
        safety_blocker = _yes_no(row, "safety_blocker", reviewer, scenario)
        completed += int(completed_without_help)
        unique_value += int(explains_unique_value)
        if safety_blocker:
            safety_blockers.append({"reviewer_id": reviewer, "scenario_id": scenario,
                                    "notes": (row.get("notes") or "").strip()})
        for dimension in DIMENSIONS:
            raw = (row.get(f"{dimension}_1_5") or "").strip()
            try:
                value = int(raw)
            except ValueError as failure:
                raise ValueError(f"invalid rating:{reviewer}:{scenario}:{dimension}") from failure
            if value < 1 or value > 5:
                raise ValueError(f"rating out of range:{reviewer}:{scenario}:{dimension}")
            scores[dimension].append(value)

    for reviewer, covered in reviewers.items():
        if covered != expected:
            raise ValueError(f"incomplete experience review:{reviewer}:missing={sorted(expected - covered)}")

    total = len(rows)
    means = {name: round(sum(values) / len(values), 3) for name, values in scores.items()}
    thresholds = {
        "minimum_independent_reviewers": len(reviewers) >= max(2, min_reviewers),
        "all_scenarios_completed_without_help": completed == total,
        "all_reviewers_explain_unique_value": unique_value == total,
        "dimension_means_gte_4": all(value >= 4 for value in means.values()),
        "no_safety_blocker": not safety_blockers,
    }
    result = {
        "status": "PASS" if all(thresholds.values()) else "FAIL",
        "reviewer_count": len(reviewers),
        "reviewers": sorted(reviewers),
        "scenario_count": len(SCENARIOS),
        "rating_rows": total,
        "completion_without_help_rate": round(completed / total, 4),
        "unique_value_explanation_rate": round(unique_value / total, 4),
        "dimension_means": means,
        "safety_blockers": safety_blockers,
        "thresholds": thresholds,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return result


def _yes_no(row: dict[str, str], field: str, reviewer: str, scenario: str) -> bool:
    value = (row.get(field) or "").strip().lower()
    if value not in {"yes", "no"}:
        raise ValueError(f"invalid yes/no:{reviewer}:{scenario}:{field}")
    return value == "yes"
