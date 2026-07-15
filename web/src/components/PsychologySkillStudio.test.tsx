import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PsychologySkillStudio, SkillSuggestionBanner } from "./PsychologySkillStudio";
import type { PsychologySkillManifest, PsychologySkillRun, PsychologySkillSuggestion } from "../api";

afterEach(cleanup);

const skill: PsychologySkillManifest = {
  id: "emotion-needs-clarifier", version: "1", owner: "aurora", title: { "zh-CN": "情绪与需求澄清", "en-SG": "Emotion & needs clarifier" },
  description: { "zh-CN": "先看清感受和需求", "en-SG": "See the feeling and the need" },
  estimatedMinutes: 5, riskTier: "L1", agentInvocation: "SUGGEST_ONLY", userInvocation: "EXPLICIT",
  requiredScopes: [], allowedData: [], allowedTools: [], requiredInputs: ["situation", "feeling", "need"],
  evidence: ["NVC framework"], limitations: { "zh-CN": "不是诊断", "en-SG": "Not a diagnosis" },
  retentionChoices: ["DISCARD_AFTER_SESSION", "SAVE_RESULT", "PROFILE_ELIGIBLE"], evaluationSuite: "suite-1",
  fallback: "aurora-only", escalation: "safety-resources"
};

const run: PsychologySkillRun = {
  id: 1, skillId: "emotion-needs-clarifier", skillVersion: "1", locale: "zh-CN", status: "COMPLETED", riskTier: "L1",
  retentionChoice: "SAVE_RESULT", consentScopes: [], result: { summary: "你更需要被理解" }, evidence: [],
  escalationCode: null, createdAt: "2026-07-15T00:00:00Z", revokedAt: null
};

const suggestion: PsychologySkillSuggestion = { skillId: skill.id, skillVersion: "1", title: "情绪与需求澄清", reason: "你提到了拉扯感", invocation: "SUGGEST_ONLY", createsRun: false };

describe("PsychologySkillStudio", () => {
  it("requires consent before starting and reports selection/locale changes", () => {
    const onSelectSkill = vi.fn();
    const onLocaleChange = vi.fn();
    render(<PsychologySkillStudio skills={[skill]} skillRuns={[]} selectedSkill={skill} skillAnswers={{}}
      skillConsent={false} skillRetention="DISCARD_AFTER_SESSION" skillBusy={false} skillLocale="zh-CN"
      onLocaleChange={onLocaleChange} onSelectSkill={onSelectSkill} onAnswerChange={() => undefined}
      onRetentionChange={() => undefined} onConsentChange={() => undefined} onRun={() => undefined}
      onContinueWithAurora={() => undefined} onRevokeRun={() => undefined} />);
    expect(screen.getByRole("button", { name: /开始这次反思/ })).toBeDisabled();
    fireEvent.click(screen.getByRole("tab", { name: /情绪与需求澄清/ }));
    expect(onSelectSkill).toHaveBeenCalledWith("emotion-needs-clarifier");
    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(onLocaleChange).toHaveBeenCalledWith("en-SG");
  });

  it("lets the user continue a saved run with Aurora or revoke it", () => {
    const onContinue = vi.fn();
    const onRevoke = vi.fn();
    render(<PsychologySkillStudio skills={[skill]} skillRuns={[run]} selectedSkill={skill} skillAnswers={{}}
      skillConsent onRun={() => undefined} skillRetention="SAVE_RESULT" skillBusy={false} skillLocale="zh-CN"
      onLocaleChange={() => undefined} onSelectSkill={() => undefined} onAnswerChange={() => undefined}
      onRetentionChange={() => undefined} onConsentChange={() => undefined}
      onContinueWithAurora={onContinue} onRevokeRun={onRevoke} />);
    fireEvent.click(screen.getByRole("button", { name: "和 Aurora 继续谈" }));
    expect(onContinue).toHaveBeenCalledWith(run);
    fireEvent.click(screen.getByRole("button", { name: "撤回这次结果" }));
    expect(onRevoke).toHaveBeenCalledWith(1);
  });
});

describe("SkillSuggestionBanner", () => {
  it("lets the user open or dismiss Aurora's suggestion", () => {
    const onOpen = vi.fn();
    const onDismiss = vi.fn();
    render(<SkillSuggestionBanner suggestion={suggestion} locale="zh-CN" onOpen={onOpen} onDismiss={onDismiss} />);
    fireEvent.click(screen.getByRole("button", { name: "由我决定是否打开" }));
    expect(onOpen).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole("button", { name: "这次不要" }));
    expect(onDismiss).toHaveBeenCalledOnce();
  });
});
