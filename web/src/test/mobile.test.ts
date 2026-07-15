import { describe, expect, it } from "vitest";
import { apiUrl } from "../api";
import { parseWakeIntentDeepLink } from "../mobile";

describe("mobile deep-link boundary", () => {
  it("accepts only owned Aurora wake routes", () => {
    expect(parseWakeIntentDeepLink("innercosmos://aurora/wake/42")).toBe(42);
    expect(parseWakeIntentDeepLink("https://app.innercosmos.sg/app/aurora/index.html?wakeIntent=9")).toBe(9);
    expect(parseWakeIntentDeepLink("https://localhost/app/aurora?wakeIntent=7")).toBe(7);
  });

  it("rejects untrusted origins, routes and malformed identifiers", () => {
    expect(parseWakeIntentDeepLink("https://evil.example/app/aurora?wakeIntent=9")).toBeNull();
    expect(parseWakeIntentDeepLink("innercosmos://admin/wake/9")).toBeNull();
    expect(parseWakeIntentDeepLink("innercosmos://aurora/wake/-1")).toBeNull();
    expect(parseWakeIntentDeepLink("innercosmos://aurora/wake/1?next=https://evil.example")).toBe(1);
  });
});

describe("mobile API boundary", () => {
  it("keeps API requests scoped to the Inner Cosmos API namespace", () => {
    expect(apiUrl("/api/auth/csrf")).toBe("/api/auth/csrf");
    expect(() => apiUrl("https://evil.example/collect")).toThrow("Only Inner Cosmos API paths are allowed");
  });
});
