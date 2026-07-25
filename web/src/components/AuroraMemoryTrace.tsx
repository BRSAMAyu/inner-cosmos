import type { MemoryCard } from "../api";
import type { AuroraMemoryTrace as MemoryTrace } from "../hooks/useAuroraSession";
import type { Locale } from "../i18n";

const COPY: Record<Locale, {
  aria: string; eyebrow: string; title: string; exact: string; contextual: string;
  open: string; dismiss: string; theme: (value: string) => string;
}> = {
  "zh-CN": {
    aria: "Aurora 本轮使用的记忆",
    eyebrow: "MEMORY ECHO · 本轮依据",
    title: "Aurora 刚才想起了",
    exact: "这些是这轮回应实际调用的记忆，不是装饰性的推荐。你可以回到来源，纠正或让它不再参与。",
    contextual: "这轮使用了你的长期上下文，但没有把依据锁定到单独一颗星。它不会被包装成确定事实。",
    open: "查看来源与修正",
    dismiss: "收起这次记忆回声",
    theme: value => `这一轮的主题：${value}`
  },
  "en-SG": {
    aria: "Memories Aurora used this turn",
    eyebrow: "MEMORY ECHO · THIS TURN",
    title: "Aurora remembered",
    exact: "These memories were actually retrieved for this reply. Open the source to correct them or stop their future use.",
    contextual: "This turn used your longer-term context, but did not pin the basis to one star. It is not presented as a settled fact.",
    open: "View source & correct",
    dismiss: "Fold away this memory echo",
    theme: value => `This turn's theme: ${value}`
  }
};

export function AuroraMemoryTrace({ trace, memories, locale = "zh-CN", onOpenMemory, onDismiss }: {
  trace: MemoryTrace | null;
  memories: MemoryCard[];
  locale?: Locale;
  onOpenMemory: (id: number) => void;
  onDismiss: () => void;
}) {
  if (!trace) return null;
  const t = COPY[locale];
  const byId = new Map(memories.map(memory => [memory.id, memory]));
  const referenced = trace.referencedMemoryIds
    .map(id => ({ id, memory: byId.get(id) }))
    .filter((entry): entry is { id: number; memory: MemoryCard } => Boolean(entry.memory));

  return <aside className="aurora-memory-trace" aria-label={t.aria}>
    <div className="aurora-memory-trace-head">
      <div>
        <span className="eyebrow">{t.eyebrow}</span>
        <strong>{t.title}</strong>
      </div>
      <button type="button" className="quiet" onClick={onDismiss} aria-label={t.dismiss}>×</button>
    </div>
    <p>{referenced.length > 0 ? t.exact : t.contextual}</p>
    {trace.detectedTheme && <small>{t.theme(trace.detectedTheme)}</small>}
    {referenced.length > 0 && <div className="aurora-memory-trace-list">
      {referenced.map(({ id, memory }) => <button type="button" key={id} onClick={() => onOpenMemory(id)}>
        <span>{memory.memoryLayer === "SEMANTIC"
          ? (locale === "en-SG" ? "Long-term understanding" : "长期理解")
          : (locale === "en-SG" ? "Lived moment" : "经历片段")}</span>
        <strong>{memory.title}</strong>
        <em>{t.open} →</em>
      </button>)}
    </div>}
  </aside>;
}
