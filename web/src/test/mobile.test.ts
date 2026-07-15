import { afterEach, describe, expect, it, vi } from "vitest";
import { apiUrl, subscribeProactive } from "../api";
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

  it("accepts only typed proactive events and closes the live channel", () => {
    const listeners = new Map<string, EventListener>();
    const close = vi.fn();
    class FakeEventSource {
      onopen: (() => void) | null = null;
      onerror: (() => void) | null = null;
      constructor(public url: string, public init: EventSourceInit) {}
      addEventListener(name: string, listener: EventListener) { listeners.set(name, listener); }
      close = close;
    }
    vi.stubGlobal("EventSource", FakeEventSource);
    const received = vi.fn();
    const unsubscribe = subscribeProactive(received);
    listeners.get("proactive")?.({ data: JSON.stringify({ type: "wake_intent", content: "回来", ts: "2026-07-15T00:00:00Z" }) } as MessageEvent);
    listeners.get("proactive")?.({ data: "not-json" } as MessageEvent);
    expect(received).toHaveBeenCalledOnce();
    expect(received).toHaveBeenCalledWith({ type: "wake_intent", content: "回来", ts: "2026-07-15T00:00:00Z" });
    unsubscribe();
    expect(close).toHaveBeenCalledOnce();
  });
});

afterEach(() => vi.unstubAllGlobals());
