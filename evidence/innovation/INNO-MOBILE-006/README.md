# INNO-MOBILE-006 — desktop (Tauri/Windows) compile pre-flight on the integrated HEAD

## Scope (read honestly)

This is a **compile pre-flight**, not a driven-journey proof. It verifies that the Tauri Rust
desktop shell's source still type-checks and compiles cleanly against the current integrated HEAD
(after the W1 voice merges: `codex/w0-integration` @ `96b039f`). It does **not** prove that PKCE /
Aurora / recovery / notification / deep-link / offline-draft journeys actually run end-to-end on
Windows — that real-device-journey proof is the still-open `G7.MOBILE-MACHINE-RUNTIME` work, which a
first dedicated round did not complete (its agent was terminated by an external API session-limit
before producing any commits).

## Command and result

```
cd web/src-tauri
cargo check --message-format=short
```

```
   Compiling inner-cosmos v0.1.0 (D:\code\inner cosmos\web\src-tauri)
warning: error finalizing incremental compilation session directory
  `...target\debug\incremental\inner_cosmos_lib-...\s-...\working`: 拒绝访问。 (os error 5)
warning: `inner-cosmos` (lib) generated 1 warning
    Finished `dev` profile [unoptimized + debuginfo] target(s) in 9.74s
```

- Exit code: **0**. Build finished in 9.74s against the warm `target/` cache (the desktop shell has
  been compiled in this workspace before, so this is not a cold dependency compile).
- The single warning is a Windows file-lock/permission quirk while finalizing the **incremental
  compilation cache directory** (`os error 5 / 拒绝访问` — "access denied" on the cache's own
  `working` folder). It is not a code defect: it does not affect the produced artifact, recurs
  across unrelated projects on Windows, and vanishes on a non-concurrent run. No source warning, no
  missing-API error, no type mismatch — every Tauri command/plugin/feature referenced by the Rust
  shell (`tauri-plugin-deep-link`, `-notification`, `-opener`, `-stronghold`, `-single-instance`,
  `keyring`, etc.) resolves.

## Why this matters for the open journey work

The journey round was about to spend its budget on `tauri build`/`tauri dev` plus the Android
emulator. A failing compile would have been discovered only after the slow emulator boot and a
frontend rebuild — burning the round on a bug instead of journeys. This pre-flight de-risks that:
the desktop shell compiles, so the next round can go straight to `tauri dev` + a running dev backend
and drive the journeys, rather than debug a compile break first.

## What is still NOT proven here

- No actual `tauri dev`/`tauri build` was run (a full `tauri build` would execute its
  `beforeBuildCommand` `pnpm build:tauri`, which rebuilds the frontend in tauri mode into the
  committed Spring static bundle dir — deliberately avoided here to keep the committed bundle clean;
  the Integrator owns that single rebuild).
- No journey was driven; no Windows process was launched.
- The Android (Capacitor) side is entirely untouched by this check.
