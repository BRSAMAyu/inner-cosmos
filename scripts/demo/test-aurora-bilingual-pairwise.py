#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import sys
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("run-aurora-bilingual-pairwise.py")
SPEC = importlib.util.spec_from_file_location("aurora_pairwise", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class BusinessFailureFixtureTest(unittest.TestCase):
    def test_http_200_business_failures_are_not_successes(self) -> None:
        fixture_path = Path(__file__).with_name("fixtures") / (
            "aurora-business-failure-fixtures.json")
        fixtures = json.loads(fixture_path.read_text(encoding="utf-8"))
        for fixture in fixtures:
            with self.subTest(fixture=fixture["id"]):
                reasons = MODULE.business_failure_reasons(
                    fixture["payload"], fixture["response"])
                self.assertEqual(bool(reasons), fixture["expected_failure"])

    def test_fixture_covers_every_supported_signal_family(self) -> None:
        self.assertIn(
            "explicit_english_failure_template",
            MODULE.business_failure_reasons({}, (
                "Your message is saved, but Aurora could not finish the reply. "
                "Please try again.")))
        self.assertIn(
            "explicit_chinese_failure_template",
            MODULE.business_failure_reasons(
                {}, "你的消息已经保存，但这次未完成，请稍后重试。"))
        self.assertIn(
            "risk_flag",
            MODULE.business_failure_reasons({"riskFlags": ["FALLBACK_USED"]}, "ok"))
        self.assertIn(
            "agent_loop_degraded",
            MODULE.business_failure_reasons({
                "agentLoop": {"continueReason": "provider-recovery-required"}}, "ok"))
        self.assertIn(
            "ai_state_failed",
            MODULE.business_failure_reasons({"aiState": {"status": "FAILED"}}, "ok"))


if __name__ == "__main__":
    unittest.main()
