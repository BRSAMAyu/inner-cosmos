# INNO-MOBILE-003 — verified web-link ownership handoff

Status: `CONFIGURATION_PASS / DOMAIN_OWNER_GATE`

Date: 2026-07-15

The app-side configuration is now executable and fail closed:

- Android declares `android:autoVerify=true` only on the HTTPS `app.innercosmos.sg/app/aurora*` filter.
- The merged debug manifest retains the verified-link attributes, and app/debug instrumentation APKs compile.
- iOS Debug and Release use `App/App.entitlements`, which contains only `applinks:app.innercosmos.sg`.
- Templates pin Android package `sg.innercosmos.app`, Apple bundle `sg.innercosmos.app`, the Aurora route and the optional owned HTTPS OIDC callback.
- `scripts/mobile_domain_association.py` validates native configuration, rejects malformed owner identifiers, renders `.well-known` files only from a real release SHA-256 certificate fingerprint and Apple Team ID, and performs redirect/content-type/exact-payload checks against the live HTTPS domain.

Local verification passed 3/3 Python tests and the configuration preflight. A live check on 2026-07-15 found that `app.innercosmos.sg` did not resolve in DNS, so neither association endpoint is published. System ownership is not claimed.

The remaining domain-owner procedure is in `human-domain-ownership-checklist.md`. It is intentionally impossible to render production files without the owner-controlled certificate fingerprint and Apple Team ID.
