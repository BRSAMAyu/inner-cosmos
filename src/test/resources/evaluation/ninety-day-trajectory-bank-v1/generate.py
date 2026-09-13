#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""One-shot deterministic generator for the CP-57 ninety-day trajectory bank.

Produces (in this directory):
  - scenarios.jsonl  : >=200 frozen trajectories, one JSON object per line
  - manifest.json    : freeze manifest with the scenarios.jsonl SHA-256

Determinism contract: no randomness, no clock reads, no dict ordering that can
vary between runs (CPython dicts are insertion-ordered; every structure below is
built by explicit ordered loops). Running this script twice must produce
byte-identical files -- verify with `python generate.py --check` (default mode
regenerates and compares the SHA-256 against the manifest already on disk).

Semantics: every scenario is synthesized from the 12 memories of
../memory-retrieval-v1.json (same key + "@<day>" time-point suffix variants,
plus hand-authored correction/replacement/habit stage texts derived from those
memories). No user data. Three families per the CP-57 registry entry:
  repeated_correction  - CONTRADICT/SUPERSEDE chains over >=90 simulated days
  multi_person_relations - multi-person discrimination + one owner FORGET
  life_migration       - TODO -> ARCHIVE -> HABIT migration + one owner FORGET

The generator self-checks every probe against a Python mirror of
MemoryRetrievalServiceImpl's admission gate (token set + compact character
2-gram set, lexical = |Q∩D|/|Q|): the expected memory must score >= 0.35 and
every other memory that is still ACTIVE at the probe day must score < 0.18,
so a fixture can never accidentally demand something the real service's
admission gate would refuse -- any later evaluation failure then points at the
product, not the fixture. Queries are also checked against mirrors of
RetrievalQueryNormalizer meta markers and TimeWindowParser hard windows.
"""

import hashlib
import itertools
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCE = json.loads((HERE / ".." / "memory-retrieval-v1.json").read_text(encoding="utf-8"))
BASE_BY_KEY = {m["key"]: m for m in SOURCE["memories"]}
BASE_KEYS = [m["key"] for m in SOURCE["memories"]]

FROZEN_AT = "2026-09-13"
BANK_ID = "ninety-day-trajectory-bank-v1"
VERSION = "1.0.0"
PER_FAMILY = 70  # 3 x 70 = 210 >= 200 frozen trajectories (registry CP-57)

# ---------------------------------------------------------------------------
# Python mirrors of the production scoring gates (kept deliberately tiny).
# ---------------------------------------------------------------------------

META_MARKERS = [
    "帮我分析", "分析一下", "帮我梳理", "梳理一下", "帮我理清", "理清一下",
    "帮我看看", "怎么看待", "你怎么看", "你怎么理解", "为什么会这样",
    "我想聊聊", "想聊聊", "我想说说", "想说出来", "只是想说", "只想说",
    "随便聊聊", "陪我聊聊", "聊一聊", "我们聊聊", "跟我说说",
    "帮帮我", "谢谢", "在吗", "你好", "晚安", "早上好", "然后呢", "后来呢", "一下",
]

WINDOW_RE = re.compile(
    r"最近(\d+|[一二两三四五六七八九十]+)\s*(天|日|周|星期|个月|月|年)"
    r"|(今[天晚]|这[一]?[周星期]+|本[周月]|上[个]?[周月]|今年|最近[一两三]?个?月|最近[一两三]?年)"
)


def _terms(text):
    """Mirror of MemoryRetrievalServiceImpl.terms(): word tokens (len>1) plus
    character 2-grams of the whitespace-collapsed lowercase string."""
    tokens = {t for t in re.split(r"[^\w]+", text.lower()) if len(t) > 1}
    compact = re.sub(r"\s+", "", text.lower())
    n = 1 if len(compact) < 3 else 2
    grams = {compact[i:i + n] for i in range(len(compact) - n + 1)}
    return tokens | grams


def lexical(query, title, summary):
    """Mirror of MemoryRetrievalServiceImpl.lexical(); the document joins
    title, summary and the '[]' tag placeholders exactly like the scorer."""
    q = _terms(query)
    if not q:
        return 0.0
    d = _terms("%s %s [] []" % (title or "", summary or ""))
    return len(q & d) / len(q)


def check_query_text(query):
    assert not any(m in query for m in META_MARKERS), "query hits a meta marker: %s" % query
    assert not WINDOW_RE.search(query), "query opens a hard time window: %s" % query
    assert query == query.strip() and query, "query not normalized: %r" % query


# ---------------------------------------------------------------------------
# Hand-authored stage content, derived from the 12 base memories.
# Each correction family key gets 3 successive understanding stages
# (title, summary, probe query). Stage queries are written so that the stage
# document scores >= 0.35 while every ACTIVE non-expected memory scores < 0.18
# (verified mechanically below).
# ---------------------------------------------------------------------------

CORRECTION_STAGES = {
    "friend-contradicted": [
        ("低落时也需要朋友", "纠正了从不需朋友的判断，低落时也想有人听我说说",
         "低落时也需要朋友 听我说说"),
        ("主动找朋友聊", "愿意在低落时主动找朋友聊聊，不再硬撑着装没事",
         "主动找朋友聊聊 不再硬撑"),
        ("稳定的朋友节奏", "现在保持和朋友定期见面的节奏，不再假装不需要任何人",
         "朋友 定期见面 节奏 不再假装"),
    ],
    "escape-superseded": [
        ("不是逃避，是在谨慎选择", "停下来是在辨认风险和真正想要的方向，不是躲开",
         "不是逃避 谨慎选择 方向"),
        ("暂停后确定了方向", "经过一段暂停，明确了下一步的方向，已经重新出发",
         "暂停之后 明确 方向 重新出发"),
        ("边走边调整", "不再把停顿看成逃避，也不再急于给自己贴标签",
         "停顿 看成逃避 贴标签"),
    ],
    "careful-choice": [
        ("选择有了结论", "谨慎权衡之后决定先留在当前团队，方向更清楚了",
         "谨慎权衡 之后 决定 留在当前团队"),
        ("决定开始新尝试", "对下一步的选择更新为接受新项目的机会，边做边看",
         "下一步的选择 新项目 边做边看"),
        ("新尝试稳定下来", "新角色已经稳定，回头看当时的谨慎是值得的",
         "新角色 稳定 回头看 谨慎"),
    ],
    "listen-first": [
        ("被倾听后自己找到办法", "上次被完整听完，我自己就把方案想清楚了",
         "被完整听完 方案 想清楚"),
        ("先听自己再说", "现在会先把自己的需要说出来，再听别人的建议",
         "先听自己 把需要说出来 建议"),
        ("倾听成了习惯", "和同事交流时也先听完整再回应，效果更好",
         "和同事交流 先听完整 再回应"),
    ],
}

DISTRACTOR_QUERIES = {
    "walk-recovery": "压力 大 散步 恢复 集中",
    "rain-anxiety": "雨夜 焦虑 呼吸 平稳",
    "solo-travel": "独自 旅行 车站 有能力",
}
DISTRACTORS = ["walk-recovery", "rain-anxiety", "solo-travel"]

# Multi-person family content (all persons derived from the relation memories).
XIAOLIN_BASE_QUERY = "小林 边界 独处 精力"
XIAOLIN_STAGE = ("和小林重新谈边界", "现在能直接说出需要独处的时间，小林已经理解了这种需要",
                 "小林 重新谈边界 直接说出 理解")
MOTHER_QUERY = "母亲 沟通 沉默 冲突 真实需要"
COUSIN_VARIANTS = [
    ("和表姐谈边界", "跟表姐说明白了自己需要的空间，她没有多问", "和表姐谈边界 说明白 空间"),
    ("和堂哥谈边界", "跟堂哥说明白了自己需要的空间，他没有多问", "和堂哥谈边界 说明白 空间"),
]
MOTHER_FORGET_VARIANTS = [
    ("和母亲谈报班的纠结", "母亲想让我报会计班，我犹豫了很久，这段之后要求忘记",
     "母亲 报班 纠结 犹豫"),
    ("和母亲谈换城市的纠结", "母亲希望我留在家附近工作，我犹豫过很久，这段之后要求忘记",
     "母亲 换城市 留在家附近 犹豫"),
]

# Life-migration family: each pair is (todoKey, siblingKey, habit, plans[2]).
TODO_QUERIES = {
    "dentist": "下周 处理 持续不适 牙齿",
    "course-report": "周五 提交 课程 报告 引用",
}
MIGRATION_PAIRS = [
    {
        "todo": "course-report", "sibling": "dentist",
        "habit": ("固定周五整理进度的习惯", "把课程报告式的复盘变成每周五固定的整理节奏，执行轻松",
                  "固定 每周五 整理 节奏 习惯"),
        "plans": [
            ("旧路线独自旅行计划", "原来记录的独自旅行路线与车站安排，之后要求忘记",
             "独自旅行 旧路线 车站 安排"),
            ("旧装备清单计划", "第一次独自旅行前列的装备与车辆安排清单，之后要求忘记",
             "独自旅行 装备 清单 车辆"),
        ],
    },
    {
        "todo": "dentist", "sibling": "course-report",
        "habit": ("定期检查牙齿的习惯", "把预约牙医变成每半年一次的定期检查，问题早发现早处理",
                  "定期 检查 牙齿 习惯 半年"),
        "plans": [
            ("旧的下一步选择草稿", "谨慎选择期间留下的路线草稿与权衡记录，之后要求忘记",
             "选择 草稿 权衡 记录"),
            ("旧的换岗权衡记录", "换岗之前写下的利弊权衡与顾虑记录，之后要求忘记",
             "换岗 利弊 权衡 顾虑"),
        ],
    },
]
HABIT_SOURCE = {"固定周五整理进度的习惯": "course-report", "定期检查牙齿的习惯": "dentist"}


def add_event(day, key, title, summary, type_, layer):
    return {"day": day, "op": "ADD", "key": key, "title": title,
            "summary": summary, "type": type_, "layer": layer}


def correction_event(day, op, key, replacement_key, title, summary, type_, layer):
    return {"day": day, "op": op, "key": key, "replacementKey": replacement_key,
            "title": title, "summary": summary, "type": type_, "layer": layer}


def probe(day, kind, task, query, expected, prohibited, note):
    return {"day": day, "kind": kind, "task": task, "query": query,
            "expectedKeys": expected, "prohibitedKeys": prohibited, "note": note}


# ---------------------------------------------------------------------------
# Family builders. Each returns (sourceKey, events, probes) with events sorted
# by day; every trajectory spans >= 90 days.
# ---------------------------------------------------------------------------

def build_repeated_correction(ki, rounds, c, jitter, reinforce, probe_set, di):
    """CONTRADICT/SUPERSEDE chain on one self-understanding memory, plus a
    stable distractor memory from a different base key."""
    key = ["friend-contradicted", "escape-superseded", "careful-choice", "listen-first"][ki]
    base = BASE_BY_KEY[key]
    distractor = DISTRACTORS[di % len(DISTRACTORS)]
    d_base = BASE_BY_KEY[distractor]
    shift = 7 * c + 2 * jitter
    corr_days = [30 + shift + 28 * j for j in range(rounds)]
    distractor_day = 40 + 3 * c + jitter
    final_probe_day = max(92, corr_days[-1] + 6)
    reinforce_day = final_probe_day - 3

    events = [add_event(0, "%s@0" % key, base["title"], base["summary"],
                        base["type"], base["layer"])]
    chain_keys = ["%s@0" % key]
    for j, day in enumerate(corr_days):
        stage = CORRECTION_STAGES[key][j]
        op = "CONTRADICT" if j % 2 == 0 else "SUPERSEDE"  # exercise both statuses
        new_key = "%s@%d" % (key, day)
        events.append(correction_event(day, op, chain_keys[-1], new_key,
                                       stage[0], stage[1], base["type"], base["layer"]))
        chain_keys.append(new_key)
    events.append(add_event(distractor_day, "%s@%d" % (distractor, distractor_day),
                            d_base["title"], d_base["summary"],
                            d_base["type"], d_base["layer"]))
    if reinforce:
        events.append({"day": reinforce_day, "op": "REINFORCE", "key": chain_keys[-1]})

    probes = []
    stage1_day = corr_days[0]
    stage1_key = "%s@%d" % (key, stage1_day)
    # Only keys that already exist at the mid-probe day may be prohibited.
    mid_prohibited = ["%s@0" % key]
    if distractor_day <= stage1_day + 6:
        mid_prohibited.append("%s@%d" % (distractor, distractor_day))
    probes.append(probe(stage1_day + 6, "CORRECTION_PREFERENCE", "PROFILE_REVIEW",
                        CORRECTION_STAGES[key][0][2], [stage1_key], mid_prohibited,
                        "链中途探针：第一代纠正后，旧理解不得复活"))
    head = chain_keys[-1]
    final_prohibited = [k for k in chain_keys if k != head] + ["%s@%d" % (distractor, distractor_day)]
    probes.append(probe(final_probe_day, "CORRECTION_PREFERENCE", "PROFILE_REVIEW",
                        CORRECTION_STAGES[key][rounds - 1][2], [head], final_prohibited,
                        "90日终点探针：最新理解必须取代全部旧版本"))
    if probe_set:
        probes.append(probe(final_probe_day, "FACT_RECALL", "AURORA_CONVERSATION",
                            DISTRACTOR_QUERIES[distractor],
                            ["%s@%d" % (distractor, distractor_day)], [head, "%s@0" % key],
                            "稳定分心记忆仍可被精确召回，纠正链词汇不得混入"))
    events.sort(key=lambda e: e["day"])
    probes.sort(key=lambda p: p["day"])
    return key, events, probes


def build_multi_person(person_set, with_correction, c, jitter, cousin_flavor,
                       forget_flavor, late_reinforce):
    """Boundary/silence memories about 小林、母亲 (+ optional 表姐/堂哥);
    the mother variant is FORGOTTEN by the owner mid-trajectory."""
    xiaolin = BASE_BY_KEY["xiaolin-boundary"]
    mother = BASE_BY_KEY["mother-silence"]
    shift = 3 * c + jitter
    corr_day = 38 + 7 * c + 2 * jitter
    cousin_day = 52 + 5 * c + jitter
    mvar_add_day = 26 + 2 * c + jitter
    mvar_forget_day = 66 + 2 * c + jitter
    reinforce_day = 86 + 2 * c + jitter
    cousin = COUSIN_VARIANTS[cousin_flavor]
    mvar = MOTHER_FORGET_VARIANTS[forget_flavor]

    events = [add_event(0, "xiaolin-boundary@0", xiaolin["title"], xiaolin["summary"],
                        xiaolin["type"], xiaolin["layer"]),
              add_event(mvar_add_day, "mother-silence@%d" % mvar_add_day,
                        mvar[0], mvar[1], mother["type"], mother["layer"]),
              add_event(14 + shift, "mother-silence@%d" % (14 + shift),
                        mother["title"], mother["summary"], mother["type"], mother["layer"])]
    xiaolin_head = "xiaolin-boundary@0"
    xiaolin_query = XIAOLIN_BASE_QUERY
    if with_correction:
        events.append(correction_event(corr_day, "SUPERSEDE", "xiaolin-boundary@0",
                                       "xiaolin-boundary@%d" % corr_day,
                                       XIAOLIN_STAGE[0], XIAOLIN_STAGE[1],
                                       xiaolin["type"], xiaolin["layer"]))
        xiaolin_head = "xiaolin-boundary@%d" % corr_day
        xiaolin_query = XIAOLIN_STAGE[2]
    if person_set:
        events.append(add_event(cousin_day, "xiaolin-boundary@%d" % cousin_day,
                                cousin[0], cousin[1], xiaolin["type"], xiaolin["layer"]))
    events.append({"day": mvar_forget_day, "op": "FORGET",
                   "key": "mother-silence@%d" % mvar_add_day})
    if late_reinforce:
        events.append({"day": reinforce_day, "op": "REINFORCE", "key": xiaolin_head})

    p1_day = max(corr_day if with_correction else 0, 14 + shift, mvar_add_day) + 6
    p1_prohibited = ["mother-silence@%d" % (14 + shift)]
    if with_correction:
        p1_prohibited.append("xiaolin-boundary@0")
    if person_set and cousin_day <= p1_day:
        p1_prohibited.append("xiaolin-boundary@%d" % cousin_day)
    if mvar_add_day <= p1_day:
        p1_prohibited.append("mother-silence@%d" % mvar_add_day)
    probes = [probe(p1_day, "FACT_RECALL", "RELATION_REVIEW", xiaolin_query,
                    [xiaolin_head], p1_prohibited, "小林关系探针：他人记忆不得凭共同话题词进入")]

    p_prev = p1_day
    if person_set:
        p3_day = max(p1_day, cousin_day) + 5
        p3_prohibited = [xiaolin_head, "mother-silence@%d" % (14 + shift)]
        if mvar_add_day <= p3_day and mvar_forget_day > p3_day:
            p3_prohibited.append("mother-silence@%d" % mvar_add_day)
        probes.append(probe(p3_day, "FACT_RECALL", "RELATION_REVIEW", cousin[2],
                            ["xiaolin-boundary@%d" % cousin_day], p3_prohibited,
                            "第三位关系人探针：同话题不同人必须区分"))
        p_prev = p3_day
    p2_day = max(p_prev, 14 + shift) + 5
    p2_prohibited = [xiaolin_head]
    if person_set:
        p2_prohibited.append("xiaolin-boundary@%d" % cousin_day)
    if mvar_forget_day > p2_day:
        p2_prohibited.append("mother-silence@%d" % mvar_add_day)
    probes.append(probe(p2_day, "FACT_RECALL", "RELATION_REVIEW", MOTHER_QUERY,
                        ["mother-silence@%d" % (14 + shift)], p2_prohibited,
                        "母亲关系探针：沉默主题必须锚定到母亲本人的记忆"))

    probes.append(probe(mvar_forget_day + 8, "WITHDRAWN_ZERO", "RELATION_REVIEW",
                        mvar[2], [],
                        ["mother-silence@%d" % (14 + shift), xiaolin_head,
                         "mother-silence@%d" % mvar_add_day] +
                        (["xiaolin-boundary@%d" % cousin_day] if person_set else []),
                        "撤回探针：被忘记的母亲变体不得以任何形式返回（应零结果）"))

    p5_day = max(92, (reinforce_day if late_reinforce else 0), mvar_forget_day + 8) + 2
    p5_prohibited = ["mother-silence@%d" % (14 + shift),
                     "mother-silence@%d" % mvar_add_day]
    if with_correction:
        p5_prohibited.append("xiaolin-boundary@0")
    if person_set:
        p5_prohibited.append("xiaolin-boundary@%d" % cousin_day)
    probes.append(probe(p5_day, "FACT_RECALL", "RELATION_REVIEW", xiaolin_query,
                        [xiaolin_head], p5_prohibited, "90日终点探针：小林关系在长程后仍稳定召回"))
    events.sort(key=lambda e: e["day"])
    probes.sort(key=lambda p: p["day"])
    return "xiaolin-boundary", events, probes


def build_life_migration(pair, c, jitter, early_probe, reinforce, plan_flavor,
                         forget_late):
    """TODO -> (completed, ARCHIVE) -> HABIT migration on one life thread,
    with a stable sibling thread and a forgotten stale plan."""
    spec = MIGRATION_PAIRS[pair]
    todo = BASE_BY_KEY[spec["todo"]]
    sibling = BASE_BY_KEY[spec["sibling"]]
    shift = c + jitter
    sibling_day = 8 + shift
    plan_day = 22 + 2 * c + jitter
    archive_day = 35 + 2 * c + jitter
    habit_day = 58 + 2 * c + jitter
    forget_day = (78 if forget_late else 66) + 2 * c + jitter
    reinforce_day = 88 + jitter
    plan = spec["plans"][plan_flavor]
    plan_source = "solo-travel" if pair == 0 else "careful-choice"
    plan_key = "%s@%d" % (plan_source, plan_day)
    habit_key = "%s@%d" % (HABIT_SOURCE[spec["habit"][0]], habit_day)
    todo_key = "%s@0" % spec["todo"]
    sibling_key = "%s@%d" % (spec["sibling"], sibling_day)

    events = [add_event(0, todo_key, todo["title"], todo["summary"], todo["type"], todo["layer"]),
              add_event(sibling_day, sibling_key, sibling["title"], sibling["summary"],
                        sibling["type"], sibling["layer"]),
              add_event(plan_day, plan_key, plan[0], plan[1],
                        BASE_BY_KEY[plan_source]["type"], BASE_BY_KEY[plan_source]["layer"]),
              {"day": archive_day, "op": "ARCHIVE", "key": todo_key,
               "title": "%s（已完成）" % todo["title"]},
              add_event(habit_day, habit_key, spec["habit"][0], spec["habit"][1],
                        "HABIT", "PROCEDURAL"),
              {"day": forget_day, "op": "FORGET", "key": plan_key}]
    if reinforce:
        events.append({"day": reinforce_day, "op": "REINFORCE", "key": habit_key})

    probes = []
    if early_probe:
        probes.append(probe(16 + shift, "FACT_RECALL", "ACTION_SPLIT",
                            TODO_QUERIES[spec["todo"]], [todo_key], [sibling_key],
                            "迁移前探针：TODO 尚未完成，应原样召回"))
    migration_day = habit_day + 6
    probes.append(probe(migration_day, "FACT_RECALL", "PROFILE_SUPPORT", spec["habit"][2],
                        [habit_key], [todo_key, sibling_key],
                        "迁移探针：生活已迁移为习惯，已完成的 TODO 不得复活"))
    probes.append(probe(forget_day + 8, "WITHDRAWN_ZERO", "AURORA_CONVERSATION",
                        plan[2], [], [habit_key, sibling_key, todo_key, plan_key],
                        "撤回探针：要求忘记的旧计划必须零返回"))
    late_day = 92 + 2 * jitter
    probes.append(probe(late_day, "FACT_RECALL", "ACTION_SPLIT",
                        TODO_QUERIES[spec["sibling"]], [sibling_key],
                        [habit_key, todo_key, plan_key],
                        "90日终点探针：并行的稳定线程仍可精确召回"))
    events.sort(key=lambda e: e["day"])
    probes.sort(key=lambda p: p["day"])
    return spec["todo"], events, probes


# ---------------------------------------------------------------------------
# Assembly, self-check, freeze.
# ---------------------------------------------------------------------------

def replay_state(events, day):
    """Return {key: (status, title, summary)} as of `day` (FORGET redacts)."""
    state = {}
    for event in sorted(events, key=lambda e: e["day"]):
        if event["day"] > day:
            continue
        op = event["op"]
        if op == "ADD":
            state[event["key"]] = ("ACTIVE", event["title"], event["summary"])
        elif op in ("CONTRADICT", "SUPERSEDE"):
            old_status = state.get(event["key"], (None,))[0]
            assert old_status == "ACTIVE", "correction on non-ACTIVE key %s" % event["key"]
            state[event["key"]] = ("CONTRADICTED" if op == "CONTRADICT" else "SUPERSEDED",
                                   state[event["key"]][1], state[event["key"]][2])
            state[event["replacementKey"]] = ("ACTIVE", event["title"], event["summary"])
        elif op == "REINFORCE":
            assert state.get(event["key"], (None,))[0] == "ACTIVE"
        elif op == "ARCHIVE":
            state[event["key"]] = ("ARCHIVED", event.get("title", state[event["key"]][1]),
                                   state[event["key"]][2])
        elif op == "FORGET":
            state[event["key"]] = ("FORGOTTEN", "已按你的请求忘记", None)
    return state


def self_check(scenario):
    events = scenario["events"]
    days = [e["day"] for e in events] + [p["day"] for p in scenario["probes"]]
    assert max(days) - min(days) >= 90, "span below 90 days: %s" % scenario["id"]
    added = set()
    for event in events:
        assert event["key"] not in ("", None)
        if event["op"] == "ADD":
            assert event["key"] not in added, "duplicate ADD %s" % event["key"]
            added.add(event["key"])
        elif event["op"] in ("CONTRADICT", "SUPERSEDE"):
            assert event["key"] in added and event["replacementKey"] not in added
            added.add(event["replacementKey"])
    for probe_ in scenario["probes"]:
        check_query_text(probe_["query"])
        state = replay_state(events, probe_["day"])
        assert probe_["prohibitedKeys"], "probe without prohibited keys: %s" % scenario["id"]
        if probe_["kind"] == "WITHDRAWN_ZERO":
            assert probe_["expectedKeys"] == []
            assert any(state[k][0] == "FORGOTTEN" for k in probe_["prohibitedKeys"]), \
                "withdrawn probe without a FORGOTTEN prohibited key: %s" % scenario["id"]
        else:
            assert probe_["expectedKeys"], "probe without expected keys: %s" % scenario["id"]
            for key in probe_["expectedKeys"]:
                assert key in state and state[key][0] == "ACTIVE", \
                    "%s: expected key %s not ACTIVE at day %d" % (scenario["id"], key, probe_["day"])
                score = lexical(probe_["query"], state[key][1], state[key][2])
                assert score >= 0.35, \
                    "%s: expected %s lexical %.3f < 0.35 for query %r" % (
                        scenario["id"], key, score, probe_["query"])
        for key in probe_["prohibitedKeys"]:
            assert key in state, "%s: prohibited key %s missing at day %d" % (
                scenario["id"], key, probe_["day"])
        for key, (status, title, summary) in state.items():
            if status == "ACTIVE" and key not in probe_["expectedKeys"]:
                score = lexical(probe_["query"], title, summary)
                assert score < 0.18, \
                    "%s: ACTIVE non-expected %s lexical %.3f >= 0.18 for query %r" % (
                        scenario["id"], key, score, probe_["query"])


def build_all():
    scenarios = []

    # Axis order matters: itertools.product varies the LAST axis fastest, so the
    # semantically divisive axes (key/person/pair/flavor) go last and the first
    # PER_FAMILY elements cover them evenly.
    a_grid = itertools.product(range(3), (2, 3), (0, 1), (0, 1), (0, 1), (0, 1), range(4))
    for di, rounds, c, jitter, reinforce, probe_set, ki in itertools.islice(a_grid, PER_FAMILY):
        source, events, probes = build_repeated_correction(
            ki, rounds, c, jitter, reinforce, probe_set, di)
        scenarios.append({"family": "repeated_correction", "sourceKey": source,
                          "events": events, "probes": probes,
                          "note": "反复纠正链：%d 代纠正（CONTRADICT/SUPERSEDE 交替）跨 90 日" % rounds})

    b_grid = itertools.product((0, 1), (0, 1), (0, 1), (0, 1), (0, 1), (0, 1), (0, 1))
    for c, jitter, late_reinforce, forget_flavor, cousin_flavor, with_correction, person_set \
            in itertools.islice(b_grid, PER_FAMILY):
        source, events, probes = build_multi_person(
            person_set, with_correction, c, jitter, cousin_flavor, forget_flavor, late_reinforce)
        scenarios.append({"family": "multi_person_relations", "sourceKey": source,
                          "events": events, "probes": probes,
                          "note": "多关系多人：小林/母亲%s 边界与沉默主题，含一次撤回" %
                                  ("/表姐" if person_set and cousin_flavor == 0 else
                                   "/堂哥" if person_set else "")})

    c_grid = itertools.product((0, 1), (0, 1), (0, 1), (0, 1), (0, 1), (0, 1), (0, 1))
    for c, jitter, forget_late, early_probe, reinforce, plan_flavor, pair \
            in itertools.islice(c_grid, PER_FAMILY):
        source, events, probes = build_life_migration(
            pair, c, jitter, early_probe, reinforce, plan_flavor, forget_late)
        scenarios.append({"family": "life_migration", "sourceKey": source,
                          "events": events, "probes": probes,
                          "note": "生活迁移：TODO 完成归档后沉淀为 HABIT，含一次撤回"})

    for index, scenario in enumerate(scenarios, start=1):
        scenario["id"] = "NDT-%03d" % index
        # Reorder fields deterministically: id first, then family, sourceKey, span,
        # events, probes, note.
        scenarios[index - 1] = {
            "id": scenario["id"],
            "family": scenario["family"],
            "sourceKey": scenario["sourceKey"],
            "spanDays": max([e["day"] for e in scenario["events"]] +
                            [p["day"] for p in scenario["probes"]]),
            "events": scenario["events"],
            "probes": scenario["probes"],
            "note": scenario["note"],
        }
        self_check(scenarios[index - 1])
    return scenarios


def main():
    scenarios = build_all()
    families = {}
    for scenario in scenarios:
        families[scenario["family"]] = families.get(scenario["family"], 0) + 1
    assert len(scenarios) >= 200, "registry CP-57 requires >= 200 frozen trajectories"
    assert set(families) == {"repeated_correction", "multi_person_relations", "life_migration"}

    lines = []
    for scenario in scenarios:
        lines.append(json.dumps(scenario, ensure_ascii=False, separators=(", ", ": ")))
    body = ("\n".join(lines) + "\n").encode("utf-8")
    sha = hashlib.sha256(body).hexdigest()

    manifest = {
        "id": BANK_ID,
        "version": VERSION,
        "frozen_at": FROZEN_AT,
        "generator": "deterministic synthetic expansion of memory-retrieval-v1.json "
                     "(generate.py, no randomness, no user data)",
        "scenario_sha256": sha,
        "scenario_count": len(scenarios),
        "families": families,
        "purpose": "CP-57 90-day trajectory evaluation: per-trajectory event replay "
                   "(ADD/CONTRADICT/SUPERSEDE/REINFORCE/ARCHIVE/FORGET) with time-point "
                   "probes scored against the real MemoryRetrievalService per the frozen "
                   "experiment registry (accuracy >= 0.95, correction preference = 1.0, "
                   "withdrawn resurrection = 0, cross-user leakage = 0)",
        "license": "project-internal",
        "schema": "keys are memory-retrieval-v1 keys with @<day> time-point suffixes; "
                  "events[]{day,op,key,...}; correction events carry replacementKey; "
                  "probes[]{day,kind,task,query,expectedKeys[],prohibitedKeys[]}; "
                  "kind in FACT_RECALL|CORRECTION_PREFERENCE|WITHDRAWN_ZERO",
        "change_log": "v1.0.0: initial freeze - 210 deterministic 90+ day trajectories "
                      "synthesized from the 12 memory-retrieval-v1.json memories "
                      "(same key + time-point suffix variants) across repeated "
                      "correction, multi-person relation and TODO-to-HABIT migration "
                      "families; every probe self-checked against the retrieval "
                      "admission gate mirror in generate.py",
    }

    scenarios_path = HERE / "scenarios.jsonl"
    manifest_path = HERE / "manifest.json"
    if "--check" in sys.argv and scenarios_path.exists():
        current = hashlib.sha256(scenarios_path.read_bytes()).hexdigest()
        print("on-disk  scenarios.jsonl sha256 = %s" % current)
        print("regenerated         sha256 = %s" % sha)
        if current != sha:
            print("MISMATCH: generator output is not byte-identical to the frozen bank")
            return 1
        print("deterministic: two runs agree")
        return 0

    scenarios_path.write_bytes(body)
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("wrote %s (%d scenarios, %d bytes)" % (scenarios_path.name, len(scenarios), len(body)))
    print("scenario_sha256 = %s" % sha)
    return 0


if __name__ == "__main__":
    sys.exit(main())
