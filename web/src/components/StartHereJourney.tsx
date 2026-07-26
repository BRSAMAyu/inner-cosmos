import { useState } from "react";
import type { Locale } from "../i18n";

export type JourneyStep = "aurora" | "memory" | "capsule" | "match" | "letter";

type JourneyCopy = {
  aria: string;
  eyebrow: string;
  title: string;
  intro: string;
  expand: string;
  collapse: string;
  progress: (done: number) => string;
  complete: string;
  current: string;
  steps: Array<{ id: JourneyStep; name: string; value: string; action: string }>;
};

const COPY: Record<Locale, JourneyCopy> = {
  "zh-CN": {
    aria: "从这里开始：完整旅程",
    eyebrow: "从这里开始",
    title: "一次倾诉，怎样慢慢走向真实连接",
    intro: "五步都由你决定；记忆默认私密，公开与联系从不自动发生。",
    expand: "展开五步旅程",
    collapse: "收起旅程",
    progress: done => `已完成 ${done}/5`,
    complete: "已完成",
    current: "下一步",
    steps: [
      { id: "aurora", name: "和 Aurora 说", value: "先说出此刻真实发生的事，让它被认真听见。", action: "开始倾诉" },
      { id: "memory", name: "留下记忆", value: "有意义的片刻会成为私密、可追溯的记忆。", action: "查看记忆" },
      { id: "capsule", name: "编织共鸣体", value: "只选择愿意让一个抽象侧影承载的部分。", action: "塑造侧影" },
      { id: "match", name: "遇见共鸣", value: "先因经历和节律靠近，再决定是否认识彼此。", action: "寻找共鸣" },
      { id: "letter", name: "写一封慢信", value: "让联系带着边界和时间抵达，不催促即时回应。", action: "写慢信" }
    ]
  },
  "en-SG": {
    aria: "Start here: the complete journey",
    eyebrow: "START HERE",
    title: "How one honest moment can grow into real connection",
    intro: "You decide at every step. Memories stay private; sharing and contact never happen automatically.",
    expand: "Expand the five-step journey",
    collapse: "Collapse journey",
    progress: done => `${done}/5 complete`,
    complete: "Complete",
    current: "Up next",
    steps: [
      { id: "aurora", name: "Talk with Aurora", value: "Begin with what is real right now, and let it be heard carefully.", action: "Start talking" },
      { id: "memory", name: "Keep a memory", value: "A meaningful moment becomes private, traceable memory.", action: "See memories" },
      { id: "capsule", name: "Shape a capsule", value: "Choose only what a safe, abstracted facet may carry.", action: "Shape a facet" },
      { id: "match", name: "Meet resonance", value: "Connect through lived experience before deciding to know each other.", action: "Find resonance" },
      { id: "letter", name: "Write slowly", value: "Let contact arrive with boundaries and time, without demanding an instant reply.", action: "Write a slow letter" }
    ]
  }
};

export function StartHereJourney({
  locale = "zh-CN",
  isDemoSandbox = false,
  demoPresentation = "collapsed",
  completedSteps = [],
  onStep
}: {
  locale?: Locale;
  isDemoSandbox?: boolean;
  demoPresentation?: "collapsed" | "hidden";
  completedSteps?: JourneyStep[];
  onStep: (step: JourneyStep) => void;
}) {
  const [expanded, setExpanded] = useState(!isDemoSandbox);
  const t = COPY[locale];
  const completed = new Set(completedSteps);
  const firstIncomplete = t.steps.find(step => !completed.has(step.id))?.id;

  if (isDemoSandbox && demoPresentation === "hidden") return null;

  return <section className="start-here-journey" aria-label={t.aria} lang={locale}>
    <div className="start-here-heading">
      <div>
        <span className="eyebrow">{t.eyebrow}</span>
        <strong>{t.title}</strong>
        <p>{t.intro}</p>
      </div>
      <button type="button" className="quiet" aria-label={expanded ? t.collapse : t.expand} aria-expanded={expanded}
        aria-controls="start-here-journey-steps" onClick={() => setExpanded(value => !value)}>
        <span className="journey-progress">{t.progress(completed.size)}</span>
        {expanded ? t.collapse : t.expand}
      </button>
    </div>
    <ol id="start-here-journey-steps" className="start-here-steps" hidden={!expanded}>
      {t.steps.map((step, index) => {
        const isComplete = completed.has(step.id);
        const isCurrent = firstIncomplete === step.id;
        return <li key={step.id} className={isComplete ? "complete" : isCurrent ? "current" : undefined}>
        <span className="start-here-number" aria-hidden="true">{isComplete ? "✓" : index + 1}</span>
        <div><strong>{step.name}</strong><p>{step.value}</p></div>
        <button type="button" aria-label={`${step.action} · ${isComplete ? t.complete : isCurrent ? t.current : ""}`}
          onClick={() => onStep(step.id)}>
          {isComplete ? t.complete : isCurrent ? `${t.current} · ${step.action}` : step.action}<span aria-hidden="true"> →</span>
        </button>
      </li>;
      })}
    </ol>
  </section>;
}
