import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { ConsentRequestDialog } from "./ConsentRequestDialog";
import { PortraitClaimsPanel } from "./PortraitClaimsPanel";
import { AuroraOpeningContinuity } from "./AuroraOpeningContinuity";
import type { PortraitClaimsView } from "../api";

afterEach(cleanup);

/**
 * CP-12 non-author usability — the codable slice of the WCAG 2.2 AA targets: keyboard/focus
 * behavior on the consent decision (J01's most consequential surface), screen-reader names
 * on every interactive control of the newest surfaces, and the static stylesheet contract
 * for reduced motion, visible focus and relative text scaling. Real-device, 200%-zoom and
 * three-carrier field matrices remain the human research gate (CP-12A).
 */

const claimsView: PortraitClaimsView = {
  claims: [
    { claimId: 12, claimKey: "表达习惯", claimType: "EXPRESSION_STYLE", state: "INFERRED",
      authorityLevel: "MODEL_INFERENCE", value: "\"喜欢长段落自我分析\"", version: "1",
      scope: "CAPSULE_RUNTIME", sourceType: "AUTO_EXTRACTION" }
  ],
  unknownDimensions: 3,
  explanation: "每项理解都标明来源与状态。",
  suppressed: []
};

describe("CP-12 usability contract — keyboard and focus (J01 consent decision)", () => {
  it("moves focus INTO the consent dialog on open, without pre-selecting either choice", () => {
    const opener = document.createElement("button");
    opener.textContent = "发送";
    document.body.appendChild(opener);
    opener.focus();

    render(<ConsentRequestDialog open purposeCode="AI_PROVIDER_EGRESS"
      description="将你的对话内容发送到所选的境内大模型服务以生成回应。"
      withdrawalEffect="拒绝后 AI 回应功能不可用。"
      busy={false} onGrant={vi.fn()} onDismiss={vi.fn()} />);

    const dialog = screen.getByRole("dialog");
    expect(document.activeElement).toBe(dialog.querySelector(".consent-request"));
    expect(document.activeElement).not.toBe(screen.getByRole("button", { name: "同意并继续" }));
    expect(document.activeElement).not.toBe(screen.getByRole("button", { name: "暂不" }));
    opener.remove();
  });

  it("Escape dismisses without granting (busy guards it), and focus returns to the opener", () => {
    const opener = document.createElement("button");
    opener.textContent = "发送";
    document.body.appendChild(opener);
    opener.focus();

    const onGrant = vi.fn();
    const onDismiss = vi.fn();
    const { rerender } = render(<ConsentRequestDialog open purposeCode="AI_PROVIDER_EGRESS"
      description="d" withdrawalEffect="w" busy={false} onGrant={onGrant} onDismiss={onDismiss} />);

    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    expect(onDismiss).toHaveBeenCalledOnce();
    expect(onGrant).not.toHaveBeenCalled();

    // While the choice is being recorded, Escape must not sneak in a dismissal.
    rerender(<ConsentRequestDialog open purposeCode="AI_PROVIDER_EGRESS"
      description="d" withdrawalEffect="w" busy={true} onGrant={onGrant} onDismiss={onDismiss} />);
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    expect(onDismiss).toHaveBeenCalledOnce();

    rerender(<ConsentRequestDialog open={false} purposeCode="AI_PROVIDER_EGRESS"
      description="d" withdrawalEffect="w" busy={false} onGrant={onGrant} onDismiss={onDismiss} />);
    expect(document.activeElement).toBe(opener);
    opener.remove();
  });
});

describe("CP-12 usability contract — screen-reader names on every interactive control", () => {
  it("portrait claims panel: every button is named and the reason input has a real label", () => {
    render(<PortraitClaimsPanel view={claimsView} loading={false} loaded={true}
      busyClaimId={null} onLoad={vi.fn()} onSuppress={vi.fn()}
      onRestore={vi.fn()} onDelete={vi.fn()} locale="zh-CN" />);
    const buttons = screen.getAllByRole("button");
    expect(buttons.length).toBeGreaterThan(0);
    for (const button of buttons) {
      expect(button.getAttribute("aria-label") ?? button.textContent?.trim() ?? "")
        .not.toBe("");
    }
    fireEvent.click(screen.getByRole("button", { name: "搁置" }));
    expect(screen.getByRole("textbox", { name: "可选：为什么这不太是你" })).toBeInTheDocument();
  });

  it("opening continuity card: the dismiss control is named, not a bare ×", () => {
    render(<AuroraOpeningContinuity
      continuity={{ hasPrior: true, priorSessionId: 7, priorActiveAt: "2026-09-10",
        carryForward: [{ kind: "PRIOR_SUMMARY", text: "上次谈到职业犹豫", provenance: "上次对话（9月10日）的整理" }],
        openingLine: "你聊过一次。" }}
      locale="zh-CN" onDismiss={vi.fn()} />);
    expect(screen.getByRole("button", { name: "收起开场上下文" })).toBeInTheDocument();
  });

  it("consent dialog: both choices are named buttons", () => {
    render(<ConsentRequestDialog open purposeCode="AI_PROVIDER_EGRESS"
      description="d" withdrawalEffect="w" busy={false} onGrant={vi.fn()} onDismiss={vi.fn()} />);
    expect(screen.getByRole("button", { name: "同意并继续" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "暂不" })).toBeInTheDocument();
  });
});

describe("CP-12 usability contract — stylesheet hooks for motion, focus and text scaling", () => {
  const css = readFileSync(resolve(__dirname, "../styles.css"), "utf-8");

  it("honors prefers-reduced-motion with explicit overrides", () => {
    expect(css).toMatch(/@media\s*\(prefers-reduced-motion/i);
  });

  it("keeps a visible :focus-visible style so keyboard focus is never invisible", () => {
    expect(css).toMatch(/:focus-visible/);
  });

  it("does not pin the root font to a fixed pixel size (text scaling must survive)", () => {
    const rootRule = css.match(/(?:^|})\s*html\s*,?\s*\{[^}]*\}/);
    if (rootRule) {
      expect(rootRule[0]).not.toMatch(/font-size\s*:\s*\d+px/i);
    }
    const htmlFontSize = css.match(/html\s*\{[^}]*font-size\s*:\s*([^;}]+)/i);
    if (htmlFontSize) {
      expect(htmlFontSize[1].trim()).not.toMatch(/^\d+(\.\d+)?px$/);
    }
  });
});
