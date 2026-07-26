import { describe, expect, it } from "vitest";
import type { MemoryCard } from "./api";
import { capsuleDraftDefaults, journeyStepsFromFacts, latestSettledMemory } from "./newUserJourney";

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
