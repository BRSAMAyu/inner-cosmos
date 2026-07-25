import { describe, expect, it } from "vitest";
import type { CapsuleMatch, PublicCapsule } from "./api";
import {
  isAutomatedDemoCapsule,
  userVisiblePublicCapsules,
  userVisibleResonanceMatches
} from "./demoFixtureVisibility";

const fixture: PublicCapsule = {
  id: 1,
  pseudonym: "Demo Echo 1784892728793",
  intro: "A consent-bound facet created by the public demo verifier.",
  capsuleType: "FACET",
  publicTags: "class demo,thoughtful support",
  echoEnergy: 1,
  freshnessScore: 1,
  conversationLimitPerDay: 30,
  lastActivityAt: null
};

const realCapsule: PublicCapsule = {
  ...fixture,
  id: 2,
  pseudonym: "星际旅人",
  intro: "在关系变化和任务挤压之间，先保留一点不确定性。"
};

describe("demo fixture visibility", () => {
  it("hides only the exact automated verifier fixture", () => {
    expect(isAutomatedDemoCapsule(fixture)).toBe(true);
    expect(isAutomatedDemoCapsule(realCapsule)).toBe(false);
    expect(userVisiblePublicCapsules([fixture, realCapsule])).toEqual([realCapsule]);
  });

  it("removes verifier fixtures from user-facing resonance candidates", () => {
    const toMatch = (capsule: PublicCapsule): CapsuleMatch => ({
      capsule,
      matchScore: 0,
      matchReasons: [],
      matchSummary: "",
      resonant: false,
      strategy: "MIRROR",
      strategyLabel: "相似共鸣",
      strategyDescription: ""
    });

    expect(userVisibleResonanceMatches([toMatch(fixture), toMatch(realCapsule)]))
      .toEqual([toMatch(realCapsule)]);
  });
});
