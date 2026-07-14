import unittest
from pathlib import Path

from evals.adapters import CurrentProductionContractAdapter
from evals.datasets import load_scenarios
from evals.metrics import evaluate_runs, metric_definitions


ROOT = Path(__file__).resolve().parents[1]


class MetricsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.scenarios = load_scenarios(ROOT / "evals/datasets/scenarios.jsonl")
        adapter = CurrentProductionContractAdapter(ROOT.parent)
        cls.runs = [adapter.run(item, "b" * 40, 20260714) for item in cls.scenarios]
        cls.metrics = {item.name: item for item in evaluate_runs(cls.scenarios, cls.runs)}

    def test_all_metric_definitions_have_results(self):
        self.assertEqual({item["name"] for item in metric_definitions()}, set(self.metrics))

    def test_metric_count(self):
        self.assertEqual(20, len(self.metrics))

    def test_bubble_count_accuracy(self):
        self.assertEqual(1.0, self.metrics["expected_bubble_count_accuracy"].value)

    def test_interruption_hard_gate(self):
        self.assertTrue(self.metrics["interruption_success_rate"].passed)

    def test_stale_bubble_hard_gate(self):
        self.assertTrue(self.metrics["stale_bubble_after_cancel_rate"].passed)

    def test_unauthorized_memory_hard_gate(self):
        self.assertTrue(self.metrics["unauthorized_memory_recall_rate"].passed)

    def test_privacy_hard_gate(self):
        self.assertTrue(self.metrics["privacy_leakage_rate"].passed)

    def test_evidence_traceability_hard_gate(self):
        self.assertTrue(self.metrics["evidence_traceability"].passed)

    def test_held_out_hard_gate(self):
        self.assertTrue(self.metrics["held_out_leakage"].passed)

    def test_latency_and_cost_metrics_exist(self):
        self.assertGreater(self.metrics["latency_ms_mean"].value, 0)
        self.assertEqual(0, self.metrics["model_call_count_mean"].value)


if __name__ == "__main__":
    unittest.main()

