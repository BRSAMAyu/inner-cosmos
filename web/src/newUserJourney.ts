import type { MemoryCard } from "./api";
import type { JourneyStep } from "./components/StartHereJourney";
import type { Locale } from "./i18n";

export type JourneyFacts = {
  hasUserMessage: boolean;
  hasMemory: boolean;
  hasActiveCapsule: boolean;
  hasVisitorSession: boolean;
  hasResonantMatch: boolean;
  hasSentLetter: boolean;
};

/**
 * The onboarding journey is evidence-backed: every completed step comes from data already
 * returned by the real product APIs (or the current persisted Aurora session), never localStorage.
 */
export function journeyStepsFromFacts(facts: JourneyFacts): JourneyStep[] {
  const completed: JourneyStep[] = [];
  if (facts.hasUserMessage) completed.push("aurora");
  if (facts.hasMemory) completed.push("memory");
  if (facts.hasActiveCapsule) completed.push("capsule");
  if (facts.hasVisitorSession || facts.hasResonantMatch) completed.push("match");
  if (facts.hasSentLetter) completed.push("letter");
  return completed;
}

/** Prefer the newest card created by this settlement; tolerate APIs that return newest-first. */
export function latestSettledMemory(previous: MemoryCard[], refreshed: MemoryCard[]): MemoryCard | null {
  const previousIds = new Set(previous.map(card => card.id));
  const newlyCreated = refreshed.filter(card => !previousIds.has(card.id));
  if (newlyCreated.length > 0) {
    return newlyCreated.reduce((latest, card) => card.id > latest.id ? card : latest);
  }
  return refreshed[0] ?? null;
}

/**
 * A useful private draft should already be waiting after Capsule Shaping. The user still reviews
 * the authorization preview and explicitly compiles it; this helper never publishes anything.
 */
export function capsuleDraftDefaults(
  memory: Pick<MemoryCard, "title" | "summary"> | null,
  locale: Locale
): { name: string; intro: string } {
  const title = memory?.title?.trim();
  const summary = memory?.summary?.trim();
  if (locale === "en-SG") {
    return {
      name: (title ? `An echo of ${title}` : "A living facet of me").slice(0, 80),
      intro: (summary || "A private facet shaped from the conversation I just had with Aurora.").slice(0, 500)
    };
  }
  return {
    name: (title ? `${title}的回声` : "我的鲜活侧影").slice(0, 80),
    intro: (summary || "这是从我刚才与 Aurora 的对话中形成的私密侧影。").slice(0, 500)
  };
}
