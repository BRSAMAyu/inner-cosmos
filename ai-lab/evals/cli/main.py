from __future__ import annotations

import argparse
import json
import subprocess
from dataclasses import asdict
from pathlib import Path

from evals.adapters import CurrentProductionContractAdapter, OfflineBaselineAdapter, build_registry
from evals.datasets import load_manifest, load_scenarios, validate_dataset
from evals.datasets.loader import assert_no_split_leakage
from evals.judges import DeterministicOfflineJudge, JudgeEnsemble, OptionalLlmJudge, export_blind_pairs
from evals.metrics import evaluate_runs
from evals.reports import build_report, write_report
from evals.schemas.validator import validate_schema_documents
from evals.real_provider_pairwise import config_from_environment, config_from_local_profile, rescore_report, run_pairwise, score_pairwise
from evals.capsule_runtime_pairwise import run_pairwise as run_capsule_pairwise
from evals.psychology import run as run_psychology
from evals.psychology_compare import run as run_psychology_compare, score as score_psychology_compare
from evals.living_experience import score as score_living_experience

LAB_ROOT = Path(__file__).resolve().parents[2]
REPOSITORY_ROOT = LAB_ROOT.parent
DATASET_DIR = LAB_ROOT / "evals" / "datasets"


def _load():
    return load_manifest(DATASET_DIR / "manifest.json"), load_scenarios(DATASET_DIR / "scenarios.jsonl")


def validate() -> dict[str, object]:
    manifest, scenarios = _load()
    errors = validate_dataset(manifest, scenarios)
    errors.extend(validate_schema_documents(LAB_ROOT / "evals" / "schemas"))
    train = [scenario for scenario in scenarios if scenario.split == "compiler_train"]
    assert_no_split_leakage(scenarios, [scenario.id + " " + scenario.title for scenario in train])
    contracts = CurrentProductionContractAdapter(REPOSITORY_ROOT).verify_contracts()
    if errors:
        raise SystemExit("\n".join(errors))
    return {"status": "PASS", "scenarios": len(scenarios), "splits": {k: len(v) for k, v in manifest.splits.items()}, "contracts": contracts}


def run(output: Path, seed: int) -> dict[str, object]:
    validation = validate()
    manifest, scenarios = _load()
    git_sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=REPOSITORY_ROOT, text=True).strip()
    current_runs = [CurrentProductionContractAdapter(REPOSITORY_ROOT).run(scenario, git_sha, seed) for scenario in scenarios]
    baseline_runs = {
        baseline_id: [OfflineBaselineAdapter(REPOSITORY_ROOT, baseline_id).run(scenario, git_sha, seed) for scenario in scenarios]
        for baseline_id in sorted(OfflineBaselineAdapter.SUPPORTED)
    }
    runs = current_runs + [run for group in baseline_runs.values() for run in group]
    metrics = evaluate_runs(scenarios, current_runs)
    comparative_metrics = {system_id: evaluate_runs(scenarios, group) for system_id, group in baseline_runs.items()}
    all_metrics = [metrics, *comparative_metrics.values()]
    failed_gates = [
        metric.name for group in all_metrics for metric in group
        if metric.hard_gate is not None and metric.passed is not True
    ]
    if failed_gates:
        raise SystemExit(f"hard gates failed: {','.join(failed_gates)}")
    report = build_report(manifest, runs, metrics, git_sha, comparative_metrics)
    write_report(report, output)
    export_blind_pairs(current_runs, baseline_runs["single-prompt"], output / "human-pairwise-template.csv", seed)
    judges = JudgeEnsemble([DeterministicOfflineJudge(), OptionalLlmJudge()]).evaluate(current_runs[0])
    (output / "judge-status.json").write_text(json.dumps([asdict(item) for item in judges], indent=2) + "\n", encoding="utf-8")
    (output / "system-registry.json").write_text(json.dumps([asdict(item) for item in build_registry()], indent=2) + "\n", encoding="utf-8")
    return {
        **validation, "systems": 4, "runs": len(runs), "metrics": sum(len(group) for group in all_metrics),
        "hard_gates": "PASS", "output": str(output),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Inner Cosmos innovation evaluation harness")
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("validate")
    run_parser = subparsers.add_parser("run")
    run_parser.add_argument("--output", type=Path, required=True)
    run_parser.add_argument("--seed", type=int, default=20260714)
    real_parser = subparsers.add_parser("real-pairwise")
    real_parser.add_argument("--output", type=Path, required=True)
    real_parser.add_argument("--seed", type=int, default=20260715)
    real_parser.add_argument("--profile", help="Profile in ~/.config/inner-cosmos/providers.local.json")
    real_score_parser = subparsers.add_parser("real-pairwise-score")
    real_score_parser.add_argument("--ratings", type=Path, nargs="+", required=True)
    real_score_parser.add_argument("--runs", type=Path, required=True)
    real_score_parser.add_argument("--output", type=Path, required=True)
    real_score_parser.add_argument("--min-reviewers", type=int, default=2)
    real_rescore_parser = subparsers.add_parser("real-pairwise-rescore")
    real_rescore_parser.add_argument("--runs", type=Path, required=True)
    real_rescore_parser.add_argument("--output", type=Path, required=True)
    capsule_parser = subparsers.add_parser("capsule-pairwise")
    capsule_parser.add_argument("--output", type=Path, required=True)
    capsule_parser.add_argument("--profile", help="Profile in ~/.config/inner-cosmos/providers.local.json")
    experience_score_parser = subparsers.add_parser("experience-score")
    experience_score_parser.add_argument("--ratings", type=Path, nargs="+", required=True)
    experience_score_parser.add_argument("--output", type=Path, required=True)
    experience_score_parser.add_argument("--min-reviewers", type=int, default=2)
    psychology_parser = subparsers.add_parser("psychology")
    psychology_parser.add_argument("--output", type=Path, required=True)
    psychology_compare_parser = subparsers.add_parser("psychology-compare")
    psychology_compare_parser.add_argument("--output", type=Path, required=True)
    psychology_compare_parser.add_argument("--seed", type=int, default=20260715)
    psychology_score_parser = subparsers.add_parser("psychology-compare-score")
    psychology_score_parser.add_argument("--ratings", type=Path, required=True)
    psychology_score_parser.add_argument("--key", type=Path, required=True)
    psychology_score_parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "validate":
        result = validate()
    elif args.command == "real-pairwise":
        config = config_from_local_profile(args.profile) if args.profile else config_from_environment()
        result = run_pairwise(config, args.output, args.seed)
    elif args.command == "real-pairwise-score":
        result = score_pairwise(args.ratings, args.runs, args.output, args.min_reviewers)
    elif args.command == "real-pairwise-rescore":
        result = rescore_report(args.runs, args.output)
    elif args.command == "capsule-pairwise":
        config = config_from_local_profile(args.profile) if args.profile else config_from_environment()
        result = run_capsule_pairwise(config, args.output)
    elif args.command == "experience-score":
        result = score_living_experience(args.ratings, args.output, args.min_reviewers)
    elif args.command == "psychology":
        result = run_psychology(REPOSITORY_ROOT, args.output)
    elif args.command == "psychology-compare":
        result = run_psychology_compare(REPOSITORY_ROOT, args.output, args.seed)
    elif args.command == "psychology-compare-score":
        result = score_psychology_compare(args.ratings, args.key, args.output)
    else:
        result = run(args.output, args.seed)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
