from __future__ import annotations

import json
from dataclasses import asdict
from pathlib import Path

from evals.models import DatasetManifest, EvaluationReport, MetricResult, SystemRun, utc_now


def build_report(
    manifest: DatasetManifest,
    runs: list[SystemRun],
    metrics: list[MetricResult],
    git_sha: str,
    comparative_metrics: dict[str, list[MetricResult]] | None = None,
) -> EvaluationReport:
    total_cost = sum(run.cost.estimated_cost_usd for run in runs)
    hard_gates = {metric.name: metric.passed for metric in metrics if metric.hard_gate is not None}
    return EvaluationReport(
        schema_version=1,
        report_id=f"inno-eval-001-{git_sha[:12]}",
        generated_at=utc_now(),
        configuration={
            "system": "current-production-contract", "provider": "offline-fixture", "model": "none",
            "prompt_version": "production-contract-observed", "policy_version": "production-contract-observed",
            "genome_version": None, "temperature": 0.0, "seed": 20260714,
            "evaluated_systems": sorted({run.system.id for run in runs}),
        },
        dataset_manifest=asdict(manifest),
        aggregate_metrics=[asdict(metric) for metric in metrics],
        pairwise_results={"status": "NOT_RUN", "reason": "Human blind review is pending; no system superiority claim."},
        latency_and_cost={
            "runs": len(runs), "latency_ms_total": sum(run.cost.latency_ms for run in runs),
            "input_tokens": sum(run.cost.input_tokens for run in runs),
            "output_tokens": sum(run.cost.output_tokens for run in runs),
            "model_calls": sum(run.cost.model_calls for run in runs), "estimated_cost_usd": total_cost,
        },
        ablations=[
            {
                "system": system_id,
                "status": "OFFLINE_FIXTURE_RUN",
                "metrics": [asdict(metric) for metric in system_metrics],
                "interpretation": "Structural synthetic comparison only; not a model-quality result.",
            }
            for system_id, system_metrics in sorted((comparative_metrics or {}).items())
        ] + [{"name": "future-candidate-systems", "status": "NOT_RUN", "reason": "candidate systems unavailable"}],
        failure_cases=[],
        privacy_and_safety={"hard_gates": hard_gates, "real_user_data_used": False, "provider_called": False},
        human_review={"status": "PENDING", "blind_format": "human-pairwise-template.csv"},
        reproducibility={
            "git_sha": git_sha, "command": "python -m evals.cli.main run --output <directory>",
            "python": ">=3.11", "dependencies": "stdlib only", "input_split": sorted(manifest.splits),
        },
    )


def write_report(report: EvaluationReport, output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    payload = asdict(report)
    (output / "sample-report.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    metrics = "\n".join(f"- `{m['name']}`: {m['value']:.6f}" for m in payload["aggregate_metrics"])
    markdown = f"""# INNO-EVAL-001 sample report

- Report: `{report.report_id}`
- System: `{report.configuration['system']}`
- Provider/model: offline fixture / none
- Human pairwise: NOT_RUN
- Real provider: BLOCKED_BY_CREDENTIAL_GATE

## Aggregate metrics

{metrics}

## Latency and cost

- Runs: {report.latency_and_cost['runs']}
- Model calls: {report.latency_and_cost['model_calls']}
- Estimated provider cost: USD {report.latency_and_cost['estimated_cost_usd']:.4f}

## Privacy and safety

No real user data or Provider call was used. Hard gates: `{json.dumps(report.privacy_and_safety['hard_gates'], sort_keys=True)}`.

## Ablations, failures, human review

Future candidates and human blind review are NOT_RUN. This contract fixture proves harness reproducibility, not model quality or system superiority.

## Reproducibility

`{report.reproducibility['command']}` at Git `{report.reproducibility['git_sha']}`.
"""
    (output / "sample-report.md").write_text(markdown, encoding="utf-8")
