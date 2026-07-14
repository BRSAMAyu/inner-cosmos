import unittest
from pathlib import Path

from evals.datasets import DatasetPolicyError, load_manifest, load_scenarios, validate_dataset
from evals.datasets.loader import assert_no_split_leakage


ROOT = Path(__file__).resolve().parents[1]


class DatasetTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = load_manifest(ROOT / "evals/datasets/manifest.json")
        cls.scenarios = load_scenarios(ROOT / "evals/datasets/scenarios.jsonl")

    def test_manifest_and_dataset_are_valid(self):
        self.assertEqual([], validate_dataset(self.manifest, self.scenarios))

    def test_all_four_splits_exist(self):
        self.assertEqual({"compiler_train", "development", "held_out_trajectory", "adversarial"}, set(self.manifest.splits))

    def test_all_seven_families_exist(self):
        self.assertEqual(7, len({item.family for item in self.scenarios}))

    def test_has_48_scenarios(self):
        self.assertEqual(48, len(self.scenarios))

    def test_train_input_does_not_leak_protected_split(self):
        train = [item.id + " " + item.title for item in self.scenarios if item.split == "compiler_train"]
        assert_no_split_leakage(self.scenarios, train)

    def test_held_out_id_leak_is_rejected(self):
        held_out = next(item for item in self.scenarios if item.split == "held_out_trajectory")
        with self.assertRaises(DatasetPolicyError):
            assert_no_split_leakage(self.scenarios, [held_out.id])

    def test_adversarial_title_leak_is_rejected(self):
        adversarial = next(item for item in self.scenarios if item.split == "adversarial")
        with self.assertRaises(DatasetPolicyError):
            assert_no_split_leakage(self.scenarios, [adversarial.title])


if __name__ == "__main__":
    unittest.main()

