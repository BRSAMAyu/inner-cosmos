import { App, type URLOpenListenerEvent } from "@capacitor/app";
import { Browser } from "@capacitor/browser";
import { Capacitor } from "@capacitor/core";
import { KeychainAccess, SecureStorage } from "@aparajita/capacitor-secure-storage";
import { apiUrl, desktopLocalBuild, mobileLocalBuild } from "./api";
import { desktopRuntime, isTauriRuntime } from "./desktop-runtime";

type OidcBootstrap = {
  enabled: boolean;
  flow: "authorization_code";
  pkceRequired: true;
  codeChallengeMethod: "S256";
  issuer: string;
  authorizationEndpoint: string;
  tokenEndpoint: string;
  clientId: string;
  redirectUri: string;
  scopes: string[];
};

type OidcDiscovery = {
  issuer: string;
  authorization_endpoint: string;
  token_endpoint: string;
  revocation_endpoint: string;
  jwks_uri: string;
};

type AuthTransaction = { state: string; nonce: string; verifier: string; bootstrap: OidcBootstrap; revocationEndpoint: string; jwksUri: string };
type TokenBundle = {
  accessToken: string;
  refreshToken: string | null;
  idToken: string;
  expiresAt: number;
  issuer: string;
  clientId: string;
  tokenEndpoint: string;
  revocationEndpoint: string;
  jwksUri: string;
};

const TOKEN_KEY = "oidc-token-bundle";
const TRANSACTION_KEY = "oidc-auth-transaction";
const CALLBACK_SCHEME = "innercosmos:";
const CALLBACK_HOST = "oauth";
const CALLBACK_PATH = "/callback";
const NATIVE_AUTH_STEP_TIMEOUT_MS = 15_000;

function installedAuthRuntime(): boolean { return Capacitor.isNativePlatform() || isTauriRuntime(); }

async function configureSecureStorage(): Promise<void> {
  if (Capacitor.isNativePlatform()) {
    await SecureStorage.setKeyPrefix("inner-cosmos_");
    await SecureStorage.setSynchronize(false);
    await SecureStorage.setDefaultKeychainAccess(KeychainAccess.whenUnlockedThisDeviceOnly);
  } else if (isTauriRuntime()) await desktopRuntime.initialize();
}

async function secureGet(key: string): Promise<string | null> {
  const value = Capacitor.isNativePlatform() ? await SecureStorage.get(key).catch(() => null)
    : isTauriRuntime() ? await desktopRuntime.get(key).catch(() => null) : null;
  return typeof value === "string" ? value : null;
}

async function secureSet(key: string, value: string): Promise<void> {
  if (Capacitor.isNativePlatform()) await SecureStorage.set(key, value);
  else if (isTauriRuntime()) await desktopRuntime.set(key, value);
}

async function secureRemove(key: string): Promise<void> {
  if (Capacitor.isNativePlatform()) await SecureStorage.remove(key).catch(() => undefined);
  else if (isTauriRuntime()) await desktopRuntime.remove(key).catch(() => undefined);
}

async function withTimeout<T>(operation: Promise<T>, message: string): Promise<T> {
  let timeout: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      operation,
      new Promise<T>((_, reject) => {
        timeout = setTimeout(() => reject(new Error(message)), NATIVE_AUTH_STEP_TIMEOUT_MS);
      })
    ]);
  } finally {
    if (timeout) clearTimeout(timeout);
  }
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach(value => { binary += String.fromCharCode(value); });
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function randomValue(bytes = 32): string {
  const value = new Uint8Array(bytes);
  crypto.getRandomValues(value);
  return base64Url(value);
}

export async function createPkceChallenge(verifier: string): Promise<string> {
  return base64Url(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier))));
}

function jsonFromJwt(token: string): Record<string, unknown> {
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("OIDC ID token is malformed");
  const encoded = parts[1].replace(/-/g, "+").replace(/_/g, "/");
  const json = atob(encoded.padEnd(Math.ceil(encoded.length / 4) * 4, "="));
  return JSON.parse(new TextDecoder().decode(Uint8Array.from(json, char => char.charCodeAt(0)))) as Record<string, unknown>;
}

function bytesFromBase64Url(value: string): Uint8Array {
  const encoded = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(encoded.padEnd(Math.ceil(encoded.length / 4) * 4, "="));
  return Uint8Array.from(binary, char => char.charCodeAt(0));
}

function asArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
}

export function validateIdTokenClaims(token: string, issuer: string, clientId: string, nonce?: string): void {
  const claims = jsonFromJwt(token);
  const audiences = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
  if (claims.iss !== issuer || !audiences.includes(clientId)) throw new Error("OIDC ID token issuer or audience mismatch");
  if (typeof claims.exp !== "number" || claims.exp * 1000 <= Date.now()) throw new Error("OIDC ID token is expired");
  if (nonce !== undefined && claims.nonce !== nonce) throw new Error("OIDC nonce mismatch");
}

export async function validateSignedIdToken(token: string, issuer: string, clientId: string, jwksUri: string, nonce?: string): Promise<void> {
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("OIDC ID token is malformed");
  const header = JSON.parse(new TextDecoder().decode(bytesFromBase64Url(parts[0]))) as { alg?: string; kid?: string };
  if (header.alg !== "RS256" || !header.kid) throw new Error("OIDC ID token must use a keyed RS256 signature");
  const response = await fetch(jwksUri, { credentials: "omit" });
  if (!response.ok) throw new Error("OIDC signing keys are unavailable");
  const jwks = await response.json() as { keys?: Array<JsonWebKey & { kid?: string; alg?: string; use?: string }> };
  const jwk = jwks.keys?.find(key => key.kid === header.kid && key.kty === "RSA"
    && (!key.alg || key.alg === "RS256") && (!key.use || key.use === "sig"));
  if (!jwk) throw new Error("OIDC signing key does not match the ID token");
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, asArrayBuffer(bytesFromBase64Url(parts[2])),
    new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
  if (!valid) throw new Error("OIDC ID token signature is invalid");
  validateIdTokenClaims(token, issuer, clientId, nonce);
}

export function isOwnedOidcCallback(raw: string, expectedRedirect: string): boolean {
  try {
    const actual = new URL(raw);
    const expected = new URL(expectedRedirect);
    const ownedCustom = expected.protocol === CALLBACK_SCHEME && expected.hostname === CALLBACK_HOST && expected.pathname === CALLBACK_PATH;
    const ownedHttps = expected.protocol === "https:" && expected.hostname === "app.innercosmos.sg" && expected.pathname === "/oauth/callback";
    return (ownedCustom || ownedHttps) && !actual.username && !actual.password && !actual.hash
      && actual.origin === expected.origin && actual.port === expected.port
      && actual.protocol === expected.protocol && actual.hostname === expected.hostname && actual.pathname === expected.pathname;
  } catch { return false; }
}

function secureEndpoint(raw: string, label: string): string {
  const url = new URL(raw);
  const configuredLocalOidcOrigin = import.meta.env.VITE_LOCAL_OIDC_ORIGIN ?? "";
  const exactLocal = mobileLocalBuild && Boolean(configuredLocalOidcOrigin) && url.origin === configuredLocalOidcOrigin;
  const exactDesktopLocal = desktopLocalBuild && Boolean(configuredLocalOidcOrigin) && url.origin === configuredLocalOidcOrigin;
  if ((!exactLocal && !exactDesktopLocal && url.protocol !== "https:") || url.username || url.password || url.hash) {
    throw new Error(`${label} must be a trusted HTTPS URL`);
  }
  return url.href;
}

async function loadBootstrap(): Promise<{ bootstrap: OidcBootstrap; revocationEndpoint: string; jwksUri: string }> {
  const response = await fetch(apiUrl("/api/public/auth/mobile-oidc"), { credentials: "omit" });
  const envelope = await response.json() as { success: boolean; data: OidcBootstrap; message?: string };
  if (!response.ok || !envelope.success || !envelope.data.enabled) throw new Error(envelope.message ?? "Mobile OIDC is not enabled");
  const bootstrap = envelope.data;
  if (bootstrap.flow !== "authorization_code" || !bootstrap.pkceRequired || bootstrap.codeChallengeMethod !== "S256") {
    throw new Error("Server does not require Authorization Code with PKCE S256");
  }
  secureEndpoint(bootstrap.issuer, "OIDC issuer");
  const authorization = secureEndpoint(bootstrap.authorizationEndpoint, "OIDC authorization endpoint");
  const token = secureEndpoint(bootstrap.tokenEndpoint, "OIDC token endpoint");
  if (!isOwnedOidcCallback(bootstrap.redirectUri, bootstrap.redirectUri)) throw new Error("OIDC redirect URI is not owned by this app");
  const discoveryUrl = `${bootstrap.issuer.replace(/\/$/, "")}/.well-known/openid-configuration`;
  const discoveryResponse = await fetch(discoveryUrl, { credentials: "omit" });
  if (!discoveryResponse.ok) throw new Error("OIDC discovery failed");
  const discovery = await discoveryResponse.json() as OidcDiscovery;
  if (discovery.issuer !== bootstrap.issuer
    || secureEndpoint(discovery.authorization_endpoint, "Discovered authorization endpoint") !== authorization
    || secureEndpoint(discovery.token_endpoint, "Discovered token endpoint") !== token) {
    throw new Error("OIDC discovery does not match the signed server bootstrap");
  }
  return {
    bootstrap,
    revocationEndpoint: secureEndpoint(discovery.revocation_endpoint, "OIDC revocation endpoint"),
    jwksUri: secureEndpoint(discovery.jwks_uri, "OIDC JWK endpoint")
  };
}

async function exchange(form: URLSearchParams, endpoint: string): Promise<Record<string, unknown>> {
  const response = await fetch(endpoint, {
    method: "POST", credentials: "omit",
    headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" }, body: form
  });
  const body = await response.json() as Record<string, unknown>;
  if (!response.ok || typeof body.access_token !== "string" || body.token_type?.toString().toLowerCase() !== "bearer") {
    throw new Error("OIDC token exchange failed");
  }
  return body;
}

async function saveBundle(body: Record<string, unknown>, transaction: AuthTransaction, priorRefresh?: string | null): Promise<TokenBundle> {
  if (typeof body.id_token !== "string" && !priorRefresh) throw new Error("Initial OIDC response must include an ID token");
  const idToken = typeof body.id_token === "string" ? body.id_token : "";
  if (idToken) await validateSignedIdToken(idToken, transaction.bootstrap.issuer, transaction.bootstrap.clientId,
    transaction.jwksUri, priorRefresh ? undefined : transaction.nonce);
  const expiresIn = typeof body.expires_in === "number" ? Math.max(30, body.expires_in) : 300;
  const bundle: TokenBundle = {
    accessToken: String(body.access_token),
    refreshToken: typeof body.refresh_token === "string" ? body.refresh_token : priorRefresh ?? null,
    idToken,
    expiresAt: Date.now() + expiresIn * 1000,
    issuer: transaction.bootstrap.issuer,
    clientId: transaction.bootstrap.clientId,
    tokenEndpoint: transaction.bootstrap.tokenEndpoint,
    revocationEndpoint: transaction.revocationEndpoint
    ,jwksUri: transaction.jwksUri
  };
  await secureSet(TOKEN_KEY, JSON.stringify(bundle));
  return bundle;
}

export class MobileOidcClient {
  private bundle: TokenBundle | null = null;
  private listenerCleanup: (() => void | Promise<void>) | null = null;
  private authenticated: (() => void | Promise<void>) | null = null;
  private authError: ((error: Error) => void) | null = null;
  private refreshInFlight: Promise<string | null> | null = null;

  async initialize(onAuthenticated: () => void | Promise<void>, onError?: (error: Error) => void): Promise<() => Promise<void>> {
    if (!installedAuthRuntime()) return async () => undefined;
    this.authenticated = onAuthenticated;
    this.authError = onError ?? null;
    await configureSecureStorage();
    const stored = await secureGet(TOKEN_KEY);
    if (typeof stored === "string") {
      try {
        const candidate = JSON.parse(stored) as TokenBundle;
        secureEndpoint(candidate.issuer, "Stored OIDC issuer");
        secureEndpoint(candidate.tokenEndpoint, "Stored OIDC token endpoint");
        secureEndpoint(candidate.revocationEndpoint, "Stored OIDC revocation endpoint");
        secureEndpoint(candidate.jwksUri, "Stored OIDC JWK endpoint");
        if (!candidate.clientId || !candidate.accessToken || !Number.isFinite(candidate.expiresAt)) throw new Error("Stored OIDC token is incomplete");
        this.bundle = candidate;
      } catch { await secureRemove(TOKEN_KEY); }
    }
    const callback = (url: string) => void this.handleCallback({ url })
      .catch(error => this.authError?.(error instanceof Error ? error : new Error("OIDC callback failed")));
    if (Capacitor.isNativePlatform()) {
      const listener = await App.addListener("appUrlOpen", event => callback(event.url));
      this.listenerCleanup = () => listener.remove();
      const launch = await App.getLaunchUrl();
      if (launch?.url) await this.handleCallback({ url: launch.url });
    } else {
      this.listenerCleanup = await desktopRuntime.listenDeepLinks(callback);
    }
    return async () => { await this.listenerCleanup?.(); this.listenerCleanup = null; this.authenticated = null; this.authError = null; };
  }

  async beginLogin(): Promise<void> {
    if (!installedAuthRuntime()) throw new Error("OIDC is available only in the installed app");
    const { bootstrap, revocationEndpoint, jwksUri } = await withTimeout(
      loadBootstrap(), "OIDC bootstrap timed out while reaching the local stack");
    const verifier = randomValue(64);
    const transaction: AuthTransaction = { state: randomValue(), nonce: randomValue(), verifier, bootstrap, revocationEndpoint, jwksUri };
    await withTimeout(secureSet(TRANSACTION_KEY, JSON.stringify(transaction)),
      "Secure storage did not accept the OIDC transaction");
    const authorization = new URL(bootstrap.authorizationEndpoint);
    authorization.searchParams.set("response_type", "code");
    authorization.searchParams.set("client_id", bootstrap.clientId);
    authorization.searchParams.set("redirect_uri", bootstrap.redirectUri);
    authorization.searchParams.set("scope", bootstrap.scopes.join(" "));
    authorization.searchParams.set("state", transaction.state);
    authorization.searchParams.set("nonce", transaction.nonce);
    authorization.searchParams.set("code_challenge", await createPkceChallenge(verifier));
    authorization.searchParams.set("code_challenge_method", "S256");
    await withTimeout(Capacitor.isNativePlatform()
      ? Browser.open({ url: authorization.href, presentationStyle: "popover" })
      : desktopRuntime.openSystemBrowser(authorization.href),
      "The system browser did not open for OIDC sign-in");
  }

  async accessToken(): Promise<string | null> {
    if (!installedAuthRuntime()) return null;
    if (!this.bundle) return null;
    if (this.bundle.expiresAt > Date.now() + 30_000) return this.bundle.accessToken;
    if (!this.bundle.refreshToken) { await this.clear(); return null; }
    this.refreshInFlight ??= this.refresh().finally(() => { this.refreshInFlight = null; });
    return this.refreshInFlight;
  }

  async expireAccessToken(): Promise<void> {
    if (!this.bundle) return;
    this.bundle.expiresAt = 0;
    await secureSet(TOKEN_KEY, JSON.stringify(this.bundle));
  }

  async logout(): Promise<void> {
    const bundle = this.bundle;
    let revoked = true;
    if (bundle) {
      for (const token of [bundle.refreshToken, bundle.accessToken].filter(Boolean) as string[]) {
        const response = await fetch(bundle.revocationEndpoint, {
          method: "POST", credentials: "omit", headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: new URLSearchParams({ token, client_id: bundle.clientId })
        }).catch(() => null);
        if (!response?.ok) revoked = false;
      }
    }
    await this.clear();
    if (!revoked) throw new Error("已清除本机凭据，但身份提供方未确认远程撤销");
  }

  private async handleCallback(event: Pick<URLOpenListenerEvent, "url">): Promise<void> {
    const stored = await secureGet(TRANSACTION_KEY);
    if (typeof stored !== "string") return;
    let transaction: AuthTransaction;
    try { transaction = JSON.parse(stored) as AuthTransaction; }
    catch { await secureRemove(TRANSACTION_KEY); return; }
    if (!isOwnedOidcCallback(event.url, transaction.bootstrap.redirectUri)) return;
    if (Capacitor.isNativePlatform()) await Browser.close().catch(() => undefined);
    const callback = new URL(event.url);
    await secureRemove(TRANSACTION_KEY);
    if (callback.searchParams.get("state") !== transaction.state) throw new Error("OIDC state mismatch");
    const code = callback.searchParams.get("code");
    if (!code || callback.searchParams.has("error")) throw new Error("OIDC authorization was not completed");
    const body = await exchange(new URLSearchParams({
      grant_type: "authorization_code", code, redirect_uri: transaction.bootstrap.redirectUri,
      client_id: transaction.bootstrap.clientId, code_verifier: transaction.verifier
    }), transaction.bootstrap.tokenEndpoint);
    this.bundle = await saveBundle(body, transaction);
    await this.authenticated?.();
  }

  private async refresh(): Promise<string | null> {
    const prior = this.bundle;
    if (!prior?.refreshToken) return null;
    try {
      const body = await exchange(new URLSearchParams({
        grant_type: "refresh_token", refresh_token: prior.refreshToken, client_id: prior.clientId
      }), prior.tokenEndpoint);
      const transaction: AuthTransaction = {
        state: "", nonce: "", verifier: "", revocationEndpoint: prior.revocationEndpoint, jwksUri: prior.jwksUri,
        bootstrap: { enabled: true, flow: "authorization_code", pkceRequired: true, codeChallengeMethod: "S256",
          issuer: prior.issuer, authorizationEndpoint: "", tokenEndpoint: prior.tokenEndpoint,
          clientId: prior.clientId, redirectUri: "", scopes: [] }
      };
      this.bundle = await saveBundle(body, transaction, prior.refreshToken);
      return this.bundle.accessToken;
    } catch {
      await this.clear();
      return null;
    }
  }

  private async clear(): Promise<void> {
    this.bundle = null;
    await Promise.all([secureRemove(TOKEN_KEY), secureRemove(TRANSACTION_KEY)]);
  }
}

export const mobileOidc = new MobileOidcClient();
