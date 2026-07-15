# Five-space AppShell foundation checkpoint

Status: `BUILDER_VERIFIED / IN_PROGRESS`

Date: 2026-07-15

## User-visible result

The React/TypeScript product now has one persistent AppShell with five explicit spaces:

- `今天` — Aurora conversation, interruption/replan, WakeIntent returns and Aurora Self.
- `内宇宙` — understanding correction, memory provenance/starfield and psychology Skills.
- `共鸣` — consented Capsule Genome creation, simulator and resonance discovery.
- `连接` — arriving slow letters and bilateral relationship consent.
- `我的` — authentication/device state, proactive-return controls, confirmed claims and sharing boundaries.

Space selection is represented by the `space` URL query parameter, restored on browser navigation and
page reload, and never resets product state. Desktop uses a sticky top switcher; narrow mobile layouts
use a safe-area-aware bottom navigation. Cross-space operations expose one global status channel, so a
correction, rollback, publication or consent action remains perceivable outside the Aurora composer.

This change reorganizes existing capability without deleting or reducing Aurora proactive behavior,
Self/Emergence, psychological modelling, Capsule personality, slow letters, starfield or matching.

## Executable verification

See `test-summary.md`. The packaged-JAR browser suite exercises all five product spaces rather than
bypassing the shell, including preserved URL state after logout/login and reload. The independent
Living Aurora contract additionally confirms that scheduler delivery, proactive SSE, deep-link
continuation and feedback still work through the new shell.

## Honest boundary

This is the information-architecture and navigation foundation, not closure of `UX-SHELL` or G3.
`AuroraApp.tsx` remains oversized and domain sections have not yet been extracted into a shared
component system. Visual regression, automated accessibility, complete i18n and performance budgets
also remain open. The next machine front is component decomposition with state and API contracts kept
stable.
