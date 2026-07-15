import csv
import json
import tempfile
import unittest
from pathlib import Path

from evals.psychology_compare import REVIEW_DIMENSIONS, build_pairs, run, score


class PsychologyComparisonTest(unittest.TestCase):
    root = Path(__file__).resolve().parents[2]

    def test_pairs_are_reproducible_balanced_and_blind(self):
        first, first_key = build_pairs(self.root, 20260715)
        second, second_key = build_pairs(self.root, 20260715)
        self.assertEqual(first, second)
        self.assertEqual(first_key, second_key)
        self.assertEqual(9, len(first))
        self.assertEqual({"zh-CN", "en-SG"}, {row["locale"] for row in first})
        self.assertTrue(all("skill" not in row["response_a"].lower() for row in first))
        self.assertTrue(all("skill" not in row["response_b"].lower() for row in first))
        self.assertGreater(len(set(first_key.values())), 1)
        self.assertTrue(all(dimension + "_winner" in first[0] for dimension in REVIEW_DIMENSIONS))

    def test_report_refuses_to_claim_unreviewed_uplift(self):
        with tempfile.TemporaryDirectory() as directory:
            result = run(self.root, Path(directory), 7)
            self.assertEqual("READY_FOR_BLIND_REVIEW", result["status"])
            self.assertEqual("PENDING_HUMAN_REVIEW", result["humanPreference"])
            self.assertFalse(result["effectivenessClaim"])
            with (Path(directory) / "psychology-no-skill-pairwise.csv").open(encoding="utf-8-sig") as handle:
                rows = list(csv.DictReader(handle))
            self.assertEqual(9, len(rows))
            report = json.loads((Path(directory) / "psychology-comparison-report.json").read_text())
            self.assertTrue(report["identityHiddenInReviewCsv"])

    def test_completed_ratings_can_be_unblinded_without_effectiveness_claim(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            run(self.root, output, 11)
            ratings = output / "psychology-no-skill-pairwise.csv"
            with ratings.open(encoding="utf-8-sig", newline="") as handle:
                rows = list(csv.DictReader(handle))
            for index, row in enumerate(rows):
                row["preference"] = "A" if index % 2 == 0 else "TIE"
                for dimension in REVIEW_DIMENSIONS:
                    row[f"{dimension}_winner"] = "B"
                row["unsafe_response"] = "NONE"
            with ratings.open("w", encoding="utf-8-sig", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
                writer.writeheader()
                writer.writerows(rows)
            report = score(ratings, output / "psychology-no-skill-key.json", output / "scored.json")
            self.assertEqual("HUMAN_RATINGS_RECORDED", report["status"])
            self.assertFalse(report["effectivenessClaim"])
            self.assertEqual(9, report["pairs"])

    def test_incomplete_ratings_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            run(self.root, output, 11)
            with self.assertRaisesRegex(ValueError, "preference must be"):
                score(output / "psychology-no-skill-pairwise.csv",
                      output / "psychology-no-skill-key.json", output / "scored.json")

    def test_missing_pair_cannot_bias_score(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            run(self.root, output, 11)
            ratings = output / "psychology-no-skill-pairwise.csv"
            with ratings.open(encoding="utf-8-sig", newline="") as handle:
                rows = list(csv.DictReader(handle))
            with ratings.open("w", encoding="utf-8-sig", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
                writer.writeheader()
                writer.writerows(rows[:-1])
            with self.assertRaisesRegex(ValueError, "missing rated pairs"):
                score(ratings, output / "psychology-no-skill-key.json", output / "scored.json")


if __name__ == "__main__":
    unittest.main()
