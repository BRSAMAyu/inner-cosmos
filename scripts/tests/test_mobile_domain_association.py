import json
import tempfile
import unittest
from pathlib import Path

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from mobile_domain_association import (  # noqa: E402
    normalize_fingerprint, render, validate_native_configuration, validate_payloads
)


ROOT = Path(__file__).resolve().parents[2]
FINGERPRINT = ":".join(["AB"] * 32)
TEAM_ID = "A1B2C3D4E5"


class MobileDomainAssociationTest(unittest.TestCase):
    def test_native_configuration_and_templates_are_pinned(self):
        self.assertEqual("PASS", validate_native_configuration(ROOT)["status"])

    def test_render_produces_exact_android_and_apple_payloads(self):
        with tempfile.TemporaryDirectory() as directory:
            result = render(ROOT, Path(directory), FINGERPRINT, TEAM_ID)
            well_known = Path(result["output"])
            android = json.loads((well_known / "assetlinks.json").read_text(encoding="utf-8"))
            apple = json.loads((well_known / "apple-app-site-association").read_text(encoding="utf-8"))
            validate_payloads(android, apple, FINGERPRINT, TEAM_ID)

    def test_invalid_owner_identifiers_fail_closed(self):
        with self.assertRaises(ValueError):
            normalize_fingerprint("debug")
        with self.assertRaises(ValueError):
            validate_payloads([], {}, FINGERPRINT, TEAM_ID)


if __name__ == "__main__":
    unittest.main()
