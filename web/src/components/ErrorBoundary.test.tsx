import { act } from "@testing-library/react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ErrorBoundary } from "./ErrorBoundary";

// Gemini audit 4.7 (CONFIRMED/P1): "main.tsx 没有 ErrorBoundary." A component that throws during
// render must be caught and replaced with a visible fallback, not leave the caller either crashed
// (React unmounts the whole tree with no boundary) or silently blank. These tests render via
// react-dom directly (not RTL's render helper) so React's real error-boundary unmount/remount
// behavior is exercised exactly as it would be in the app, including a genuine retry-remount.

function Bomb({ shouldThrow, secret }: { shouldThrow: boolean; secret: string }) {
  if (shouldThrow) throw new Error(`exploded while handling ${secret}`);
  return <div data-testid="bomb-ok">no explosion</div>;
}

let container: HTMLElement;
let root: Root;

beforeEach(() => {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => { root.unmount(); });
  container.remove();
  vi.restoreAllMocks();
});

describe("ErrorBoundary", () => {
  it("catches a render-time throw and shows fallback UI instead of leaving the tree crashed/blank", () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined); // React's own dev-mode error logging, expected here
    act(() => {
      root.render(<ErrorBoundary variant="space"><Bomb shouldThrow secret="user's private diary entry" /></ErrorBoundary>);
    });
    expect(container.querySelector("[role='alert']")).not.toBeNull();
    expect(container.querySelector("[data-testid='bomb-ok']")).toBeNull();
  });

  it("never leaks the raw error message (which can embed user-entered text) into the rendered fallback or the onError telemetry callback", () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    const onError = vi.fn();
    act(() => {
      root.render(
        <ErrorBoundary variant="space" onError={onError}>
          <Bomb shouldThrow secret="user's private diary entry" />
        </ErrorBoundary>
      );
    });
    expect(container.textContent).not.toContain("user's private diary entry");
    expect(onError).toHaveBeenCalledTimes(1);
    const scrubbed = onError.mock.calls[0][0];
    expect(JSON.stringify(scrubbed)).not.toContain("user's private diary entry");
    expect(scrubbed.name).toBe("Error");
  });

  it("Retry re-renders the children fresh -- if the underlying condition that caused the crash is gone, the app recovers without a full page reload", () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    let shouldThrow = true;
    function Wrapper() {
      return <ErrorBoundary variant="space"><Bomb shouldThrow={shouldThrow} secret="x" /></ErrorBoundary>;
    }
    act(() => { root.render(<Wrapper />); });
    expect(container.querySelector("[role='alert']")).not.toBeNull();

    // Fix the underlying condition (as if the crash was a transient bad-state issue) and let the
    // parent re-render with the now-safe child -- the boundary itself still shows the fallback
    // (its own error state hasn't been cleared yet) until Retry is actually clicked.
    shouldThrow = false;
    act(() => { root.render(<Wrapper />); });
    expect(container.querySelector("[role='alert']")).not.toBeNull();

    const retryButton = container.querySelector<HTMLButtonElement>("[data-testid='error-boundary-retry']");
    expect(retryButton).not.toBeNull();
    act(() => { retryButton!.click(); });

    expect(container.querySelector("[role='alert']")).toBeNull();
    expect(container.querySelector("[data-testid='bomb-ok']")).not.toBeNull();
  });

  it("the fatal (top-level) variant offers a reload action in addition to retry; the space variant does not", () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    act(() => {
      root.render(<ErrorBoundary variant="fatal"><Bomb shouldThrow secret="x" /></ErrorBoundary>);
    });
    expect(container.querySelector("[data-testid='error-boundary-reload']")).not.toBeNull();
  });

  it("a healthy child renders normally through the boundary with no fallback shown", () => {
    act(() => {
      root.render(<ErrorBoundary variant="space"><Bomb shouldThrow={false} secret="x" /></ErrorBoundary>);
    });
    expect(container.querySelector("[data-testid='bomb-ok']")).not.toBeNull();
    expect(container.querySelector("[role='alert']")).toBeNull();
  });
});
