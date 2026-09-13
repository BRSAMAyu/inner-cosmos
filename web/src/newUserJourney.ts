import type { ConsentView, MemoryCard } from "./api";
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

// ---------------------------------------------------------------------------
// CP-09/CP-10 · J01: progressive consent + permission-comprehension gates.
//
// Every consent ask appears at the guide step where the permission first
// matters (progressive, not a wall of checkboxes), and every quiz answer is
// derived from the backend ConsentPurpose registry's REAL semantics
// (src/main/java/com/innercosmos/service/consent/ConsentPurpose.java) — the
// correct option of each quiz restates that purpose's withdrawalEffect. The
// zh fallback description/withdrawalEffect strings are verbatim registry text
// so the guide stays honest even before GET /api/me/consents resolves.
// ---------------------------------------------------------------------------

export type OnboardingQuizOption = {
  key: string;
  label: string;
  correct: boolean;
  /** Educational feedback shown when this wrong option is picked — explains why it is
   *  wrong and points back to the explanation, never a bare "incorrect". */
  wrongFeedback: string;
};

export type OnboardingConsentAsk = {
  id: "egress-understanding" | "capsule-withdrawal" | "voice-understanding";
  /** Guide step (by destination) where the ask appears — the moment the permission matters. */
  stepDestination: "aurora" | "resonance" | "voice";
  /** Optional live consent decision recorded through the existing consent endpoints. */
  consent?: {
    purposeCode: "AI_PROVIDER_EGRESS" | "PUBLIC_DISCOVERABLE" | "VOICE_PROCESSING";
    title: string;
    /** Registry-verbatim (zh) fallback used until the live ConsentView arrives. */
    description: string;
    withdrawalEffect: string;
  };
  /** Shown when the permission is managed per-item elsewhere (capsule workbench). */
  managedNote?: string;
  quiz: {
    prompt: string;
    options: OnboardingQuizOption[];
    correctFeedback: string;
  };
};

const ASKS_ZH: OnboardingConsentAsk[] = [
  {
    id: "egress-understanding",
    stepDestination: "aurora",
    consent: {
      purposeCode: "AI_PROVIDER_EGRESS",
      title: "此刻需要你知道：AI 回应如何产生",
      description: "将你的对话内容发送到所选的境内大模型服务以生成回应",
      withdrawalEffect: "拒绝后 AI 回应功能不可用；本地功能与数据权利不受影响"
    },
    quiz: {
      prompt: "如果你选择「暂不」同意把对话内容发送给大模型服务，会出现什么结果？",
      options: [
        {
          key: "a",
          label: "AI 回应功能不可用，但本地功能与数据权利不受影响",
          correct: true,
          wrongFeedback: ""
        },
        {
          key: "b",
          label: "账号会被暂停，直到重新同意",
          correct: false,
          wrongFeedback: "拒绝一项可选授权不会暂停或限制你的账号——账号状态与这项选择无关。可以再看一遍上方的说明再作答。"
        },
        {
          key: "c",
          label: "已保存的记忆会被自动删除",
          correct: false,
          wrongFeedback: "不会。拒绝只影响 AI 回应是否产生；你的记忆与数据权利不受影响，可随时导出。再看一遍上方的说明。"
        }
      ],
      correctFeedback: "答对了：拒绝后 AI 回应功能不可用，本地功能与数据权利不受影响。你的选择随时可在同意中心更改。"
    }
  },
  {
    id: "capsule-withdrawal",
    stepDestination: "resonance",
    consent: {
      purposeCode: "PUBLIC_DISCOVERABLE",
      title: "被他人发现，是你单独授权的开关",
      description: "让你的授权侧面在星海广场被他人发现",
      withdrawalEffect: "撤回后立即不可见"
    },
    managedNote: "共鸣体的编译授权由你在共鸣体工作台里逐条记忆单独授予；撤回某条授权，对应共鸣体会下线并失效派生物。这个引导不代替你做这个决定。",
    quiz: {
      prompt: "当你撤回某条记忆用于共鸣体编译的授权后，会发生什么？",
      options: [
        {
          key: "a",
          label: "对应的共鸣体下线，由它派生的内容一并失效",
          correct: true,
          wrongFeedback: ""
        },
        {
          key: "b",
          label: "共鸣体只是暂时隐藏，别人仍能看到旧的派生内容",
          correct: false,
          wrongFeedback: "不是隐藏。撤回授权会让共鸣体下线并使派生物失效——包括已经存在的那些。再看一遍上方的说明。"
        },
        {
          key: "c",
          label: "撤回要到下个结算日才生效",
          correct: false,
          wrongFeedback: "撤回是即时生效的，不存在等待期。再看一遍上方的说明。"
        }
      ],
      correctFeedback: "答对了：撤回对应授权后，共鸣体下线并失效派生物——立即生效。"
    }
  },
  {
    id: "voice-understanding",
    stepDestination: "voice",
    consent: {
      purposeCode: "VOICE_PROCESSING",
      title: "语音是敏感信息，需要单独同意",
      description: "语音转文字与语音朗读（涉及声学特征处理）",
      withdrawalEffect: "拒绝后语音功能停用，文字功能不受影响"
    },
    quiz: {
      prompt: "如果你拒绝「语音处理」这项单独同意，会发生什么？",
      options: [
        {
          key: "a",
          label: "语音功能停用，文字功能不受影响",
          correct: true,
          wrongFeedback: ""
        },
        {
          key: "b",
          label: "连文字对话也无法使用",
          correct: false,
          wrongFeedback: "不会。拒绝只停用语音功能，文字功能不受影响。再看一遍上方的说明。"
        },
        {
          key: "c",
          label: "需要重新注册账号才能恢复",
          correct: false,
          wrongFeedback: "不需要。你随时可以在同意中心重新同意，可选功能即时恢复。再看一遍上方的说明。"
        }
      ],
      correctFeedback: "答对了：拒绝后语音功能停用，文字功能不受影响。这项同意随时可改。"
    }
  }
];

const ASKS_EN: OnboardingConsentAsk[] = [
  {
    id: "egress-understanding",
    stepDestination: "aurora",
    consent: {
      purposeCode: "AI_PROVIDER_EGRESS",
      title: "Worth knowing now: how AI replies are made",
      description: "Send your conversation content to the selected mainland model service to generate replies",
      withdrawalEffect: "If declined, AI replies are unavailable; local features and data rights are unaffected"
    },
    quiz: {
      prompt: "If you choose \"Not now\" on sending conversation content to the model service, what happens?",
      options: [
        {
          key: "a",
          label: "AI replies are unavailable; local features and data rights are unaffected",
          correct: true,
          wrongFeedback: ""
        },
        {
          key: "b",
          label: "Your account is suspended until you agree again",
          correct: false,
          wrongFeedback: "Declining an optional consent never suspends or restricts your account — the two are unrelated. Re-read the explanation above and answer again."
        },
        {
          key: "c",
          label: "Your saved memories are automatically deleted",
          correct: false,
          wrongFeedback: "No. Declining only affects whether AI replies are generated; your memories and data rights are unaffected and exportable. Re-read the explanation above."
        }
      ],
      correctFeedback: "Correct: if declined, AI replies are unavailable while local features and data rights are unaffected. You can change this any time in the consent centre."
    }
  },
  {
    id: "capsule-withdrawal",
    stepDestination: "resonance",
    consent: {
      purposeCode: "PUBLIC_DISCOVERABLE",
      title: "Being discovered by others is its own separate switch",
      description: "Make your authorised facets discoverable by others in the star-sea plaza",
      withdrawalEffect: "After withdrawal, immediately invisible"
    },
    managedNote: "Capsule compilation is authorised by you per memory inside the capsule workbench; withdrawing one takes that capsule offline and invalidates its derivatives. This guide never makes that decision for you.",
    quiz: {
      prompt: "After you withdraw a memory's authorisation for capsule compilation, what happens?",
      options: [
        {
          key: "a",
          label: "The capsule goes offline and its derived content is invalidated",
          correct: true,
          wrongFeedback: ""
        },
        {
          key: "b",
          label: "The capsule is only hidden; others can still see old derived content",
          correct: false,
          wrongFeedback: "Not hidden. Withdrawal takes the capsule offline and invalidates its derivatives — including ones that already exist. Re-read the explanation above."
        },
        {
          key: "c",
          label: "Withdrawal takes effect at the next settlement day",
          correct: false,
          wrongFeedback: "Withdrawal is effective immediately; there is no waiting period. Re-read the explanation above."
        }
      ],
      correctFeedback: "Correct: withdrawal takes the capsule offline and invalidates its derivatives — immediately."
    }
  },
  {
    id: "voice-understanding",
    stepDestination: "voice",
    consent: {
      purposeCode: "VOICE_PROCESSING",
      title: "Voice is sensitive data and needs separate consent",
      description: "Speech-to-text and spoken playback (acoustic-feature processing)",
      withdrawalEffect: "After declining, voice features are disabled; text features are unaffected"
    },
    quiz: {
      prompt: "If you decline the separate consent for voice processing, what happens?",
      options: [
        {
          key: "a",
          label: "Voice features are disabled; text features are unaffected",
          correct: true,
          wrongFeedback: ""
        },
        {
          key: "b",
          label: "Even text conversations stop working",
          correct: false,
          wrongFeedback: "No. Declining only disables voice features; text features are unaffected. Re-read the explanation above."
        },
        {
          key: "c",
          label: "You must re-register the account to restore it",
          correct: false,
          wrongFeedback: "Not needed. You can grant it again any time in the consent centre and optional features resume. Re-read the explanation above."
        }
      ],
      correctFeedback: "Correct: after declining, voice features are disabled while text features are unaffected. This consent can change at any time."
    }
  }
];

/** The three progressive asks, locale-paired (same ids, same step anchors, same correct keys). */
export function onboardingConsentAsks(locale: Locale): OnboardingConsentAsk[] {
  return locale === "en-SG" ? ASKS_EN : ASKS_ZH;
}

/** The option a user currently has selected for an ask (undefined = unanswered). */
export function quizOptionFor(
  ask: OnboardingConsentAsk,
  choice: string | undefined
): OnboardingQuizOption | undefined {
  return choice === undefined ? undefined : ask.quiz.options.find(option => option.key === choice);
}

/** True only when the option selected for this ask is the correct one. */
export function quizAnsweredCorrectly(
  ask: OnboardingConsentAsk,
  choice: string | undefined
): boolean {
  return quizOptionFor(ask, choice)?.correct === true;
}

/** The gate for leaving one guide step forward: every ask anchored on that step is answered right. */
export function stepQuizPassed(
  asks: OnboardingConsentAsk[],
  stepDestination: string,
  choices: Record<string, string>
): boolean {
  const anchored = asks.filter(ask => ask.stepDestination === stepDestination);
  return anchored.length === 0 || anchored.every(ask => quizAnsweredCorrectly(ask, choices[ask.id]));
}

/** The gate for completing the guide at all: all three comprehension quizzes answered right. */
export function onboardingQuizzesPassed(
  asks: OnboardingConsentAsk[],
  choices: Record<string, string>
): boolean {
  return asks.every(ask => quizAnsweredCorrectly(ask, choices[ask.id]));
}

/** How many comprehension quizzes still block completion (used for the honest "N remaining" hint). */
export function remainingQuizCount(
  asks: OnboardingConsentAsk[],
  choices: Record<string, string>
): number {
  return asks.filter(ask => !quizAnsweredCorrectly(ask, choices[ask.id])).length;
}

/** Prefer the live consent-center view's server-authoritative text/state over the static fallback. */
export function consentAskLiveView(
  views: ConsentView[] | null,
  purposeCode: string
): ConsentView | undefined {
  return views?.find(view => view.purposeCode === purposeCode);
}
