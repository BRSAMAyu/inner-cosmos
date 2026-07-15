import csv
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from evals.real_provider_pairwise import (
    ProviderConfig, RATING_DIMENSIONS, SYNTHETIC_TRAJECTORIES,
    config_from_environment, config_from_local_profile, rescore_report, run_pairwise, score_pairwise,
)


class RealProviderPairwiseTest(unittest.TestCase):
    def test_missing_credentials_fail_closed_without_provider_call(self):
        with patch.dict("os.environ", {}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "BLOCKED_BY_CREDENTIAL_GATE"):
                config_from_environment()

    def test_persistent_local_profile_loads_without_environment_reconfiguration(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "providers.local.json"
            path.write_text(json.dumps({"schema_version": 1, "providers": {"quality": {
                "base_url": "https://provider.invalid/v1", "api_key": "local-only-secret", "model": "quality-model"
            }}}), encoding="utf-8")
            config = config_from_local_profile("quality", path)
            self.assertEqual("quality-model", config.model)
            self.assertEqual("local-only-secret", config.api_key)
            with self.assertRaisesRegex(RuntimeError, "LOCAL_PROVIDER_PROFILE_NOT_FOUND"):
                config_from_local_profile("missing", path)

    def test_outputs_blind_pairs_without_persisting_secret(self):
        calls = []

        def fake(config, model, system, turns):
            calls.append((model, system, turns))
            return f"{model} response", {"latency_ms": 12, "input_tokens": 3, "output_tokens": 4, "request_id": "redacted-test"}

        config = ProviderConfig("https://provider.invalid/v1", "top-secret", "model-under-test")
        with tempfile.TemporaryDirectory() as folder:
            report = run_pairwise(config, Path(folder), transport=fake)
            serialized = (Path(folder) / "real-provider-runs.json").read_text(encoding="utf-8")
            csv_text = (Path(folder) / "blind-human-pairwise.csv").read_text(encoding="utf-8-sig")

        self.assertEqual("AWAITING_HUMAN_PAIRWISE", report["status"])
        self.assertEqual(len(SYNTHETIC_TRAJECTORIES) * 3, len(calls))
        self.assertEqual({"model-under-test"}, {call[0] for call in calls})
        self.assertNotIn("top-secret", serialized)
        self.assertNotIn("provider.invalid", serialized)
        self.assertIn("felt_understanding_left_1_5", csv_text)
        parsed = json.loads(serialized)
        self.assertTrue(parsed["provider_called"])
        self.assertFalse(parsed["fallback_used"])
        self.assertEqual("single-pass.v1", parsed["records"][0]["systems"]["A"]["runtime"])
        self.assertEqual("dual-kernel.v1", parsed["records"][0]["systems"]["B"]["runtime"])
        self.assertEqual(2, parsed["records"][0]["systems"]["B"]["llm_calls"])
        self.assertEqual(11, len(parsed["records"]))
        self.assertEqual("living-aurora-pairwise.v3.1", parsed["prompt_contract_version"])
        self.assertEqual({"A", "B"}, set(parsed["deterministic_summary"]))
        self.assertIn("deterministic_score", parsed["records"][0]["systems"]["A"])
        self.assertIn("continuity_and_boundary_right_1_5", csv_text)
        longitudinal = [record for record in parsed["records"] if record["input"]["longitudinal_id"]]
        self.assertEqual([1, 2, 3], [record["input"]["day"] for record in longitudinal])
        self.assertEqual(5, len(longitudinal[-1]["input"]["conversation"]))

    def test_scoring_requires_complete_independent_reviews_and_unblinds_systems(self):
        def fake(config, model, system, turns):
            return f"{system[:12]}:{turns[-1]}", {"latency_ms": 10, "input_tokens": 2, "output_tokens": 3}

        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            run_pairwise(ProviderConfig("https://provider.invalid/v1", "secret", "model"), root, transport=fake)
            runs = json.loads((root / "real-provider-runs.json").read_text(encoding="utf-8"))
            records = {record["blind_pair_id"]: record for record in runs["records"]}
            with (root / "blind-human-pairwise.csv").open(encoding="utf-8-sig", newline="") as handle:
                template = list(csv.DictReader(handle))
                fieldnames = list(template[0])

            rating_paths = []
            for reviewer in ("reviewer-01", "reviewer-02"):
                path = root / f"{reviewer}.csv"
                with path.open("w", encoding="utf-8-sig", newline="") as handle:
                    writer = csv.DictWriter(handle, fieldnames=fieldnames)
                    writer.writeheader()
                    for source in template:
                        row = dict(source)
                        row["reviewer_id"] = reviewer
                        order = records[row["blind_pair_id"]]["blind_order"]
                        dual_side = "left" if order[0] == "B" else "right"
                        for dimension in RATING_DIMENSIONS:
                            row[f"{dimension}_left_1_5"] = "5" if dual_side == "left" else "2"
                            row[f"{dimension}_right_1_5"] = "5" if dual_side == "right" else "2"
                        row["preference_left_right_tie"] = dual_side
                        row["reason"] = "full runtime preserved the latest boundary"
                        writer.writerow(row)
                rating_paths.append(path)

            result = score_pairwise(rating_paths, root / "real-provider-runs.json", root / "score.json")
            self.assertEqual("PASS", result["status"])
            self.assertTrue(result["effectiveness_claim"])
            self.assertEqual(2, result["reviewer_count"])
            self.assertEqual(len(SYNTHETIC_TRAJECTORIES) * 2, result["preferences"]["B"])
            self.assertTrue(all(result["thresholds"].values()))

            clamped = score_pairwise(rating_paths[:1], root / "real-provider-runs.json", root / "clamped.json", min_reviewers=1)
            self.assertEqual("FAIL", clamped["status"])
            self.assertFalse(clamped["thresholds"]["minimum_independent_reviewers"])

            with self.assertRaisesRegex(ValueError, "reviewer_id is required"):
                score_pairwise([root / "blind-human-pairwise.csv"], root / "real-provider-runs.json", root / "invalid.json")

    def test_rescore_treats_cancelled_old_plan_as_negated_but_never_claims_effectiveness(self):
        def fake(config, model, system, turns):
            response = "好，明晚八点的提醒已经作废。改成周六上午十点，只继续面试。"
            return response, {"latency_ms": 1, "input_tokens": 1, "output_tokens": 1}

        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            run_pairwise(ProviderConfig("https://provider.invalid/v1", "secret", "model"), root, transport=fake)
            rescored = rescore_report(root / "real-provider-runs.json", root / "rescored.json")
            temporal = next(row for row in rescored["records"] if row["scenario_id"] == "temporal-reschedule")
            score = temporal["systems"]["B"]["deterministic_score"]
            self.assertNotIn("明晚八点", score["forbidden_hits"])
            self.assertIn("明晚八点", score["negated_forbidden_mentions"])
            self.assertTrue(rescored["post_hoc_calibration"])
            self.assertFalse(rescored["effectiveness_claim"])


if __name__ == "__main__":
    unittest.main()
