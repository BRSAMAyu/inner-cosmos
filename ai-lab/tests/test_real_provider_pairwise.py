import json
import tempfile
import unittest
from pathlib import Path

from evals.real_provider_pairwise import ProviderConfig, SYNTHETIC_TRAJECTORIES, run_pairwise


class RealProviderPairwiseTest(unittest.TestCase):
    def test_outputs_blind_pairs_without_persisting_secret(self):
        calls = []

        def fake(config, model, system, turns):
            calls.append((model, system, turns))
            return f"{model} response", {"latency_ms": 12, "input_tokens": 3, "output_tokens": 4, "request_id": "redacted-test"}

        config = ProviderConfig("https://provider.invalid/v1", "top-secret", "model-a", "model-b")
        with tempfile.TemporaryDirectory() as folder:
            report = run_pairwise(config, Path(folder), transport=fake)
            serialized = (Path(folder) / "real-provider-runs.json").read_text(encoding="utf-8")
            csv_text = (Path(folder) / "blind-human-pairwise.csv").read_text(encoding="utf-8-sig")

        self.assertEqual("AWAITING_HUMAN_PAIRWISE", report["status"])
        self.assertEqual(len(SYNTHETIC_TRAJECTORIES) * 2, len(calls))
        self.assertNotIn("top-secret", serialized)
        self.assertNotIn("provider.invalid", serialized)
        self.assertIn("felt_understanding_1_5", csv_text)
        parsed = json.loads(serialized)
        self.assertTrue(parsed["provider_called"])
        self.assertFalse(parsed["fallback_used"])


if __name__ == "__main__":
    unittest.main()
