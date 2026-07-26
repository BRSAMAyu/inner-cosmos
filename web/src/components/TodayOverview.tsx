import type { Locale } from "../i18n";

export function TodayOverview({ memoryCount, latestMemory, arrivedLetters, latestLetter,
  publicCapsules, wakeIntents, onOpenCosmos, onOpenLetters, onOpenResonance, onWriteLetter,
  onOpenReturns, locale = "zh-CN" }: {
  memoryCount: number; latestMemory: string | null;
  arrivedLetters: number; latestLetter: string | null;
  publicCapsules: number; wakeIntents: number;
  onOpenCosmos: () => void; onOpenLetters: () => void;
  onOpenResonance: () => void; onWriteLetter: () => void;
  onOpenReturns: () => void;
  locale?: Locale;
}) {
  const en = locale === "en-SG";
  const localisedLatestMemory = en && latestMemory === "今日沉淀"
    ? "Today's reflection"
    : latestMemory;
  return <section className="today-overview" aria-label={en ? "Your Inner Cosmos today" : "今天的内宇宙概览"}>
    <div className="today-overview-heading">
      <div><span className="eyebrow">{en ? "YOUR COSMOS, TODAY" : "今日 · 内宇宙"}</span>
        <h2>{en ? "Nothing important is buried." : "重要的变化，不必再翻很久才能看见"}</h2></div>
      <button type="button" className="today-write-letter" onClick={onWriteLetter}>
        {en ? "Write a slow letter" : "写一封慢信"}
      </button>
    </div>
    <div className="today-overview-grid">
      <button type="button" onClick={onOpenCosmos}>
        <span>{en ? "Memory alive" : "正在生长的记忆"}</span>
        <strong>{memoryCount}</strong>
        <small>{localisedLatestMemory ?? (en ? "Start by telling Aurora what happened today." : "从告诉 Aurora 今天发生了什么开始")}</small>
        <em>{en ? "Open the cosmos" : "进入内宇宙 →"}</em>
      </button>
      <button type="button" onClick={onOpenLetters}>
        <span>{en ? "Letters arrived" : "抵达的慢信"}</span>
        <strong>{arrivedLetters}</strong>
        <small>{latestLetter ?? (en ? "No new arrival; waiting is part of the letter." : "还没有新抵达；等待也是信的一部分")}</small>
        <em>{en ? "Read and reply" : "阅读与回信 →"}</em>
      </button>
      <button type="button" onClick={onOpenResonance}>
        <span>{en ? "Resonance in the world" : "在星海里的侧影"}</span>
        <strong>{publicCapsules}</strong>
        <small>{en ? "Meet a facet before deciding about a person." : "先遇见一个侧影，再决定要不要靠近一个人"}</small>
        <em>{en ? "Go to resonance" : "去共鸣广场 →"}</em>
      </button>
      <button type="button" onClick={onOpenReturns}>
        <span>{en ? "Aurora returns" : "Aurora 的回来约定"}</span>
        <strong>{wakeIntents}</strong>
        <small>{en ? "Several rhythms can coexist; each can be changed." : "可以同时保留多个生活节律，也可以随时调整"}</small>
        <em>{en ? "Review plans" : "查看约定 →"}</em>
      </button>
    </div>
  </section>;
}
