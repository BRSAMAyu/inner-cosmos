import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { cleanup, render, screen } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { HeartDiary } from "./HeartDiary";

// W2 UIUX audit (doc 24 section 5.2: "可见焦点... 是硬门" -- visible focus is a hard gate): live
// browser inspection found `.diary-textarea:focus { outline: none; }` in the real, shipped
// styles.css with no compensating border/box-shadow, so keyboard and screen-magnifier users
// tabbing into (or clicking) this borderless, background-transparent textarea got ZERO visible
// focus indicator. Confirmed live: `getComputedStyle(textarea).outlineStyle === "none"` and
// `boxShadow === "none"` on real Tab-key focus. Fixed by replacing the bare `:focus { outline:
// none }` with a `:focus-visible` rule that supplies a warm underline glow instead of removing
// the indicator outright.
//
// This test loads the real stylesheet via readFileSync (not a hand-copied duplicate) so it can't
// silently stop testing anything if the rule drifts, and exercises the real HeartDiary component
// (not a hand-built dummy element) so the fix is proven wired into production markup.

const recorder = vi.hoisted(() => ({ start: vi.fn(), stop: vi.fn() }));
vi.mock("../audio-recorder", () => ({
  PcmWavRecorder: class {
    start = recorder.start;
    stop = recorder.stop;
  }
}));

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

function baseProps() {
  return {
    rawText: "", displayText: "", activeLevel: 0 as const, polishBusy: false, submitBusy: false,
    onTextChange: vi.fn(), onSwitchLevel: vi.fn(), onTranscribeAudio: vi.fn().mockResolvedValue(undefined),
    onSubmit: vi.fn()
  };
}

describe("diary textarea focus visibility (styles.css, real stylesheet pinned via readFileSync)", () => {
  it("no longer ships a bare `.diary-textarea:focus { outline: none }` with nothing standing in for it", () => {
    expect(stylesheetText).not.toMatch(/\.diary-textarea:focus\s*\{\s*outline:\s*none;?\s*\}/);
  });

  it("declares a `.diary-textarea:focus-visible` replacement indicator", () => {
    expect(stylesheetText).toMatch(/\.diary-textarea:focus-visible\s*\{[^}]*box-shadow:[^}]+\}/);
  });

  it("is actually wired into the real HeartDiary component: focusing it produces a visible box-shadow, not just a suppressed outline", () => {
    render(<HeartDiary {...baseProps()} />);
    const textarea = screen.getByLabelText("心声日记正文");
    textarea.focus();
    expect(textarea).toHaveFocus();
    expect(textarea.matches(":focus-visible")).toBe(true);
    const computed = getComputedStyle(textarea);
    expect(computed.boxShadow).not.toBe("none");
  });
});
