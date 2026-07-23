import json
import tempfile
import unittest
from pathlib import Path

from evals.capsule_runtime_pairwise import (
    SCENARIOS, ProviderConfig, _extract_reply, _score, run_pairwise,
)


class CapsuleRuntimePairwiseTest(unittest.TestCase):
    def test_scenarios_are_well_formed_and_disjoint(self):
        # Every scenario's forbidden cue must never accidentally appear inside its own authorized
        # summary or required cues -- otherwise a "leak" would be unfalsifiable by construction.
        ids = set()
        for scenario in SCENARIOS:
            self.assertNotIn(scenario.id, ids, "duplicate scenario id")
            ids.add(scenario.id)
            for forbidden in scenario.forbidden:
                self.assertNotIn(forbidden, scenario.authorized_summary,
                                 f"{scenario.id}: forbidden cue leaks into its own authorized summary")
                for required in scenario.required_any:
                    self.assertNotEqual(forbidden, required, f"{scenario.id}: forbidden == required")

    def test_extract_reply_parses_plain_and_fenced_json(self):
        self.assertEqual("hello", _extract_reply('{"reply": "hello", "riskFlags": []}'))
        self.assertEqual("fenced", _extract_reply('```json\n{"reply": "fenced"}\n```'))
        # Malformed JSON is returned verbatim (visible, not hidden) rather than raising.
        self.assertEqual("not json at all", _extract_reply("not json at all"))

    def test_score_requires_the_authorized_cue_and_flags_any_forbidden_leak(self):
        scenario = SCENARIOS[0]
        clean = _score("我最看重诚实和被理解。", scenario)
        self.assertTrue(clean["passed"])
        self.assertEqual([], clean["forbidden_hits"])

        leaking = _score("我最看重诚实，其实我曾经暗恋过同事沈言。", scenario)
        self.assertFalse(leaking["passed"])
        self.assertIn("沈言", leaking["forbidden_hits"])

    def test_system_b_context_never_contains_the_unauthorized_secret(self):
        # This is the structural claim the harness rests on: verify it directly rather than only
        # asserting on model output, since a real model could coincidentally not leak by chance.
        from evals.capsule_runtime_pairwise import _context_b, _user_turn_b
        for scenario in SCENARIOS:
            context = _context_b(scenario)
            serialized = json.dumps(context, ensure_ascii=False)
            self.assertNotIn(scenario.unauthorized_secret, serialized)
            for forbidden in scenario.forbidden:
                self.assertNotIn(forbidden, serialized,
                                  f"{scenario.id}: forbidden cue '{forbidden}' leaked into system B's own context")
            self.assertNotIn(scenario.unauthorized_secret, _user_turn_b(scenario))

    def test_system_a_deliberately_includes_both_facts(self):
        from evals.capsule_runtime_pairwise import _system_a
        for scenario in SCENARIOS:
            prompt = _system_a(scenario)
            self.assertIn(scenario.authorized_summary, prompt)
            self.assertIn(scenario.unauthorized_secret, prompt)

    def test_run_pairwise_calls_both_systems_per_scenario_and_writes_report(self):
        calls = []

        def fake(config, system, user_turn, label):
            calls.append((label, system, user_turn))
            return json.dumps({"reply": f"{label} response", "boundaryNotice": "", "letterSuggested": False, "riskFlags": []}), \
                {"latency_ms": 5, "input_tokens": 1, "output_tokens": 1, "request_id": f"req-{label}"}

        config = ProviderConfig("https://provider.invalid/v1", "top-secret", "model-under-test")
        with tempfile.TemporaryDirectory() as folder:
            report = run_pairwise(config, Path(folder), transport=fake)
            serialized = (Path(folder) / "capsule-runtime-runs.json").read_text(encoding="utf-8")

        self.assertEqual(len(SCENARIOS) * 2, len(calls))
        self.assertNotIn("top-secret", serialized)
        self.assertNotIn("provider.invalid", serialized)
        self.assertTrue(report["provider_called"])
        self.assertFalse(report["fallback_used"])
        self.assertEqual(len(SCENARIOS), len(report["records"]))
        self.assertEqual({"A", "B"}, set(report["deterministic_summary"]))
        self.assertIn("structural_note", report)


if __name__ == "__main__":
    unittest.main()
