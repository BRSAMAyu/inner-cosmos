import type { AuroraContinuitySignal } from "../hooks/useAuroraSession";
import type { SkillLocale } from "./PsychologySkillStudio";

type Props = {
  signal: AuroraContinuitySignal | null;
  locale: SkillLocale;
  onDismiss: () => void;
};

const COPY = {
  "zh-CN": {
    eyebrow: "Aurora 连续性保护",
    recoveringTitle: "连接短暂中断，正在安全恢复",
    recoveredTitle: "恢复完成，消息与会话都没有丢失",
    interruptedTitle: "已恢复到中断位置",
    detail: "客户端正在通过另一实例重放持久化时间线。",
    recoveredDetail: "同一轮对话已从持久化时间线恢复，历史记录保持完整。",
    interruptedDetail: "已保留安全提交的内容，你可以从这里继续。",
    disconnected: "检测到连接中断",
    replaying: "从持久化时间线恢复",
    complete: "消息与会话完整",
    close: "关闭连续性状态"
  },
  "en-SG": {
    eyebrow: "Aurora continuity protection",
    recoveringTitle: "Connection interrupted — recovering safely",
    recoveredTitle: "Recovery complete — no message or history was lost",
    interruptedTitle: "Recovered to the interruption point",
    detail: "The client is replaying the durable timeline through another instance.",
    recoveredDetail: "The same turn was restored from durable state and conversation history remains intact.",
    interruptedDetail: "Safely committed content was preserved; you can continue from here.",
    disconnected: "Connection interruption detected",
    replaying: "Replaying the durable timeline",
    complete: "Messages and history intact",
    close: "Dismiss continuity status"
  }
} as const;

export function AuroraContinuityRecovery({ signal, locale, onDismiss }: Props) {
  if (!signal) return null;
  const t = COPY[locale];
  const recovered = signal.phase === "recovered";
  const interrupted = signal.phase === "interrupted";
  const title = recovered ? t.recoveredTitle : interrupted ? t.interruptedTitle : t.recoveringTitle;
  const detail = recovered ? t.recoveredDetail : interrupted ? t.interruptedDetail : t.detail;

  return (
    <aside className={`continuity-recovery ${signal.phase}`} role="status"
      aria-live="assertive" aria-atomic="true" data-testid="continuity-recovery">
      <header>
        <span className="continuity-recovery-mark" aria-hidden="true">◎</span>
        <div>
          <small>{t.eyebrow}</small>
          <strong>{title}</strong>
        </div>
        <button type="button" onClick={onDismiss} aria-label={t.close}>×</button>
      </header>
      <p>{detail}</p>
      <ol aria-label={title}>
        <li className="done"><i aria-hidden="true">✓</i><span>{t.disconnected}</span></li>
        <li className={signal.phase === "recovering" ? "active" : "done"}>
          <i aria-hidden="true">{signal.phase === "recovering" ? "↻" : "✓"}</i>
          <span>{t.replaying}</span>
        </li>
        <li className={recovered ? "done" : interrupted ? "safe" : "pending"}>
          <i aria-hidden="true">{recovered ? "✓" : interrupted ? "•" : "○"}</i>
          <span>{t.complete}</span>
        </li>
      </ol>
    </aside>
  );
}
