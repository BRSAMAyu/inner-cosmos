# INNO-MOBILE-009 — PKCE re-proven end-to-end against a local Keycloak on the integrated HEAD

Status: `BUILDER_VERIFIED_AGAINST_REAL_LOCAL_IDP_ON_INTEGRATED_HEAD`

Date: 2026-07-23 (session 2026-07-24 +08)

This checkpoint closes the `G2.AUTH-OIDC` / `G7.MOBILE-MACHINE-RUNTIME` "IdP-gated on Mock dev"
caveat for the **current integrated HEAD** (branch `codex/w2-pkce-reconcile`, commit `fdae565` —
which already carries the merged voice, demo, capsule-voice and slow-letter work). A local
Keycloak is a machine resource (Docker), not a human gate, so re-proving the PKCE contract here
is machine work, not external. The prior `INNO-MOBILE-004` proof was made against a
pre-integration HEAD; this one confirms the integration did not break native OIDC PKCE.

## What was driven (real HTTP, not "should work")

`scripts/start-keycloak.sh` stands up a real Keycloak 26.7.0 dev instance in Docker, importing
the exact prior-session realm (`deploy/compose/keycloak/inner-cosmos-realm.json`): realm
`inner-cosmos`, public client `inner-cosmos-mobile-local`, PKCE `S256` required, redirect URI
`innercosmos://oauth/callback`, the `inner-cosmos-api` audience mapper, and the `mobile-demo`
test user. The only deliberate difference from the prior session's compose is the hostname: the
container is started with `--hostname=http://localhost:8081` (instead of the Android-emulator-only
`10.0.2.2`) so the authorize/token/JWKS endpoints are host-reachable without an emulator. The
realm/client/redirect/PKCE policy are byte-identical to the prior proof.

The Spring Boot backend was booted natively (`./mvnw spring-boot:run`, default `dev` profile =
file-backed H2 + Mock LLM, no Redis/Postgres needed) with OIDC enabled and pointed at that
Keycloak: `OIDC_ISSUER_URI=http://localhost:8081/realms/inner-cosmos`,
`OIDC_JWK_SET_URI=…/certs`, `OIDC_AUDIENCE=inner-cosmos-api`, client
`inner-cosmos-mobile-local`, redirect `innercosmos://oauth/callback`.

`scripts/pkce-roundtrip.mjs` then drives the **same Authorization Code + PKCE (S256) contract**
that `web/src/mobile-auth.ts` drives on a device/Tauri shell, but from the host:

1. Bootstrap the native OIDC contract from `GET /api/public/auth/mobile-oidc` and assert it
   requires `authorization_code` + PKCE `S256` with the app-owned `innercosmos://oauth/callback`.
2. Fetch OIDC discovery and assert issuer + authorize/token endpoints match the signed bootstrap.
3. Generate a local `code_verifier` (64 random bytes, base64url) and `code_challenge`
   (base64url(SHA-256(verifier))) — identical to `createPkceChallenge` in `mobile-auth.ts`.
4. Drive the real Keycloak authorize endpoint, authenticate `mobile-demo` through the real login
   form (cookie jar), and **intercept** the `innercosmos://oauth/callback?code=…&state=…`
   redirect (followed manually to a custom scheme, never auto-followed).
5. Exchange `code` + `code_verifier` at the token endpoint — the PKCE check happens here.
6. Verify the minted ID token: RS256 signature via the discovered JWKS (`kid` match +
   `crypto.subtle.verify`) plus `iss` / `aud` / `nonce` / `exp` — mirroring
   `validateSignedIdToken` exactly.
7. Prove the backend resource server accepts the access token: an authenticated, owner-scoped
   `GET /api/me/data-rights/receipts` with `Authorization: Bearer …`.

## Observed outcome (real, redacted)

```
[1] bootstrap enabled=true flow=authorization_code pkce=S256 issuer=http://localhost:8081/realms/inner-cosmos
    clientId=inner-cosmos-mobile-local redirectUri=innercosmos://oauth/callback
[2] discovery matchesBootstrap=true (issuer+endpoints agree with the signed bootstrap)
[3] verifier=fycJTMYz5Z…(len=86) challenge=PRCT2Nk-pa…(len=43)
[4] authorize -> login form -> callback innercosmos://oauth/callback state=MATCH code=cb5784c0-c…(len=98)
[5] POST token exchange -> HTTP 200 token_type=Bearer expires_in=300 (refresh_token issued)
[6] ID token signature=VALID(RS256) iss=true aud=true nonce=true exp=true alg=RS256 kid=zcTehjtXxz…
[7] GET /api/me/data-rights/receipts (Bearer) -> HTTP 200 success=true (auto-provisioned owner, 0 receipts)
[OK] PKCE round-trip complete on the integrated HEAD.
```

Full machine-readable result: `logs/pkce-result.json`. Tokens are redacted to `prefix…(len=N)`
everywhere they appear (stdout, the result JSON, and `logs/access-token.redacted.txt`); the full
token was used in-process only and never written to disk.

## Conclusion

The integrated HEAD's native OIDC/PKCE contract is **intact**. The end-to-end path that
`mobile-auth.ts` relies on — bootstrap → authorize → PKCE token exchange → RS256 ID-token
verification → Bearer-authenticated, owner-scoped API session — passes against a real, locally
hosted Keycloak IdP, against backend code built from the current HEAD (which includes the voice,
demo, capsule-voice and slow-letter merges). No production source change was needed; the diff is
scripts + this evidence only.

Step 7 is also the focused production-decoder proof: the resource server used the exact
`OidcSecurityConfiguration.oidcJwtDecoder` bean (issuer + audience + signature-via-JWKS) and
accepted the Keycloak-minted token, so the contract `OidcLiveDecoderTest` expresses is shown
green against a real token here without re-running that opt-in JUnit test (which is hardcoded to
the Android-emulator `10.0.2.2` issuer and so cannot run against a host-reachable IdP without a
test edit).

## Reproduce

```bash
# 1. Keycloak (host-reachable; realm/client/PKCE policy imported from the repo)
bash evidence/innovation/INNO-MOBILE-009/scripts/start-keycloak.sh

# 2. Backend (separate shell) — dev profile + OIDC pointed at the local Keycloak
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.10"
export OIDC_ENABLED=true \
       OIDC_ISSUER_URI=http://localhost:8081/realms/inner-cosmos \
       OIDC_JWK_SET_URI=http://localhost:8081/realms/inner-cosmos/protocol/openid-connect/certs \
       OIDC_AUDIENCE=inner-cosmos-api \
       OIDC_AUTHORIZATION_URI=http://localhost:8081/realms/inner-cosmos/protocol/openid-connect/auth \
       OIDC_TOKEN_URI=http://localhost:8081/realms/inner-cosmos/protocol/openid-connect/token \
       OIDC_MOBILE_CLIENT_ID=inner-cosmos-mobile-local \
       OIDC_MOBILE_REDIRECT_URI=innercosmos://oauth/callback \
       CORS_ALLOWED_ORIGINS=http://localhost,https://localhost
./mvnw spring-boot:run

# 3. Drive the round-trip
node evidence/innovation/INNO-MOBILE-009/scripts/pkce-roundtrip.mjs

# 4. Stop Keycloak when done
bash evidence/innovation/INNO-MOBILE-009/scripts/stop-keycloak.sh
```

## Keycloak setup (reproducible)

| Field | Value |
|---|---|
| Image | `quay.io/keycloak/keycloak:26.7.0` |
| Realm | `inner-cosmos` (imported from `deploy/compose/keycloak/inner-cosmos-realm.json`) |
| Client | `inner-cosmos-mobile-local` (public, `standardFlow`, PKCE `S256` required, no secret) |
| Redirect URI | `innercosmos://oauth/callback` (the app-owned deep link) |
| Web origins | `http://localhost`, `https://localhost`, `tauri://localhost`, `http://tauri.localhost` |
| Audience mapper | `inner-cosmos-api` injected into the access token (`oidc-audience-mapper`) |
| Hostname | `http://localhost:8081` (host-reachable; differs from `10.0.2.2` only so the host can drive it) |
| Admin | `local-admin` / `test-only-mobile-local-admin` (dev bootstrap; loopback only) |
| Test user | `mobile-demo` / see realm JSON (throwaway dev cred) |

## Boundaries / what was deferred (honest)

- **Desktop/Tauri vs Android deep-link**: the PKCE round-trip here is driven from the host via a
  script that performs the *identical* `code_verifier`/`code_challenge`/token-exchange/ID-token
  verification that the Tauri shell and the Android app perform — the PKCE contract under test is
  the same code path. The Tauri/Android *shell* glue (system-browser open + deep-link hand-off)
  was not driven in a real Tauri window / on an Android emulator in this session; that is the same
  device/UX boundary `INNO-MOBILE-004` left open and is not a change to the PKCE contract itself.
- A **production** IdP tenant, real device signing, APNs/FCM production credentials, and
  App Links / Universal Links publication remain human/external gates (unchanged from
  `INNO-MOBILE-004`).
