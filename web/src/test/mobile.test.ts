import { afterEach, describe, expect, it, vi } from "vitest";
import { apiUrl, subscribeProactive, validateApiBase } from "../api";
import {
  dueWakeIntentIds, parseWakeIntentDeepLink, wakeIntentFromLocalNotificationId,
  wakeIntentFromNotification, wakeNotificationId
} from "../mobile";

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

describe("notification payload boundary", () => {
  it("accepts only positive safe wake-intent identifiers", () => {
    expect(wakeIntentFromNotification({ wakeIntent: "42" })).toBe(42);
    expect(wakeIntentFromNotification('{"wakeIntent":42}')).toBe(42);
    expect(wakeIntentFromNotification({ wakeIntent: -1 })).toBeNull();
    expect(wakeIntentFromNotification({ wakeIntent: "not-an-id" })).toBeNull();
    expect(wakeIntentFromNotification(null)).toBeNull();
  });

  it("recovers only due durable WakeIntent schedules in delivery order", () => {
    expect(dueWakeIntentIds({ "9": 101, "4": 99, invalid: 1, "-2": 1 }, 100)).toEqual([4]);
    expect(dueWakeIntentIds({ "9": 101, "4": 99 }, 101)).toEqual([4, 9]);
  });

  it("maps owned WakeIntents to stable Android notification identifiers", () => {
    expect(wakeNotificationId(42)).toBe(1_000_042);
    expect(wakeNotificationId(1_000_000_042)).toBe(1_000_042);
    expect(wakeIntentFromLocalNotificationId(1_000_042)).toBe(42);
    expect(wakeIntentFromLocalNotificationId("1000042")).toBe(42);
    expect(wakeIntentFromLocalNotificationId(42)).toBeNull();
    expect(() => wakeNotificationId(0)).toThrow();
  });
});

describe("mobile API boundary", () => {
  it("accepts only the exact trusted HTTPS production origin", () => {
    expect(validateApiBase("https://api.innercosmos.sg", "https://api.innercosmos.sg", true))
      .toBe("https://api.innercosmos.sg");
    for (const value of [
      "http://api.innercosmos.sg", "https://user@api.innercosmos.sg", "https://api.innercosmos.sg/v1",
      "https://api.innercosmos.sg?next=evil", "https://api.innercosmos.sg.evil.example",
      "https://localhost", "https://127.0.0.1", "https://10.0.0.8", "https://[::1]"
    ]) expect(() => validateApiBase(value, "https://api.innercosmos.sg", true)).toThrow();
  });

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
