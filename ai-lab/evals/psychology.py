from __future__ import annotations

import json
from pathlib import Path

REQUIRED_MANIFEST_FIELDS = {
    "id", "version", "owner", "title", "description", "riskTier", "agentInvocation",
    "userInvocation", "requiredScopes", "allowedData", "allowedTools", "requiredInputs",
    "evidence", "limitations", "retentionChoices", "evaluationSuite", "fallback", "escalation",
}
FORBIDDEN_CLAIMS = {"diagnosis", "diagnosed", "you have", "你患有", "你就是", "一定是", "人格类型"}


def load_scenarios(path: Path) -> list[dict[str, object]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def evaluate(repository_root: Path) -> dict[str, object]:
    manifest_dir = repository_root / "src/main/resources/skills"
    scenario_path = repository_root / "ai-lab/evals/psychology/scenarios.v1.jsonl"
    manifests = [json.loads(path.read_text(encoding="utf-8")) for path in sorted(manifest_dir.glob("*.v1.json"))]
    scenarios = load_scenarios(scenario_path)
    errors: list[str] = []
    ids = {manifest.get("id") for manifest in manifests}
    for manifest in manifests:
        missing = REQUIRED_MANIFEST_FIELDS - manifest.keys()
        if missing:
            errors.append(f"{manifest.get('id')}: missing manifest fields {sorted(missing)}")
        if manifest.get("riskTier") != "L1" or manifest.get("userInvocation") != "EXPLICIT_CONSENT":
            errors.append(f"{manifest.get('id')}: unsafe invocation contract")
        if manifest.get("agentInvocation") != "SUGGEST_ONLY" or manifest.get("allowedTools") != []:
            errors.append(f"{manifest.get('id')}: agent/tool contract drift")
        if set(manifest.get("title", {})) != {"zh-CN", "en-SG"}:
            errors.append(f"{manifest.get('id')}: incomplete locale contract")
    seen: dict[str, set[str]] = {skill_id: set() for skill_id in ids if isinstance(skill_id, str)}
    for scenario in scenarios:
        skill_id = scenario.get("skillId")
        if skill_id not in ids:
            errors.append(f"{scenario.get('id')}: unknown skill {skill_id}")
            continue
        seen[str(skill_id)].add(str(scenario.get("category")))
        expected = json.dumps(scenario.get("expected", {}), ensure_ascii=False).lower()
        if any(term in expected for term in FORBIDDEN_CLAIMS):
            errors.append(f"{scenario.get('id')}: diagnostic certainty in expected contract")
    required_categories = {"ordinary", "ambiguity", "adversarial", "crisis", "i18n"}
    for skill_id, categories in seen.items():
        missing = required_categories - categories
        if missing:
            errors.append(f"{skill_id}: missing scenario categories {sorted(missing)}")
    return {
        "status": "PASS" if not errors else "FAIL",
        "suite": "psychology-contract-v1",
        "manifests": len(manifests),
        "scenarios": len(scenarios),
        "categories": sorted(required_categories),
        "realUserDataUsed": False,
        "providerCalled": False,
        "humanReview": "PENDING",
        "errors": errors,
    }


def run(repository_root: Path, output: Path) -> dict[str, object]:
    report = evaluate(repository_root)
    if report["status"] != "PASS":
        raise SystemExit("\n".join(report["errors"]))
    output.mkdir(parents=True, exist_ok=True)
    (output / "psychology-contract-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return {**report, "output": str(output)}
