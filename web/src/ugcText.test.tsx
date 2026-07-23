import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { cleanup, render, screen } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { LettersInbox } from "./components/LettersInbox";
import type { SlowLetter } from "./api";

// Gemini audit 4.11 (CONFIRMED/P2): every genuinely user-authored/derived free-text render site
// must carry the shared `.ugc-text` primitive (overflow-wrap: anywhere; word-break: break-word;
// min-width: 0) so a long unbroken token (URL, CJK run, emoji chain) wraps instead of overflowing
// its container.
//
// Honest jsdom boundary: jsdom has no real layout/reflow engine, so this suite cannot measure
// actual pixel overflow at a given viewport width the way a real browser could -- there is no
// "does this element visually overflow at 320px" assertion available here (see the same caveat
// documented in src/pwaManifest.test.ts for a different jsdom gap). What jsdom's `getComputedStyle`
// *can* honestly prove is CSS cascade/specificity resolution: that the real, shipped `.ugc-text`
// rule from styles.css (not a hand-copied duplicate that could drift and silently stop testing
// anything) resolves to the expected computed values on a real class name, and that this holds
// unconditionally -- i.e. the rule is not accidentally gated behind a `@media` breakpoint or any
// other conditional that only kicks in at some viewport widths or font scales. We simulate 320px,
// 390px and 200% root font-size specifically to prove the primitive has no such gating: if it were
// wrapped in an errant `@media (min-width: ...)` block, jsdom's CSSOM would still resolve
// `getComputedStyle` against the cascade for whatever `window.innerWidth`/`documentElement` state
// is current, so a conditional rule would show up as the properties being unset in at least one of
// these conditions. That is the full (and correct) extent of what this tier can assert; true
// visual non-overflow at those breakpoints is a real-browser/E2E concern, not a unit-test one.

const here = path.dirname(fileURLToPath(import.meta.url));
const stylesheetPath = path.join(here, "styles.css");
const stylesheetText = readFileSync(stylesheetPath, "utf-8");

let styleTag: HTMLStyleElement;

beforeAll(() => {
  styleTag = document.createElement("style");
  styleTag.textContent = stylesheetText;
  document.head.appendChild(styleTag);
});

afterAll(() => {
  styleTag.remove();
});

afterEach(cleanup);

const originalInnerWidth = window.innerWidth;
const originalFontSize = document.documentElement.style.fontSize;

// Track any bare (non-RTL) scratch elements appended directly to document.body so they are always
// removed in afterEach -- even if the test's own assertion throws first -- otherwise a failed
// assertion would leak a stray element into the shared jsdom document and corrupt a later test's
// screen.getByText() query (this file's LettersInbox case queries by exact text content, which a
// leftover dummy element with the same text would make ambiguous).
let scratchEl: HTMLElement | null = null;

afterEach(() => {
  scratchEl?.remove();
  scratchEl = null;
  Object.defineProperty(window, "innerWidth", { value: originalInnerWidth, configurable: true, writable: true });
  document.documentElement.style.fontSize = originalFontSize;
  window.dispatchEvent(new Event("resize"));
});

function setViewportWidth(width: number) {
  Object.defineProperty(window, "innerWidth", { value: width, configurable: true, writable: true });
  window.dispatchEvent(new Event("resize"));
}

// A long unbroken token mixing an ASCII run and a CJK run -- no spaces anywhere, so any container
// without wrap protection would be forced wider than its box (or, in a real browser, overflow it).
const longUnbrokenText = "a".repeat(80) + "的".repeat(80) + "b".repeat(80);

function expectUgcComputedStyle(element: Element) {
  const computed = getComputedStyle(element);
  expect(computed.overflowWrap).toBe("anywhere");
  expect(computed.wordBreak).toBe("break-word");
  expect(computed.minWidth).toBe("0px");
}

describe("ugc-text shared primitive (styles.css, real stylesheet pinned via readFileSync)", () => {
  it("declares overflow-wrap: anywhere; word-break: break-word; min-width: 0 unconditionally", () => {
    expect(stylesheetText).toMatch(
      /\.ugc-text\s*\{[^}]*overflow-wrap:\s*anywhere;[^}]*word-break:\s*break-word;[^}]*min-width:\s*0;?[^}]*\}/
    );
  });

  it("applies its computed style at a 320px viewport width", () => {
    setViewportWidth(320);
    scratchEl = document.createElement("p");
    scratchEl.className = "ugc-text";
    scratchEl.textContent = longUnbrokenText;
    document.body.appendChild(scratchEl);
    expectUgcComputedStyle(scratchEl);
  });

  it("applies its computed style at a 390px viewport width", () => {
    setViewportWidth(390);
    scratchEl = document.createElement("p");
    scratchEl.className = "ugc-text";
    scratchEl.textContent = longUnbrokenText;
    document.body.appendChild(scratchEl);
    expectUgcComputedStyle(scratchEl);
  });

  it("applies its computed style at 200% root font-size scaling", () => {
    document.documentElement.style.fontSize = "200%";
    scratchEl = document.createElement("p");
    scratchEl.className = "ugc-text";
    scratchEl.textContent = longUnbrokenText;
    document.body.appendChild(scratchEl);
    expectUgcComputedStyle(scratchEl);
  });

  it("is actually wired into a real production component (LettersInbox), not only asserted against a hand-built dummy element", () => {
    const letter: SlowLetter = {
      id: 7, senderUserId: 2, receiverUserId: 1, receiverCapsuleId: 4,
      title: "一封信", letterBody: longUnbrokenText, status: "READ", parallaxDistance: 1,
      estimatedArrivalAt: "2026-07-15T00:00:00Z"
    };
    render(<LettersInbox letterInbox={[letter]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    const paragraph = screen.getByText(longUnbrokenText);
    expect(paragraph).toHaveClass("ugc-text");
    expectUgcComputedStyle(paragraph);
  });
});
