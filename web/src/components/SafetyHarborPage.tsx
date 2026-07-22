import { useEffect, useState } from "react";
import type { Locale } from "../i18n";
import { SafetyResourceList } from "./SafetyResourceList";

// Port of src/main/resources/static/pages/safety-harbor.html into the AppShell (Phase 0,
// safety-critical): breathing exercise, 5-4-3-2-1 grounding, self-care suggestions and real crisis
// resources, freely reachable -- not gated behind a triggered safety alert, matching the legacy
// page's own framing ("你先安全，其他都先等等" / "you come first, everything else can wait").
const BREATH_HINTS: Record<Locale, string[]> = {
  "zh-CN": ["吸气...", "屏住...", "呼气...", "休息..."],
  "en-SG": ["Breathe in...", "Hold...", "Breathe out...", "Rest..."]
};

const GROUNDING: Record<Locale, Array<[string, string]>> = {
  "zh-CN": [
    ["你能看到的东西", "慢慢环顾四周，数出来。"],
    ["你能摸到的东西", "感受它们的温度和质地。"],
    ["你能听到的声音", "闭上眼睛，专注倾听。"],
    ["你能闻到的气味", "即使很淡的味道也算。"],
    ["你能尝到的味道", "可以喝一口水，感受它。"]
  ],
  "en-SG": [
    ["Something you can see", "Look slowly around you and count it."],
    ["Something you can touch", "Notice its temperature and texture."],
    ["Something you can hear", "Close your eyes and just listen."],
    ["Something you can smell", "Even a faint scent counts."],
    ["Something you can taste", "A sip of water works well."]
  ]
};

const CARE: Record<Locale, Array<[string, string]>> = {
  "zh-CN": [
    ["喝一杯温水", "慢慢喝，感受水的温度。"],
    ["打开窗户", "让新鲜空气进来。"],
    ["给信任的人发一条消息", "不需要说太多，“我需要你”就够了。"],
    ["放下手机五分钟", "什么都不要做，只是坐着。"]
  ],
  "en-SG": [
    ["Drink a glass of warm water", "Slowly, and notice its warmth."],
    ["Open a window", "Let some fresh air in."],
    ["Message someone you trust", "It doesn't need to be much -- “I need you” is enough."],
    ["Put your phone down for five minutes", "Do nothing at all. Just sit."]
  ]
};

const COPY = {
  "zh-CN": {
    routeHint: "你先安全，其他都先等等", heading: "安全避风港", sub: "这里没有任务、没有进度。你可以先停下来。",
    breathHeading: "呼吸练习", breathIntro: "跟着圆圈呼吸。吸气时圆圈变大，呼气时变小。",
    groundHeading: "着陆练习 (5-4-3-2-1)", groundIntro: "当你觉得不真实或焦虑上升时，试着找到：",
    careHeading: "你可以先做的事",
    resourcesHeading: "紧急资源", resourcesIntro: "如果你或身边的人正经历危机，请拨打以下电话。", dial: "拨打 ",
    talkToAurora: "和 Aurora 聊聊", back: "返回核心"
  },
  "en-SG": {
    routeHint: "You come first. Everything else can wait.", heading: "Safety Harbor", sub: "There is no task and no progress here. You can stop for a moment.",
    breathHeading: "Breathing exercise", breathIntro: "Breathe with the circle. It grows as you breathe in, and shrinks as you breathe out.",
    groundHeading: "5-4-3-2-1 grounding", groundIntro: "When things feel unreal or anxiety rises, try to find:",
    careHeading: "Things you can do right now",
    resourcesHeading: "Emergency resources", resourcesIntro: "If you or someone near you is in crisis, please call one of these.", dial: "Call ",
    talkToAurora: "Talk to Aurora", back: "Back to today"
  }
} as const;

export function SafetyHarborPage({ resources, locale, onBack, onTalkToAurora }: {
  resources: string[];
  locale: Locale;
  onBack: () => void;
  onTalkToAurora: () => void;
}) {
  const t = COPY[locale];
  const hints = BREATH_HINTS[locale];
  const [hintIndex, setHintIndex] = useState(0);
  useEffect(() => {
    const id = window.setInterval(() => setHintIndex(i => (i + 1) % hints.length), 3000);
    return () => window.clearInterval(id);
  }, [hints.length]);

  return <main className="safety-harbor" aria-label={t.heading} lang={locale}>
    <section className="safety-hero">
      <span className="route-hint">{t.routeHint}</span>
      <h1>{t.heading}</h1>
      <p>{t.sub}</p>
    </section>

    <section className="safety-section">
      <h2>{t.resourcesHeading}</h2>
      <p>{t.resourcesIntro}</p>
      <SafetyResourceList resources={resources} dialLabel={t.dial} />
    </section>

    <section className="safety-section">
      <h2>{t.breathHeading}</h2>
      <p>{t.breathIntro}</p>
      <div className="breath-area">
        <div className="breathing-circle" aria-hidden="true" />
        <p className="breath-label" aria-live="off">{hints[hintIndex]}</p>
      </div>
    </section>

    <section className="safety-section">
      <h2>{t.groundHeading}</h2>
      <p>{t.groundIntro}</p>
      <div className="grounding-list">
        {GROUNDING[locale].map(([label, hint], index) => <div className="grounding-item" key={label}>
          <span className="grounding-num" aria-hidden="true">{5 - index}</span>
          <div className="grounding-text"><strong>{label}</strong><p>{hint}</p></div>
        </div>)}
      </div>
    </section>

    <section className="safety-section">
      <h2>{t.careHeading}</h2>
      <div className="care-grid">
        {CARE[locale].map(([label, hint]) => <article className="care-card" key={label}>
          <strong>{label}</strong><p>{hint}</p>
        </article>)}
      </div>
    </section>

    <div className="safety-actions">
      <button type="button" className="button" onClick={onTalkToAurora}>{t.talkToAurora}</button>
      <button type="button" className="button quiet" onClick={onBack}>{t.back}</button>
    </div>
  </main>;
}
