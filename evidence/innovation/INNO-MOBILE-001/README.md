# INNO-MOBILE-001 — Living Aurora native shell and recovery checkpoint

Status: `BUILDER_VERIFIED / IN_PROGRESS`.

Date: 2026-07-15

## Product slice implemented

- Capacitor 8 Android and iOS projects package the same React Living Aurora experience.
- Owned deep links (`innercosmos://aurora/wake/{id}`) and the trusted HTTPS Aurora route
  reopen an owner-checked `WakeIntent`, restore its dialog session, and replace the browser
  location without creating a parallel conversation.
- App foreground and network recovery re-read the durable turn timeline and notifications.
  React StrictMode startup is generation-owned so stale asynchronous cleanup cannot remove
  the active native listeners.
- Push permission is user initiated. Registration tokens use iOS Keychain / Android Keystore;
  the unencrypted web fallback is explicitly refused for credentials.
- Microphone access is user initiated, immediately releases the probe stream, and does not
  claim to save audio. Android disables backup and cleartext traffic.
- A native build without `VITE_API_BASE_URL` stops at a visible environment gate instead of
  silently sending session traffic to Capacitor's local origin. `.env.mobile.example` records
  the non-secret build contract.

## Reproducible verification

From `web/`:

```text
npm test -- --run
  2 files / 6 tests passed
npm run build
  TypeScript + Vite production bundle passed
npm run mobile:sync
  Android and iOS synchronized; 4 plugins discovered on each platform
pnpm exec playwright test -g "offline mobile interruption"
  1/1 passed
scripts/run-living-aurora-e2e.ps1
  packaged-JAR Playwright 11/11 passed
../.tools/apache-maven-3.9.9/bin/mvn.cmd test
  Java 21 / Spring Boot 3.5.14 full gate 783/783 passed
```

From `web/android/` on Java 21 with Android SDK 36:

```text
gradlew.bat assembleDebug
  BUILD SUCCESSFUL
  output: app/build/outputs/apk/debug/app-debug.apk
```

The merged Android manifest was inspected after build and contains `allowBackup=false`,
`usesCleartextTraffic=false`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`, the owned custom scheme,
and the trusted HTTPS host. The APK is a local reproducibility artifact and is intentionally
ignored by Git; its exact size and SHA-256 are recorded in `artifact-sha256.txt`.

## Honest remaining gates

- No `google-services.json`, APNs entitlement/certificate, store signing identity, verified
  production API origin, or native session/CORS policy was available. Remote delivery is not
  claimed, and no credential-shaped placeholder is committed.
- Android compiled on this host, but install/deep-link/push/microphone behavior still requires
  a real Android device. iOS was synchronized only: Xcode, Apple signing, associated domains,
  APNs registration, and a real iPhone are external macOS/owner gates.
- `MOBILE-NATIVE` therefore moves from `UNASSESSED` to `IN_PROGRESS`, never `PASS`.
