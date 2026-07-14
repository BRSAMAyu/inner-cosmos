import csv
import tempfile
import unittest
from pathlib import Path

from evals.adapters import CurrentProductionContractAdapter
from evals.datasets import load_scenarios
from evals.judges import DeterministicOfflineJudge, JudgeEnsemble, OptionalLlmJudge, export_blind_pairs, import_human_annotations


ROOT = Path(__file__).resolve().parents[1]


class JudgeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        scenario = load_scenarios(ROOT / "evals/datasets/scenarios.jsonl")[0]
        cls.sample_run = CurrentProductionContractAdapter(ROOT.parent).run(scenario, "c" * 40, 9)

    def test_offline_judge_completes(self):
        self.assertEqual("COMPLETED", DeterministicOfflineJudge().evaluate(self.sample_run).status)

    def test_llm_judge_is_not_run_without_provider(self):
        annotation = OptionalLlmJudge().evaluate(self.sample_run)
        self.assertEqual("NOT_RUN", annotation.status)
        self.assertEqual({}, annotation.scores)

    def test_enabled_llm_requires_approved_adapter(self):
        with self.assertRaises(RuntimeError):
            OptionalLlmJudge(enabled=True, model="unspecified").evaluate(self.sample_run)

    def test_single_completed_judge_cannot_decide(self):
        annotations = JudgeEnsemble([DeterministicOfflineJudge(), OptionalLlmJudge()]).evaluate(self.sample_run)
        aggregate = JudgeEnsemble.aggregate(annotations)
        self.assertFalse(aggregate["decision_allowed"])

    def test_blind_export_hides_system_names(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "pairs.csv"
            export_blind_pairs([self.sample_run], [self.sample_run], path, 42)
            content = path.read_text(encoding="utf-8")
            self.assertNotIn("current-production", content)
            self.assertIn("response_a", content)

    def test_human_annotation_import(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "pairs.csv"
            export_blind_pairs([self.sample_run], [self.sample_run], path, 42)
            with path.open(encoding="utf-8", newline="") as handle:
                rows = list(csv.DictReader(handle))
            rows[0]["naturalness"] = "4"
            rows[0]["preference"] = "A"
            with path.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
                writer.writeheader(); writer.writerows(rows)
            annotation = import_human_annotations(path, "blind-reviewer-1")[0]
            self.assertEqual(4, annotation.naturalness)
            self.assertEqual("A", annotation.preference)


if __name__ == "__main__":
    unittest.main()
