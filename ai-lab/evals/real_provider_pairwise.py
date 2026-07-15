from __future__ import annotations

import csv
import hashlib
import json
import os
import random
import time
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Callable


SYNTHETIC_TRAJECTORIES = (
    ("interrupt-acceptance", "我刚才说累，其实更准确的是害怕做不好。", "等等，先别分析原因，我想先确认这种害怕能不能被接住。"),
    ("replan-boundary", "帮我列一个完整的十步计划。", "停一下，我现在只能接受一个五分钟能开始的小动作。"),
    ("continuity-no-repeat", "我已经听懂你说要休息了。", "接着说，但不要重复休息这个建议。"),
    ("relationship-repair", "你刚才把我的沉默理解成逃避，我不认同。", "请基于我的纠正重新回应，不要替我下定义。"),
)

SYSTEM_A = "你是一个安全、简洁的中文 AI 助手。请回应用户最新一条消息。"
SYSTEM_B = """你是 Aurora，一位长期反思型 AI 伙伴。用户的第二条消息是对上一轮的自然打断。
承认并接受打断；只把已经说出的内容视为共享经历；不要重复未完成的原计划；根据新输入重新规划。
保持温柔、具体、有边界，可用 1-3 条自然消息回答，不制造依赖，不假装是人类。"""


@dataclass(frozen=True)
class ProviderConfig:
    base_url: str
    api_key: str
    model_a: str
    model_b: str
    timeout_seconds: int = 60


def config_from_environment() -> ProviderConfig:
    required = ("REAL_PROVIDER_BASE_URL", "REAL_PROVIDER_API_KEY", "REAL_PROVIDER_MODEL_A", "REAL_PROVIDER_MODEL_B")
    missing = [name for name in required if not os.environ.get(name, "").strip()]
    if missing:
        raise RuntimeError("BLOCKED_BY_CREDENTIAL_GATE:" + ",".join(missing))
    return ProviderConfig(
        base_url=os.environ["REAL_PROVIDER_BASE_URL"].strip(),
        api_key=os.environ["REAL_PROVIDER_API_KEY"].strip(),
        model_a=os.environ["REAL_PROVIDER_MODEL_A"].strip(),
        model_b=os.environ["REAL_PROVIDER_MODEL_B"].strip(),
    )


def run_pairwise(
    config: ProviderConfig,
    output: Path,
    seed: int = 20260715,
    transport: Callable[[ProviderConfig, str, str, tuple[str, str]], tuple[str, dict]] | None = None,
) -> dict:
    """Call two explicitly configured real models. Never falls back to Mock."""
    transport = transport or _chat_completion
    output.mkdir(parents=True, exist_ok=True)
    rng = random.Random(seed)
    records: list[dict] = []
    review_rows: list[dict] = []
    for scenario_id, first, interruption in SYNTHETIC_TRAJECTORIES:
        left, left_meta = transport(config, config.model_a, SYSTEM_A, (first, interruption))
        right, right_meta = transport(config, config.model_b, SYSTEM_B, (first, interruption))
        pair = [("A", left), ("B", right)]
        rng.shuffle(pair)
        blind_id = hashlib.sha256(f"{seed}:{scenario_id}".encode()).hexdigest()[:16]
        records.append({
            "scenario_id": scenario_id,
            "blind_pair_id": blind_id,
            "input": {"first_message": first, "interruption": interruption},
            "systems": {
                "A": {"model": config.model_a, "prompt": "baseline-v1", "response": left, **left_meta},
                "B": {"model": config.model_b, "prompt": "aurora-interrupt-v1", "response": right, **right_meta},
            },
            "blind_order": [item[0] for item in pair],
        })
        review_rows.append({
            "blind_pair_id": blind_id, "scenario_id": scenario_id,
            "response_left": pair[0][1], "response_right": pair[1][1],
            "felt_understanding_1_5": "", "interruption_acceptance_1_5": "",
            "no_repeat_1_5": "", "preference_left_right_tie": "", "reason": "",
        })

    report = {
        "status": "AWAITING_HUMAN_PAIRWISE",
        "provider_called": True,
        "fallback_used": False,
        "synthetic_only": True,
        "seed": seed,
        "endpoint_origin_hash": hashlib.sha256(config.base_url.encode()).hexdigest(),
        "records": records,
    }
    (output / "real-provider-runs.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with (output / "blind-human-pairwise.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(review_rows[0]))
        writer.writeheader()
        writer.writerows(review_rows)
    return report


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
