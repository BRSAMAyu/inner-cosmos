import { afterEach, describe, expect, it, vi } from "vitest";
import { apiUrl, configureBearerAuth, subscribeProactive, validateApiBase } from "../api";
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

  // Demo connectivity (DEMO-RUNBOOK): a debug build flagged VITE_DEMO_MODE may point at a public
  // HTTPS tunnel OR a plain-HTTP LAN hotspot origin, so the class can reach a laptop server over
  // school WiFi. Production stays fully strict (the cases above). Demo still rejects credentials,
  // query, fragment, non-http(s) schemes, and paths.
  it("demo mode allows a public HTTPS tunnel origin and a plain-HTTP LAN origin, but still rejects unsafe shapes", () => {
    expect(validateApiBase("https://some-words.trycloudflare.com", "", false, false, false, "", true))
      .toBe("https://some-words.trycloudflare.com");
    expect(validateApiBase("http://192.168.137.1:8080", "", false, false, false, "", true))
      .toBe("http://192.168.137.1:8080");
    for (const value of [
      "ftp://192.168.137.1", "https://user:pw@host", "https://host/path", "https://host?q=1",
      "https://host#frag", "not-a-url"
    ]) expect(() => validateApiBase(value, "", false, false, false, "", true)).toThrow();
  });

  it("production still rejects what demo would allow (demoMode does not weaken signed builds)", () => {
    // Same HTTP/LAN origins, but with demoMode=false (a real production build): must throw.
    expect(() => validateApiBase("http://192.168.137.1:8080", "http://192.168.137.1:8080", true)).toThrow();
    expect(() => validateApiBase("https://some-words.trycloudflare.com", "", true)).toThrow();
  });

  it("keeps API requests scoped to the Inner Cosmos API namespace", () => {
    expect(apiUrl("/api/auth/csrf")).toBe("/api/auth/csrf");
    expect(() => apiUrl("https://evil.example/collect")).toThrow("Only Inner Cosmos API paths are allowed");
  });

  // Gemini audit 4.3 (CONFIRMED/P0): subscribeProactive converged onto a fetch-based
  // implementation (native EventSource cannot read the HTTP status of a failed connection
  // attempt, so it can never detect a real 401 vs. a transient blip). This rewrites the old
  // FakeEventSource-based test as a fetch/ReadableStream mock -- same two behaviors pinned:
  // only well-typed events reach the caller, and unsubscribing tears down the live connection.
  it("accepts only typed proactive events and tears down the live connection on unsubscribe", async () => {
    const encoder = new TextEncoder();
    const frames = "event: proactive\ndata: "
      + JSON.stringify({ type: "wake_intent", content: "回来", ts: "2026-07-15T00:00:00Z" })
      + "\n\nevent: proactive\ndata: not-json\n\n";
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode(frames));
        controller.close();
      }
    });
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      new Response(body, { status: 200, headers: { "Content-Type": "text/event-stream" } }));
    vi.stubGlobal("fetch", fetchMock);

    const received = vi.fn();
    const unsubscribe = subscribeProactive(received);
    await vi.waitFor(() => expect(received).toHaveBeenCalledOnce());

    expect(received).toHaveBeenCalledWith({ type: "wake_intent", content: "回来", ts: "2026-07-15T00:00:00Z" });

    unsubscribe();
    const firstCallInit = fetchMock.mock.calls[0]?.[1] as RequestInit | undefined;
    expect(firstCallInit?.signal?.aborted).toBe(true);
  });

  // Gemini audit 4.3 (CONFIRMED/P0): a real 401 must attempt AT MOST ONE token refresh before
  // giving up -- never loop forever against a session/token that is actually invalid.
  it("gives up after exactly one token refresh attempt on a real 401, never looping forever", async () => {
    const invalidator = vi.fn(async () => undefined);
    let tokenCalls = 0;
    configureBearerAuth(async () => { tokenCalls += 1; return `token-${tokenCalls}`; }, true, invalidator);

    const fetchMock = vi.fn(async () => new Response(null, { status: 401 }));
    vi.stubGlobal("fetch", fetchMock);

    const onConnectionChange = vi.fn();
    subscribeProactive(vi.fn(), onConnectionChange);

    await vi.waitFor(() => expect(onConnectionChange).toHaveBeenCalledWith(false));

    // Exactly two fetch attempts (initial + the one bounded retry after refresh), never a third.
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(invalidator).toHaveBeenCalledOnce();

    // Waiting further must not trigger any additional attempt -- the loop has genuinely stopped,
    // not just paused for a backoff interval.
    await new Promise(resolve => setTimeout(resolve, 50));
    expect(fetchMock).toHaveBeenCalledTimes(2);

    configureBearerAuth(null);
  });

  // Gemini audit 4.3 (CONFIRMED/P0): sustained transient failures must trip a circuit breaker
  // rather than hammering the server forever with an unbounded retry loop.
  it("stops retrying after sustained consecutive transient failures (circuit breaker)", async () => {
    vi.useFakeTimers();
    try {
      const fetchMock = vi.fn(async () => { throw new Error("network down"); });
      vi.stubGlobal("fetch", fetchMock);
      const onConnectionChange = vi.fn();

      subscribeProactive(vi.fn(), onConnectionChange);

      // Drive enough fake-timer advances to exhaust the circuit breaker's failure budget --
      // exponential backoff caps at 30s, so a generous total covers every attempt.
      for (let i = 0; i < 12; i++) {
        await vi.advanceTimersByTimeAsync(30000);
      }

      const callsAtBreaker = fetchMock.mock.calls.length;
      expect(callsAtBreaker).toBeGreaterThan(1);
      expect(onConnectionChange).toHaveBeenCalledWith(false);

      // Advancing further must not produce any additional attempt -- the breaker has tripped.
      await vi.advanceTimersByTimeAsync(120000);
      expect(fetchMock.mock.calls.length).toBe(callsAtBreaker);
    } finally {
      vi.useRealTimers();
    }
  });
});

afterEach(() => { vi.unstubAllGlobals(); configureBearerAuth(null); });
