# INNO-MOBILE-002 — native OIDC and production API trust boundary

Status: `BUILDER_VERIFIED / REAL_IDP_AND_DEVICE_PENDING`

Date: 2026-07-15

This checkpoint replaces the Capacitor shell's cookie/password assumption with a native public-client contract:

- System-browser Authorization Code flow with PKCE S256, cryptographic state and nonce.
- Exact app-owned callback validation for `innercosmos://oauth/callback` (or the owned HTTPS callback).
- Server bootstrap and OIDC discovery must agree on issuer, authorization and token endpoints.
- ID tokens require a keyed RS256 signature from the discovered JWK set plus issuer, audience, expiry and nonce validation.
- Access/refresh/ID tokens and the short-lived authorization transaction use iOS Keychain / Android Keystore-backed storage, survive process restart, refresh before expiry, retry once after API 401, and are removed on logout after revocation attempts.
- Native REST, Aurora stream, timeline replay and proactive SSE use `Authorization: Bearer`; native traffic cannot fall back to username/password, Cookie, or CSRF authentication.
- Spring Security now consumes the MVC CORS contract, and the Capacitor `https://localhost` origin can preflight Authorization and Last-Event-ID headers.

The production API origin is fail closed. A configured base must be an absolute allowlisted HTTPS origin with no credentials, query, fragment, path or custom port. Local, private, link-local, benchmark, IPv6 and host-confusion variants are rejected. `VITE_API_ALLOWED_ORIGINS` is compiled into the build; runtime errors never silently fall back to the WebView origin.

## Verification

- React/Vitest: 3 files, 12 tests.
- Frontend production build: PASS.
- OIDC resource-server/CORS focused Java tests: 8 tests, 0 failures/errors/skips.
- Capacitor sync discovers Browser, App, Network, Push and Secure Storage plugins on Android and iOS.
- Android `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: PASS. This compiles the corrected `sg.innercosmos.app` instrumentation assertion.
- Security-risk full gate: Java `784/784`, SpotBugs `0`, packaged-JAR Playwright `11/11`, Jar and SBOM PASS.

The aggregate Gradle `assembleAndroidTest` additionally builds tests for generated plugin subprojects and currently fails inside the empty Cordova compatibility project on duplicate Kotlin 1.6/1.8 stdlib classes. The correct application-scoped tasks are green; no device execution is claimed.

## Remaining human/external gates

- A real external IdP tenant must register the exact public client, callback and CORS policy, then run code exchange, refresh, revoke, process-death restoration and invalid-token scenarios on devices.
- Real Android/iOS devices and store signing are still required.
- Android App Links and iOS Universal Links still require published domain-association files and release certificate/team identifiers; no domain ownership claim is made by this checkpoint.
