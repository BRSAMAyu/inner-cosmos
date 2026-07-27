import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GuideCenter, OnboardingGuide, hasCompletedOnboarding } from "./OnboardingGuide";

describe("OnboardingGuide", () => {
  beforeEach(() => localStorage.clear());
  afterEach(cleanup);

  it("walks a new user through the core journey and persists completion", () => {
    const onClose = vi.fn();
    const onNavigate = vi.fn();
    render(<OnboardingGuide open userId={42} onClose={onClose} onNavigate={onNavigate} />);
    expect(screen.getByRole("dialog", { name: "欢迎来到 Inner Cosmos" })).toBeVisible();
    for (let index = 0; index < 4; index += 1) {
      fireEvent.click(screen.getByRole("button", { name: "继续" }));
    }
    fireEvent.click(screen.getByRole("button", { name: "打开设置" }));
    expect(hasCompletedOnboarding(42)).toBe(true);
    expect(onClose).toHaveBeenCalledOnce();
    expect(onNavigate).toHaveBeenCalledWith("voice");
  });

  it("keeps the whole guide available from settings", () => {
    const onReplay = vi.fn();
    const onNavigate = vi.fn();
    render(<GuideCenter onReplay={onReplay} onNavigate={onNavigate} />);
    fireEvent.click(screen.getByRole("button", { name: "重看首次引导" }));
    fireEvent.click(screen.getByRole("button", { name: /看懂内宇宙/ }));
    expect(onReplay).toHaveBeenCalledOnce();
    expect(onNavigate).toHaveBeenCalledWith("cosmos");
    fireEvent.click(screen.getByRole("button", { name: /慢信与连接/ }));
    expect(onNavigate).toHaveBeenCalledWith("letters");
  });

  it("closes on Escape without marking the tour complete, restores focus, and restarts from step one", () => {
    const onClose = vi.fn();
    const trigger = document.createElement("button");
    document.body.appendChild(trigger);
    trigger.focus();
    const { rerender } = render(<OnboardingGuide open userId={7} onClose={onClose} onNavigate={() => undefined} />);
    expect(screen.getByRole("button", { name: "跳过引导" })).toHaveFocus();
    fireEvent.click(screen.getByRole("button", { name: "继续" }));
    expect(screen.getByText("看见它如何成为记忆")).toBeVisible();
    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalledOnce();
    expect(hasCompletedOnboarding(7)).toBe(false);
    rerender(<OnboardingGuide open={false} userId={7} onClose={onClose} onNavigate={() => undefined} />);
    expect(trigger).toHaveFocus();
    rerender(<OnboardingGuide open userId={7} onClose={onClose} onNavigate={() => undefined} />);
    expect(screen.getByText("先从一句真话开始")).toBeVisible();
    trigger.remove();
  });

  it("dismisses from the dimmed surface without marking onboarding complete", () => {
    const onClose = vi.fn();
    const { container } = render(<OnboardingGuide open userId={9} onClose={onClose} onNavigate={() => undefined} />);
    fireEvent.mouseDown(container.querySelector(".onboarding-backdrop")!);
    expect(onClose).toHaveBeenCalledOnce();
    expect(hasCompletedOnboarding(9)).toBe(false);
  });

  it("ships as a compact desktop side sheet and a consistent mobile bottom sheet", () => {
    const here = path.dirname(fileURLToPath(import.meta.url));
    const css = readFileSync(path.join(here, "..", "styles.css"), "utf8");
    expect(css).toMatch(/\.onboarding-backdrop\s*\{[^}]*place-items:\s*stretch end/);
    expect(css).toMatch(/\.onboarding-guide\s*\{[^}]*width:\s*min\(460px,\s*100%\)/);
    expect(css).toMatch(/@media \(max-width:\s*620px\)[^]*?\.onboarding-backdrop\s*\{[^}]*place-items:\s*end stretch/);
    expect(css).toMatch(/@media \(max-width:\s*620px\)[^]*?\.onboarding-copy\s*\{[^}]*min-height:\s*190px/);
  });
});
