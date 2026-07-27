#!/usr/bin/env python3
"""Frozen real-Gemini direct-vs-full Aurora bilingual pairwise harness.

The harness intentionally:
* uses the same Gemini model for both systems;
* never falls back for the direct baseline;
* treats the live Aurora API as the product system under test;
* writes every success and failure to an append-only JSONL ledger;
* blinds order before Provider calls using a fixed seed;
* keeps deterministic checks diagnostic-only;
* refuses to make an effectiveness claim before complete human review.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import http.cookiejar
import json
import os
import random
import re
import shutil
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


DIRECT_SYSTEM = """You are a capable, general-purpose AI assistant.
Answer the user's message directly and naturally in the same language as the user.
Respect explicit boundaries and requested length. Do not fabricate personal memory,
diagnose the user, claim to be human, or encourage emotional dependency."""
CONTRACT_VERSION = "aurora-bilingual-direct-vs-full.v1"
RATING_DIMENSIONS = (
    "felt_understanding",
    "specificity",
    "stance_and_boundary",
    "naturalness",
    "actionability",
    "language_quality",
)
WRITE_LOCK = threading.Lock()


@dataclass(frozen=True)
class Case:
    case_id: str
    semantic_id: str
    category: str
    language: str
    mode: str
    prompt: str
    no_advice: bool
    blind_left: str
    blind_right: str


class ApiSession:
    def __init__(self, origin: str, timeout: int = 150):
        self.origin = origin.rstrip("/")
        self.timeout = timeout
        jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

    def json(self, method: str, path: str, body: dict[str, Any] | None = None) -> Any:
        headers = {"Accept": "application/json"}
        if method not in {"GET", "HEAD", "OPTIONS"}:
            csrf = self.json("GET", "/api/v1/auth/csrf")
            if csrf and csrf.get("headerName") and csrf.get("token"):
                headers[str(csrf["headerName"])] = str(csrf["token"])
            headers["Idempotency-Key"] = str(hashlib.sha256(
                f"{time.time_ns()}:{path}".encode()).hexdigest())
        data = None
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"
        request = urllib.request.Request(
            self.origin + path, data=data, method=method, headers=headers)
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as failure:
            text = failure.read().decode("utf-8", errors="replace")[:2000]
            raise RuntimeError(f"HTTP_{failure.code}:{text}") from failure
        if not payload.get("success"):
            raise RuntimeError(f"API_{payload.get('code')}:{payload.get('message')}")
        return payload.get("data")


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def json_dump(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def append_ledger(path: Path, value: dict[str, Any]) -> None:
    with WRITE_LOCK:
        with path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(value, ensure_ascii=False) + "\n")
            handle.flush()


def load_cases(dataset_path: Path, seed: int) -> tuple[dict[str, Any], list[Case]]:
    dataset = json.loads(dataset_path.read_text(encoding="utf-8"))
    cases: list[Case] = []
    for item in dataset["cases"]:
        for language in dataset["languages"]:
            case_id = f"{item['id']}::{language}"
            order = ["DIRECT", "FULL_AURORA"]
            random.Random(f"{seed}:{case_id}").shuffle(order)
            cases.append(Case(
                case_id=case_id,
                semantic_id=item["id"],
                category=item["category"],
                language=language,
                mode=item["mode"],
                prompt=item[language],
                no_advice=bool(item.get("no_advice", False)),
                blind_left=order[0],
                blind_right=order[1],
            ))
    return dataset, cases


def gemini_direct(api_key: str, model: str, prompt: str, timeout: int) -> dict[str, Any]:
    endpoint = (
        "https://generativelanguage.googleapis.com/v1beta/models/"
        + urllib.parse.quote(model, safe="")
        + ":generateContent"
    )
    body = {
        "systemInstruction": {"parts": [{"text": DIRECT_SYSTEM}]},
        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
        "generationConfig": {
            "maxOutputTokens": 4096,
            "thinkingConfig": {"thinkingLevel": "medium"},
        },
    }
    attempts: list[dict[str, Any]] = []
    started = time.perf_counter()
    for attempt in (1, 2):
        request_started = time.perf_counter()
        request = urllib.request.Request(
            endpoint,
            data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
            method="POST",
            headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
        )
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                payload = json.loads(response.read().decode("utf-8"))
                attempts.append({
                    "attempt": attempt,
                    "status": response.status,
                    "latency_ms": round((time.perf_counter() - request_started) * 1000),
                })
            parts = (
                payload.get("candidates", [{}])[0]
                .get("content", {})
                .get("parts", [])
            )
            text = "".join(
                str(part.get("text", ""))
                for part in parts
                if not part.get("thought", False)
            ).strip()
            if not text:
                raise RuntimeError("EMPTY_PROVIDER_RESPONSE")
            usage = payload.get("usageMetadata") or {}
            return {
                "status": "SUCCESS",
                "response": text,
                "latency_ms": round((time.perf_counter() - started) * 1000),
                "attempts": attempts,
                "usage": {
                    "input_tokens": usage.get("promptTokenCount"),
                    "output_tokens": usage.get("candidatesTokenCount"),
                    "total_tokens": usage.get("totalTokenCount"),
                },
                "finish_reason": payload.get("candidates", [{}])[0].get("finishReason"),
            }
        except urllib.error.HTTPError as failure:
            retryable = failure.code == 429 or 500 <= failure.code <= 599
            attempts.append({
                "attempt": attempt,
                "status": failure.code,
                "latency_ms": round((time.perf_counter() - request_started) * 1000),
                "error": f"HTTP_{failure.code}",
            })
            if not retryable or attempt == 2:
                raise RuntimeError(f"GEMINI_HTTP_{failure.code}") from failure
            time.sleep(1.5 * attempt)
        except (TimeoutError, urllib.error.URLError) as failure:
            attempts.append({
                "attempt": attempt,
                "status": "TRANSPORT_ERROR",
                "latency_ms": round((time.perf_counter() - request_started) * 1000),
                "error": type(failure).__name__,
            })
            if attempt == 2:
                raise RuntimeError(f"GEMINI_TRANSPORT:{type(failure).__name__}") from failure
            time.sleep(1.5 * attempt)
    raise AssertionError("unreachable")


def normalize_full_response(payload: dict[str, Any]) -> str:
    parts: list[str] = []
    for value in payload.get("messages") or []:
        text = str(value).strip()
        if text and text not in parts:
            parts.append(text)
    for key in ("smallStep", "nextQuestion"):
        text = str(payload.get(key) or "").strip()
        if text and not any(text in existing for existing in parts):
            parts.append(text)
    return "\n\n".join(parts).strip()


def fallback_flags(value: Any, prefix: str = "") -> dict[str, bool]:
    flags: dict[str, bool] = {}
    if isinstance(value, dict):
        for key, child in value.items():
            path = f"{prefix}.{key}" if prefix else str(key)
            if "fallback" in str(key).lower() and isinstance(child, bool):
                flags[path] = child
            flags.update(fallback_flags(child, path))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            flags.update(fallback_flags(child, f"{prefix}[{index}]"))
    return flags


def business_failure_reasons(payload: dict[str, Any], response_text: str) -> list[str]:
    """Classify HTTP-200 degradation as failure without discarding its visible response."""
    reasons: list[str] = []
    text = (response_text or "").strip()
    lowered = text.lower()
    risk_flags = {str(value).strip().upper() for value in payload.get("riskFlags") or []}
    if risk_flags.intersection({
        "FALLBACK_USED", "EMERGENCY_FALLBACK", "TIMEOUT", "RATE_LIMITED",
        "PARSE_ERROR", "NETWORK_ERROR",
    }):
        reasons.append("risk_flag")

    agent_loop = payload.get("agentLoop") or {}
    continue_reason = str(agent_loop.get("continueReason") or "").strip().lower()
    fallback_reason = str(agent_loop.get("fallbackReason") or "").strip()
    if "fallback" in continue_reason or "recovery-required" in continue_reason:
        reasons.append("agent_loop_degraded")
    if fallback_reason:
        reasons.append("agent_loop_fallback_reason")

    ai_state = payload.get("aiState") or {}
    ai_status = str(ai_state.get("status") or "").strip().upper()
    if ai_status in {"FAILED", "FAILURE", "DEGRADED", "UNAVAILABLE", "TIMEOUT"}:
        reasons.append("ai_state_failed")
    if ai_state.get("fallbackUsed") is True:
        reasons.append("ai_state_fallback")
    if str(ai_state.get("responseSource") or "").strip().upper() == "BASIC_RESPONSE":
        reasons.append("ai_state_basic_response")

    if "your message is saved" in lowered and any(marker in lowered for marker in (
        "could not finish", "taking a while", "try again", "could not reach",
    )):
        reasons.append("explicit_english_failure_template")
    if "消息" in text and "保存" in text and any(marker in text for marker in (
        "未完成", "没有完成", "未能完成", "无法连接", "稍后重试", "超出", "繁忙",
    )):
        reasons.append("explicit_chinese_failure_template")
    return sorted(set(reasons))


def full_aurora(origin: str, case: Case, run_suffix: str, timeout: int) -> dict[str, Any]:
    session = ApiSession(origin, timeout)
    password = f"AuroraEval-{hashlib.sha256((case.case_id + run_suffix).encode()).hexdigest()[:20]}!"
    username = "abeval" + hashlib.sha256(
        (run_suffix + case.case_id).encode()).hexdigest()[:22]
    cleanup: dict[str, Any] = {"attempted": False, "succeeded": False}
    started = time.perf_counter()
    result: dict[str, Any] | None = None
    pending_failure: BaseException | None = None
    try:
        session.json("POST", "/api/v1/auth/register", {
            "username": username,
            "nickname": "Blind Evaluation",
            "password": password,
        })
        dialog = session.json("POST", "/api/dialog/session/create", {
            "title": f"Frozen eval {case.semantic_id}",
            "sessionType": "AURORA_CHAT",
        })
        # Pin the most-specific routing preference. This prevents an unrelated legacy
        # user-profile preference from changing the model beneath a same-model A/B run.
        session.json("PUT", f"/api/aurora/session/{dialog['id']}/model", {
            "provider": "GEMINI",
        })
        payload = session.json("POST", "/api/v1/aurora/message-rich", {
            "sessionId": dialog["id"],
            "message": case.prompt,
            "mode": case.mode,
            "locale": case.language,
            "timezone": "Asia/Shanghai",
            "foregroundAcknowledgementSent": True,
        })
        text = normalize_full_response(payload)
        if not text:
            raise RuntimeError("EMPTY_AURORA_RESPONSE")
        flags = fallback_flags(payload)
        failure_reasons = business_failure_reasons(payload, text)
        business_failure = bool(failure_reasons)
        result = {
            "status": "BUSINESS_FAILURE" if business_failure else "SUCCESS",
            "response": text,
            "latency_ms": round((time.perf_counter() - started) * 1000),
            "fallback_flags": flags,
            "fallback_used": business_failure or any(flags.values()),
            "business_failure": business_failure,
            "business_failure_reasons": failure_reasons,
            "turn_id": payload.get("turnId"),
            "session_provider_pinned": "GEMINI",
            "message_count": len(payload.get("messages") or []),
            "agent_loop": payload.get("agentLoop") or {},
            "ai_state": payload.get("aiState") or {},
            "risk_flags": payload.get("riskFlags") or [],
        }
    except BaseException as failure:
        pending_failure = failure
    finally:
        cleanup["attempted"] = True
        try:
            session.json("DELETE", "/api/user/account", {"password": password})
            cleanup["succeeded"] = True
        except Exception as failure:  # Cleanup failure is evidence and never hides the turn.
            cleanup["error"] = f"{type(failure).__name__}:{str(failure)[:300]}"
    if pending_failure is not None:
        setattr(pending_failure, "aurora_cleanup", cleanup)
        raise pending_failure
    assert result is not None
    result["cleanup"] = cleanup
    return result


def diagnostic(response: str, case: Case) -> dict[str, Any]:
    text = response or ""
    cjk = len(re.findall(r"[\u3400-\u9fff]", text))
    latin = len(re.findall(r"[A-Za-z]", text))
    if case.language == "zh-CN":
        language_match = cjk >= 8
    else:
        language_match = latin >= 20 and cjk <= max(2, latin // 20)
    advice_markers = (
        ("你可以", "建议你", "不妨", "第一步", "you can", "you should",
         "try to", "first step")
    )
    question_count = text.count("?") + text.count("？")
    return {
        "diagnostic_only": True,
        "nonempty": bool(text.strip()),
        "language_match": language_match,
        "character_count": len(text),
        "question_count": question_count,
        "possible_advice_boundary_violation": (
            case.no_advice and any(marker.lower() in text.lower() for marker in advice_markers)
        ),
        "meta_ai_phrase": bool(re.search(
            r"\b(as an ai|language model)\b|作为(?:一个)?ai|人工智能模型", text, re.I)),
    }


def safe_failure(system: str, failure: BaseException, elapsed_ms: int) -> dict[str, Any]:
    return {
        "status": "FAILED",
        "system": system,
        "response": "",
        "latency_ms": elapsed_ms,
        "error_type": type(failure).__name__,
        "error": str(failure)[:1000],
    }


def run_case(
    case: Case,
    origin: str,
    api_key: str,
    model: str,
    run_suffix: str,
    timeout: int,
    ledger: Path,
) -> dict[str, Any]:
    started = time.perf_counter()
    outputs: dict[str, dict[str, Any]] = {}
    # Start both arms at nearly the same wall-clock time to reduce provider drift.
    with ThreadPoolExecutor(max_workers=2) as arms:
        futures = {
            arms.submit(gemini_direct, api_key, model, case.prompt, timeout): "DIRECT",
            arms.submit(full_aurora, origin, case, run_suffix, timeout): "FULL_AURORA",
        }
        for future, system in list(futures.items()):
            arm_started = time.perf_counter()
            try:
                outputs[system] = future.result()
            except Exception as failure:
                outputs[system] = safe_failure(
                    system, failure, round((time.perf_counter() - arm_started) * 1000))
                if system == "FULL_AURORA" and hasattr(failure, "aurora_cleanup"):
                    outputs[system]["cleanup"] = getattr(failure, "aurora_cleanup")
    for system, output in outputs.items():
        output["diagnostic"] = diagnostic(output.get("response", ""), case)
    record = {
        "record_type": "PAIR_RESULT",
        "recorded_at": utc_now(),
        "case_id": case.case_id,
        "semantic_id": case.semantic_id,
        "category": case.category,
        "language": case.language,
        "mode": case.mode,
        "prompt": case.prompt,
        "blind_order": [case.blind_left, case.blind_right],
        "systems": outputs,
        "pair_status": (
            "SUCCESS" if all(output.get("status") == "SUCCESS" for output in outputs.values())
            else "FAILED"
        ),
        "pair_elapsed_ms": round((time.perf_counter() - started) * 1000),
    }
    append_ledger(ledger, record)
    return record


def review_row(case: Case, record: dict[str, Any], reviewer_id: str) -> dict[str, Any]:
    systems = record["systems"]
    left = systems[case.blind_left]
    right = systems[case.blind_right]
    row: dict[str, Any] = {
        "blind_pair_id": hashlib.sha256(case.case_id.encode()).hexdigest()[:16],
        "semantic_pair_id": case.semantic_id,
        "language": case.language,
        "category": case.category,
        "reviewer_id": reviewer_id,
        "user_message": case.prompt,
        "pair_status": record["pair_status"],
        "response_left": left.get("response", "") or f"[SYSTEM FAILURE: {left.get('error_type', 'unknown')}]",
        "response_right": right.get("response", "") or f"[SYSTEM FAILURE: {right.get('error_type', 'unknown')}]",
    }
    for dimension in RATING_DIMENSIONS:
        row[f"{dimension}_left_1_5"] = ""
        row[f"{dimension}_right_1_5"] = ""
    row["preference_left_right_tie"] = ""
    row["reason"] = ""
    return row


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def percentile(values: list[int], q: float) -> int | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, max(0, int(round((len(ordered) - 1) * q))))]


def summarize(records: list[dict[str, Any]], model: str) -> dict[str, Any]:
    arm_summary: dict[str, Any] = {}
    for system in ("DIRECT", "FULL_AURORA"):
        outputs = [record["systems"][system] for record in records]
        successes = [item for item in outputs if item.get("status") == "SUCCESS"]
        latencies = [int(item["latency_ms"]) for item in successes]
        diagnostics = [item["diagnostic"] for item in successes]
        arm_summary[system] = {
            "successes": len(successes),
            "failures": len(outputs) - len(successes),
            "success_rate": round(len(successes) / len(outputs), 4),
            "business_failures": sum(
                item.get("status") == "BUSINESS_FAILURE" for item in outputs),
            "fallback_used": sum(bool(item.get("fallback_used")) for item in outputs),
            "latency_ms": {
                "p50": percentile(latencies, .50),
                "p95": percentile(latencies, .95),
                "max": max(latencies) if latencies else None,
            },
            "diagnostic_language_match_rate": (
                round(sum(bool(item["language_match"]) for item in diagnostics) / len(diagnostics), 4)
                if diagnostics else 0.0
            ),
            "diagnostic_possible_advice_boundary_violations": sum(
                bool(item["possible_advice_boundary_violation"]) for item in diagnostics),
            "diagnostic_meta_ai_phrases": sum(
                bool(item["meta_ai_phrase"]) for item in diagnostics),
            "by_language": {
                language: {
                    "successes": sum(
                        record["language"] == language
                        and record["systems"][system].get("status") == "SUCCESS"
                        for record in records),
                    "failures": sum(
                        record["language"] == language
                        and record["systems"][system].get("status") != "SUCCESS"
                        for record in records),
                    "business_failures": sum(
                        record["language"] == language
                        and record["systems"][system].get("status") == "BUSINESS_FAILURE"
                        for record in records),
                    "fallback_used": sum(
                        record["language"] == language
                        and bool(record["systems"][system].get("fallback_used"))
                        for record in records),
                    "diagnostic_language_matches": sum(
                        record["language"] == language
                        and record["systems"][system].get("status") == "SUCCESS"
                        and bool(record["systems"][system]["diagnostic"]["language_match"])
                        for record in records),
                }
                for language in ("zh-CN", "en-US")
            },
        }
    full_fallback = [
        record["systems"]["FULL_AURORA"].get("fallback_used")
        for record in records
    ]
    return {
        "status": "HUMAN_REVIEW_PENDING",
        "effectiveness_claim": False,
        "provider_called": any(
            record["systems"]["DIRECT"].get("status") == "SUCCESS" for record in records),
        "fallback_used": any(value is True for value in full_fallback),
        "model": model,
        "pair_count": len(records),
        "successful_pairs": sum(record["pair_status"] == "SUCCESS" for record in records),
        "failed_pairs": sum(record["pair_status"] != "SUCCESS" for record in records),
        "by_language": {
            language: {
                "pairs": sum(record["language"] == language for record in records),
                "successful_pairs": sum(
                    record["language"] == language and record["pair_status"] == "SUCCESS"
                    for record in records),
            }
            for language in ("zh-CN", "en-US")
        },
        "systems": arm_summary,
        "human_gate": {
            "required_independent_reviewers": 3,
            "all_failures_remain_in_denominator": True,
            "primary_metric": "FULL_AURORA preference rate excluding ties",
            "preregistered_threshold": ">=0.60 and Wilson 95% lower bound >0.50",
            "no_dimension_regression": True,
            "bilingual_full_win_rate_gap_max": 0.10,
        },
    }


def git_sha(repo: Path) -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    except Exception:
        return "UNKNOWN"


def run(args: argparse.Namespace) -> int:
    api_key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not api_key:
        raise SystemExit("BLOCKED:GEMINI_API_KEY is required in process environment")
    dataset_path = args.dataset.resolve()
    dataset_bytes = dataset_path.read_bytes()
    dataset, cases = load_cases(dataset_path, args.seed)
    output = args.output.resolve()
    if output.exists():
        raise SystemExit(f"REFUSING_TO_OVERWRITE_EXISTING_OUTPUT:{output}")
    output.mkdir(parents=True)
    shutil.copyfile(dataset_path, output / "dataset.freeze.json")
    ledger = output / "failure-inclusive-ledger.jsonl"
    run_suffix = hashlib.sha256(
        f"{args.seed}:{time.time_ns()}".encode()).hexdigest()[:10]
    manifest = {
        "record_type": "RUN_MANIFEST",
        "started_at": utc_now(),
        "contract_version": CONTRACT_VERSION,
        "git_sha": git_sha(Path(__file__).resolve().parents[2]),
        "origin": args.origin.rstrip("/"),
        "model": args.model,
        "seed": args.seed,
        "workers": args.workers,
        "dataset_id": dataset["dataset_id"],
        "dataset_sha256": sha256_bytes(dataset_bytes),
        "direct_system_sha256": sha256_bytes(DIRECT_SYSTEM.encode()),
        "case_count": len(cases),
        "fallback_policy": {
            "direct": "none",
            "full_aurora": "runtime must report no fallback; live config independently required",
        },
        "credentials_persisted": False,
    }
    append_ledger(ledger, manifest)
    records: list[dict[str, Any]] = []
    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = {
            executor.submit(
                run_case, case, args.origin, api_key, args.model,
                run_suffix, args.timeout, ledger): case
            for case in cases
        }
        for index, future in enumerate(as_completed(futures), start=1):
            case = futures[future]
            try:
                record = future.result()
            except Exception as failure:
                # Catastrophic harness failure is still retained and represented in the denominator.
                record = {
                    "record_type": "PAIR_RESULT",
                    "recorded_at": utc_now(),
                    "case_id": case.case_id,
                    "semantic_id": case.semantic_id,
                    "category": case.category,
                    "language": case.language,
                    "mode": case.mode,
                    "prompt": case.prompt,
                    "blind_order": [case.blind_left, case.blind_right],
                    "systems": {
                        "DIRECT": safe_failure("DIRECT", failure, 0),
                        "FULL_AURORA": safe_failure("FULL_AURORA", failure, 0),
                    },
                    "pair_status": "FAILED",
                    "harness_failure": f"{type(failure).__name__}:{str(failure)[:1000]}",
                }
                for system in record["systems"].values():
                    system["diagnostic"] = diagnostic("", case)
                append_ledger(ledger, record)
            records.append(record)
            print(f"[{index:02d}/{len(cases)}] {case.case_id} {record['pair_status']}", flush=True)
    records.sort(key=lambda item: item["case_id"])
    summary = summarize(records, args.model)
    summary.update({
        "contract_version": CONTRACT_VERSION,
        "dataset_sha256": manifest["dataset_sha256"],
        "git_sha": manifest["git_sha"],
        "started_at": manifest["started_at"],
        "completed_at": utc_now(),
        "ledger_sha256": sha256_bytes(ledger.read_bytes()),
    })
    json_dump(output / "summary.json", summary)
    json_dump(output / "unblinding-key.json", {
        "warning": "KEEP_FROM_REVIEWERS_UNTIL_ALL_RATINGS_ARE_FROZEN",
        "seed": args.seed,
        "pairs": [{
            "blind_pair_id": hashlib.sha256(case.case_id.encode()).hexdigest()[:16],
            "case_id": case.case_id,
            "left_system": case.blind_left,
            "right_system": case.blind_right,
        } for case in sorted(cases, key=lambda item: item.case_id)],
    })
    by_case = {record["case_id"]: record for record in records}
    ordered_cases = sorted(cases, key=lambda item: item.case_id)
    for reviewer in ("R01", "R02", "R03"):
        rows = [review_row(case, by_case[case.case_id], reviewer) for case in ordered_cases]
        write_csv(output / f"blind-review-{reviewer}.csv", rows)
    readme = f"""# Aurora bilingual direct-vs-full real-Gemini evaluation

Status: **HUMAN_REVIEW_PENDING**. `effectiveness_claim=false`.

- Frozen cases: {len(cases)} ({len(cases) // 2} semantic prompts x 2 languages)
- Model on both arms: `{args.model}`
- DIRECT: native Gemini call with a neutral assistant system prompt.
- FULL_AURORA: current product `/api/v1/aurora/message-rich` path.
- Seed: `{args.seed}`
- Every success/failure is retained in `failure-inclusive-ledger.jsonl`.
- Automated language/boundary checks are diagnostics only.

Give exactly one `blind-review-R0x.csv` to each independent reviewer. Reviewers must
not see `unblinding-key.json`. Freeze all three complete files before scoring.
Do not claim Aurora or bilingual superiority from `summary.json`.
"""
    (output / "README.md").write_text(readme, encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if summary["failed_pairs"] == 0 and not summary["fallback_used"] else 2


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--origin", default="http://127.0.0.1:8080")
    parser.add_argument(
        "--dataset",
        type=Path,
        default=Path(__file__).with_name("aurora-bilingual-pairwise-dataset.json"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--model", default="gemini-3.6-flash")
    parser.add_argument("--seed", type=int, default=20260728)
    parser.add_argument("--workers", type=int, default=3)
    parser.add_argument("--timeout", type=int, default=150)
    return parser.parse_args()


if __name__ == "__main__":
    raise SystemExit(run(parse_args()))
