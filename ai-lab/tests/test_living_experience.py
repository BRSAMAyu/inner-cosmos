import csv
import tempfile
import unittest
from pathlib import Path

from evals.living_experience import DIMENSIONS, SCENARIOS, score


class LivingExperienceReviewTest(unittest.TestCase):
    def _write(self, root: Path, reviewer: str, scenarios=SCENARIOS) -> Path:
        path = root / f"{reviewer}.csv"
        fields = ["reviewer_id", "scenario_id", "completed_without_help",
                  *[f"{name}_1_5" for name in DIMENSIONS],
                  "explains_unique_value", "safety_blocker", "notes"]
        with path.open("w", encoding="utf-8-sig", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fields)
            writer.writeheader()
            for scenario in scenarios:
                writer.writerow({"reviewer_id": reviewer, "scenario_id": scenario,
                                 "completed_without_help": "yes",
                                 **{f"{name}_1_5": "4" for name in DIMENSIONS},
                                 "explains_unique_value": "yes", "safety_blocker": "no",
                                 "notes": "felt continuous without implementation guidance"})
        return path

    def test_two_complete_independent_reviews_can_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            result = score([self._write(root, "r1"), self._write(root, "r2")], root / "report.json")
            self.assertEqual("PASS", result["status"])
            self.assertTrue(all(result["thresholds"].values()))

    def test_missing_scenario_and_single_reviewer_fail_closed(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            with self.assertRaisesRegex(ValueError, "incomplete experience review"):
                score([self._write(root, "r1", SCENARIOS[:-1])], root / "missing.json")
            result = score([self._write(root, "r1")], root / "single.json", min_reviewers=1)
            self.assertEqual("FAIL", result["status"])
            self.assertFalse(result["thresholds"]["minimum_independent_reviewers"])


if __name__ == "__main__":
    unittest.main()
