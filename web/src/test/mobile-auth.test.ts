import { afterEach, describe, expect, it, vi } from "vitest";
import { createPkceChallenge, isOwnedOidcCallback, validateIdTokenClaims, validateSignedIdToken } from "../mobile-auth";

function unsignedJwt(claims: Record<string, unknown>): string {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `${encode({ alg: "RS256", kid: "test" })}.${encode(claims)}.signature`;
}

describe("native OIDC security contract", () => {
  it("creates the RFC 7636 S256 challenge", async () => {
    await expect(createPkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
      .resolves.toBe("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
  });

  it("accepts only the app-owned exact callback", () => {
    expect(isOwnedOidcCallback("innercosmos://oauth/callback?code=abc&state=ok", "innercosmos://oauth/callback")).toBe(true);
    expect(isOwnedOidcCallback("innercosmos://aurora/callback?code=abc", "innercosmos://oauth/callback")).toBe(false);
    expect(isOwnedOidcCallback("https://evil.example/oauth/callback?code=abc", "https://app.innercosmos.sg/oauth/callback")).toBe(false);
  });

  it("fails closed on issuer, audience, expiry and nonce mismatches", () => {
    const valid = unsignedJwt({ iss: "https://identity.example", aud: "mobile", exp: Math.floor(Date.now() / 1000) + 60, nonce: "n" });
    expect(() => validateIdTokenClaims(valid, "https://identity.example", "mobile", "n")).not.toThrow();
    expect(() => validateIdTokenClaims(valid, "https://attacker.example", "mobile", "n")).toThrow();
    expect(() => validateIdTokenClaims(valid, "https://identity.example", "other", "n")).toThrow();
    expect(() => validateIdTokenClaims(valid, "https://identity.example", "mobile", "wrong")).toThrow();
    const expired = unsignedJwt({ iss: "https://identity.example", aud: "mobile", exp: 1, nonce: "n" });
    expect(() => validateIdTokenClaims(expired, "https://identity.example", "mobile", "n")).toThrow();
  });

  it("verifies the ID token signature against the discovered keyed JWK", async () => {
    const keyPair = await crypto.subtle.generateKey(
      { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
      true, ["sign", "verify"]
    );
    const jwk = await crypto.subtle.exportKey("jwk", keyPair.publicKey);
    Object.assign(jwk, { kid: "current", alg: "RS256", use: "sig" });
    const encodeBytes = (bytes: Uint8Array) => {
      let binary = ""; bytes.forEach(value => { binary += String.fromCharCode(value); });
      return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    };
    const encodeJson = (value: unknown) => encodeBytes(new TextEncoder().encode(JSON.stringify(value)));
    const signingInput = `${encodeJson({ alg: "RS256", kid: "current" })}.${encodeJson({
      iss: "https://identity.example", aud: "mobile", exp: Math.floor(Date.now() / 1000) + 60, nonce: "nonce"
    })}`;
    const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", keyPair.privateKey, new TextEncoder().encode(signingInput));
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ keys: [jwk] }), { status: 200 })));
    await expect(validateSignedIdToken(`${signingInput}.${encodeBytes(new Uint8Array(signature))}`,
      "https://identity.example", "mobile", "https://identity.example/jwks", "nonce")).resolves.toBeUndefined();
  });
});

afterEach(() => vi.unstubAllGlobals());
