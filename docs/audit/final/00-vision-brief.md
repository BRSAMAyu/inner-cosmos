# Inner Cosmos — Vision Brief (for final perfection audit)

> Distilled from the four source-of-truth docs: 愿景文档 (vision), 工程总纲 (engineering master plan), Codex 推进书 (implementation push), UIUXdesign (art direction), 功能说明书 (feature spec). Each claim is tagged with its origin: [V]=愿景 / [E]=总纲 / [P]=推进书 / [UX]=UIUX / [F]=功能说明书.

---

## 1. North Star (one sentence: the product + core value)

> **A gentle, trustworthy, long-term AI inner-world system that remembers you, understands you, helps you organize yourself — and, when you consent, leads you toward real, deliberate resonance with others.** [V §0.1, §1.1, §14]

Concrete product framing: an *AI self-resonance & slow-social platform* where a friend-style Agent (Aurora) turns free-form daily venting into structured, emotion-gravity-weighted memory, which the user can authorize into a desensitized "echo capsule" that others meet before writing a slow letter to the real person. [E §1.2–1.3, §26]

Operating creed (must hold everywhere): **"AI 是镜子，不是医生；AI 是桥梁，不是真人的替代品。"** [E §1.4, V §3.1–3.2]

---

## 2. Primary users & emotional promise (who, what feeling we deliver)

**Who:** People who are hurting diffusely but can't name it; who want to journal but freeze at the blank page; who want to be heard but fear bothering/judgment; who are stuck in repeating patterns they haven't noticed; who crave connection but find real-time social intrusive. [V §2.1]

**The promise — the one feeling that defines done:** after pouring out a messy paragraph the user feels *"gently caught"*; looking back at old memory, *"I finally understood that past self"*; meeting another's echo in the star-sea, *"it's not only me."* [V §15, §12.3]

**Product philosophy (the sequence that MUST be preserved in Aurora):** 先陪用户看清 → 再陪用户整理 → 最后才轻轻指向行动. Users come to be *seen*, not *solved*. [V §2.2]

---

## 3. The Core Loop (the canonical user journey that MUST be perfect)

This is the V1 closed loop — every link must be wired end-to-end, no isolated features. [E §2.1, P §2, §10]

```
1. Text/voice vent  ("今天和 Aurora 聊聊")
2. Aurora friend-style active follow-up  (拆分/澄清/承接 — NOT advice)
3. Save P0 raw conversation + voice metadata (never raw audio)
4. End session → async distill into P1: MemoryCard / ThoughtFragment / EmotionTrace / TodoItem / RelationMention
5. Compute emotional_gravity (nonlinear + time decay)
6. Visualize in Memory Starfield (size=gravity, brightness=freshness, color=type)
7. Aurora suggests weaving a high-gravity theme → user authorizes
8. DataMaskingService desensitizes → P2 EchoCapsule
9. Echo Plaza shows user + seed capsules
10. Other user chats with capsule (limited turns) → guided toward slow letter
11. Slow Letter state machine: DRAFT→SENT→FLYING→DELIVERED→READ→REPLIED/DECLINED/BLOCKED/ARCHIVED
```

Privacy layering is structural, not prompt-based: **P0 raw never reaches P3 social layer; capsule chat may only read personaPrompt + publicTags + authorized abstract memories.** [E §5, §8.3, P §3.4]

---

## 4. Must-Have Features / Experiences (verifiable deliverables the vision promises)

1. **Aurora as conversational-journal Agent** — 4 modes (今日倾诉/思维整理/睡前复盘/苏格拉底追问), active multi-message follow-up, tone personalization (温柔安静/理性清晰/朋友式/哲学式/行动导向), end-of-chat "正在整理…" distill flow. [V §4.2, §5.1; P Phase B; F]
2. **Daily Record Card** — structured self-echo: theme / events / core emotion / triggers / worries / values / todos / Aurora's gentle summary. [V §4.3; P D.2.1]
3. **Thought Shredder** — dump chaos, ritual "shred" animation, extract core need + one keepable line, optional no-save mode. [V §4.4; P Phase F]
4. **Emotional-Gravity Memory Starfield** — `G(t)=ln(1+αI+βR+γU+δM)·e^(−λt)`; click a star → life-fragment + related shards + "放入星海" button. [V §4.5, §5.3; E §7; P Phase E]
5. **Authorized Echo Capsule** — created from memory with desensitization; has boundary (allowTopics/blockedTopics/maxTurns); visible freshness decay; user controls (view authorized info / delete / close). [V §4.6, §5.5; E §8; P Phase G]
6. **Echo Plaza + Persona Chat** — ≥5 seed capsules (斯多葛信使/苏格拉底之问/庄周之梦/存在主义旅人/热烈的画家); daily turn limit (~3–5) then "write a slow letter" handoff. [V §4.7; E §19; P Phase H/I]
7. **Slow Letters** — ceremony-driven write page (guided prompts), parallax-delivery delay, full status machine, LetterSafetyFilter pre-delivery. [V §4.8; E §9; P Phase J]
8. **Safety boundary** — SafetyBoundaryFilter (LOW/MEDIUM/HIGH), fixed safe-flow on crisis (no free-form LLM), rhythm protection (long-session pause), support-resource page (非医疗声明 + real-world help). [V §8; E §10; P Phase K]
9. **Mock-first + remote-swappable LLM** — `llm.mode=mock` default; DeepSeek/Qwen/Kimi adapters; JSON-output fault tolerance; cost limits; full AiInteractionLog. [E §12; P Phase L]
10. **Admin backoffice** — stats, user/capsule/report/AI-log/model-config management; never expose P0 unless explicit demo mode. [E §2.2; P Phase M]
11. **Long-term memory tiers** — short/mid/long; summary-anchor sliding window; "user never re-explains themselves." [V §5.2; P B.2.3]

---

## 5. Positioning — what it is NOT (non-goals / boundaries)

Hard anti-goals — if any of these is true, the product has failed its vision: [V §3; E §1.5; P §9]

- **NOT psychological diagnosis / therapy** — no labels, no "你有焦虑症", no claiming to treat depression/anxiety; not a replacement for counselors/doctors/hotlines.
- **NOT a generic chatbot shell** — must differ from DeepSeek/ChatGPT via long-term memory + structured sediment + visualization + slow-social.
- **NOT instant stranger chat / AI lover** — no real-time DMs, no "我永远都在/只有我懂你" dependency language, no persona pretending to be the real person.
- **NOT a diary CRUD** — recording happens *inside* conversation, not as a chore.
- **NOT privacy-by-prompt** — boundaries enforced at data-architecture layer.
- **NOT a token-burning autonomous-agent social system** — Agent social is user-triggered only.
- **No raw audio persistence; no vector DB; no mobile app; no real psych assessment — in V1.** [E §2.3]
- **Design never:** pure black `#000`, cold blue, neon/cyber, cold dashboards, meaningless show-off motion. [UX §0]

---

## 6. What "Complete & Perfect" Means Here (concrete done-checklist)

A new user, on a fresh machine, with **no API key**, can complete this with zero instruction: [P §5, §10; E §25]

- [ ] Register/login → set Aurora style → text/voice vent → streaming friend-style reply.
- [ ] End chat → see Daily Record (theme/emotion/events/cognition/todos/summary).
- [ ] Open Starfield → see own long-term themes, click a star, understand *why* it's heavy.
- [ ] Authorize a memory → desensitized capsule appears in Plaza.
- [ ] Browse Plaza (seed + real capsules), chat within turn-limit, naturally reach "write a slow letter."
- [ ] Write/send letter → it flies → delivered → read → reply/decline/block all work.
- [ ] Control privacy & social reachability (GREEN/YELLOW/RED/CLOSED); view/delete/export own data.
- [ ] High-risk input triggers fixed safe flow + resource page; long session triggers rhythm pause.
- [ ] Admin sees stats/logs/reports/config; Mock mode runs full demo offline.
- [ ] Subjectively: the 10 questions in P §10 all answer "yes" — esp. "does this clearly differ from a generic AI chat site?"

---

## 7. Vision-Promise Coverage Gaps (flag each for audit verification)

Each is a promise the docs make that risks being under-built — verify in code/UI.

1. **Aurora "active multi-message follow-up" & mode-specific behavior** — vision insists replies split into multiple natural messages and that mode changes prompt/follow-up style/output structure. Risk: single-blob replies, modes cosmetic only. [P B.2.1–B.2.2; verify PromptBuilder + AuroraAgentServiceImpl]
2. **Conversation → memory sediment actually fires async & non-blocking** — DialogFinishedEvent must trigger MemoryExtract/EmotionTrace/Todo/Gravity/CapsuleSuggestion listeners. Risk: listeners missing/broken or blocking chat. [E §11, §14.2; P §3.2; verify event package]
3. **Emotional gravity is the *real* nonlinear formula with time decay** — not a stub. Risk: linear/placeholder calc, λ missing, no recompute triggers. [E §7; P E.2.1–E.2.2; verify GravityService]
4. **Echo Capsule chat privacy is enforced at data layer** — must be impossible for capsule prompt to read P0 DialogMessage raw text or ThoughtFragment rawExcerpt. Risk: prompt string-concatenates raw memory. [E §8.3; P §3.4, I.2.2; verify CapsuleAgent/PromptBuilder]
5. **DataMaskingService actually desensitizes before capsule creation** — names/places/contact/precise-time/school removed; concrete→abstract. Risk: masking stubbed or bypassed. [P G.2.2; verify service]
6. **Slow-letter state machine is complete & guarded** — all 9 states + legal transitions + LetterSafetyFilter pre-delivery + parallax delay. Risk: partial transitions, no safety filter, instant delivery. [E §9; P Phase J; verify letterstate package + LetterSafetyFilter]
7. **SafetyBoundaryFilter HIGH-risk fixed flow (no free-form LLM)** — crisis keywords divert to resource page; no "我会一直陪你" language. Risk: filter absent/soft, LLM still free on HIGH. [E §10.2; P K.2.1–K.2.2; verify safety package]
8. **Rhythm protection (session duration / token / message caps)** with transparent gentle-pause copy. Risk: not implemented or fake. [E §10.3; P K.2.3]
9. **Seed capsules exist with distinct prompts/tags/topics & quality** — ≥5, not placeholders. [E §19; P H.2.1; verify MockDataInitializer]
10. **Mock mode is genuinely keyword-aware (not fixed text)** and logs to AiInteractionLog. [E §12.3; P L; verify MockLlmClient]
11. **Starfield "life-fragment" detail view + 放入星海 path** — click star → narrative + related shards + weave button → actual capsule creation. Risk: chart-only, dead detail. [V §4.5; P E.2.4; verify memory-starfield page + controller]
12. **Voice input path (mock ASR ok) with metadata capture & gentle prompt injection** — duration/rate/pauses, "避免直接判断情绪". Risk: voice UI missing or metadata dropped. [E §13; P Phase C; verify aurora-chat + DialogMessage fields]
13. **Long-term memory: summary-anchor sliding window & high-gravity hints injected** — "user never re-explains themselves." Risk: full-history concat or no memory injection. [V §5.2; P B.2.3; verify PromptBuilder]
14. **UX art direction actually delivered** — flowing warm palette (no pure black/cold blue), 7 time-bands + 6 weather, bloom/drift motion curves, glass panels, breathing/stardust. Risk: generic dark theme, static. [UX throughout; verify app.css + pages]
15. **Thought Shredder end-to-end** — shred ritual + extract + optional no-save + sediment into MemoryCard/ThoughtFragment/EmotionTrace. Risk: input-only or no persistence. [P Phase F]
16. **User data control** — view raw / delete / control capsule authorization / hide capsule / export / delete account. [V §8.3; verify user controllers]

---

## 8. Differentiators / "Wow" (what makes this special vs a generic chatbot)

1. **Emotional-gravity memory universe** — memory weighted by intensity×recurrence×importance×trigger with time decay, visualized as a living starfield where stars breathe, cluster into themes, and black holes pull recurring pain. Not a date list — a "情感引力场". [V §3.4, §5.3; E §7; UX §9.6]
2. **The capsule-as-bridge social model** — others meet a *desensitized authorized echo* of you first, feel resonance, then earn the right to write a slow letter. Connection-by-spiritual-theme with structural privacy — neither instant-chat nor AI-lover. [V §3.2, §4.6–4.8; E §8–9]
3. **Aurora as a conversational journal, not a Q&A bot** — active follow-up that splits tangled feelings, knows when to pause for rhythm, turns venting into structured self-echo *inside* the chat. The win condition is "我好像终于把自己说清楚了一点," not "this AI is smart." [V §2.2, §7; P Phase B]
4. **Ethics-as-architecture** — P0/P1/P2/P3 data isolation, safety filter, rhythm protection, anti-dependency copy, real-world re-orientation. The product's success metric is *not* session length — it's whether users grow and return to real life. [V §8, §12.1; E §10]
5. **A flowing, designer-website-grade inner-universe aesthetic** — warm time/weather-aware ambiance, bloom/drift motion, water-drop sound design, hand-written typography — feels like an artist's site, not an industrial dashboard. [UX §0, §14]

---

### Compact summary for synthesis stage

**North Star:** A gentle, trustworthy, long-term AI inner-world that remembers & understands you, helps you organize yourself, and — on consent — leads you toward real, deliberate resonance with others. Creed: AI is a mirror/bridge, not a doctor/replacement.

**Top 5 must-have features:** (1) Aurora conversational-journal Agent w/ 4 modes + active follow-up; (2) Daily Record Card from async distill; (3) Emotional-gravity Memory Starfield; (4) Authorized desensitized Echo Capsule + Echo Plaza + limited persona chat; (5) Ceremony-driven Slow Letters w/ full state machine + safety filter. (Plus: safety boundary/rhythm protection, Mock-first LLM, admin backoffice.)

**Top 5 vision-coverage gaps to verify:** (1) Aurora multi-message + mode-specific behavior real; (2) conversation→memory async sediment listeners wired & non-blocking; (3) real nonlinear gravity formula + recompute; (4) capsule-chat privacy enforced at data layer (no P0 raw leak); (5) slow-letter state machine + LetterSafetyFilter + parallax delay complete. (Also high-risk: DataMaskingService, SafetyBoundaryFilter HIGH fixed-flow, UX art direction actually delivered.)

**Top 3 differentiators:** (1) emotional-gravity memory universe; (2) capsule-as-bridge slow-social model with structural privacy; (3) Aurora as conversational journal whose win-state is "I finally said it clearly," not "smart AI."
