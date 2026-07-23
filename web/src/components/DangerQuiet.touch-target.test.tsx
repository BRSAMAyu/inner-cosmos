import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { cleanup, render, screen } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { MeSpace } from "./ProductShell";

// W2 UIUX audit (doc 24 section 5.2: "48dp 触控目标... 是硬门" -- a hard gate). `.danger-quiet` is
// used bare in several places (ProductShell.tsx's MeSpace sign-out, AccountSettings.tsx,
// SocialGroupsView.tsx) with only `.resonance-actions .danger-quiet` (CapsuleWorkbench) carrying its
// own scoped color override -- there was no base rule supplying padding/sizing at all. Live-measured
// the bare Me-space sign-out button before the fix: 62x20px, a near-invisible, sub-touch-target
// native button with zero padding. Fixed with a base `.danger-quiet` rule giving every instance a
// real >=48px touch target; the scoped rule's colors still layer on top via normal cascade.
//
// Honest jsdom boundary (matching the established pattern in ugcText.test.tsx / HeartDiary.focus-
// visible.test.tsx): the real, shipped styles.css is loaded via readFileSync (not a hand-copied
// duplicate) so the rule can't silently drift out of sync, and jsdom's getComputedStyle proves real
// cascade resolution against a live production component's markup -- not full browser layout.

const here = path.dirname(fileURLToPath(import.meta.url));
const stylesheetText = readFileSync(path.join(here, "..", "styles.css"), "utf-8");

let styleTag: HTMLStyleElement;

beforeAll(() => {
  styleTag = document.createElement("style");
  styleTag.textContent = stylesheetText;
  document.head.appendChild(styleTag);
});

afterAll(() => styleTag.remove());
afterEach(cleanup);

function meSpaceProps() {
  return {
    native: false, connected: true, wakeIntentCount: 0, activeClaimCount: 0,
    publicCapsuleCount: 0, friendCount: 0, onNavigate: () => undefined,
    onRequestPush: () => undefined, onRequestMicrophone: () => undefined,
    onLogout: () => undefined, onOpenSafetyHarbor: () => undefined
  };
}

describe(".danger-quiet base sizing (styles.css, real stylesheet pinned via readFileSync)", () => {
  it("declares a base rule (not only the .resonance-actions-scoped color override) with a >=48px touch target", () => {
    const baseRuleMatch = stylesheetText.match(/(?<!-actions )\.danger-quiet\s*\{[^}]*\}/);
    expect(baseRuleMatch, "expected an unscoped `.danger-quiet { ... }` base rule").not.toBeNull();
    const [rule] = baseRuleMatch!;
    expect(rule).toMatch(/min-height:\s*48px/);
    expect(rule).toMatch(/padding:\s*\S+/);
  });

  it("is actually wired into a real production component (MeSpace's sign-out button): computed height reaches 48px", () => {
    render(<MeSpace {...meSpaceProps()} />);
    const button = screen.getByRole("button", { name: "安全退出这台设备" });
    expect(button).toHaveClass("danger-quiet");
    expect(getComputedStyle(button).minHeight).toBe("48px");
  });
});
