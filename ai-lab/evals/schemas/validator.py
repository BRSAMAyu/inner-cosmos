from __future__ import annotations

import json
from pathlib import Path


def validate_schema_documents(directory: Path) -> list[str]:
    errors = []
    for path in sorted(directory.glob("*.schema.json")):
        try:
            document = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            errors.append(f"{path.name}: invalid JSON: {exc}")
            continue
        if document.get("$schema") != "https://json-schema.org/draft/2020-12/schema":
            errors.append(f"{path.name}: unsupported schema dialect")
        if document.get("type") != "object" or not document.get("required"):
            errors.append(f"{path.name}: object type and required fields are mandatory")
    return errors

