import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from evals.real_provider_pairwise import ProviderConfig, SYNTHETIC_TRAJECTORIES, config_from_environment, run_pairwise


class RealProviderPairwiseTest(unittest.TestCase):
    def test_missing_credentials_fail_closed_without_provider_call(self):
        with patch.dict("os.environ", {}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "BLOCKED_BY_CREDENTIAL_GATE"):
                config_from_environment()

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
        self.assertIn("felt_understanding_1_5", csv_text)
        parsed = json.loads(serialized)
        self.assertTrue(parsed["provider_called"])
        self.assertFalse(parsed["fallback_used"])
        self.assertEqual("single-pass.v1", parsed["records"][0]["systems"]["A"]["runtime"])
        self.assertEqual("dual-kernel.v1", parsed["records"][0]["systems"]["B"]["runtime"])
        self.assertEqual(2, parsed["records"][0]["systems"]["B"]["llm_calls"])
        self.assertEqual(8, len(parsed["records"]))
        self.assertEqual("living-aurora-pairwise.v2", parsed["prompt_contract_version"])
        self.assertEqual({"A", "B"}, set(parsed["deterministic_summary"]))
        self.assertIn("deterministic_score", parsed["records"][0]["systems"]["A"])
        self.assertIn("continuity_and_boundary_1_5", csv_text)


if __name__ == "__main__":
    unittest.main()
