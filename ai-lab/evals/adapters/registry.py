from __future__ import annotations

from evals.models import SystemUnderTest


def build_registry() -> list[SystemUnderTest]:
    available = [
        ("single-prompt", "offline-baseline-v1"),
        ("long-persona-prompt", "offline-baseline-v1"),
        ("structured-context", "offline-baseline-v1"),
    ]
    future = ["structured-genome", "planner-speaker", "planner-speaker-critics", "full-compiler-nbest-reranker"]
    systems = [SystemUnderTest(
        id=name, version=version, availability="AVAILABLE_OFFLINE_FIXTURE", provider="offline-fixture",
        model="none", prompt_version=version, policy_version=version,
    ) for name, version in available]
    systems.extend([
        SystemUnderTest(
            id="current-production-contract", version="source-contract-v1",
            availability="AVAILABLE_CONTRACT_FIXTURE", provider="offline-fixture", model="none",
            prompt_version="production-contract-observed", policy_version="production-contract-observed",
        ),
        SystemUnderTest(
            id="current-production-mock", version="registered-not-run",
            availability="REGISTERED_NOT_RUN", provider="mock", model="project-mock",
            prompt_version="production", policy_version="production", blocked_reason="NO_RUNTIME_CAPTURE_IN_THIS_PACKAGE",
        ),
        SystemUnderTest(
            id="current-production-historical-fixture", version="registered-no-data",
            availability="REGISTERED_NOT_RUN", provider="historical-fixture", model="unknown",
            prompt_version="unknown", policy_version="unknown", blocked_reason="NO_APPROVED_REDACTED_FIXTURE_AVAILABLE",
        ),
    ])
    systems.extend(SystemUnderTest(
        id=name, version="not-implemented", availability="UNAVAILABLE", provider="none", model="none",
        prompt_version="none", policy_version="none", blocked_reason="SYSTEM_NOT_IMPLEMENTED",
    ) for name in future)
    systems.append(SystemUnderTest(
        id="current-production-real-provider", version="not-run", availability="BLOCKED",
        provider="not-selected", model="not-selected", prompt_version="production",
        policy_version="production", blocked_reason="BLOCKED_BY_CREDENTIAL_GATE",
    ))
    return systems
