# Track B — B2 visible innovation: Aurora thinking + deliberate pacing, made legible in-stream

> Status: BUILDER_VERIFIED_IN_PROGRESS (Vitest + production build green; live in-browser beat during a
> real streamed turn is a remaining environment-gated check this session — see §4)
> Binds to: `docs/goal/complete-product-acceptance.yml` G3 `UX-SHELL` and Track B workstream B2
> ("Aurora stagecraft: render planning/thinking as subtle state; multi-message output with intentional spacing").

## 1. The gap this closes

The backend already emits the raw material for Aurora's "living" stagecraft, but the client threw it
away where the user is actually looking (the conversation):

- The server emits a deliberate `segment {break:true}` pacing event between bubbles (with its own
  ~220ms pause, `AuroraAgentServiceImpl` L534) — but `useAuroraSession.handleEvent` had **no
  `segment` case**, so the intended multi-message rhythm collapsed into an undifferentiated wrap.
- The planning/thinking runtime signal (understanding → composing → speaking) was rendered only as a
  small hero badge far from the stream, so before the first token the conversation area looked inert
  even while Aurora was "thinking".

## 2. What was implemented (frontend only — `web/**`)

1. `useAuroraSession.ts`: added a `segment` case that sets the runtime stage to `composing`, so the
   deliberate inter-bubble break reads as a brief "composing the next message" beat instead of being
   dropped. The next `bubble.started` flips back to `speaking`.
2. `AuroraConversation.tsx`: a new optional `thinkingStage` prop drives an inline, `aria-label`led
   "thinking" beat (animated dots + localized copy: "Aurora 正在理解这一刻…" / "正在组织下一句…") rendered
   at the tail of the conversation — but only while a turn is active and Aurora is pre-speech. During
   token streaming (`thinkingStage=null`) and after the turn ends (`activeTurnId=null`) it disappears.
3. `AuroraApp.tsx`: derives `thinkingStage` from the session runtime signal + `activeTurnId` and
   passes it down (one-line wiring; no domain logic moved).
4. `styles.css`: `.message.thinking` + `.thinking-dots` reuse the existing `loading-dot` keyframe and
   inherit the global `prefers-reduced-motion` guard (styles.css:350), so the beat is calm and
   respects reduced motion.
5. `vite.config.ts`: added an explicit `{ url: URL }` type to the PWA `runtimeCaching` `urlPattern`
   callback so `tsc -b` is robust to workbox type-version drift (runtime-neutral; see §4 note).

## 3. Verification (this session)

- `npm test -- --run src/components/AuroraConversation.test.tsx src/hooks/useAuroraSession.test.ts`
  → 2 files, **19 tests, 0 failing**. New assertions: the beat shows for `understanding`/`composing`,
  and is absent when the turn ended or while streaming tokens; a `segment` event moves the hook to
  `composing` and the following `bubble.started` returns it to `speaking`.
- Full suite `npm test -- --run --no-file-parallelism` → **30 files, 200 tests, 0 failing**
  (199 baseline + 1 new). `--no-file-parallelism` was required only because this machine OOM-kills
  vitest's default parallel worker forks — a local resource limit, not a test issue.
- `npm run build` (`tsc -b && vite build`) → **PASS**; Vite PWA `generateSW` emitted **18 precache
  entries** (matches baseline) and rebuilt `src/main/resources/static/app/aurora/{assets/app.js,assets/index.css,sw.js}`.
- `git diff --check` → clean.

## 4. Remaining / not yet proven

- **Live in-browser verification** of the beat during a real streamed turn is pending this session.
  The machine's C: drive was at ~0 GB free, so booting Spring Boot with file-backed H2 risked an
  ENOSPC mid-run corrupting the dev database — an unsafe action deferred deliberately. The Mock
  provider does produce multi-segment replies, so this is directly exercisable at a less
  disk-constrained point: register → send → observe the "正在理解…/正在组织下一句…" beat and the
  inter-bubble pacing. Recommended as the next Track B live check.
- Dependency note: `web/node_modules` was found broadly corrupted (several packages missing their
  `.mjs` builds) and `npm ci` fails because the committed `package-lock.json` is out of sync with
  `package.json` (missing `@emnapi/*`). A full `npm install` was needed to get a working tree; the
  regenerated lockfile was **reverted** so this checkpoint carries no dependency churn. Reconciling
  the committed lockfile so `npm ci` works from a clean clone is a real pre-existing infra gap for
  the integrator/Track B delivery front (B6).
- This is one B2 beat (Aurora stagecraft). Inline memory-provenance chips ("Aurora just used memory
  X") still need a backend contract (a Track A contract delta / Track B integration request), and the
  Inner Cosmos / Resonance B2 items remain open.
