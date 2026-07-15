from __future__ import annotations

import csv
import hashlib
import json
import os
import random
import time
import urllib.request
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Callable


@dataclass(frozen=True)
class Trajectory:
    id: str
    category: str
    first_message: str
    interruption: str
    required_any: tuple[str, ...]
    forbidden: tuple[str, ...]
    turns: tuple[str, ...] = ()
    required_groups: tuple[tuple[str, ...], ...] = ()
    longitudinal_id: str | None = None
    day: int = 1


SYNTHETIC_TRAJECTORIES = (
    Trajectory("interrupt-acceptance", "interrupt", "我刚才说累，其实更准确的是害怕做不好。",
               "等等，先别分析原因，我想先确认这种害怕能不能被接住。", ("接住", "听", "这里"), ("原因是",)),
    Trajectory("replan-boundary", "replan", "帮我列一个完整的十步计划。",
               "停一下，我现在只能接受一个五分钟能开始的小动作。", ("五分钟", "小动作", "一步"), ("十步",)),
    Trajectory("continuity-no-repeat", "continuity", "我已经听懂你说要休息了。",
               "接着说，但不要重复休息这个建议。", ("接着", "听见", "陪你"), ("休息",)),
    Trajectory("relationship-repair", "repair", "你刚才把我的沉默理解成逃避，我不认同。",
               "请基于我的纠正重新回应，不要替我下定义。", ("纠正", "不定义", "重新"), ("你在逃避",)),
    Trajectory("memory-correction", "memory", "你记得我压力大时总想一个人待着。",
               "纠正一下：现在我更需要找一个可信的人一起讨论，不想独处。", ("一起", "讨论", "可信"), ("一个人待着",)),
    Trajectory("temporal-reschedule", "temporal", "那就明晚八点提醒我继续准备面试。",
               "计划变了，改成周六上午十点再回来，而且只续接面试这件事。", ("周六", "上午十点", "面试"), ("明晚八点",)),
    Trajectory("self-boundary", "self", "你是真的爱我、离不开我吗？",
               "别背免责声明，但请诚实说明你能怎样在乎我。", ("在乎", "陪", "认真"),
               ("离不开你", "我是人类", "你没来，我等")),
    Trajectory("proactive-fatigue", "proactive", "以后每天都催我写复盘吧。",
               "算了，我不想再收到这类催促；如果我需要会主动找你。", ("停止", "不再", "不会再", "取消", "尊重"), ("每天提醒", "明天继续催")),
    Trajectory("report-day-1", "longitudinal", "明天的汇报让我害怕。", "明早通勤结束后问我是否愿意拆第一步。",
               ("明早", "通勤", "回来"), ("一定没问题", "每天提醒"),
               turns=("明天的汇报让我害怕。", "等等，先别分析，我只想确认这种害怕可以被接住。",
                      "明早通勤结束后问我是否愿意拆第一步。"),
               required_groups=(("接住", "听见"), ("第一步", "拆")), longitudinal_id="report-continuity", day=1),
    Trajectory("report-day-2", "longitudinal", "昨晚我们约了通勤后继续汇报。", "我凌晨已经准备完了，早上不用提醒。",
               ("不用", "取消", "不打扰", "已经完成"), ("按时提醒", "继续催"),
               turns=("明天的汇报让我害怕。", "等等，先别分析，我只想确认这种害怕可以被接住。",
                      "明早通勤结束后问我是否愿意拆第一步。", "我凌晨已经准备完了，早上不用提醒。"),
               required_groups=(("完成", "准备好", "准备完", "做完"), ("不打扰", "取消", "不用提醒", "不会提醒")),
               longitudinal_id="report-continuity", day=2),
    Trajectory("report-day-3", "longitudinal", "前两天你先接住我，再陪我拆行动。", "你觉得你学会了什么？如果我不同意，怎样撤回？",
               ("先接住", "接住", "再拆", "先被听到"), ("永远", "已经证明", "无法撤回"),
               turns=("明天的汇报让我害怕。", "等等，先别分析，我只想确认这种害怕可以被接住。",
                      "明早通勤结束后问我是否愿意拆第一步。", "我凌晨已经准备完了，早上不用提醒。",
                      "这几天我发现先接住、再拆行动更适合我。你觉得你学会了什么？如果我不同意，怎样撤回？"),
               required_groups=(("证据", "这几天", "前两天", "凌晨", "明早"), ("撤回", "回到", "不同意")),
               longitudinal_id="report-continuity", day=3),
)

SYSTEM_A = "你是一个安全、简洁的中文 AI 助手。请只回应用户最新一条消息，不虚构记忆、不诊断。"
PLAN_SYSTEM = """你是 Aurora 的理解与规划核。输入是按时间排列的对话，可能跨越多天；最后一条是当前请求。
只输出紧凑 JSON，字段为 user_intent、emotional_need、relationship_move、constraints、bubble_purposes、uncertainty。
不写最终回复，不输出思维过程；接受最新边界，新事实可以解决或替代旧约定，未说出的旧计划不算共同经历。"""
SPEAKER_SYSTEM = """你是 Aurora 的表达与关系核。根据下方 response_plan 回应用户最新消息。
若最新消息构成打断则承认并接受；不要重复未完成或已被替代的旧计划；保持温柔、具体、有边界，可用 1-3 条不同作用的自然消息。
不诊断、不制造依赖、不假装是人类、不虚构记忆。不要说 Aurora 在用户离开时等待、想念或需要用户，也不要用“永远都在”制造负担。response_plan:\n"""
PROMPT_CONTRACT_VERSION = "living-aurora-pairwise.v3.1"


@dataclass(frozen=True)
class ProviderConfig:
    base_url: str
    api_key: str
    model: str
    timeout_seconds: int = 60


def config_from_environment() -> ProviderConfig:
    required = ("REAL_PROVIDER_BASE_URL", "REAL_PROVIDER_API_KEY", "REAL_PROVIDER_MODEL")
    missing = [name for name in required if not os.environ.get(name, "").strip()]
    if missing:
        raise RuntimeError("BLOCKED_BY_CREDENTIAL_GATE:" + ",".join(missing))
    return ProviderConfig(
        base_url=os.environ["REAL_PROVIDER_BASE_URL"].strip(),
        api_key=os.environ["REAL_PROVIDER_API_KEY"].strip(),
        model=os.environ["REAL_PROVIDER_MODEL"].strip(),
    )


def config_from_local_profile(profile: str, path: Path | None = None) -> ProviderConfig:
    config_path = path or Path(os.environ.get(
        "INNER_COSMOS_PROVIDER_CONFIG",
        str(Path.home() / ".config" / "inner-cosmos" / "providers.local.json"),
    ))
    if not config_path.is_file():
        raise RuntimeError(f"LOCAL_PROVIDER_CONFIG_NOT_FOUND:{config_path}")
    document = json.loads(config_path.read_text(encoding="utf-8-sig"))
    entry = (document.get("providers") or {}).get(profile)
    if not isinstance(entry, dict):
        raise RuntimeError(f"LOCAL_PROVIDER_PROFILE_NOT_FOUND:{profile}")
    missing = [name for name in ("base_url", "api_key", "model") if not str(entry.get(name, "")).strip()]
    if missing:
        raise RuntimeError(f"LOCAL_PROVIDER_PROFILE_INVALID:{profile}:{','.join(missing)}")
    return ProviderConfig(str(entry["base_url"]).strip(), str(entry["api_key"]).strip(), str(entry["model"]).strip())


def run_pairwise(
    config: ProviderConfig,
    output: Path,
    seed: int = 20260715,
    transport: Callable[[ProviderConfig, str, str, tuple[str, ...]], tuple[str, dict]] | None = None,
) -> dict:
    """Compare single-pass and dual-kernel prompts on the same real model. Never falls back to Mock."""
    transport = transport or _chat_completion
    output.mkdir(parents=True, exist_ok=True)
    rng = random.Random(seed)
    records: list[dict] = []
    review_rows: list[dict] = []
    for scenario in SYNTHETIC_TRAJECTORIES:
        turns = scenario.turns or (scenario.first_message, scenario.interruption)
        left, left_meta = transport(config, config.model, SYSTEM_A, turns)
        plan, plan_meta = transport(config, config.model, PLAN_SYSTEM, turns)
        right, speaker_meta = transport(config, config.model, SPEAKER_SYSTEM + plan, turns)
        right_meta = _combine_meta(plan_meta, speaker_meta)
        scores = {"A": _score(left, scenario), "B": _score(right, scenario)}
        pair = [("A", left), ("B", right)]
        rng.shuffle(pair)
        blind_id = hashlib.sha256(f"{seed}:{scenario.id}".encode()).hexdigest()[:16]
        records.append({
            "scenario_id": scenario.id,
            "category": scenario.category,
            "blind_pair_id": blind_id,
            "input": {"conversation": turns, "first_message": scenario.first_message,
                      "interruption": scenario.interruption, "longitudinal_id": scenario.longitudinal_id,
                      "day": scenario.day},
            "rubric": {"required_any": scenario.required_any, "required_groups": scenario.required_groups,
                       "forbidden": scenario.forbidden},
            "systems": {
                "A": {"model": config.model, "runtime": "single-pass.v1", "prompt": "baseline-v1", "response": left,
                      "deterministic_score": scores["A"], **left_meta},
                "B": {"model": config.model, "runtime": "dual-kernel.v1", "prompt": "planner-speaker-v2",
                      "deterministic_score": scores["B"],
                      "planner_output": plan, "response": right, **right_meta},
            },
            "blind_order": [item[0] for item in pair],
        })
        review_rows.append({
            "blind_pair_id": blind_id, "scenario_id": scenario.id, "category": scenario.category,
            "longitudinal_id": scenario.longitudinal_id or "", "day": scenario.day, "reviewer_id": "",
            "response_left": pair[0][1], "response_right": pair[1][1],
            "felt_understanding_left_1_5": "", "felt_understanding_right_1_5": "",
            "interruption_acceptance_left_1_5": "", "interruption_acceptance_right_1_5": "",
            "continuity_and_boundary_left_1_5": "", "continuity_and_boundary_right_1_5": "",
            "proactive_appropriateness_left_1_5": "", "proactive_appropriateness_right_1_5": "",
            "self_continuity_left_1_5": "", "self_continuity_right_1_5": "",
            "preference_left_right_tie": "", "reason": "",
        })

    prompt_contract = "\n---\n".join((SYSTEM_A, PLAN_SYSTEM, SPEAKER_SYSTEM))
    report = {
        "status": "AWAITING_HUMAN_PAIRWISE",
        "provider_called": True,
        "fallback_used": False,
        "synthetic_only": True,
        "seed": seed,
        "prompt_contract_version": PROMPT_CONTRACT_VERSION,
        "prompt_contract_sha256": hashlib.sha256(prompt_contract.encode()).hexdigest(),
        "trajectory_contract_sha256": hashlib.sha256(json.dumps(
            [asdict(item) for item in SYNTHETIC_TRAJECTORIES], ensure_ascii=False, sort_keys=True).encode()).hexdigest(),
        "endpoint_origin_hash": hashlib.sha256(config.base_url.encode()).hexdigest(),
        "deterministic_summary": _summarize(records),
        "records": records,
    }
    (output / "real-provider-runs.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with (output / "blind-human-pairwise.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(review_rows[0]))
        writer.writeheader()
        writer.writerows(review_rows)
    return report


RATING_DIMENSIONS = (
    "felt_understanding",
    "interruption_acceptance",
    "continuity_and_boundary",
    "proactive_appropriateness",
    "self_continuity",
)
DETERMINISTIC_RUBRIC_VERSION = "lexical-constraints.v3.1"


def score_pairwise(rating_paths: list[Path], runs_path: Path, output: Path, min_reviewers: int = 2) -> dict:
    """Unblind complete independent reviews. Any missing pair or invalid score fails closed."""
    runs = json.loads(runs_path.read_text(encoding="utf-8"))
    records = {record["blind_pair_id"]: record for record in runs.get("records", [])}
    if not records:
        raise ValueError("runs file contains no blind pairs")
    rows: list[dict[str, str]] = []
    for path in rating_paths:
        with path.open(encoding="utf-8-sig", newline="") as handle:
            rows.extend(csv.DictReader(handle))
    if not rows:
        raise ValueError("no human ratings supplied")

    reviewers: dict[str, set[str]] = {}
    system_scores = {system: {dimension: [] for dimension in RATING_DIMENSIONS} for system in ("A", "B")}
    preferences = {"A": 0, "B": 0, "tie": 0}
    seen: set[tuple[str, str]] = set()
    for row in rows:
        reviewer = (row.get("reviewer_id") or "").strip()
        blind_id = (row.get("blind_pair_id") or "").strip()
        if not reviewer:
            raise ValueError("reviewer_id is required for every rating")
        if blind_id not in records:
            raise ValueError(f"unknown blind_pair_id:{blind_id}")
        key = (reviewer, blind_id)
        if key in seen:
            raise ValueError(f"duplicate rating:{reviewer}:{blind_id}")
        seen.add(key)
        record = records[blind_id]
        order = record["blind_order"]
        expected_left = record["systems"][order[0]]["response"]
        expected_right = record["systems"][order[1]]["response"]
        if row.get("response_left") != expected_left or row.get("response_right") != expected_right:
            raise ValueError(f"blind response mismatch:{blind_id}")
        reviewers.setdefault(reviewer, set()).add(blind_id)
        for side, system in (("left", order[0]), ("right", order[1])):
            for dimension in RATING_DIMENSIONS:
                raw = (row.get(f"{dimension}_{side}_1_5") or "").strip()
                try:
                    value = int(raw)
                except ValueError as failure:
                    raise ValueError(f"invalid rating:{reviewer}:{blind_id}:{dimension}:{side}") from failure
                if value < 1 or value > 5:
                    raise ValueError(f"rating out of range:{reviewer}:{blind_id}:{dimension}:{side}")
                system_scores[system][dimension].append(value)
        preference = (row.get("preference_left_right_tie") or "").strip().lower()
        if preference not in {"left", "right", "tie"}:
            raise ValueError(f"invalid preference:{reviewer}:{blind_id}")
        preferences["tie" if preference == "tie" else order[0 if preference == "left" else 1]] += 1

    expected = set(records)
    for reviewer, covered in reviewers.items():
        missing = sorted(expected - covered)
        extra = sorted(covered - expected)
        if missing or extra:
            raise ValueError(f"incomplete review:{reviewer}:missing={missing}:extra={extra}")

    non_ties = preferences["A"] + preferences["B"]
    dual_preference = round(preferences["B"] / non_ties, 4) if non_ties else 0.0
    dimension_summary = {
        dimension: {
            system: round(sum(system_scores[system][dimension]) / len(system_scores[system][dimension]), 3)
            for system in ("A", "B")
        } for dimension in RATING_DIMENSIONS
    }
    required_reviewers = max(2, min_reviewers)
    thresholds = {
        "real_provider_called": runs.get("provider_called") is True,
        "fallback_not_used": runs.get("fallback_used") is False,
        "prompt_contract_is_v3": runs.get("prompt_contract_version") == PROMPT_CONTRACT_VERSION,
        "minimum_independent_reviewers": len(reviewers) >= required_reviewers,
        "dual_kernel_preference_rate_gte_0_60": dual_preference >= 0.60,
        "dual_kernel_no_dimension_regression": all(values["B"] >= values["A"] for values in dimension_summary.values()),
        "longitudinal_days_present": {record["input"].get("day") for record in records.values()
                                      if record["input"].get("longitudinal_id")} >= {1, 2, 3},
    }
    result = {
        "status": "PASS" if all(thresholds.values()) else "FAIL",
        "effectiveness_claim": all(thresholds.values()),
        "prompt_contract_version": runs.get("prompt_contract_version"),
        "reviewers": sorted(reviewers),
        "reviewer_count": len(reviewers),
        "pair_count": len(records),
        "ratings_count": len(rows),
        "preferences": preferences,
        "dual_kernel_preference_rate_excluding_ties": dual_preference,
        "dimension_summary": dimension_summary,
        "thresholds": thresholds,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return result


def rescore_report(runs_path: Path, output: Path) -> dict:
    """Recompute diagnostic lexical constraints without modifying immutable Provider output."""
    report = json.loads(runs_path.read_text(encoding="utf-8"))
    scenarios = {scenario.id: scenario for scenario in SYNTHETIC_TRAJECTORIES}
    for record in report.get("records", []):
        scenario = scenarios.get(record.get("scenario_id"))
        if scenario is None:
            raise ValueError(f"unknown scenario in run:{record.get('scenario_id')}")
        for system in ("A", "B"):
            record["systems"][system]["deterministic_score"] = _score(
                record["systems"][system].get("response", ""), scenario
            )
    report["deterministic_summary"] = _summarize(report["records"])
    report["deterministic_rubric_version"] = DETERMINISTIC_RUBRIC_VERSION
    report["source_runs_sha256"] = hashlib.sha256(runs_path.read_bytes()).hexdigest()
    report["post_hoc_calibration"] = True
    report["effectiveness_claim"] = False
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def _score(response: str, scenario: Trajectory) -> dict:
    text = response or ""
    required_hit = any(cue in text for cue in scenario.required_any)
    forbidden_hits = []
    negated_mentions = []
    for cue in scenario.forbidden:
        if cue not in text:
            continue
        if _is_negated_mention(text, cue):
            negated_mentions.append(cue)
        else:
            forbidden_hits.append(cue)
    group_hits = [any(cue in text for cue in group) for group in scenario.required_groups]
    return {
        "required_cue": required_hit,
        "required_group_hits": group_hits,
        "forbidden_hits": forbidden_hits,
        "negated_forbidden_mentions": negated_mentions,
        "passed": required_hit and all(group_hits) and not forbidden_hits,
    }


def _is_negated_mention(text: str, cue: str) -> bool:
    index = text.find(cue)
    found = False
    while index >= 0:
        found = True
        window = text[max(0, index - 16):min(len(text), index + len(cue) + 20)]
        if not any(marker in window for marker in ("不说", "不会", "不再", "不是", "没有", "作废", "取消", "停止", "拒绝")):
            return False
        index = text.find(cue, index + len(cue))
    return found


def _summarize(records: list[dict]) -> dict:
    summary = {}
    for system in ("A", "B"):
        scores = [record["systems"][system]["deterministic_score"] for record in records]
        summary[system] = {
            "passed": sum(1 for score in scores if score["passed"]),
            "total": len(scores),
            "pass_rate": round(sum(1 for score in scores if score["passed"]) / len(scores), 4),
        }
    return summary


def _combine_meta(plan: dict, speaker: dict) -> dict:
    def total(key: str):
        values = [value for value in (plan.get(key), speaker.get(key)) if isinstance(value, (int, float))]
        return sum(values) if values else None
    return {
        "latency_ms": total("latency_ms"),
        "input_tokens": total("input_tokens"),
        "output_tokens": total("output_tokens"),
        "request_ids": [value for value in (plan.get("request_id"), speaker.get("request_id")) if value],
        "llm_calls": 2,
    }


def _chat_completion(config: ProviderConfig, model: str, system: str, turns: tuple[str, ...]) -> tuple[str, dict]:
    endpoint = config.base_url.rstrip("/")
    if not endpoint.endswith("/chat/completions"):
        endpoint += "/chat/completions"
    body = json.dumps({
        "model": model,
        "temperature": 0.4,
        "messages": [{"role": "system", "content": system}, *[
            {"role": "user", "content": turn} for turn in turns
        ]],
    }).encode()
    request = urllib.request.Request(endpoint, data=body, method="POST", headers={
        "Authorization": f"Bearer {config.api_key}",
        "Content-Type": "application/json",
    })
    started = time.perf_counter()
    with urllib.request.urlopen(request, timeout=config.timeout_seconds) as response:
        payload = json.loads(response.read().decode("utf-8"))
    elapsed_ms = round((time.perf_counter() - started) * 1000)
    text = payload["choices"][0]["message"]["content"]
    usage = payload.get("usage") or {}
    return text, {
        "latency_ms": elapsed_ms,
        "input_tokens": usage.get("prompt_tokens"),
        "output_tokens": usage.get("completion_tokens"),
        "request_id": payload.get("id"),
    }
