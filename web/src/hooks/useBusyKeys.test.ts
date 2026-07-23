import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useBusyKeys } from "./useBusyKeys";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(res => { resolve = res; });
  return { promise, resolve };
}

// Gemini audit 4.8 (CONFIRMED/P1): the whole point of a per-resource busy guard is that an
// in-flight action on resource A must NEVER report busy for an unrelated resource B -- a single
// shared boolean (the anti-pattern the audit calls out) would report both as busy.
describe("useBusyKeys", () => {
  it("marks only the key currently in flight as busy -- an unrelated key is never affected", async () => {
    const { result } = renderHook(() => useBusyKeys<number>());
    const slow = deferred<void>();

    let runPromise: Promise<void>;
    act(() => { runPromise = result.current.run(1, () => slow.promise); });

    expect(result.current.isBusy(1)).toBe(true);
    expect(result.current.isBusy(2)).toBe(false); // the anti-pattern this fixes: unrelated resource stays free

    await act(async () => { slow.resolve(); await runPromise; });
    expect(result.current.isBusy(1)).toBe(false);
  });

  it("clears the busy key even when the action throws", async () => {
    const { result } = renderHook(() => useBusyKeys<number>());
    await act(async () => {
      await expect(result.current.run(5, () => Promise.reject(new Error("boom")))).rejects.toThrow("boom");
    });
    expect(result.current.isBusy(5)).toBe(false);
  });

  it("two different keys can be in flight concurrently, independently", async () => {
    const { result } = renderHook(() => useBusyKeys<number>());
    const slowA = deferred<void>();
    const slowB = deferred<void>();

    let runA: Promise<void>; let runB: Promise<void>;
    act(() => {
      runA = result.current.run(1, () => slowA.promise);
      runB = result.current.run(2, () => slowB.promise);
    });
    expect(result.current.isBusy(1)).toBe(true);
    expect(result.current.isBusy(2)).toBe(true);

    await act(async () => { slowA.resolve(); await runA; });
    expect(result.current.isBusy(1)).toBe(false);
    expect(result.current.isBusy(2)).toBe(true); // B is still in flight, unaffected by A finishing

    await act(async () => { slowB.resolve(); await runB; });
    expect(result.current.isBusy(2)).toBe(false);
  });
});
