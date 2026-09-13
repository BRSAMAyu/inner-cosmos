import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ConsentView } from "../api";
import { onboardingConsentAsks } from "../newUserJourney";
import { GuideCenter, OnboardingGuide, hasCompletedOnboarding } from "./OnboardingGuide";

// Live consent-center fixtures shaped exactly like GET /api/me/consents rows. The
// AI_PROVIDER_EGRESS row carries the registry-verbatim text so live-vs-fallback rendering
// can be told apart from the static fallback by the PUBLIC_DISCOVERABLE row below.
function consentViews(egressGranted = false): ConsentView[] {
  const row = (purposeCode: string, group: ConsentView["group"], granted: boolean,
    extra: Partial<ConsentView> = {}): ConsentView => ({
    purposeCode, group, granted, userSettable: true,
    description: `（注册表文案）${purposeCode}`,
    withdrawalEffect: `（撤回效果）${purposeCode}`,
    version: "PV-2026-09", source: "DEFAULT", ...extra
  });
  return [
    row("CORE_SERVICE", "REQUIRED", true),
    row("AI_PROVIDER_EGRESS", "OPTIONAL_ASK", egressGranted),
    row("PUBLIC_DISCOVERABLE", "OPTIONAL", false),
    row("VOICE_PROCESSING", "SENSITIVE", false)
  ];
}

function renderGuide({
  loadError = false, decideResult = "resolve",
  load
}: {
  loadError?: boolean; decideResult?: "resolve" | "reject";
  load?: () => Promise<ConsentView[]>;
} = {}) {
  const onClose = vi.fn();
  const onNavigate = vi.fn();
  const loadConsents = vi.fn(load !== undefined
    ? load
    : loadError
      ? () => Promise.reject(new Error("offline"))
      : () => Promise.resolve(consentViews()));
  const decideConsent = vi.fn(decideResult === "resolve"
    ? (_purposeCode: string, _grant: boolean) => Promise.resolve(consentViews()[0])
    : (_purposeCode: string, _grant: boolean) => Promise.reject(new Error("offline")));
  const view = render(<OnboardingGuide open userId={42} onClose={onClose} onNavigate={onNavigate}
    loadConsents={loadConsents} decideConsent={decideConsent} />);
  return { onClose, onNavigate, loadConsents, decideConsent, rerender: view.rerender };
}

/** Waits until the live consent chip has rendered the server state (not the loading dots). */
async function awaitConsentChip(expected: string) {
  await waitFor(() => expect(screen.getByText(/当前状态：/)).toHaveTextContent(expected));
}

/** Picks the correct option of the quiz anchored on the given step (zh copy). */
function answerQuiz(destination: "aurora" | "resonance" | "voice") {
  const ask = onboardingConsentAsks("zh-CN").find(item => item.stepDestination === destination)!;
  const correct = ask.quiz.options.find(option => option.correct)!;
  fireEvent.click(screen.getByRole("radio", { name: correct.label }));
}

/** Picks the first wrong option and returns it (for educational-feedback assertions). */
function pickWrong(destination: "aurora" | "resonance" | "voice") {
  const ask = onboardingConsentAsks("zh-CN").find(item => item.stepDestination === destination)!;
  const wrong = ask.quiz.options.find(option => !option.correct)!;
  fireEvent.click(screen.getByRole("radio", { name: wrong.label }));
  return wrong;
}

describe("OnboardingGuide", () => {
  beforeEach(() => localStorage.clear());
  afterEach(cleanup);

  it("walks a new user through the core journey and persists completion", () => {
    const { onClose, onNavigate } = renderGuide();
    expect(screen.getByRole("dialog", { name: "欢迎来到 Inner Cosmos" })).toBeVisible();
    // Steps 0/2/4 are gated by their comprehension quizzes; 1/3 are plain.
    answerQuiz("aurora");
    fireEvent.click(screen.getByRole("button", { name: "继续" }));
    fireEvent.click(screen.getByRole("button", { name: "继续" }));
    answerQuiz("resonance");
    fireEvent.click(screen.getByRole("button", { name: "继续" }));
    fireEvent.click(screen.getByRole("button", { name: "继续" }));
    answerQuiz("voice");
    fireEvent.click(screen.getByRole("button", { name: "打开设置" }));
    expect(hasCompletedOnboarding(42)).toBe(true);
    expect(onClose).toHaveBeenCalledOnce();
    expect(onNavigate).toHaveBeenCalledWith("voice");
  });

  it("J01: blocks Continue on a gated step until the quiz is answered correctly", () => {
    renderGuide();
    const continueButton = screen.getByRole("button", { name: "继续" });
    expect(continueButton).toBeDisabled();
    const wrong = pickWrong("aurora");
    // Educational feedback, not a punishment: explains why the pick is wrong and offers re-reading.
    expect(screen.getByRole("status")).toHaveTextContent(wrong.wrongFeedback);
    expect(screen.getByRole("button", { name: "重看说明" })).toBeVisible();
    expect(continueButton).toBeDisabled();
    answerQuiz("aurora");
    expect(screen.getByRole("status")).toHaveTextContent("答对了");
    expect(continueButton).not.toBeDisabled();
  });

  it("J01: re-reading focuses the explanation card for another pass", () => {
    renderGuide();
    pickWrong("aurora");
    fireEvent.click(screen.getByRole("button", { name: "重看说明" }));
    expect(screen.getByText("此刻需要你知道：AI 回应如何产生").closest("article")).toHaveFocus();
  });

  it("J01: dot-jumping ahead cannot finish the guide with quizzes unanswered", () => {
    renderGuide();
    // Jump straight to the final step without answering anything.
    fireEvent.click(screen.getByRole("button", { name: "5. 把节奏交还给你" }));
    const finishButton = screen.getByRole("button", { name: "打开设置" });
    expect(finishButton).toBeDisabled();
    expect(screen.getByText("还有 3 道理解题未完成，答对后即可结束引导。")).toBeVisible();
    answerQuiz("voice");
    expect(finishButton).toBeDisabled();
    expect(screen.getByText("还有 2 道理解题未完成，答对后即可结束引导。")).toBeVisible();
  });

  it("J01: closing via Later dismisses without marking onboarding complete (no completion shortcut)", () => {
    const { onClose } = renderGuide();
    fireEvent.click(screen.getByRole("button", { name: "稍后再说" }));
    expect(onClose).toHaveBeenCalledOnce();
    expect(hasCompletedOnboarding(42)).toBe(false);
  });

  it("J01: records a progressive consent decision through the existing endpoint and reflects the server truth", async () => {
    // The server truth flips only on the read AFTER the decision is recorded (read 1 = initial).
    let reads = 0;
    const { decideConsent, loadConsents } = renderGuide({
      load: () => { reads += 1; return Promise.resolve(consentViews(reads > 1)); }
    });
    await awaitConsentChip("未同意");
    fireEvent.click(screen.getByRole("button", { name: "同意: AI_PROVIDER_EGRESS" }));
    await waitFor(() => expect(decideConsent).toHaveBeenCalledExactlyOnceWith("AI_PROVIDER_EGRESS", true));
    // The chip only flips after the re-read of the server state confirms it.
    await awaitConsentChip("已同意");
    expect(loadConsents).toHaveBeenCalledTimes(2); // initial read + post-decision re-read
    expect(screen.getByText("已记录你的选择：同意")).toBeVisible();
  });

  it("J01: declining is an equally valid recorded choice", async () => {
    const { decideConsent } = renderGuide();
    fireEvent.click(screen.getByRole("button", { name: "暂不: AI_PROVIDER_EGRESS" }));
    await waitFor(() => expect(decideConsent).toHaveBeenCalledExactlyOnceWith("AI_PROVIDER_EGRESS", false));
    expect(screen.getByText("已记录你的选择：暂不")).toBeVisible();
    // Declining never blocks the guide — the quiz, not the choice, is the gate.
    answerQuiz("aurora");
    expect(screen.getByRole("button", { name: "继续" })).not.toBeDisabled();
  });

  it("J01: a failed decision is reported honestly and the chip keeps the last server truth", async () => {
    renderGuide({ decideResult: "reject" });
    await awaitConsentChip("未同意");
    fireEvent.click(screen.getByRole("button", { name: "暂不: AI_PROVIDER_EGRESS" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("这次选择未能记录"));
    expect(screen.getByText(/当前状态：/)).toHaveTextContent("未同意");
    expect(screen.queryByText(/已记录你的选择/)).not.toBeInTheDocument();
  });

  it("J01: falls back to registry-verbatim text and says so when the consent state cannot load", async () => {
    renderGuide({ loadError: true });
    await waitFor(() => expect(screen.getByText(/同意状态暂时无法读取/)).toBeVisible());
    // The fallback explanation mirrors the backend ConsentPurpose registry verbatim.
    expect(screen.getByText("将你的对话内容发送到所选的境内大模型服务以生成回应")).toBeVisible();
    expect(screen.getByText("拒绝后 AI 回应功能不可用；本地功能与数据权利不受影响")).toBeVisible();
  });

  it("J01: renders the asks and quizzes in English for en-SG", () => {
    render(<OnboardingGuide open userId={11} locale="en-SG" onClose={() => undefined} onNavigate={() => undefined}
      loadConsents={() => Promise.resolve(consentViews())}
      decideConsent={() => Promise.resolve(consentViews()[0])} />);
    expect(screen.getByText("Worth knowing now: how AI replies are made")).toBeVisible();
    expect(screen.getByRole("radio", {
      name: "AI replies are unavailable; local features and data rights are unaffected"
    })).toBeVisible();
    const wrong = onboardingConsentAsks("en-SG")[0].quiz.options.find(option => !option.correct)!;
    fireEvent.click(screen.getByRole("radio", { name: wrong.label }));
    expect(screen.getByRole("status")).toHaveTextContent(wrong.wrongFeedback);
    expect(screen.getByRole("button", { name: "Continue" })).toBeDisabled();
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
    const props = { userId: 7, onClose, onNavigate: () => undefined,
      loadConsents: () => Promise.resolve(consentViews()),
      decideConsent: () => Promise.resolve(consentViews()[0]) } as const;
    const { rerender } = render(<OnboardingGuide open {...props} />);
    expect(screen.getByRole("button", { name: "稍后再说" })).toHaveFocus();
    answerQuiz("aurora");
    fireEvent.click(screen.getByRole("button", { name: "继续" }));
    expect(screen.getByText("看见它如何成为记忆")).toBeVisible();
    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalledOnce();
    expect(hasCompletedOnboarding(7)).toBe(false);
    rerender(<OnboardingGuide open={false} {...props} />);
    expect(trigger).toHaveFocus();
    rerender(<OnboardingGuide open {...props} />);
    expect(screen.getByText("先从一句真话开始")).toBeVisible();
    trigger.remove();
  });

  it("reopened guides restart the comprehension quizzes from scratch", () => {
    const { rerender } = renderGuide();
    answerQuiz("aurora");
    expect(screen.getByRole("button", { name: "继续" })).not.toBeDisabled();
    // Close, then reopen (the GuideCenter replay path) — the quizzes start over.
    rerender(<OnboardingGuide open={false} userId={42} onClose={() => undefined} onNavigate={() => undefined}
      loadConsents={() => Promise.resolve(consentViews())}
      decideConsent={() => Promise.resolve(consentViews()[0])} />);
    rerender(<OnboardingGuide open userId={42} onClose={() => undefined} onNavigate={() => undefined}
      loadConsents={() => Promise.resolve(consentViews())}
      decideConsent={() => Promise.resolve(consentViews()[0])} />);
    expect(screen.getByRole("button", { name: "继续" })).toBeDisabled();
  });

  it("dismisses from the dimmed surface without marking onboarding complete", () => {
    const onClose = vi.fn();
    const { container } = render(<OnboardingGuide open userId={9} onClose={onClose} onNavigate={() => undefined}
      loadConsents={() => Promise.resolve(consentViews())}
      decideConsent={() => Promise.resolve(consentViews()[0])} />);
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
