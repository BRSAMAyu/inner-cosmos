import { describe, expect, it } from "vitest";
import type { ConsentView, MemoryCard } from "./api";
import {
  capsuleDraftDefaults,
  consentAskLiveView,
  journeyStepsFromFacts,
  latestSettledMemory,
  onboardingConsentAsks,
  onboardingQuizzesPassed,
  quizAnsweredCorrectly,
  remainingQuizCount,
  stepQuizPassed
} from "./newUserJourney";

const memory = (id: number, title = `Memory ${id}`, summary: string | null = `Summary ${id}`): MemoryCard => ({
  id,
  title,
  summary,
  status: "ACTIVE",
  versionNo: 1,
  consentScope: "CAPSULE_ALLOWED",
  memoryLayer: "EPISODIC",
  confidence: 0.8
});

describe("new-user journey evidence", () => {
  it("does not claim progress when no real product state exists", () => {
    expect(journeyStepsFromFacts({
      hasUserMessage: false,
      hasMemory: false,
      hasActiveCapsule: false,
      hasVisitorSession: false,
      hasResonantMatch: false,
      hasSentLetter: false
    })).toEqual([]);
  });

  it("derives every completed step from loaded session and API facts", () => {
    expect(journeyStepsFromFacts({
      hasUserMessage: true,
      hasMemory: true,
      hasActiveCapsule: true,
      hasVisitorSession: false,
      hasResonantMatch: true,
      hasSentLetter: true
    })).toEqual(["aurora", "memory", "capsule", "match", "letter"]);
  });

  it("counts a real visitor session as the match step even before a refreshed match list", () => {
    expect(journeyStepsFromFacts({
      hasUserMessage: false,
      hasMemory: false,
      hasActiveCapsule: false,
      hasVisitorSession: true,
      hasResonantMatch: false,
      hasSentLetter: false
    })).toEqual(["match"]);
  });
});

describe("Capsule Shaping handoff", () => {
  it("selects the newest card created by this settlement", () => {
    expect(latestSettledMemory([memory(1)], [memory(3), memory(1), memory(2)])?.id).toBe(3);
  });

  it("falls back to the API's newest-first card when settlement updates an existing card", () => {
    expect(latestSettledMemory([memory(1)], [memory(1)])?.id).toBe(1);
  });

  it("prefills a localized private draft without any publish decision", () => {
    expect(capsuleDraftDefaults(memory(2, "Starting again", "I can begin more gently."), "en-SG")).toEqual({
      name: "An echo of Starting again",
      intro: "I can begin more gently."
    });
    expect(capsuleDraftDefaults(null, "zh-CN")).toEqual({
      name: "我的鲜活侧影",
      intro: "这是从我刚才与 Aurora 的对话中形成的私密侧影。"
    });
  });
});

describe("J01 progressive consent + comprehension quizzes", () => {
  const asks = onboardingConsentAsks("zh-CN");
  const byId = (id: string) => asks.find(ask => ask.id === id)!;

  it("anchors each ask at the guide step where the permission first matters", () => {
    expect(asks.map(ask => [ask.id, ask.stepDestination])).toEqual([
      ["egress-understanding", "aurora"],
      ["capsule-withdrawal", "resonance"],
      ["voice-understanding", "voice"]
    ]);
  });

  it("zh fallback consent texts mirror the backend ConsentPurpose registry verbatim", () => {
    // Source of truth: src/main/java/com/innercosmos/service/consent/ConsentPurpose.java.
    expect(byId("egress-understanding").consent).toMatchObject({
      purposeCode: "AI_PROVIDER_EGRESS",
      description: "将你的对话内容发送到所选的境内大模型服务以生成回应",
      withdrawalEffect: "拒绝后 AI 回应功能不可用；本地功能与数据权利不受影响"
    });
    expect(byId("capsule-withdrawal").consent).toMatchObject({
      purposeCode: "PUBLIC_DISCOVERABLE",
      withdrawalEffect: "撤回后立即不可见"
    });
    expect(byId("voice-understanding").consent).toMatchObject({
      purposeCode: "VOICE_PROCESSING",
      withdrawalEffect: "拒绝后语音功能停用，文字功能不受影响"
    });
  });

  it("every quiz correct answer restates the real backend withdrawal semantics", () => {
    // AI_PROVIDER_EGRESS withdrawalEffect → egress quiz.
    expect(byId("egress-understanding").quiz.options.find(option => option.correct)!.label)
      .toBe("AI 回应功能不可用，但本地功能与数据权利不受影响");
    // CAPSULE_COMPILE withdrawalEffect ("撤回对应授权，共鸣体下线并失效派生物") → resonance quiz.
    expect(byId("capsule-withdrawal").quiz.options.find(option => option.correct)!.label)
      .toBe("对应的共鸣体下线，由它派生的内容一并失效");
    // VOICE_PROCESSING withdrawalEffect → voice quiz.
    expect(byId("voice-understanding").quiz.options.find(option => option.correct)!.label)
      .toBe("语音功能停用，文字功能不受影响");
  });

  it("each quiz has exactly one correct option and educational feedback for every wrong one", () => {
    for (const ask of asks) {
      const correct = ask.quiz.options.filter(option => option.correct);
      expect(correct, ask.id).toHaveLength(1);
      for (const option of ask.quiz.options.filter(option => !option.correct)) {
        expect(option.wrongFeedback.length, `${ask.id}/${option.key}`).toBeGreaterThan(8);
        expect(option.wrongFeedback, `${ask.id}/${option.key}`).toMatch(/说明|guide|above|explanation/);
      }
    }
  });

  it("keeps zh and en asks structurally identical (ids, anchors, correct keys, registry parity)", () => {
    const en = onboardingConsentAsks("en-SG");
    expect(en.map(ask => ask.id)).toEqual(asks.map(ask => ask.id));
    expect(en.map(ask => ask.stepDestination)).toEqual(asks.map(ask => ask.stepDestination));
    for (let index = 0; index < asks.length; index += 1) {
      const zhOptions = asks[index].quiz.options.map(option => [option.key, option.correct] as const);
      expect(en[index].quiz.options.map(option => [option.key, option.correct] as const))
        .toEqual(zhOptions);
      expect(en[index].consent?.purposeCode).toBe(asks[index].consent?.purposeCode);
    }
  });

  it("gates: unanswered or wrong answers never pass; all three correct passes", () => {
    const correctKeys = Object.fromEntries(
      asks.map(ask => [ask.id, ask.quiz.options.find(option => option.correct)!.key]));
    expect(onboardingQuizzesPassed(asks, {})).toBe(false);
    expect(remainingQuizCount(asks, {})).toBe(3);
    // Wrong on the first ask only.
    expect(onboardingQuizzesPassed(asks, { "egress-understanding": "b" })).toBe(false);
    expect(remainingQuizCount(asks, { "egress-understanding": "b" })).toBe(3);
    // Right on the first ask: that step passes, but the guide as a whole does not.
    const oneRight = { "egress-understanding": correctKeys["egress-understanding"] };
    expect(stepQuizPassed(asks, "aurora", oneRight)).toBe(true);
    expect(stepQuizPassed(asks, "resonance", oneRight)).toBe(false);
    expect(onboardingQuizzesPassed(asks, oneRight)).toBe(false);
    expect(remainingQuizCount(asks, oneRight)).toBe(2);
    // All three right.
    expect(onboardingQuizzesPassed(asks, correctKeys)).toBe(true);
    expect(remainingQuizCount(asks, correctKeys)).toBe(0);
  });

  it("quizAnsweredCorrectly only accepts the exact correct option key", () => {
    const ask = byId("egress-understanding");
    expect(quizAnsweredCorrectly(ask, undefined)).toBe(false);
    expect(quizAnsweredCorrectly(ask, "b")).toBe(false);
    expect(quizAnsweredCorrectly(ask, "c")).toBe(false);
    expect(quizAnsweredCorrectly(ask, "a")).toBe(true);
  });

  it("prefers the live consent-center view by purpose code for server-authoritative text", () => {
    const views: ConsentView[] = [{
      purposeCode: "AI_PROVIDER_EGRESS", group: "OPTIONAL_ASK", granted: true, userSettable: true,
      description: "live description", withdrawalEffect: "live withdrawal",
      version: "PV-2026-09", source: "USER"
    }];
    expect(consentAskLiveView(views, "AI_PROVIDER_EGRESS")?.withdrawalEffect).toBe("live withdrawal");
    expect(consentAskLiveView(views, "VOICE_PROCESSING")).toBeUndefined();
    expect(consentAskLiveView(null, "AI_PROVIDER_EGRESS")).toBeUndefined();
  });
});
