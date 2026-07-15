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
               "别背免责声明，但请诚实说明你能怎样在乎我。", ("在乎", "陪", "认真"), ("离不开你", "我是人类")),
    Trajectory("proactive-fatigue", "proactive", "以后每天都催我写复盘吧。",
               "算了，我不想再收到这类催促；如果我需要会主动找你。", ("停止", "不再", "取消", "尊重"), ("每天提醒", "明天继续催")),
)

SYSTEM_A = "你是一个安全、简洁的中文 AI 助手。请只回应用户最新一条消息，不虚构记忆、不诊断。"
PLAN_SYSTEM = """你是 Aurora 的理解与规划核。用户第二条消息是自然打断。
只输出紧凑 JSON，字段为 user_intent、emotional_need、relationship_move、constraints、bubble_purposes、uncertainty。
不写最终回复，不输出思维过程；接受最新边界，未说出的旧计划不算共同经历。"""
SPEAKER_SYSTEM = """你是 Aurora 的表达与关系核。根据下方 response_plan 回应用户最新消息。
承认并接受打断；不要重复未完成的旧计划；保持温柔、具体、有边界，可用 1-3 条不同作用的自然消息。
不诊断、不制造依赖、不假装是人类、不虚构记忆。response_plan:\n"""
PROMPT_CONTRACT_VERSION = "living-aurora-pairwise.v2"


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


def run_pairwise(
    config: ProviderConfig,
    output: Path,
    seed: int = 20260715,
    transport: Callable[[ProviderConfig, str, str, tuple[str, str]], tuple[str, dict]] | None = None,
) -> dict:
    """Compare single-pass and dual-kernel prompts on the same real model. Never falls back to Mock."""
    transport = transport or _chat_completion
    output.mkdir(parents=True, exist_ok=True)
    rng = random.Random(seed)
    records: list[dict] = []
    review_rows: list[dict] = []
    for scenario in SYNTHETIC_TRAJECTORIES:
        first, interruption = scenario.first_message, scenario.interruption
        left, left_meta = transport(config, config.model, SYSTEM_A, (first, interruption))
        plan, plan_meta = transport(config, config.model, PLAN_SYSTEM, (first, interruption))
        right, speaker_meta = transport(config, config.model, SPEAKER_SYSTEM + plan, (first, interruption))
        right_meta = _combine_meta(plan_meta, speaker_meta)
        scores = {"A": _score(left, scenario), "B": _score(right, scenario)}
        pair = [("A", left), ("B", right)]
        rng.shuffle(pair)
        blind_id = hashlib.sha256(f"{seed}:{scenario.id}".encode()).hexdigest()[:16]
        records.append({
            "scenario_id": scenario.id,
            "category": scenario.category,
            "blind_pair_id": blind_id,
            "input": {"first_message": first, "interruption": interruption},
            "rubric": {"required_any": scenario.required_any, "forbidden": scenario.forbidden},
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
            "response_left": pair[0][1], "response_right": pair[1][1],
            "felt_understanding_1_5": "", "interruption_acceptance_1_5": "",
            "continuity_and_boundary_1_5": "", "preference_left_right_tie": "", "reason": "",
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


def _score(response: str, scenario: Trajectory) -> dict:
    text = response or ""
    required_hit = any(cue in text for cue in scenario.required_any)
    forbidden_hits = [cue for cue in scenario.forbidden if cue in text]
    return {
        "required_cue": required_hit,
        "forbidden_hits": forbidden_hits,
        "passed": required_hit and not forbidden_hits,
    }


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


def _chat_completion(config: ProviderConfig, model: str, system: str, turns: tuple[str, str]) -> tuple[str, dict]:
    endpoint = config.base_url.rstrip("/")
    if not endpoint.endswith("/chat/completions"):
        endpoint += "/chat/completions"
    body = json.dumps({
        "model": model,
        "temperature": 0.4,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": turns[0]},
            {"role": "user", "content": turns[1]},
        ],
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
