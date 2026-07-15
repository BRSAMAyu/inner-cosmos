import tempfile
import unittest
from pathlib import Path

from evals.psychology import evaluate, run

ROOT = Path(__file__).resolve().parents[2]


class PsychologyContractTest(unittest.TestCase):
    def test_all_manifests_and_scenario_families_pass(self):
        report = evaluate(ROOT)
        self.assertEqual("PASS", report["status"], report["errors"])
        self.assertEqual(3, report["manifests"])
        self.assertEqual(15, report["scenarios"])
        self.assertEqual("PENDING", report["humanReview"])

    def test_report_is_reproducible_and_offline(self):
        with tempfile.TemporaryDirectory() as directory:
            report = run(ROOT, Path(directory))
            self.assertFalse(report["realUserDataUsed"])
            self.assertFalse(report["providerCalled"])
            self.assertTrue((Path(directory) / "psychology-contract-report.json").exists())


if __name__ == "__main__":
    unittest.main()
