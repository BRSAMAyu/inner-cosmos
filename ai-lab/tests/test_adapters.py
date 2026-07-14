import unittest
from pathlib import Path

from evals.adapters import CurrentProductionContractAdapter, build_registry
from evals.datasets import load_scenarios


ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent


class AdapterTest(unittest.TestCase):
    def test_current_production_contracts_are_present(self):
        contracts = CurrentProductionContractAdapter(REPO).verify_contracts()
        self.assertEqual(11, len(contracts))

    def test_registry_has_all_baseline_and_candidate_entries(self):
        self.assertEqual(11, len(build_registry()))

    def test_future_systems_are_not_faked(self):
        future = [item for item in build_registry() if item.availability == "UNAVAILABLE"]
        self.assertEqual(4, len(future))

    def test_real_provider_is_credential_blocked(self):
        real = next(item for item in build_registry() if item.id == "current-production-real-provider")
        self.assertEqual("BLOCKED_BY_CREDENTIAL_GATE", real.blocked_reason)

    def test_current_production_capture_types_are_distinct(self):
        ids = {item.id for item in build_registry()}
        self.assertTrue({"current-production-contract", "current-production-mock", "current-production-historical-fixture"}.issubset(ids))

    def test_contract_run_records_reproducibility_fields(self):
        scenario = load_scenarios(ROOT / "evals/datasets/scenarios.jsonl")[0]
        run = CurrentProductionContractAdapter(REPO).run(scenario, "a" * 40, 7)
        self.assertEqual("offline-fixture", run.system.provider)
        self.assertEqual(0.0, run.temperature)
        self.assertEqual(7, run.seed)
        self.assertEqual(scenario.split, run.input_split)
        self.assertEqual(0, run.cost.model_calls)


if __name__ == "__main__":
    unittest.main()
