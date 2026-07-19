# Track B — B1 nested resource routes (shareable capsule deep links)

> Status: BUILDER_VERIFIED_IN_PROGRESS
> Binds to: Track B workstream B1 (real client routes, stable deep links) / spec route model
> (`/resonance/capsule/:id`).

## 1. What was implemented (frontend only)

The five-space router already resolves nested paths to the right space; this adds the first true
**resource** deep link on top of it:

- `resourceFromPath(pathname)` in `ProductShell.tsx` parses a nested path into
  `{ space, resource, id }` (e.g. `/resonance/capsule/42` → `{resonance, capsule, 42}`); `id` is only
  a value when it is purely numeric, else `null`.
- `capsulePath(id)` builds the shareable link and round-trips through `resourceFromPath`.
- `AuroraApp` wires it both ways: an effect maps `/resonance/capsule/:id` onto the existing
  `selectedCapsuleId` (deep-link entry / back-forward), and selecting a capsule now `navigate`s to
  its path (or back to `/resonance` on deselect) so the URL reflects the open capsule. The capsule
  domain still owns loading/rendering; a stale or foreign id simply resolves to the normal empty
  state (no crash).

## 2. Files
- `web/src/components/ProductShell.tsx` (`resourceFromPath`, `capsulePath`, `ProductResource`)
- `web/src/components/ProductShell.test.tsx` (+4 cases)
- `web/src/AuroraApp.tsx` (deep-link effect + navigate-on-select)

## 3. Verification
- `npm test -- --run src/components/ProductShell.test.tsx` → **10/10** (new: nested-resource parse,
  bare-path null resource/id, non-numeric id rejected, capsule round-trip).
- Full suite `npm test -- --run --no-file-parallelism` → **206/206**; `npm run build` PASS (18 PWA
  precache entries), static assets rebuilt.

## 4. Remaining
- Live in-browser deep-link check (open `/resonance/capsule/:id` → that capsule is selected) is a
  follow-up — exercising it requires creating a capsule first; the parser round-trip + wiring are
  deterministically covered here.
- Other resource routes (`/connections/letters/:id`, `/cosmos/portrait`) can follow the same
  `resourceFromPath` pattern; only the capsule link is wired this slice.
