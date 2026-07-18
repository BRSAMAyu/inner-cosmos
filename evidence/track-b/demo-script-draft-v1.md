# Track B — 8-12 Minute Demo Script (Draft v1)

> **Status: DRAFT, not ready to run.** This is a first-cut skeleton built from what B0-B1 have
> already live-verified (see `golden-journeys.md`), so B7 has a head start instead of starting cold
> once B2-B6 land. Sections marked **[PENDING B2/B3/B5/B6]** depend on work not yet done and must not
> be presented as-is. Re-validate every beat against the live product before this is actually used —
> a demo script rots the moment the UI it describes changes underneath it.

Per `docs/tracks/TRACK-B-COMPLETE-EXPERIENCE.md` §11, four acts, 8-12 minutes total, non-author
runbook, product value explained before infrastructure.

---

## Pre-show setup (not part of the timed 8-12 minutes)

- Environment: `local-complete` if B6 is done by demo day (PostgreSQL/pgvector + TLS Redis + real
  provider); otherwise `dev` profile (H2 + Mock) is an honest fallback — **say so out loud**, don't
  pretend Mock output is a real provider. **[PENDING B6]** local-complete is not yet proven
  reproducible from a clean checkout (blocked on a real external OIDC IdP, a documented human/infra
  gate — see `docs/goal/tracks/track-b-status.yml`).
- One fresh account, created live in Act 1 (not pre-registered) — the golden-first-experience
  journey is stronger shown from zero than from a warmed-up account.
- A second, pre-warmed account with some conversation/memory history, for Act 2's longitudinal
  return (WakeIntent) beat — cannot be created live in the 8-12 minute window since it depends on
  real wall-clock passage or a fast-forward mechanism. **[PENDING]** — see the open WakeIntent
  fast-forward item below; until it exists, Act 2 must either wait real time before the demo or be
  narrated from a recording/screenshot instead of driven live.
- Have a fallback screen recording of each act ready in case a live provider call is slow or fails
  mid-demo (per §11's "fallback if a Provider is slow" requirement) — not yet recorded.
- Cleanup: delete/reset the demo accounts after the session; do not leave demo data in a shared
  environment. **[PENDING B6]** — needs the deterministic seed/import/reset command B6 calls for.

---

## Act 1 — "Aurora is alive" (~2.5 min)

**Live-verified building blocks (see `golden-journeys.md` J1):**
1. Open `/app/aurora/` — the single documented entry point now offers both login and register on
   one screen (`AuthGate`, shipped this session). Register a fresh account on camera — this alone is
   worth narrating: "this used to be a dead end; now it isn't."
2. Say something meaningful to Aurora. **Live-verified strength, not a claim**: multi-message,
   naturally paced output, a visible "正在回应"/dual-core status signal, and a stop/interrupt control
   available mid-stream. Let one response actually play out, then interrupt it on camera — the
   product's most genuinely differentiated moment is the *interrupt*, not just that it streams.
3. Point out (don't over-explain) the composer is now immediately reachable on both desktop and
   mobile without scrolling past capability displays (this session's mobile-reorder fix) — good for
   a phone-in-hand cutaway shot if doing a mobile pass.

**[PENDING B2]**: after the exchange, explicitly connect "you just talked" to "here's your
starfield" — B0 found this connection is not yet made explicit in-product; B2 needs to close this
before Act 1 can end on the intended beat rather than trailing off.

**[PENDING]**: whether a single short exchange is enough to produce a correctable memory/claim
candidate to show, or whether Act 1 needs a slightly longer seeded conversation to guarantee one
exists on demo day — UNVERIFIED per `golden-journeys.md` J1 step 6, retest before locking this act's
timing.

---

## Act 2 — "The system understands me" (~2.5-3 min)

Target: memory/portrait source, a live correction, and the starfield reflecting the change.

**[PENDING B2/B6]** — this act's WakeIntent longitudinal-return opening beat is the least-verified
journey in the whole product (`golden-journeys.md` J2): the UI to negotiate a return time is
live-verified reachable, but actually receiving and resuming a WakeIntent return has not been driven
end-to-end without waiting real hours. Do not script this act around a live WakeIntent trigger until
a dev-safe fast-forward exists (flagged as a `track-b-integration-requests.yml` candidate, not yet
filed as of this draft) or until real wall-clock time is deliberately built into the rehearsal
schedule.

Once buildable, the beat should show: arrival banner with a real "why I'm here" reason -> resume the
right context -> user makes a correction -> `UnderstandingCorrection`'s "预览会改变什么" impact
preview -> starfield updates. `ClaimCandidateReview` and `UnderstandingCorrection` both already exist
and are reachable; the *conflict-preview* and *starfield-reflects-the-change* halves are UNVERIFIED
live per B0 and should be confirmed with a real multi-turn account before this act is finalized.

---

## Act 3 — "Understanding becomes connection" (~2.5-3 min)

Target: authorized Capsule, explainable match, conversation, slow letter.

**Resolved this session**: the 11 sample Resonance personas (洛哥, 苏格拉底, 庄周, ...) are
source-confirmed intentional showcase content (`evidence/track-b/discoveries/resonance-persona-provenance.md`),
not a data leak — safe to show proudly once B2 adds the "official sample capsule" provenance badge
so the audience never mistakes them for real platform users. **[PENDING B2]** — that badge does not
exist yet; do not demo the Resonance discovery list to an audience before it does, or narrate the
distinction verbally as a stopgap.

**[PENDING]** everything past "browse the sample personas" — compiling/previewing a Capsule from the
demo account's own memories, an actual persona conversation, and a slow-letter send/receive round
trip are all UNVERIFIED live per `golden-journeys.md` J3 (they need either real memory content on the
account or a longer rehearsal window). This act cannot be locked until at least one full pass of
"my capsule -> a match -> a conversation -> a sent letter" has been driven live once.

---

## Act 4 — "The product is real" (~1.5-2 min)

Target: local-complete architecture + a short EKS scale/recovery/observability proof.

**[PENDING B6]**: local-complete's real-provider path is not yet proven reproducible from a clean
checkout — currently blocked on requiring a real, human-provisioned OIDC identity provider (this is
by design per `对齐文档/18`, not a bug, but it means Act 4 cannot yet be rehearsed against
`local-complete` and must either wait for that human gate to be resolved or fall back to describing
the architecture without a live run.
The Academy EKS half of this act is Track A/ops territory (`对齐文档/14`); coordinate with whoever
owns that proof rather than duplicating it here.

---

## What this draft already unblocks

- A concrete checklist of exactly which UNVERIFIED journeys (from `golden-journeys.md`) block which
  act, so B2/B5/B6 work can be prioritized by "which one is on the demo's critical path" rather than
  worked in an arbitrary order.
- A running list of missing pieces for Act 7 telemetry/runbook work: none of this draft's acts have
  privacy-safe telemetry events defined yet (journey completion, perceived-understanding feedback,
  correction, proactive helpfulness, resonance outcome, per §11) — that's still fully open.

## What must happen before this draft becomes a real demo script

1. WakeIntent dev-safe fast-forward (file as a `track-b-integration-requests.yml` request if it needs
   backend support — not yet filed).
2. B2's "why this feels like me" / "go see your starfield" connective UI moments.
3. At least one full live pass of Act 3's capsule-to-letter chain on a real account with memory
   content.
4. B6's local-complete reproducibility (currently gated on a real OIDC IdP, a human/infra gate).
5. A fallback recording of each act, and the actual telemetry events from §11.
