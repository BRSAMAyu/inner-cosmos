#!/usr/bin/env python3
"""Fail-closed scorer for three frozen blind-review sheets."""

from __future__ import annotations

import argparse
import csv
import json
import math
from collections import defaultdict
from pathlib import Path

DIMENSIONS = (
    "felt_understanding",
    "specificity",
    "stance_and_boundary",
    "naturalness",
    "actionability",
    "language_quality",
)


def wilson_lower(successes: int, total: int, z: float = 1.959963984540054) -> float:
    if total <= 0:
        return 0.0
    p = successes / total
    denominator = 1 + z * z / total
    centre = p + z * z / (2 * total)
    margin = z * math.sqrt((p * (1 - p) + z * z / (4 * total)) / total)
    return (centre - margin) / denominator


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ratings", type=Path, nargs=3, required=True)
    parser.add_argument("--key", type=Path, required=True)
    parser.add_argument("--summary", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    key_doc = json.loads(args.key.read_text(encoding="utf-8"))
    summary = json.loads(args.summary.read_text(encoding="utf-8"))
    keys = {item["blind_pair_id"]: item for item in key_doc["pairs"]}
    expected = set(keys)
    reviewers: dict[str, set[str]] = defaultdict(set)
    preferences = {"DIRECT": 0, "FULL_AURORA": 0, "tie": 0}
    preferences_by_language = {
        "zh-CN": {"DIRECT": 0, "FULL_AURORA": 0, "tie": 0},
        "en-US": {"DIRECT": 0, "FULL_AURORA": 0, "tie": 0},
    }
    scores = {
        system: {dimension: [] for dimension in DIMENSIONS}
        for system in ("DIRECT", "FULL_AURORA")
    }
    seen: set[tuple[str, str]] = set()
    for path in args.ratings:
        with path.open(encoding="utf-8-sig", newline="") as handle:
            rows = list(csv.DictReader(handle))
        for row in rows:
            reviewer = row["reviewer_id"].strip()
            blind_id = row["blind_pair_id"].strip()
            if not reviewer or blind_id not in keys:
                raise SystemExit(f"INVALID_ID:{path}:{reviewer}:{blind_id}")
            if (reviewer, blind_id) in seen:
                raise SystemExit(f"DUPLICATE_RATING:{reviewer}:{blind_id}")
            seen.add((reviewer, blind_id))
            reviewers[reviewer].add(blind_id)
            key = keys[blind_id]
            for side in ("left", "right"):
                system = key[f"{side}_system"]
                for dimension in DIMENSIONS:
                    raw = row[f"{dimension}_{side}_1_5"].strip()
                    if raw not in {"1", "2", "3", "4", "5"}:
                        raise SystemExit(
                            f"INVALID_SCORE:{reviewer}:{blind_id}:{dimension}:{side}")
                    scores[system][dimension].append(int(raw))
            preference = row["preference_left_right_tie"].strip().lower()
            if preference not in {"left", "right", "tie"}:
                raise SystemExit(f"INVALID_PREFERENCE:{reviewer}:{blind_id}")
            winner = "tie" if preference == "tie" else key[f"{preference}_system"]
            preferences[winner] += 1
            language = row["language"]
            preferences_by_language[language][winner] += 1

    if len(reviewers) != 3:
        raise SystemExit(f"REQUIRES_EXACTLY_3_REVIEWERS:found={len(reviewers)}")
    for reviewer, covered in reviewers.items():
        if covered != expected:
            raise SystemExit(
                f"INCOMPLETE_REVIEW:{reviewer}:missing={sorted(expected-covered)}")

    non_ties = preferences["DIRECT"] + preferences["FULL_AURORA"]
    full_rate = preferences["FULL_AURORA"] / non_ties if non_ties else 0.0
    lower = wilson_lower(preferences["FULL_AURORA"], non_ties)
    dimension_summary = {
        dimension: {
            system: round(sum(scores[system][dimension]) / len(scores[system][dimension]), 4)
            for system in ("DIRECT", "FULL_AURORA")
        }
        for dimension in DIMENSIONS
    }
    language_rates = {}
    for language, values in preferences_by_language.items():
        denominator = values["DIRECT"] + values["FULL_AURORA"]
        language_rates[language] = (
            values["FULL_AURORA"] / denominator if denominator else 0.0)
    thresholds = {
        "real_provider_outputs_complete": summary.get("failed_pairs") == 0,
        "fallback_not_used": summary.get("fallback_used") is False,
        "three_independent_complete_reviewers": len(reviewers) == 3,
        "full_preference_rate_gte_0_60": full_rate >= 0.60,
        "wilson_95pct_lower_bound_gt_0_50": lower > 0.50,
        "no_dimension_regression": all(
            value["FULL_AURORA"] >= value["DIRECT"]
            for value in dimension_summary.values()),
        "bilingual_full_win_rate_gap_lte_0_10": (
            abs(language_rates["zh-CN"] - language_rates["en-US"]) <= 0.10),
    }
    result = {
        "status": "PASS" if all(thresholds.values()) else "FAIL",
        "effectiveness_claim": all(thresholds.values()),
        "reviewers": sorted(reviewers),
        "pair_count": len(expected),
        "rating_count": len(seen),
        "preferences": preferences,
        "full_preference_rate_excluding_ties": round(full_rate, 4),
        "full_preference_wilson_95pct_lower": round(lower, 4),
        "full_win_rate_by_language": {
            key: round(value, 4) for key, value in language_rates.items()},
        "dimension_summary": dimension_summary,
        "thresholds": thresholds,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["status"] == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())
