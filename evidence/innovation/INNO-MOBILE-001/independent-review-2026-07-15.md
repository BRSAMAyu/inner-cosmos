# INNO-MOBILE-001 independent review — 2026-07-15

Review baseline: `d6bf5b518c4e8904056413feadf0d51420ac28a0`

Verdict: `ACCEPT_CHECKPOINT / CHANGES_REQUIRED_FOR_PRODUCTION_MOBILE`

## What is accepted

The checkpoint establishes a credible Capacitor 8 Android/iOS shell around the Living Aurora
React experience. Durable timeline recovery, foreground/network resume, WakeIntent deep-link
routing, explicit permission requests, native secure storage, and Android cleartext/backup
hardening are implemented. The submitted evidence is appropriately labelled
`BUILDER_VERIFIED / IN_PROGRESS` and does not claim APNs/FCM, signing, iOS build, or real-device
completion.

Independent reproduction in a detached worktree confirmed:

| Gate | Result |
|---|---|
| `mvnw.cmd clean verify` on Java 21 | `PASS` — 783 tests, 0 failures/errors/skips; Jar and CycloneDX SBOM built |
| SpotBugs gate | `PASS` — 0 findings |
| Secret scan | `PASS` — 0 findings |
| Vitest | `PASS` — 2 files, 6 tests |
| TypeScript + Vite production build | `PASS` |
| Packaged-Jar Playwright | `PASS` — 11/11 |
| Capacitor Android sync + `assembleDebug` | `PASS` after supplying the installed Android SDK path |

## Findings

### P1 — the native production authentication contract is not implemented

The production backend already exposes OIDC metadata and accepts bearer JWTs, but the mobile
React client never starts Authorization Code + PKCE, obtains or refreshes tokens, stores an
access token, or sends an `Authorization: Bearer` header. It still submits username/password
to `/api/auth/login` and relies on `credentials: include`, CSRF and a server session.

The Capacitor WebView origin is `https://localhost`, while a configured production API is a
different origin and the production cookie is `SameSite=Strict`. That combination is not a
working native cross-origin Cookie session. The shell can build without proving that a user
can authenticate to a real production API.

Required closure: implement OIDC Authorization Code + PKCE with system-browser handoff and an
owned redirect URI; validate state/nonce; keep refresh credentials in native secure storage;
attach short-lived bearer access tokens to REST and SSE; handle expiry, logout and account
switch; and cover negative, restart and revocation paths against a production-like IdP/API.

### P1 — `VITE_API_BASE_URL` is configured, not trusted

`web/src/api.ts` treats every non-empty string as a valid API base. It does not parse the URL,
require HTTPS on native builds, reject userinfo/query/fragment, enforce an approved host or
build-time origin allowlist, or fail if the resolved API path escapes the intended origin.
Consequently, the visible environment gate prevents an empty base but does not establish a
trusted production destination. The README wording should not be interpreted more strongly.

Required closure: centralize a native runtime configuration validator; allow only exact HTTPS
origins selected by the signed build flavor; reject localhost/private-network and malformed
production values; display the non-secret environment identity; and add build/unit tests for
host confusion, downgrade and path-prefix cases. Certificate pinning is optional and must have
a rotation strategy if adopted.

### P2 — deep links are routable but not yet OS-verified owned links

Android declares the HTTPS intent filter without `android:autoVerify=true` and there is no
checked Digital Asset Links contract. iOS declares a custom scheme but no Associated Domains
entitlement/`apple-app-site-association` evidence. The custom scheme can be claimed by another
installed application. Backend owner checks reduce impact, but the platform trust claim is not
complete.

Required closure: add Android App Links and iOS Universal Links for the signed production
domains, host and verify both association files, retain the custom scheme only as a controlled
fallback, and test forged/foreign links and logged-out recovery.

### P2 — the generated Android instrumentation smoke has the wrong package assertion

`web/android/app/src/androidTest/java/com/getcapacitor/myapp/ExampleInstrumentedTest.java`
expects `com.getcapacitor.app`, while the actual application id is `sg.innercosmos.app`. A real
instrumentation run will fail even though `assembleDebug` succeeds.

Required closure: move/update the test to the owned package, run it on an emulator or device,
and add deep-link, permission and process-restart instrumentation coverage rather than keeping
the generated placeholder as apparent evidence.

## Acceptance decision

No rollback is required. The implementation is a useful, reproducible foundation and its
current `IN_PROGRESS` ledger status is correct. Production-mobile wording and downstream Goal
execution must treat the two P1 findings as machine-actionable work, not as human/device gates.
APNs/FCM credentials, store signing, Xcode and physical-device receipts remain legitimate
external gates only after the authentication, origin-trust and verified-link code paths exist.
