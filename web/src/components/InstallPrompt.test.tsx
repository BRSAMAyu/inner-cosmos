import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { InstallPrompt } from "./InstallPrompt";

// B5-pwa-mobile: pins the in-app install affordance's visibility logic against the browser's
// beforeinstallprompt contract -- MUST call event.preventDefault() synchronously (or the
// browser's own mini-infobar takes over and the event can never be replayed later), MUST stash
// the event so a later user click can call event.prompt() on demand, and MUST disappear again
// once the app is actually installed (appinstalled) or the user dismisses it. jsdom cannot fire
// a *real* beforeinstallprompt (no browser install heuristics exist here) so this dispatches a
// synthetic event shaped like one -- the honest boundary of what's unit-testable; the rest is
// live-verified against real Chromium (see evidence/track-b/scripts/).
function fireBeforeInstallPrompt(outcome: "accepted" | "dismissed" = "accepted") {
  const event = new Event("beforeinstallprompt", { cancelable: true }) as Event & {
    preventDefault: () => void;
    prompt: () => Promise<void>;
    userChoice: Promise<{ outcome: "accepted" | "dismissed"; platform: string }>;
  };
  const preventDefault = vi.spyOn(event, "preventDefault");
  const prompt = vi.fn().mockResolvedValue(undefined);
  Object.assign(event, {
    prompt,
    userChoice: Promise.resolve({ outcome, platform: "web" }),
  });
  act(() => {
    window.dispatchEvent(event);
  });
  return { event, preventDefault, prompt };
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("InstallPrompt", () => {
  it("renders nothing before any beforeinstallprompt event fires (already installed, or unsupported browser e.g. iOS Safari)", () => {
    const { container } = render(<InstallPrompt />);
    expect(container).toBeEmptyDOMElement();
  });

  it("captures beforeinstallprompt, calls preventDefault synchronously, and shows the install affordance", () => {
    render(<InstallPrompt />);
    const { preventDefault } = fireBeforeInstallPrompt();
    expect(preventDefault).toHaveBeenCalledOnce();
    expect(screen.getByRole("button", { name: "安装内宇宙" })).toBeInTheDocument();
  });

  it("clicking install replays the stored event's prompt() and hides the affordance once the user answers", async () => {
    render(<InstallPrompt />);
    const { prompt } = fireBeforeInstallPrompt("accepted");
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "安装内宇宙" }));
      await Promise.resolve();
    });
    expect(prompt).toHaveBeenCalledOnce();
    expect(screen.queryByRole("button", { name: "安装内宇宙" })).not.toBeInTheDocument();
  });

  it("clicking dismiss hides the affordance without ever calling prompt()", () => {
    render(<InstallPrompt />);
    const { prompt } = fireBeforeInstallPrompt();
    fireEvent.click(screen.getByRole("button", { name: "不用了" }));
    expect(prompt).not.toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: "安装内宇宙" })).not.toBeInTheDocument();
  });

  it("hides (and stays hidden) once the browser reports the app was actually installed", () => {
    render(<InstallPrompt />);
    fireBeforeInstallPrompt();
    expect(screen.getByRole("button", { name: "安装内宇宙" })).toBeInTheDocument();
    act(() => {
      window.dispatchEvent(new Event("appinstalled"));
    });
    expect(screen.queryByRole("button", { name: "安装内宇宙" })).not.toBeInTheDocument();
  });
});
