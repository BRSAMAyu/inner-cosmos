import json
import tempfile
import unittest
from pathlib import Path

from evals.adapters import CurrentProductionContractAdapter
from evals.datasets import load_manifest, load_scenarios
from evals.metrics import evaluate_runs
from evals.reports import build_report, write_report


ROOT = Path(__file__).resolve().parents[1]


class ReportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = load_manifest(ROOT / "evals/datasets/manifest.json")
        cls.scenarios = load_scenarios(ROOT / "evals/datasets/scenarios.jsonl")
        adapter = CurrentProductionContractAdapter(ROOT.parent)
        cls.runs = [adapter.run(item, "d" * 40, 20260714) for item in cls.scenarios]
        cls.report = build_report(cls.manifest, cls.runs, evaluate_runs(cls.scenarios, cls.runs), "d" * 40)

    def test_required_report_sections_exist(self):
        required = {"configuration", "dataset_manifest", "aggregate_metrics", "pairwise_results", "latency_and_cost", "ablations", "failure_cases", "privacy_and_safety", "human_review", "reproducibility"}
        self.assertTrue(required.issubset(self.report.__dict__))

    def test_report_does_not_claim_human_review(self):
        self.assertEqual("PENDING", self.report.human_review["status"])
        self.assertEqual("NOT_RUN", self.report.pairwise_results["status"])

    def test_report_has_no_provider_calls_or_cost(self):
        self.assertEqual(0, self.report.latency_and_cost["model_calls"])
        self.assertEqual(0.0, self.report.latency_and_cost["estimated_cost_usd"])

    def test_write_report_creates_json_and_markdown(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            write_report(self.report, output)
            self.assertTrue((output / "sample-report.json").exists())
            self.assertTrue((output / "sample-report.md").exists())
            payload = json.loads((output / "sample-report.json").read_text(encoding="utf-8"))
            self.assertEqual(1, payload["schema_version"])


if __name__ == "__main__":
    unittest.main()

