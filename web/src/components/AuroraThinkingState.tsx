import type { Locale } from "../i18n";

export type AuroraThinkingStage = "understanding" | "composing" | "speaking";

const COPY: Record<Locale, {
  aria: string;
  stop: string;
  depth: string;
  sourceLabel: string;
  realFast: string;
  basicFallback: string;
  phases: [string, string, string];
  stage: Record<AuroraThinkingStage, { kicker: string; title: string; detail: string }>;
}> = {
  "zh-CN": {
    aria: "Aurora 当前回应状态",
    stop: "先停一下",
    depth: "更深一层的理解正在同步",
    sourceLabel: "这条即时反馈来自哪里？",
    realFast: "真实模型的快速确认",
    basicFallback: "基础状态回应；不代表模型已经理解",
    phases: ["听见", "梳理", "抵达"],
    stage: {
      understanding: {
        kicker: "AURORA · 正在听",
        title: "正在理解你刚才说的话",
        detail: "你可以继续补充；新消息会让 Aurora 停下当前回应，先看你刚发的内容。"
      },
      composing: {
        kicker: "AURORA · 正在组织回应",
        title: "正在整理重点",
        detail: "你仍可继续发送或点“先停一下”。"
      },
      speaking: {
        kicker: "AURORA · 正在回复",
        title: "回应正在生成",
        detail: "如果你想补充，直接发送新消息；Aurora 会停止当前回复并重新理解。"
      }
    }
  },
  "en-SG": {
    aria: "Aurora response state",
    stop: "Pause here",
    depth: "A deeper layer of understanding is syncing",
    sourceLabel: "Where did this immediate feedback come from?",
    realFast: "Fast acknowledgement from the live model",
    basicFallback: "Basic status response; it does not claim model understanding",
    phases: ["Heard", "Gathering", "Arriving"],
    stage: {
      understanding: {
        kicker: "AURORA · LISTENING",
        title: "Understanding what you just said",
        detail: "You can keep adding context. A new message stops the current response and takes priority."
      },
      composing: {
        kicker: "AURORA · ORGANISING A RESPONSE",
        title: "Pulling the main points together",
        detail: "You can still send another message or pause the response."
      },
      speaking: {
        kicker: "AURORA · REPLYING",
        title: "Generating the response",
        detail: "Send a new message to stop this reply and let Aurora reconsider."
      }
    }
  }
};

export function AuroraThinkingState({ stage, runtime = "single", locale = "zh-CN",
  acknowledgement, acknowledgementSource, onStop }: {
  stage: AuroraThinkingStage;
  runtime?: "single" | "dual";
  locale?: Locale;
  acknowledgement?: string;
  acknowledgementSource?: string;
  onStop: () => void;
}) {
  const t = COPY[locale];
  const activeIndex = stage === "understanding" ? 0 : stage === "composing" ? 1 : 2;
  const copy = t.stage[stage];

  return <article className={`aurora-thinking-state ${stage}`} role="status"
    aria-live="polite" aria-label={t.aria}>
    <div className="aurora-thinking-presence" aria-hidden="true">
      <span className="aurora-thinking-halo halo-one" />
      <span className="aurora-thinking-halo halo-two" />
      <span className="aurora-thinking-core" />
      <span className="aurora-thinking-comet" />
    </div>
    <div className="aurora-thinking-copy">
      <span className="aurora-thinking-kicker">{copy.kicker}</span>
      <strong>{copy.title}</strong>
      <p>{acknowledgement || copy.detail}</p>
      <div className="aurora-thinking-path" aria-hidden="true">
        {t.phases.map((phase, index) => <span key={phase}
          className={index < activeIndex ? "complete" : index === activeIndex ? "active" : ""}>
          <i />{phase}
        </span>)}
      </div>
      {runtime === "dual" && <small className="aurora-thinking-depth">{t.depth}</small>}
      {acknowledgementSource && <details className="aurora-thinking-source">
        <summary>{t.sourceLabel}</summary>
        <small>{acknowledgementSource === "model-fast" ? t.realFast : t.basicFallback}</small>
      </details>}
    </div>
    <button type="button" className="aurora-thinking-stop" onClick={onStop}>{t.stop}</button>
  </article>;
}
