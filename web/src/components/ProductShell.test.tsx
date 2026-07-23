import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { capsulePath, initialProductSpace, letterThreadPath, MeSpace, productSpaceFromPath, ProductShellNavigation, resourceFromPath, spacePath } from "./ProductShell";

afterEach(cleanup);

describe("ProductShell", () => {
  // Characterization: pins the pre-router `?space=` query-param behavior. The value is
  // still read once, at boot, to redirect old bookmarks/links to their real-route
  // equivalent (see AuroraApp.tsx's legacy-link redirect effect) -- it must keep meaning
  // exactly this even though it is no longer the live navigation mechanism.
  it("restores only a known space from the URL", () => {
    expect(initialProductSpace("?space=letters")).toBe("letters");
    expect(initialProductSpace("?space=unknown")).toBe("aurora");
    expect(initialProductSpace("?space=https://attacker.invalid")).toBe("aurora");
  });

  it("maps every space to a real, stable path", () => {
    expect(spacePath("aurora")).toBe("/aurora");
    expect(spacePath("cosmos")).toBe("/cosmos");
    expect(spacePath("resonance")).toBe("/resonance");
    expect(spacePath("letters")).toBe("/connections/letters");
    expect(spacePath("me")).toBe("/me");
  });

  it("resolves the active space from an exact route path", () => {
    expect(productSpaceFromPath("/aurora")).toBe("aurora");
    expect(productSpaceFromPath("/cosmos")).toBe("cosmos");
    expect(productSpaceFromPath("/resonance")).toBe("resonance");
    expect(productSpaceFromPath("/connections/letters")).toBe("letters");
    expect(productSpaceFromPath("/me")).toBe("me");
  });

  it("resolves the active space from a nested sub-route (stretch goal hook for later per-space routes)", () => {
    expect(productSpaceFromPath("/cosmos/starfield")).toBe("cosmos");
    expect(productSpaceFromPath("/connections/letters/42")).toBe("letters");
  });

  it("parses a nested resource deep link into space + resource + numeric id", () => {
    expect(resourceFromPath("/resonance/capsule/42")).toEqual({ space: "resonance", resource: "capsule", id: 42 });
    expect(resourceFromPath("/connections/letters/7")).toEqual({ space: "letters", resource: "7", id: null });
    expect(resourceFromPath("/cosmos/portrait")).toEqual({ space: "cosmos", resource: "portrait", id: null });
  });

  it("returns null resource/id for a bare space path and never a non-numeric id", () => {
    expect(resourceFromPath("/resonance")).toEqual({ space: "resonance", resource: null, id: null });
    expect(resourceFromPath("/resonance/capsule/abc")).toEqual({ space: "resonance", resource: "capsule", id: null });
    expect(resourceFromPath("/")).toEqual({ space: "aurora", resource: null, id: null });
  });

  it("builds a shareable capsule deep link that round-trips through resourceFromPath", () => {
    expect(capsulePath(42)).toBe("/resonance/capsule/42");
    expect(resourceFromPath(capsulePath(42))).toEqual({ space: "resonance", resource: "capsule", id: 42 });
  });

  it("builds a shareable letter-thread deep link that round-trips through resourceFromPath", () => {
    expect(letterThreadPath(9)).toBe("/connections/letters/thread/9");
    expect(resourceFromPath(letterThreadPath(9))).toEqual({ space: "letters", resource: "thread", id: 9 });
  });

  it("falls back to aurora for the root path and any unrecognized path", () => {
    expect(productSpaceFromPath("/")).toBe("aurora");
    expect(productSpaceFromPath("/unknown")).toBe("aurora");
    expect(productSpaceFromPath("")).toBe("aurora");
  });

  it("exposes all five spaces and marks the current one", () => {
    render(<ProductShellNavigation active="cosmos" onNavigate={() => undefined} />);
    expect(screen.getAllByRole("button")).toHaveLength(5);
    expect(screen.getByRole("button", { name: /^内宇宙/ })).toHaveAttribute("aria-current", "page");
  });

  it("delegates an explicit navigation choice", () => {
    const onNavigate = vi.fn();
    render(<ProductShellNavigation active="aurora" onNavigate={onNavigate} />);
    fireEvent.click(screen.getByRole("button", { name: /^共鸣/ }));
    expect(onNavigate).toHaveBeenCalledWith("resonance");
  });

  // W2 UIUX audit: <strong>label</strong><small>description</small> sit adjacent with no
  // whitespace in the DOM (verified live: textContent === "今天Aurora"), so a screen reader would
  // concatenate them into one run-on word. An explicit aria-label with a separator gives assistive
  // tech a properly separated announcement without changing the two-line visual layout.
  it("gives every space tab a screen-reader-friendly separated name, not a run-on concatenation", () => {
    render(<ProductShellNavigation active="aurora" onNavigate={() => undefined} />);
    expect(screen.getByRole("button", { name: "今天 · Aurora" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "内宇宙 · 记忆与自我理解" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "共鸣 · 共鸣体与相遇" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "连接 · 慢信与关系" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "我的 · 控制与边界" })).toBeInTheDocument();
  });

  // W2 UIUX audit (doc 24 section 5.1: "中文和 en-SG 均无硬编码"): live-verified with the app's own
  // locale toggle -- every other string on the page translated to English except this nav, because
  // it never took a `locale` prop at all. Pins the fix and guards the five real space keys can never
  // silently drift out of sync between the zh-CN source array and the en-SG label map.
  it("translates every space tab's label when locale is en-SG, and stays zh-CN by default", () => {
    const { unmount } = render(<ProductShellNavigation active="aurora" onNavigate={() => undefined} locale="en-SG" />);
    expect(screen.getByRole("button", { name: "Today · Aurora" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cosmos · Memory & self-understanding" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Resonance · Capsules & encounters" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Connect · Slow letters & relationships" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Me · Control & boundaries" })).toBeInTheDocument();
    unmount();
    render(<ProductShellNavigation active="aurora" onNavigate={() => undefined} />);
    expect(screen.getByRole("button", { name: "今天 · Aurora" })).toBeInTheDocument();
  });

  it("renders the Me control hub bilingually and pluralizes English counts", () => {
    const props = {
      native: false, connected: true, wakeIntentCount: 1, activeClaimCount: 2,
      publicCapsuleCount: 1, friendCount: 3, onNavigate: () => undefined,
      onRequestPush: () => undefined, onRequestMicrophone: () => undefined, onLogout: () => undefined,
      onOpenSafetyHarbor: () => undefined
    };
    const { rerender } = render(<MeSpace {...props} locale="zh-CN" />);
    expect(screen.getByRole("heading", { name: "由你决定，Aurora 怎样参与。" })).toBeVisible();
    expect(screen.getByText("1 个有效约定")).toBeVisible();

    rerender(<MeSpace {...props} locale="en-SG" />);
    expect(screen.getByRole("heading", { name: "You decide how Aurora takes part." })).toBeVisible();
    expect(screen.getByText("1 active plan")).toBeVisible(); // singular
    expect(screen.getByText("1 public capsule · 3 mutual connections")).toBeVisible(); // singular + plural
    expect(screen.getByRole("button", { name: "Sign out of this device" })).toBeVisible();
  });

  it("MeSpace always offers a way to reach the safety harbor, not just during a triggered alert", () => {
    const onOpenSafetyHarbor = vi.fn();
    const props = {
      native: false, connected: true, wakeIntentCount: 0, activeClaimCount: 0,
      publicCapsuleCount: 0, friendCount: 0, onNavigate: () => undefined,
      onRequestPush: () => undefined, onRequestMicrophone: () => undefined, onLogout: () => undefined,
      onOpenSafetyHarbor
    };
    render(<MeSpace {...props} locale="zh-CN" />);
    fireEvent.click(screen.getByRole("button", { name: /安全避风港/ }));
    expect(onOpenSafetyHarbor).toHaveBeenCalledOnce();
  });

  // W2 UIUX audit follow-up: the earlier run-on-naming fix (above) swapped the space tab's label
  // element from <span> to <strong> (so it could carry aria-hidden alongside the new aria-label)
  // but styles.css's sizing/truncation rule was never updated to match -- it still targeted
  // `.space-tabs button span`, a selector nothing in the DOM matches anymore. Live-verified in a
  // real browser at 375px width with the en-SG locale (doc 24's own bilingual mandate): the orphaned
  // selector left the label with no font-size/ellipsis rule at all, so it rendered at the browser's
  // default bold ~16px instead of the design's .8rem and visibly overlapped the neighboring tab
  // ("Resonance" measured 85px wide inside a 70px-wide button). Fixed by repointing the selector at
  // `strong`. This test loads the real, shipped styles.css (not a hand-copied duplicate, matching the
  // established DangerQuiet/HeartDiary pattern) so the rule can't silently drift out of sync again.
  describe(".space-tabs button label sizing (styles.css, real stylesheet pinned via readFileSync)", () => {
    const here = path.dirname(fileURLToPath(import.meta.url));
    const stylesheetText = readFileSync(path.join(here, "..", "styles.css"), "utf-8");
    let styleTag: HTMLStyleElement;

    beforeAll(() => {
      styleTag = document.createElement("style");
      styleTag.textContent = stylesheetText;
      document.head.appendChild(styleTag);
    });
    afterAll(() => styleTag.remove());

    it("no longer targets a <span> the JSX never renders, at either the base rule or the <=680px mobile override", () => {
      expect(stylesheetText).not.toMatch(/\.space-tabs button span/);
    });

    it("gives the real <strong> label (not a stale <span>) the .8rem truncating rule", () => {
      render(<ProductShellNavigation active="aurora" onNavigate={() => undefined} />);
      const label = screen.getByRole("button", { name: "今天 · Aurora" }).querySelector("strong")!;
      const style = getComputedStyle(label);
      expect(style.fontSize).toBe("0.8rem");
      expect(style.display).toBe("block");
      expect(style.overflow).toBe("hidden");
      expect(style.whiteSpace).toBe("nowrap");
    });

    it("also repoints the <=680px mobile override at <strong> (live-verified at 375px: label used to overlap the next tab)", () => {
      const mobileRuleMatch = stylesheetText.match(/@media \(max-width: 680px\)[^]*?\.space-tabs button (strong|span) \{ font-size: \.72rem; \}/);
      expect(mobileRuleMatch, "expected the mobile space-tabs label override to target strong").not.toBeNull();
      expect(mobileRuleMatch![1]).toBe("strong");
    });
  });
});
