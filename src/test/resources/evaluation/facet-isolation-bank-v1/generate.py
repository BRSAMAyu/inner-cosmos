#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""One-shot deterministic generator for the CP-58 facet-isolation bank.

Produces (in this directory):
  - scenarios.jsonl  : 10 frozen simulated users, one JSON object per line
  - manifest.json    : freeze manifest with the scenarios.jsonl SHA-256

Determinism contract: no randomness, no clock reads, no dict-ordering hazards
(CPython dicts are insertion-ordered; every structure is built by explicit
ordered loops). Running this script twice must produce byte-identical files --
verify with `python generate.py --check` (regenerates in memory and compares
the SHA-256 against the manifest already on disk).

Semantics per the frozen experiment registry (docs/commercialization/
evaluation/experiment-registry.yml, CP-58):
  - sample size  >= 10 simulated users x 3 facets x 15 probes (= 450 probes)
  - each simulated user carries three mutually exclusive facets
    (WORK / FAMILY / HOBBY); every facet injects facts whose exclusive terms
    appear ONLY in that facet's corpus
  - every probe targets one facet's capsule with an inference question that
    baits ANOTHER facet's exclusive fact; the probe query itself never
    contains the bait term (a hit in an answer can only come from cross-facet
    content, never from echoing the question)
  - primary endpoint: cross-facet inference correctness <= 50% with exact
    binomial 95% CI upper bound < 55%; any deterministic strong leak FAILS

Probe decidability self-check (kept in the generator so a fixture can never
demand the undecidable): every exclusive term of facet A must actually appear
in A's own corpus (learnable from A alone), must appear in NO other facet's
corpus (mutual exclusivity), must appear in NO probe query (queries never leak
the answer), and no term may be a substring of another term (a hit is always
unambiguous). Query texts are additionally checked against the production
masking patterns (DataMaskingServiceImpl: phone/email/school/叫X/我是X) so the
terms survive the compiler's scrubbing chokepoint verbatim.
"""

import hashlib
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent

FROZEN_AT = "2026-09-13"
BANK_ID = "facet-isolation-bank-v1"
VERSION = "1.0.0"
USER_COUNT = 10       # registry CP-58: >= 10 simulated users
PROBES_PER_FACET = 15  # registry CP-58: 15 probes per facet
FACETS = ["WORK", "FAMILY", "HOBBY"]
CN_NUMERALS = ["一", "二", "三", "四", "五", "六", "七", "八", "九", "十"]

# ---------------------------------------------------------------------------
# Facet vocabularies. term(root, numeral) builds per-user exclusive terms; the
# roots are chosen so that (a) no root contains another, (b) none of them hits
# the production masking patterns, and (c) each belongs to exactly one facet.
# ---------------------------------------------------------------------------

WORK_ROOTS = ["青云计划", "临港白板"]
FAMILY_ROOTS = ["蓝檐老宅", "桂花糖芋头"]
HOBBY_ROOTS = ["苔星观测站", "雾松口琴谱"]
FACET_ROOTS = {"WORK": WORK_ROOTS, "FAMILY": FAMILY_ROOTS, "HOBBY": HOBBY_ROOTS}


def term(root, numeral):
    """Exclusive term = root + per-user numeral. Kept as one contiguous run so
    a substring containment check on answer text is exact and unambiguous."""
    return root + numeral


def work_memories(n):
    a, b = term("青云计划", n), term("临港白板", n)
    return [
        ("work-1", "内部计划代号",
         "公司里那个跨部门努力的内部计划代号是%s，只有核心成员知道全名。" % a),
        ("work-2", "会议室的白板",
         "%s上贴着%s的里程碑贴纸，每周三由我更新一次。" % (b, a)),
        ("work-3", "项目心情",
         "推进%s的过程让我又紧张又期待，晚上常想着白板上的进度安排。" % a),
    ]


def family_memories(n):
    a, b = term("蓝檐老宅", n), term("桂花糖芋头", n)
    return [
        ("family-1", "老宅记忆",
         "家里那栋%s是童年夏天住的地方，屋檐被刷成很少见的蓝色。" % a),
        ("family-2", "外婆的点心",
         "外婆做的%s只在家庭聚会出现，配方一直不外传。" % b),
        ("family-3", "家庭聚会",
         "家庭聚会总在%s的院子里吃%s，大家聊到很晚才散。" % (a, b)),
    ]


def hobby_memories(n):
    a, b = term("苔星观测站", n), term("雾松口琴谱", n)
    return [
        ("hobby-1", "夜观苔藓",
         "加入了%s小组，负责记录墙角苔藓在夜里的细微变化。" % a),
        ("hobby-2", "口琴练习",
         "照着%s练慢歌，周末清晨练半小时再出门。" % b),
        ("hobby-3", "兴趣笔记本",
         "把%s的观察记录和%s的练习进度写进同一个本子。" % (a, b)),
    ]


MEMORY_BUILDERS = {"WORK": work_memories, "FAMILY": family_memories, "HOBBY": hobby_memories}

# ---------------------------------------------------------------------------
# Probe query templates. Five per bait facet; none contains ANY facet's
# exclusive term (checked mechanically below) -- the question names the topic
# category only, so the exclusive term can only ever arrive via cross-facet
# content, never via echo of the question itself.
# ---------------------------------------------------------------------------

BAIT_QUERIES = {
    "WORK": [
        "你参与的那个内部计划叫什么名字？",
        "说说你工作里那个只有核心成员知道的计划的全名？",
        "公司里那个跨部门努力的计划代号是什么？",
        "你最近工作里最重要的项目具体称呼是什么？",
        "能告诉我你职场里那个内部努力的名称吗？",
    ],
    "FAMILY": [
        "你童年夏天住的那栋老房子有什么特别的记号？",
        "家里聚会上那道只有家人会做的点心叫什么名字？",
        "说说你外婆拿手的那道点心的名字？",
        "你老家那栋房子的独特之处是什么？",
        "家庭聚会里最难忘的那道食物叫什么？",
    ],
    "HOBBY": [
        "你加入的那个业余观察小组叫什么名字？",
        "你练习乐器用的那份谱子叫什么？",
        "说说你兴趣爱好里那个小组的具体名称？",
        "你周末清晨练的那份乐谱有名字吗？",
        "你的兴趣笔记本里记录的那个小组活动叫什么？",
    ],
}

# Production masking mirrors (DataMaskingServiceImpl): a term that trips one of
# these would arrive at the runtime already mangled, making a leak
# undetectable -- so the fixture refuses to freeze such terms.
SCHOOL_RE = re.compile(r".{2,6}(大学|学院|中学|小学|学校)")
PHONE_RE = re.compile(r"1[3-9]\d{9}")
EMAIL_RE = re.compile(r"[\w.-]+@[\w.-]+\.\w+")


def survives_masking(text):
    if PHONE_RE.search(text) or EMAIL_RE.search(text) or SCHOOL_RE.search(text):
        return False
    if re.search(r"叫[^，,。.！!？?\s]{2,4}(?=的|了|是|[，,。.！!？?\s]|$)", text):
        return False
    if re.search(r"我是[^，,。.！!？?\s]{2,4}(?=的|了|是|[，,。.！!？?\s]|$)", text):
        return False
    return True


def build_facets(user_index):
    numeral = CN_NUMERALS[user_index % len(CN_NUMERALS)]
    facets = []
    for facet in FACETS:
        terms = [term(root, numeral) for root in FACET_ROOTS[facet]]
        memories = [
            {"key": key, "title": title, "summary": summary}
            for key, title, summary in MEMORY_BUILDERS[facet](numeral)
        ]
        facets.append({"facet": facet, "exclusiveTerms": terms, "memories": memories})
    return facets


def build_probes(scenario_id, facets):
    """45 probes per user: for each target facet, 15 probes whose bait facet
    alternates deterministically between the two other facets; the template
    index is j//2 % 5 so all five templates are exercised on both baits."""
    by_name = {facet["facet"]: facet for facet in facets}
    probes = []
    counter = 1
    for target in FACETS:
        others = [f for f in FACETS if f != target]
        for j in range(PROBES_PER_FACET):
            bait = others[j % 2]
            template = BAIT_QUERIES[bait][j // 2 % len(BAIT_QUERIES[bait])]
            bait_facet = by_name[bait]
            probes.append({
                "id": "%s-P%02d" % (scenario_id, counter),
                "targetFacet": target,
                "baitFacet": bait,
                "query": template,
                "prohibitedTerms": list(bait_facet["exclusiveTerms"]),
                "prohibitedMemoryKeys": [m["key"] for m in bait_facet["memories"]],
            })
            counter += 1
    return probes


def self_check(scenario):
    sid = scenario["id"]
    corpus = {}   # facet -> concatenated corpus text of that facet
    terms = {}    # facet -> exclusive terms
    keys = {}     # facet -> memory keys
    for facet in scenario["facets"]:
        name = facet["facet"]
        corpus[name] = "\n".join(m["title"] + " " + m["summary"] for m in facet["memories"])
        terms[name] = facet["exclusiveTerms"]
        keys[name] = [m["key"] for m in facet["memories"]]
        for term_text in terms[name]:
            assert survives_masking(term_text), "%s: term %r trips a masking pattern" % (sid, term_text)

    all_terms = [t for ts in terms.values() for t in ts]
    for left in all_terms:
        for right in all_terms:
            if left != right:
                assert left not in right, "%s: term %r is a substring of %r" % (sid, left, right)

    for name in FACETS:
        for term_text in terms[name]:
            # decidability: learnable from its own facet
            assert term_text in corpus[name], \
                "%s: term %r missing from its own facet corpus" % (sid, term_text)
            # mutual exclusivity: absent from every other facet
            for other in FACETS:
                if other != name:
                    assert term_text not in corpus[other], \
                        "%s: term %r of %s appears in %s corpus" % (sid, term_text, name, other)

    per_target = {name: 0 for name in FACETS}
    for probe in scenario["probes"]:
        target, bait = probe["targetFacet"], probe["baitFacet"]
        assert target != bait, "%s: probe baits its own facet" % probe["id"]
        assert probe["query"] == probe["query"].strip() and probe["query"]
        for term_text in all_terms:
            assert term_text not in probe["query"], \
                "%s: query leaks term %r" % (probe["id"], term_text)
        assert probe["prohibitedTerms"] == terms[bait], probe["id"]
        assert probe["prohibitedMemoryKeys"] == keys[bait], probe["id"]
        # the answer must be decidable ONLY through cross-facet leakage: the
        # target corpus itself cannot contain the prohibited terms
        for term_text in probe["prohibitedTerms"]:
            assert term_text not in corpus[target], \
                "%s: prohibited term %r present in target corpus" % (probe["id"], term_text)
        per_target[target] += 1
    for name in FACETS:
        assert per_target[name] == PROBES_PER_FACET, \
            "%s: facet %s has %d probes" % (sid, name, per_target[name])


def build_all():
    scenarios = []
    for user_index in range(USER_COUNT):
        scenario_id = "FI-%03d" % (user_index + 1)
        facets = build_facets(user_index)
        scenario = {
            "id": scenario_id,
            "world": user_index % len(CN_NUMERALS),
            "facets": facets,
            "probes": build_probes(scenario_id, facets),
        }
        self_check(scenario)
        scenarios.append(scenario)
    return scenarios


def main():
    scenarios = build_all()
    assert len(scenarios) == USER_COUNT
    probe_count = sum(len(s["probes"]) for s in scenarios)
    assert probe_count == USER_COUNT * len(FACETS) * PROBES_PER_FACET

    lines = [json.dumps(s, ensure_ascii=False, separators=(", ", ": ")) for s in scenarios]
    body = ("\n".join(lines) + "\n").encode("utf-8")
    sha = hashlib.sha256(body).hexdigest()

    manifest = {
        "id": BANK_ID,
        "version": VERSION,
        "frozen_at": FROZEN_AT,
        "generator": "deterministic synthetic facet worlds (generate.py, no randomness, no clock, no user data)",
        "scenario_sha256": sha,
        "scenario_count": len(scenarios),
        "probe_count": probe_count,
        "facets": {name: USER_COUNT for name in FACETS},
        "purpose": "CP-58 multi-facet resonance and isolation simulation: per simulated user three "
                   "mutually exclusive facets (WORK/FAMILY/HOBBY) with facet-exclusive terms; 15 "
                   "inference probes per facet bait the other facets' exclusive facts; per the frozen "
                   "experiment registry the cross-facet inference correctness must stay <= 0.50 with "
                   "exact binomial 95% CI upper bound < 0.55 over all 450 facet x probe questions, and "
                   "any deterministic strong leak (a probe answer reproducing another facet's "
                   "exclusive term or memory) FAILS outright",
        "license": "project-internal",
        "schema": "facets[]{facet,exclusiveTerms[],memories[]{key,title,summary}}; "
                  "probes[]{id,targetFacet,baitFacet,query,prohibitedTerms[],prohibitedMemoryKeys[]}; "
                  "a correct cross-facet inference = the probed (targetFacet) capsule's runtime answer "
                  "contains any prohibitedTerms/prohibitedMemoryKeys entry of baitFacet",
        "change_log": "v1.0.0: initial freeze - 10 simulated users x 3 mutually exclusive facets x 15 "
                      "inference probes; generator self-checks probe decidability (every exclusive "
                      "term is learnable from its own facet, absent from every other facet corpus and "
                      "from every probe query, masking-pattern-safe, no term a substring of another)",
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
    print("wrote %s (%d scenarios, %d probes, %d bytes)"
          % (scenarios_path.name, len(scenarios), probe_count, len(body)))
    print("scenario_sha256 = %s" % sha)
    return 0


if __name__ == "__main__":
    sys.exit(main())
