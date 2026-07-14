import unittest
from pathlib import Path

from evals.schemas.validator import validate_schema_documents


ROOT = Path(__file__).resolve().parents[1]


class SchemaTest(unittest.TestCase):
    def test_schema_documents_are_valid(self):
        self.assertEqual([], validate_schema_documents(ROOT / "evals/schemas"))

    def test_four_schema_documents_exist(self):
        self.assertEqual(4, len(list((ROOT / "evals/schemas").glob("*.schema.json"))))


if __name__ == "__main__":
    unittest.main()

