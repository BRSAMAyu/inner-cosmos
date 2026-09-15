import { useEffect } from "react";
import type { UsageToday } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

// CP-08 使用时长: the user's own honest view of today's conversation time. A record, never a
// verdict — no lockouts, no streaks, no judgemental copy (same tone rule as the backend's
// UsageTimeService). Presentational only, data loaded by AuroraApp exactly like QuotaPanel.
//
// Placement choice (spec offered "conversation-page top strip" OR "account surface"): the
// account surface, directly beside QuotaPanel. A strip above the conversation would re-show the
// reminder on every view and fight the "calm, once, non-nagging" rule; the account tab is where
// the user goes to look at their own usage, so the number and the (backend-authored) reminder
// note appear exactly where they were sought and nowhere else.

const COPY: Record<Locale, {
  aria: string; eyebrow: string; heading: string;
  minutes: (n: number) => string; turns: (n: number) => string;
  basisNote: string; reminderTitle: string;
  errorTitle: string; retry: string; loading: string; reload: string;
}> = {
  "zh-CN": {
    aria: "我的今日对话时长",
    eyebrow: "时长 · 只是记录",
    heading: "今天的对话时间",
    minutes: n => `今天约 ${n} 分钟`,
    turns: n => `已完成 ${n} 轮对话`,
    basisNote: "按已完成对话轮次的时长累计，只是记录，不做评判。",
    // Rendered above the backend's own reminderNote sentence, verbatim.
    reminderTitle: "一条平静的提醒",
    errorTitle: "暂时无法读取今日时长。",
    retry: "重试",
    loading: "…",
    reload: "刷新"
  },
  "en-SG": {
    aria: "My conversation time today",
    eyebrow: "TIME, SIMPLY RECORDED",
    heading: "Conversation time today",
    minutes: n => `About ${n} minute${n === 1 ? "" : "s"} today`,
    turns: n => `${n} completed turn${n === 1 ? "" : "s"}`,
    basisNote: "Counted from completed conversation turns. A record, not a verdict.",
    reminderTitle: "A quiet check-in",
    errorTitle: "Today's time is temporarily unavailable.",
    retry: "Retry",
    loading: "…",
    reload: "Reload"
  }
};

/**
 * CP-08 web surface. "今天约 X 分钟" from activeSeconds; when reminderDue is true the backend's
 * reminderNote is shown verbatim in the same calm tone (never reworded or amplified client-side).
 * A failed load degrades to an inline alert with a retry — no fabricated zero.
 */
export function UsageTodayPanel({ view, loading, loaded, error, onLoad, locale = "zh-CN" }: {
  view: UsageToday | null;
  loading: boolean;
  loaded: boolean;
  error: string | null;
  onLoad: () => void;
  locale?: Locale;
}) {
  const t = COPY[locale];

  // Auto-load once on mount (QuotaPanel's pattern); error is part of the guard so a persistent
  // failure waits for an explicit retry instead of looping the request.
  useEffect(() => {
    if (!loaded && !loading && error === null) onLoad();
  }, [loaded, loading, error, onLoad]);

  return <section className="usage-today-panel" aria-label={t.aria} data-loaded={loaded}>
    <span className="eyebrow">{t.eyebrow}</span>
    <h3>{t.heading}</h3>

    {error !== null && <div className="quota-error" role="alert">
      <p>{t.errorTitle}</p>
      <AsyncButton busy={loading} onClick={onLoad}>{t.retry}</AsyncButton>
    </div>}

    {error === null && !loaded && <p className="muted" role="status">{t.loading}</p>}

    {error === null && loaded && view && <>
      <p className="usage-today-minutes">{t.minutes(Math.round(view.activeSeconds / 60))}</p>
      <p className="usage-today-turns">
        <small>{t.turns(view.turnCount)}</small>
      </p>
      <small className="usage-today-basis">{t.basisNote}</small>
      {view.reminderDue && view.reminderNote && <p className="usage-today-reminder" role="status">
        <em>{t.reminderTitle}</em>
        {view.reminderNote}
      </p>}
    </>}
  </section>;
}
