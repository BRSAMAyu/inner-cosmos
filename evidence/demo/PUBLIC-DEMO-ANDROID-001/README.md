# PUBLIC-DEMO-ANDROID-001

## Claim

The exact APK distributed by the public Demo endpoint was installed and driven on an Android API
36.1 emulator. A fresh user registered inside the installed App, entered the five-space product,
sent an Aurora message through the public tunnel, and received a real two-bubble response plus the
additive Inner Voice.

## Runtime

- Target: `emulator-5554`, state `device`
- Package: `sg.innercosmos.app.dev`
- APK SHA-256:
  `19d4489b07313925e7423e7d9fd736a7253c4537c1a27d03c87dda86319aa61a`
- Public-download SHA-256: identical to the installed artifact
- Process: running
- Fatal exception count: `0`
- ANR count: `0`
- Raw logcat persisted: `false`

The first attempt exposed a real Redis Spring Session cookie defect: the server emitted
`SameSite=Lax`, so the Capacitor `https://localhost` WebView could register but could not retain a
cross-origin Demo session reliably. The repair configures Spring Session's own `CookieSerializer`
explicitly. The live response then emitted `Secure; HttpOnly; SameSite=None`; Android Debug alone
allows the required third-party HTTPS session cookie, while Release resources keep that permission
disabled.

The first message-stage request also exercised the SPA's intended CSRF rotation path:
`403 -> fresh CSRF token -> one retry -> 200`. The resulting stream completed normally.

## Files

- `android-launch.png`: authenticated Aurora result inside the installed App.
- `runtime.txt`: device/package/hash/timestamp receipt.
- `logcat-summary.txt`: allowlisted process-health receipt; it intentionally contains no raw logs.

## Boundaries

This is emulator evidence, not a claim about Play Store signing or a physical handset. Before the
classroom session, one real Android phone should perform a final sideload and cellular-network
smoke using the newly generated URL and APK.
