# Track B B1/B5 handoff checkpoint — 2026-07-18

Status: BUILDER_VERIFIED / IN_PROGRESS. This is a recoverable checkpoint, not Track B completion.

## Accepted implementation

- useConnectionsAndLetters extracts connection requests, friends, people discovery, relations,
  slow-letter threads and reply lifecycle from AuroraApp.tsx.
- InstallPrompt, PwaUpdateNotice and UpdateBanner provide explicit install and update UX.
- Service-worker registration now uses a prompt-controlled update flow. It does not silently reload
  an active Aurora conversation.
- Spring-served static assets were rebuilt from the reviewed React source.

## Machine verification

- cd web && npm test -- --run: 30 files, 199 tests, 0 failed.
- cd web && npm run build: PASS; Vite PWA generated 18 precache entries and
  workbox-98f7a950.js.
- git diff --check: PASS before checkpoint commit.

## Evidence scope

- Unit/component tests prove hook behavior and install/update state transitions.
- The two screenshots demonstrate the install affordance in the real app shell.
- This checkpoint does not prove browser installability on every platform, a deployed-version
  upgrade during an active turn, or native-device behavior.
- observation-log-b1-pwa-install-update-error.txt is retained as failure-inclusive evidence; it
  records an observation-script issue and must not be described as a product PASS.

## Open work

1. Continue cohesive domain extraction from AuroraApp.tsx: memory/profile, capsule/resonance,
   psychology skills, portrait and account remain inline.
2. Add nested resource routes and progressive disclosure for advanced Aurora/Self controls.
3. Replace shared busy booleans with per-item identifiers and close the slow-letter double-submit
   gap.
4. Rehearse update availability while Aurora is active and idle; validate install and update on
   supported browsers and real devices.
5. Finish localized offline copy, Capacitor/device gates, B2-B4, B6 and B7.
6. Resolve TB-REQ-001 and TB-REQ-002 with the integrating coordinator.
