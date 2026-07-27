# PUBLIC-DEMO-CURATED-001

## Claim

On 2026-07-27 the three classroom showcase capsules were made stage-grade over the public HTTPS
origin: every visitor (including anonymous browsers) can reach them, each speaks with its own
authored voice grounded in its own authorised memory, and capsule copy follows the visitor's
language instead of a hardcoded Chinese default. The DEMO_30S slow letter now arrives on time.

All outputs below are real DeepSeek provider responses over the Cloudflare Quick Tunnel
(`LLM_ALLOW_FALLBACK=false`, no Mock), captured from a freshly registered non-seeded account.

## What changed

| Change | Where |
|---|---|
| Authored persona layer for the three showcase identities | `ai/capsule/CuratedPersonaCatalog.java` |
| Dedicated `CURATED_PERSONA_CHAT` stage (real provider required, temperature 0.9, 2048-token / 30s envelope) | `service/impl/PersonaChatServiceImpl.java`, `ai/structured/StructuredAiService.java` |
| Visitor-language mirroring for replies, the identity disclaimer, quota/session-cap guidance and boundary refusals | `util/VisitorLanguage.java`, `service/impl/PersonaChatServiceImpl.java` |
| Showcase capsules lead the plaza (`echo_energy` above the ten official seed agents) | `config/MockDataInitializer.java` |
| `@Scheduled` pool sized past Boot's single thread | `src/main/resources/application.yml` |
| Stale Chinese curated-capsule names in the acceptance gate | `scripts/demo/verify-public-demo.ps1` |

The authored layer replaces only the *voice* half of the prompt. The compiled/seeded persona prompt
still travels with it, and authorisation, boundary, blocked-topic, prompt-leakage and contact-masking
gates are unchanged — ordinary user capsules keep the untouched `PERSONA_CHAT` path.

## Reach: every user, including anonymous

```text
GET /api/plaza/capsules   (no session)      -> 13 capsules, first = Lin Che's Echo
GET /api/plaza/capsules   (registered user) -> 13 capsules, the three showcase capsules ranked 1-3
POST /api/v1/persona-chat/session/create    -> ACTIVE for a freshly registered account
```

Plaza order after the energy change:

```text
 id |                  pseudonym                  | echo_energy
 11 | Lin Che's Echo                              |        0.99
 13 | The One Learning to Include Herself in Care |        0.98
 12 | The One Who Walks by the River              |        0.97
  4 | Midnight Radio                              |        0.94
```

## Authored voice, one shared prompt, three different lives

Visitor prompt (English): *"I keep saying yes to everything and then I resent people for it. I don't
know how to stop without feeling like a bad person."*

- **Lin Che's Echo** (3s) — "…I spent months saying yes to every request about the project — feature
  tweaks, extra meetings, feedback loops — then felt quietly furious at people who were just taking
  what I offered. The thing that helped wasn't a bigger boundary system. It was noticing that the
  resentment showed up about three hours before I ever said no… Are you feeling it while you're still
  in the conversation, or does it hit you later?"
- **The One Who Walks by the River** (3s) — "…The first few months here I said yes to every studio
  invitation, every group dinner — and I'd walk home along the river feeling hollow… The drawing I
  kept revising sat in the corner for weeks. I wasn't revising the drawing; I was trying to prove my
  judgment mattered by not refusing anything."
- **The One Learning to Include Herself in Care** (2s) — "…What helped me was starting with one small
  boundary that felt survivable — like leaving a work message until morning — and noticing that the
  relationship didn't actually collapse."

Each reply lands on that capsule's own authorised memory (the project loop, the studio drawing and
the riverside route, the work message left until morning), so two capsules compared in the same
session sound like two people rather than one voice twice.

## Language mirroring

Visitor prompt (Chinese): *"我最近总觉得自己不属于现在待的地方，但也说不上来哪里不对。"*

- All three replied in Chinese, with the Chinese identity disclaimer
  `（这是授权共鸣体的回应，不是真人实时在线。）`.
- The same capsules answering the English prompt used the English disclaimer
  `(You are hearing an authorised resonance capsule, not the person live.)`.

Before this change, the same English question produced one English reply and two Chinese replies,
and the disclaimer was always Chinese.

Runtime-owned copy (daily quota, per-session cap, boundary refusal, provider-unavailable) mirrors the
same signal; `PersonaChatServiceImplQuotaTest#reply_quotaAtLimit_mirrorsVisitorLanguage` pins both
directions.

## Slow letter timing

`@Scheduled` jobs shared Spring Boot's default single-thread pool, so `LetterDeliveryJob`'s
5-second poll was starved by slower neighbours and effectively ran once every 80-90 seconds:

```text
before  scheduled arrival 13:35:01  ->  delivered 13:36:00   (59s late; the 55s acceptance window failed)
after   sent 13:42:54, scheduled 13:43:24  ->  DELIVERED observed at t+32s
```

## Automated gates

```text
./mvnw test                      -> Tests run: 1467, Failures: 0, Errors: 0, Skipped: 1
CuratedPersonaCatalogTest        -> 6 tests, green (resolution gate, distinctness anchors, language mirroring)
```

## Acceptance-gate status on the live public origin

`scripts/demo/verify-public-demo.ps1` against the live Quick Tunnel origin reached and passed, in
order: public health, landing page, `/app/aurora` browser bundle, APK download, exactly three curated
personas, two isolated sandbox sessions per story, the three curated capsules discovered in the
plaza, an active visitor session for each, the curated `DEMO_30S` slow letter through its full
lifecycle (send → FLYING → DELIVERED → read → connection request → visible to both sides), two fresh
registrations, mutual discovery, friend request/accept, private group create/invite/accept/members,
and three distinct curated capsule voices for one shared prompt.

It then fails on a pre-existing assertion outside this bundle's scope:

```text
verify-public-demo.ps1:391  throw "Aurora speaking kernel fell back during public acceptance."
```

Reproduced three times with the gate's own prompt on the same build:

```text
run1  speaker 8021ms  speakerFallbackUsed=true   fallbackReason=speaker-fallback  wall 40s
run2  speaker 8009ms  speakerFallbackUsed=true   fallbackReason=speaker-fallback  wall 38s
run3  speaker 7327ms  speakerFallbackUsed=false                                   wall 27s
stage latencies: plan 19.5-25.8s (foreground), critic 0 or 6.0s, runtime dual-kernel.current-turn.v2
```

`StructuredAiService#applyLatencyContract` gives `AURORA_SPEAKER_` an 8000ms deadline. The speaker now
consumes 7.3-8.0s of it, so two runs in three time out and degrade (which also trips the critic's 6s
deadline). The deadline is old; what changed is that Aurora's planning moved into the foreground of
the current turn (`guidanceSource=current-turn-plan`, `backgroundPlannerScheduled=false`), which both
enlarges the speaker's work and pushes the visible turn to 27-40s. That runtime
(`service/impl/AuroraAgentServiceImpl.java`) was under concurrent modification by another session
while this bundle was produced, so it is deliberately left untouched here and handed back to that
owner rather than papered over by raising the deadline.

## Honest boundaries

- DeepSeek is the only chat provider actually usable on this machine: the operator key file carries
  DeepSeek and Qwen only, and `generativelanguage.googleapis.com` answers `403` from inside the app
  container, so the runbook's Gemini stage split degrades to DeepSeek here.
- Capsule *metadata* (pseudonym, intro, public tags) is still English-only; only the conversation and
  runtime copy are language-mirrored.
- The three authored personas are product-designed demo characters with no real subject behind them.
  This layer is not a template for real user capsules, which keep the compiled-persona path.
