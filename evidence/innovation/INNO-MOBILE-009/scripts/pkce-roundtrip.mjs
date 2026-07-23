#!/usr/bin/env node
/**
 * INNO-MOBILE-009 — PKCE round-trip against a locally-hosted Keycloak IdP.
 *
 * This harness drives the EXACT same Authorization Code + PKCE (S256) contract that
 * `web/src/mobile-auth.ts` drives on a real device/Tauri shell, but from the host so
 * the integrated HEAD can be re-proven without an Android emulator:
 *
 *   1. Bootstrap the native OIDC contract from the backend (`/api/public/auth/mobile-oidc`)
 *      and validate it requires authorization_code + PKCE S256 with an app-owned callback,
 *      exactly as the installed client does.
 *   2. OIDC discovery must agree with the signed server bootstrap (issuer + endpoints).
 *   3. Generate a local code_verifier + S256 code_challenge, state, nonce.
 *   4. Drive the real Keycloak authorize endpoint, authenticate the demo user through the
 *      real login form (cookie jar), and capture the `innercosmos://oauth/callback` redirect
 *      carrying the authorization `code` (intercepted at the redirect, never followed).
 *   5. Exchange code + code_verifier at the token endpoint — the PKCE check happens here.
 *   6. Verify the minted ID token: RS256 signature via the discovered JWKS + iss/aud/nonce/exp.
 *   7. Prove the backend resource server accepts the access token: an authenticated,
 *      owner-scoped GET `/api/me/data-rights/receipts` with `Authorization: Bearer`.
 *
 * Output: a redacted step log on stdout + `pkce-roundtrip.log` and `pkce-result.json`
 * in the logs dir. Tokens are NEVER printed in full (redacted to <prefix>…<len>).
 *
 * Env: API_BASE (default http://localhost:8080), OIDC_DEMO_USER, OIDC_DEMO_PASS,
 *      LOG_DIR (default ./logs next to this script).
 */
import { execFileSync } from "node:child_process";
import { webcrypto } from "node:crypto";
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const LOG_DIR = process.env.LOG_DIR || join(__dirname, "..", "logs");
const API_BASE = (process.env.API_BASE || "http://localhost:8080").replace(/\/$/, "");
const DEMO_USER = process.env.OIDC_DEMO_USER || "mobile-demo";
const DEMO_PASS = process.env.OIDC_DEMO_PASS || "demo123";
const UA = "inner-cosmos-pkce-harness/1.0";

const subtle = webcrypto.subtle;
const encode = new TextEncoder();
const logLines = [];
const log = (line) => { logLines.push(line); console.log(line); };
const redact = (token) => {
  if (typeof token !== "string" || !token) return "<none>";
  const prefix = token.slice(0, 10);
  return `${prefix}…(len=${token.length})`;
};
const b64urlFromBytes = (bytes) => Buffer.from(bytes).toString("base64url");
const b64urlToBuf = (value) => {
  const encoded = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = Buffer.from(encoded, "base64").toString("latin1");
  return Buffer.from(binary, "latin1");
};

function curl(args, { encoding = "latin1" } = {}) {
  return execFileSync("curl", ["-sS", ...args], { encoding, stdio: ["ignore", "pipe", "pipe"] });
}

/** Follow the Location chain from a POST/GET response, stopping at the custom-scheme callback. */
function followUntilCallback(location, jar) {
  const chain = [];
  let next = location;
  for (let hop = 0; hop < 10 && next && !next.startsWith("innercosmos:"); hop++) {
    chain.push(next);
    const resp = curl(["-i", "--cookie-jar", jar, "--cookie", jar, "-A", UA, "-X", "GET", next]);
    next = parseLocation(resp);
  }
  return { finalLocation: next, chain };
}

function parseLocation(resp) {
  const m = resp.match(/^location:\s*(\S[^\r\n]*)/im);
  if (!m) return null;
  return m[1].trim();
}

async function main() {
  mkdirSync(LOG_DIR, { recursive: true });
  const result = { startedAt: new Date().toISOString(), steps: {}, ok: false };
  const jar = join(LOG_DIR, "keycloak-cookies.jar");

  // ---- Step 1: backend bootstrap contract -------------------------------------
  const bootstrapResp = await fetch(`${API_BASE}/api/public/auth/mobile-oidc`, { credentials: "omit" });
  const bootstrapEnvelope = await bootstrapResp.json();
  const bootstrap = bootstrapEnvelope?.data ?? {};
  log(`[1] GET /api/public/auth/mobile-oidc -> HTTP ${bootstrapResp.status}, enabled=${bootstrap.enabled}`);
  if (!bootstrap.enabled) throw new Error("Backend reports mobile OIDC is disabled");
  if (bootstrap.flow !== "authorization_code" || !bootstrap.pkceRequired || bootstrap.codeChallengeMethod !== "S256") {
    throw new Error(`Bootstrap contract changed: ${JSON.stringify({ flow: bootstrap.flow, pkce: bootstrap.pkceRequired, m: bootstrap.codeChallengeMethod })}`);
  }
  log(`    issuer=${bootstrap.issuer}`);
  log(`    clientId=${bootstrap.clientId} redirectUri=${bootstrap.redirectUri} scopes=${(bootstrap.scopes || []).join(",")}`);
  result.steps.bootstrap = {
    enabled: bootstrap.enabled, flow: bootstrap.flow, pkceRequired: bootstrap.pkceRequired,
    codeChallengeMethod: bootstrap.codeChallengeMethod, issuer: bootstrap.issuer,
    clientId: bootstrap.clientId, redirectUri: bootstrap.redirectUri
  };

  // ---- Step 2: discovery must agree with the signed bootstrap ------------------
  const discoveryUrl = `${bootstrap.issuer.replace(/\/$/, "")}/.well-known/openid-configuration`;
  const discoveryResp = await fetch(discoveryUrl, { credentials: "omit" });
  const discovery = await discoveryResp.json();
  const discoveryMatches =
    discovery.issuer === bootstrap.issuer &&
    discovery.authorization_endpoint === bootstrap.authorizationEndpoint &&
    discovery.token_endpoint === bootstrap.tokenEndpoint;
  log(`[2] OIDC discovery @ ${discoveryUrl} -> HTTP ${discoveryResp.status}, matchesBootstrap=${discoveryMatches}`);
  if (!discoveryMatches) {
    throw new Error(`Discovery/bootstrap mismatch:\n  bootstrap=${JSON.stringify({iss:bootstrap.issuer,auth:bootstrap.authorizationEndpoint,tok:bootstrap.tokenEndpoint})}\n  discovery=${JSON.stringify({iss:discovery.issuer,auth:discovery.authorization_endpoint,tok:discovery.token_endpoint})}`);
  }
  const jwksUri = discovery.jwks_uri;
  const tokenEndpoint = bootstrap.tokenEndpoint;
  const authorizeEndpoint = bootstrap.authorizationEndpoint;
  result.steps.discovery = { issuer: discovery.issuer, jwks_uri: jwksUri, matches: discoveryMatches };

  // ---- Step 3: PKCE materials --------------------------------------------------
  const verifier = b64urlFromBytes(webcrypto.getRandomValues(new Uint8Array(64)));
  const state = b64urlFromBytes(webcrypto.getRandomValues(new Uint8Array(16)));
  const nonce = b64urlFromBytes(webcrypto.getRandomValues(new Uint8Array(16)));
  const challenge = b64urlFromBytes(new Uint8Array(await subtle.digest("SHA-256", encode.encode(verifier))));
  log(`[3] PKCE: verifier=${redact(verifier)} challenge=${redact(challenge)} (S256)`);
  result.steps.pkce = { verifierRedacted: redact(verifier), challengeRedacted: redact(challenge), method: "S256" };

  // ---- Step 4: authorize -> login form -> capture callback redirect ------------
  const authUrl = new URL(authorizeEndpoint);
  authUrl.searchParams.set("response_type", "code");
  authUrl.searchParams.set("client_id", bootstrap.clientId);
  authUrl.searchParams.set("redirect_uri", bootstrap.redirectUri);
  authUrl.searchParams.set("scope", (bootstrap.scopes || []).join(" "));
  authUrl.searchParams.set("state", state);
  authUrl.searchParams.set("nonce", nonce);
  authUrl.searchParams.set("code_challenge", challenge);
  authUrl.searchParams.set("code_challenge_method", "S256");

  const loginHtml = curl(["-L", "--cookie-jar", jar, "--cookie", jar, "-A", UA, authUrl.href], { encoding: "utf8" });
  const formActionMatch = loginHtml.match(/<form[^>]*\bid="kc-form-login"[^>]*\baction="([^"]+)"/i)
    || loginHtml.match(/<form[^>]*\baction="([^"]+(?:authenticate)[^"]*)"/i);
  if (!formActionMatch) throw new Error("Could not locate the Keycloak login form action in the rendered page");
  const formAction = formActionMatch[1].replaceAll("&amp;", "&");
  log(`[4] authorize -> login form rendered; action=${redact(formAction)}`);

  const postBody = `username=${encodeURIComponent(DEMO_USER)}&password=${encodeURIComponent(DEMO_PASS)}&credentialId=`;
  const postResp = curl(["-i", "--cookie-jar", jar, "--cookie", jar, "-A", UA,
    "-X", "POST", formAction, "--data", postBody]);
  let location = parseLocation(postResp);
  if (location && !location.startsWith("innercosmos:")) {
    const followed = followUntilCallback(location, jar);
    log(`    followed ${followed.chain.length} internal redirect(s) before the custom-scheme callback`);
    location = followed.finalLocation;
  }
  if (!location || !location.startsWith("innercosmos:")) {
    throw new Error(`Did not reach the innercosmos:// callback; last location=${location}`);
  }
  const callback = new URL(location);
  const returnedState = callback.searchParams.get("state");
  const code = callback.searchParams.get("code");
  const callbackError = callback.searchParams.get("error");
  log(`    callback=${callback.origin}${callback.pathname} state=${returnedState === state ? "MATCH" : "MISMATCH"} code=${redact(code)} error=${callbackError || "<none>"}`);
  if (returnedState !== state) throw new Error("OIDC state mismatch (possible CSRF/replay)");
  if (callbackError || !code) throw new Error(`OIDC authorization not completed: error=${callbackError}`);
  result.steps.authorize = { callbackScheme: callback.protocol, stateMatched: returnedState === state, codeRedacted: redact(code) };

  // ---- Step 5: token exchange (PKCE verified server-side here) -----------------
  const exchangeForm = new URLSearchParams({
    grant_type: "authorization_code",
    code,
    redirect_uri: bootstrap.redirectUri,
    client_id: bootstrap.clientId,
    code_verifier: verifier
  });
  const tokenResp = await fetch(tokenEndpoint, {
    method: "POST", credentials: "omit",
    headers: { "content-type": "application/x-www-form-urlencoded", accept: "application/json" },
    body: exchangeForm
  });
  const tokenBody = await tokenResp.json();
  const tokenOk = tokenResp.status === 200 && typeof tokenBody.access_token === "string"
    && String(tokenBody.token_type).toLowerCase() === "bearer";
  log(`[5] POST token exchange -> HTTP ${tokenResp.status}, token_type=${tokenBody.token_type}, expires_in=${tokenBody.expires_in}, ok=${tokenOk}`);
  if (!tokenOk) throw new Error(`Token exchange failed: ${JSON.stringify({ status: tokenResp.status, body: tokenBody })}`);
  result.steps.tokenExchange = {
    status: tokenResp.status, tokenType: tokenBody.token_type, expiresIn: tokenBody.expires_in,
    hasIdToken: typeof tokenBody.id_token === "string", hasRefreshToken: typeof tokenBody.refresh_token === "string"
  };

  // ---- Step 6: verify the ID token (RS256 via JWKS + iss/aud/nonce/exp) -------
  const idToken = tokenBody.id_token;
  const [headB64, payloadB64, sigB64] = idToken.split(".");
  const header = JSON.parse(Buffer.from(headB64, "base64url").toString("utf8"));
  const claims = JSON.parse(Buffer.from(payloadB64, "base64url").toString("utf8"));
  const jwks = await (await fetch(jwksUri, { credentials: "omit" })).json();
  const jwk = (jwks.keys || []).find(k => k.kid === header.kid && k.kty === "RSA" && (!k.use || k.use === "sig"));
  if (!jwk) throw new Error(`No JWKS signing key matched kid=${header.kid}`);
  const cryptoKey = await subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const signatureValid = await subtle.verify("RSASSA-PKCS1-v1_5", cryptoKey, b64urlToBuf(sigB64), encode.encode(`${headB64}.${payloadB64}`));
  const aud = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
  const claimsOk = {
    iss: claims.iss === bootstrap.issuer,
    aud: aud.includes(bootstrap.clientId),
    exp: typeof claims.exp === "number" && claims.exp * 1000 > Date.now(),
    nonce: claims.nonce === nonce,
    alg: header.alg === "RS256"
  };
  log(`[6] ID token verify: signature=${signatureValid ? "VALID(RS256)" : "INVALID"}, kid=${header.kid}, iss=${claims.iss === bootstrap.issuer}, aud=${claimsOk.aud}, nonce=${claimsOk.nonce}, exp=${claimsOk.exp}, alg=${header.alg}`);
  const idOk = signatureValid && Object.values(claimsOk).every(Boolean);
  if (!idOk) throw new Error(`ID token validation failed: signature=${signatureValid}, ${JSON.stringify({ ...claimsOk, iss_claim: claims.iss, expected_iss: bootstrap.issuer })}`);
  result.steps.idToken = {
    alg: header.alg, kid: header.kid, signatureValid,
    iss: claims.iss === bootstrap.issuer, audience: aud,
    nonceMatched: claimsOk.nonce, expiryOk: claimsOk.exp, subject: claims.sub
  };

  // ---- Step 7: backend resource server accepts the Bearer token ----------------
  const meResp = await fetch(`${API_BASE}/api/me/data-rights/receipts`, {
    credentials: "omit",
    headers: { Authorization: `Bearer ${tokenBody.access_token}`, accept: "application/json" }
  });
  const meBody = await meResp.json().catch(() => ({}));
  const authed = meResp.status === 200 && meBody?.success === true;
  log(`[7] GET /api/me/data-rights/receipts (Bearer) -> HTTP ${meResp.status}, success=${meBody?.success}, items=${Array.isArray(meBody?.data) ? meBody.data.length : "n/a"}, authed=${authed}`);
  if (!authed) throw new Error(`Resource server rejected the Bearer token: HTTP ${meResp.status} ${JSON.stringify(meBody)}`);
  result.steps.resourceServer = { status: meResp.status, success: meBody?.success === true, items: Array.isArray(meBody?.data) ? meBody.data.length : null };

  result.ok = true;
  result.accessTokenPrefix = redact(tokenBody.access_token);
  result.idTokenPrefix = redact(idToken);
  log(`[OK] PKCE round-trip complete on the integrated HEAD: bootstrap -> authorize -> PKCE token exchange -> RS256 ID token -> authenticated session.`);
  writeFileSync(join(LOG_DIR, "pkce-result.json"), JSON.stringify(result, null, 2));
  writeFileSync(join(LOG_DIR, "pkce-roundtrip.log"), logLines.join("\n") + "\n");
  // Redacted token-set for the optional JUnit live-decoder proof (value redacted, never full).
  writeFileSync(join(LOG_DIR, "access-token.redacted.txt"),
    `prefix=${tokenBody.access_token.slice(0, 8)} length=${tokenBody.access_token.length} note=redacted; full token was used in-process only\n`);
}

main().catch(error => {
  const message = error && error.message ? error.message : String(error);
  log(`[FAIL] ${message}`);
  writeFileSync(join(LOG_DIR, "pkce-roundtrip.log"), logLines.join("\n") + "\n");
  writeFileSync(join(LOG_DIR, "pkce-result.json"), JSON.stringify({ startedAt: new Date().toISOString(), ok: false, error: message }, null, 2));
  process.exit(1);
});
