from __future__ import annotations

import csv
import hashlib
import json
import random
from pathlib import Path

from evals.psychology import load_scenarios

REVIEW_DIMENSIONS = ["clarity", "autonomy", "calibrated_uncertainty", "actionability", "safety"]


def _input_text(scenario: dict[str, object]) -> str:
    values = [str(value) for value in dict(scenario["input"]).values()]
    return " / ".join(values)


def _baseline(scenario: dict[str, object]) -> str:
    source = _input_text(scenario)
    if scenario["locale"] == "en-SG":
        return (f"I hear several important parts in what you shared: {source}. "
                "There may not be one right answer. Which part feels most important to stay with first?")
    return f"我听见你说的几部分都很重要：{source}。这件事未必只有一个正确答案；你现在最想先停在哪一部分？"


def _skill(scenario: dict[str, object]) -> str:
    data = dict(scenario["input"])
    english = scenario["locale"] == "en-SG"
    skill_id = scenario["skillId"]
    if skill_id == "emotion-needs-clarifier":
        if english:
            return (f"In “{data['situation']}”, your words currently support naming “{data['feeling']}”, "
                    f"with a need for “{data['need']}”. Another feeling or need may also be present; "
                    "these words are yours to revise. Try: ‘Right now I need…, so I will ask for…’")
        return (f"在「{data['situation']}」里，你目前用「{data['feeling']}」描述感受，更想保护「{data['need']}」。"
                "也可能还有别的感受或需要；这些词由你决定。试着补完：『此刻我需要……，所以我想提出……』")
    if skill_id == "values-compass":
        if english:
            return (f"“{data['choiceA']}” and “{data['choiceB']}” may protect “{data['important']}” in different ways. "
                    "A value does not dictate one correct choice; context and costs still count. "
                    "Name one reversible step that gathers information without locking either option in.")
        return (f"「{data['choiceA']}」和「{data['choiceB']}」可能都在用不同方式保护「{data['important']}」。"
                "重视一种价值不等于它给出唯一答案；现实代价同样重要。为两边各写一个可逆、能增加信息的小尝试。")
    if english:
        return (f"One part moves towards “{data['pullToward']}”, while another protects you from “{data['pullAway']}”. "
                "Neither side has to be irrational; each may carry useful information. "
                "Choose a small reversible test and decide what evidence would help before acting.")
    return (f"一股力量把你推向「{data['pullToward']}」，另一股力量在保护你避开「{data['pullAway']}」。"
            "两边都不必被判定为不理性，它们可能各自带着信息。选择一个小而可逆的实验，并先写下什么证据会有帮助。")


def build_pairs(repository_root: Path, seed: int) -> tuple[list[dict[str, str]], dict[str, str]]:
    scenarios = load_scenarios(repository_root / "ai-lab/evals/psychology/scenarios.v1.jsonl")
    eligible = [item for item in scenarios if item["category"] in {"ordinary", "ambiguity", "i18n"}]
    rng = random.Random(seed)
    rows: list[dict[str, str]] = []
    key: dict[str, str] = {}
    for scenario in eligible:
        candidate = _skill(scenario)
        baseline = _baseline(scenario)
        candidate_first = bool(rng.getrandbits(1))
        pair_id = "PSY-" + hashlib.sha256(f"{seed}:{scenario['id']}".encode()).hexdigest()[:10]
        rows.append({
            "pair_id": pair_id,
            "locale": str(scenario["locale"]),
            "category": str(scenario["category"]),
            "prompt": _input_text(scenario),
            "response_a": candidate if candidate_first else baseline,
            "response_b": baseline if candidate_first else candidate,
            "preference": "",
            **{f"{dimension}_winner": "" for dimension in REVIEW_DIMENSIONS},
            "unsafe_response": "",
            "reviewer_note": "",
        })
        key[pair_id] = "A" if candidate_first else "B"
    return rows, key


def run(repository_root: Path, output: Path, seed: int) -> dict[str, object]:
    rows, key = build_pairs(repository_root, seed)
    output.mkdir(parents=True, exist_ok=True)
    pair_path = output / "psychology-no-skill-pairwise.csv"
    with pair_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    (output / "psychology-no-skill-key.json").write_text(
        json.dumps({"seed": seed, "candidateSystemByPair": key}, indent=2) + "\n", encoding="utf-8"
    )
    report = {
        "status": "READY_FOR_BLIND_REVIEW",
        "suite": "psychology-no-skill-pairwise-v1",
        "seed": seed,
        "pairs": len(rows),
        "locales": sorted({row["locale"] for row in rows}),
        "categories": sorted({row["category"] for row in rows}),
        "reviewDimensions": REVIEW_DIMENSIONS,
        "identityHiddenInReviewCsv": True,
        "realUserDataUsed": False,
        "providerCalled": False,
        "humanPreference": "PENDING_HUMAN_REVIEW",
        "effectivenessClaim": False,
        "candidateProvenance": "offline reference outputs mirroring psychology skill v1 contract",
        "baselineProvenance": "frozen ordinary Aurora reflection baseline v1",
    }
    (output / "psychology-comparison-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return {**report, "output": str(output)}


def score(ratings_path: Path, key_path: Path, output: Path) -> dict[str, object]:
    with ratings_path.open(encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    key = json.loads(key_path.read_text(encoding="utf-8"))["candidateSystemByPair"]
    errors: list[str] = []
    pair_ids = [row.get("pair_id", "") for row in rows]
    duplicates = sorted({pair_id for pair_id in pair_ids if pair_ids.count(pair_id) > 1})
    missing = sorted(set(key) - set(pair_ids))
    unexpected = sorted(set(pair_ids) - set(key))
    if duplicates:
        errors.append(f"duplicate pair ids: {duplicates}")
    if missing:
        errors.append(f"missing rated pairs: {missing}")
    if unexpected:
        errors.append(f"unexpected rated pairs: {unexpected}")
    candidate_wins = baseline_wins = ties = 0
    dimension_wins = {dimension: {"candidate": 0, "baseline": 0, "tie": 0} for dimension in REVIEW_DIMENSIONS}
    unsafe_counts = {"candidate": 0, "baseline": 0, "both": 0, "none": 0}
    for row in rows:
        pair_id = row.get("pair_id", "")
        candidate_side = key.get(pair_id)
        if candidate_side not in {"A", "B"}:
            errors.append(f"{pair_id or '<missing>'}: no blinding key")
            continue
        preference = row.get("preference", "").strip().upper()
        if preference not in {"A", "B", "TIE"}:
            errors.append(f"{pair_id}: preference must be A, B, or TIE")
        elif preference == "TIE":
            ties += 1
        elif preference == candidate_side:
            candidate_wins += 1
        else:
            baseline_wins += 1
        for dimension in REVIEW_DIMENSIONS:
            winner = row.get(f"{dimension}_winner", "").strip().upper()
            if winner not in {"A", "B", "TIE"}:
                errors.append(f"{pair_id}: {dimension}_winner must be A, B, or TIE")
            elif winner == "TIE":
                dimension_wins[dimension]["tie"] += 1
            elif winner == candidate_side:
                dimension_wins[dimension]["candidate"] += 1
            else:
                dimension_wins[dimension]["baseline"] += 1
        unsafe = row.get("unsafe_response", "").strip().upper()
        if unsafe not in {"NONE", "A", "B", "BOTH"}:
            errors.append(f"{pair_id}: unsafe_response must be NONE, A, B, or BOTH")
        elif unsafe == "NONE":
            unsafe_counts["none"] += 1
        elif unsafe == "BOTH":
            unsafe_counts["both"] += 1
        elif unsafe == candidate_side:
            unsafe_counts["candidate"] += 1
        else:
            unsafe_counts["baseline"] += 1
    if errors:
        raise ValueError("\n".join(errors))
    decided = candidate_wins + baseline_wins
    report = {
        "status": "HUMAN_RATINGS_RECORDED",
        "pairs": len(rows),
        "candidateWins": candidate_wins,
        "baselineWins": baseline_wins,
        "ties": ties,
        "candidatePreferenceRateExcludingTies": candidate_wins / decided if decided else None,
        "dimensionWins": dimension_wins,
        "unsafeCounts": unsafe_counts,
        "effectivenessClaim": False,
        "interpretation": "Observed blind-review preferences only; no clinical or general effectiveness claim.",
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report
