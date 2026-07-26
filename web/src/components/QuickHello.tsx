import { useEffect, useState } from "react";
import type { Locale } from "../i18n";
import type { UserProfileSettings } from "../api";

type QuickHelloProps = {
  profile: UserProfileSettings;
  locale?: Locale;
  onSave: (patch: Partial<UserProfileSettings>) => Promise<boolean>;
  onBegin?: () => void;
};

const COPY = {
  "zh-CN": {
    eyebrow: "45–90 秒 · 可随时跳过",
    heading: "先让 Aurora 认识此刻的你",
    intro: "不做人格问卷。四个轻选择，让 Aurora 知道怎么靠近你、从哪里自然聊起。",
    tone: "你希望她怎么回应？",
    toneOptions: [["温柔安静", "温柔"], ["理性清晰", "清晰"], ["朋友式直接", "直接"]] as const,
    pace: "她可以多主动？",
    paceOptions: [[1, "少打扰"], [3, "刚刚好"], [5, "多关心"]] as const,
    depth: "聊到心事时",
    depthOptions: [[2, "先陪伴"], [4, "一起深挖"]] as const,
    topic: "最近什么更占据你？",
    topicOptions: [["创造与学业", "创造 / 学业"], ["关系与靠近", "关系"], ["变化与选择", "变化 / 选择"], ["照顾好自己", "照顾自己"]] as const,
    note: "最近的你，正在经历什么？（可选，一句话就好）",
    placeholder: "例如：刚换了工作，想找回自己的节奏",
    privacy: "只用于 Aurora 理解当下，不会公开展示。",
    skip: "暂时跳过", save: "就这样开始", saving: "正在记住…", error: "暂时没能保存，你仍可以直接开始对话。",
  },
  "en-SG": {
    eyebrow: "45–90 SEC · ALWAYS SKIPPABLE",
    heading: "Let Aurora meet you as you are now",
    intro: "No personality test. Four light choices tell Aurora how to meet you and where a real conversation might begin.",
    tone: "How should she respond?",
    toneOptions: [["温柔安静", "Gentle"], ["理性清晰", "Clear"], ["朋友式直接", "Direct"]] as const,
    pace: "How proactive can she be?",
    paceOptions: [[1, "Give me space"], [3, "Balanced"], [5, "Check in more"]] as const,
    depth: "When things get personal",
    depthOptions: [[2, "Stay with me"], [4, "Go deeper together"]] as const,
    topic: "What has your attention lately?",
    topicOptions: [["Making and study", "Making / study"], ["Relationships and closeness", "Relationships"], ["Change and choices", "Change / choices"], ["Taking care of myself", "My wellbeing"]] as const,
    note: "What season of life are you in? (optional, one line)",
    placeholder: "e.g. I just changed jobs and want to find my rhythm again",
    privacy: "Used only to help Aurora understand your present context; never shown publicly.",
    skip: "Skip for now", save: "Begin like this", saving: "Remembering…", error: "That did not save yet. You can still begin talking.",
  },
} satisfies Record<Locale, Record<string, unknown>>;

function onboardingKey(id: number): string {
  return `ic.quick-hello.${id}`;
}

function isFreshProfile(profile: UserProfileSettings): boolean {
  return !profile.auroraTone
    && profile.reflectionDepth == null
    && profile.proactiveSensitivity == null
    && !profile.currentEnvironmentLabel;
}

export function QuickHello({ profile, locale = "zh-CN", onSave, onBegin }: QuickHelloProps) {
  const t = COPY[locale];
  const [dismissed, setDismissed] = useState(() => {
    try { return localStorage.getItem(onboardingKey(profile.id)) === "done"; }
    catch { return false; }
  });
  const [tone, setTone] = useState("温柔安静");
  const [pace, setPace] = useState(3);
  const [depth, setDepth] = useState(2);
  const [topic, setTopic] = useState(locale === "en-SG" ? "Making and study" : "创造与学业");
  const [context, setContext] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    setTopic(locale === "en-SG" ? "Making and study" : "创造与学业");
  }, [locale]);

  if (dismissed || !isFreshProfile(profile)) return null;

  const finish = () => {
    try { localStorage.setItem(onboardingKey(profile.id), "done"); } catch { /* optional preference */ }
    setDismissed(true);
  };

  const save = async () => {
    setBusy(true);
    setError("");
    const saved = await onSave({
      auroraTone: tone,
      proactiveSensitivity: pace,
      reflectionDepth: depth,
      allowMemoryRecall: true,
      currentEnvironmentLabel: context.trim() ? `${topic} · ${context.trim()}` : topic,
    });
    setBusy(false);
    if (saved) {
      finish();
      onBegin?.();
    }
    else setError(t.error as string);
  };

  return <section className="quick-hello" aria-labelledby="quick-hello-title">
    <header>
      <div>
        <span className="eyebrow">{t.eyebrow as string}</span>
        <h2 id="quick-hello-title">{t.heading as string}</h2>
        <p>{t.intro as string}</p>
      </div>
      <button type="button" className="quick-hello-skip" onClick={finish}>{t.skip as string}</button>
    </header>

    <div className="quick-hello-choices">
      <fieldset><legend>{t.tone as string}</legend><div>
        {t.toneOptions.map(([value, label]) => <button type="button" key={value}
          aria-pressed={tone === value} onClick={() => setTone(value)}>{label}</button>)}
      </div></fieldset>
      <fieldset><legend>{t.pace as string}</legend><div>
        {t.paceOptions.map(([value, label]) => <button type="button" key={value}
          aria-pressed={pace === value} onClick={() => setPace(value)}>{label}</button>)}
      </div></fieldset>
      <fieldset><legend>{t.depth as string}</legend><div>
        {t.depthOptions.map(([value, label]) => <button type="button" key={value}
          aria-pressed={depth === value} onClick={() => setDepth(value)}>{label}</button>)}
      </div></fieldset>
      <fieldset><legend>{t.topic as string}</legend><div>
        {t.topicOptions.map(([value, label]) => <button type="button" key={value}
          aria-pressed={topic === value} onClick={() => setTopic(value)}>{label}</button>)}
      </div></fieldset>
    </div>

    <label className="quick-hello-context">
      <span>{t.note as string}</span>
      <input maxLength={120} value={context} placeholder={t.placeholder as string}
        onChange={event => setContext(event.target.value)} />
      <small>{t.privacy as string}</small>
    </label>
    {error && <p className="quick-hello-error" role="alert">{error}</p>}
    <button type="button" className="quick-hello-save" disabled={busy} onClick={() => void save()}>
      {busy ? t.saving as string : t.save as string}
    </button>
  </section>;
}
