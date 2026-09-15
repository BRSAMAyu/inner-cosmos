import { useCallback, useEffect, useState } from "react";
import { api, ApiCodeError, type OutboxDeadLetterPage, type OutboxReplayResult } from "../api";
import type { Locale } from "../i18n";

const PAGE_SIZE = 20;

type DlqCopy = {
  aria: string; heading: string; note: string;
  disabledTitle: string; disabledNote: string;
  empty: string; emptyNote: string;
  loadFailed: string; unauthorized: string; retry: string; refreshing: string;
  colEventId: string; colType: string; colSummary: string; colAttempts: string;
  colLastError: string; colLastAttempt: string; colAction: string;
  replay: string; replayBusy: string;
  replayOkPrefix: string; replayFailedPrefix: string;
  prev: string; next: string;
  range: (from: number, to: number, total: number) => string;
  dash: string;
};

const COPY: Record<Locale, DlqCopy> = {
  "zh-CN": {
    aria: "事件外发死信队列", heading: "事件外发死信（DEAD）",
    note: "重试耗尽或没有注册消费者的事件会停在这里；重放只是把它们重新入队，能否处理成功由后台任务的真实结果决定。",
    disabledTitle: "事件外发未启用",
    disabledNote: "当前部署没有开启事务性 outbox（inner-cosmos.events.outbox.enabled=false），因此没有死信数据。",
    empty: "当前没有死信", emptyNote: "死信队列为空：没有 DEAD 状态的事件。",
    loadFailed: "死信数据暂时无法获取。", unauthorized: "需要管理员权限才能查看死信队列。",
    retry: "重试", refreshing: "正在加载…",
    colEventId: "事件 ID", colType: "类型", colSummary: "摘要", colAttempts: "重试次数",
    colLastError: "最后错误", colLastAttempt: "最后尝试", colAction: "操作",
    replay: "重放", replayBusy: "重放中…",
    replayOkPrefix: "已重新入队，当前状态 ", replayFailedPrefix: "重放失败：",
    prev: "上一页", next: "下一页",
    range: (from, to, total) => `第 ${from}–${to} 条，共 ${total} 条`,
    dash: "—"
  },
  "en-SG": {
    aria: "Outbox dead-letter queue", heading: "Outbox dead letters (DEAD)",
    note: "Events that exhausted their retries (or have no registered consumer) stop here. Replay only requeues them -- whether they then process successfully is the worker's honest outcome.",
    disabledTitle: "Event outbox is not enabled",
    disabledNote: "This deployment runs without the transactional outbox (inner-cosmos.events.outbox.enabled=false), so there is no dead-letter data.",
    empty: "No dead letters right now", emptyNote: "The dead-letter queue is empty: no events are in the DEAD state.",
    loadFailed: "Dead-letter data is temporarily unavailable.",
    unauthorized: "Administrator access is required to view the dead-letter queue.",
    retry: "Retry", refreshing: "Loading…",
    colEventId: "Event ID", colType: "Type", colSummary: "Summary", colAttempts: "Attempts",
    colLastError: "Last error", colLastAttempt: "Last attempt", colAction: "Action",
    replay: "Replay", replayBusy: "Replaying…",
    replayOkPrefix: "Requeued; current status ", replayFailedPrefix: "Replay failed: ",
    prev: "Previous", next: "Next",
    range: (from, to, total) => `${from}-${to} of ${total}`,
    dash: "—"
  }
};

function formatTimestamp(value: string | null, dash: string): string {
  return value ? value.replace("T", " ") : dash;
}

/**
 * CP-39/CP-40 admin DLQ page (frontend half): read/replay surface over the transactional
 * outbox's DEAD rows, mounted as a tab of the AdminConsole (admin sessions only -- AuroraApp
 * redirects non-admins away from /admin before this can mount, and the backend independently
 * requireAdmin-gates both endpoints).
 *
 * Honesty rules, mirroring the backend's own: (1) only rows the backend really returned are
 * shown -- an empty dead-letter queue renders as an honest empty state, never a fabricated one;
 * (2) enabled:false (outbox not deployed, the default) is reported as exactly that, not dressed
 * up as an empty queue behind a running one; (3) replay results are shown as the backend states
 * them -- success only requeues (the worker's later outcome is not pretended here), and a 409
 * (no longer DEAD) / 404 (unknown eventId) rejection is displayed verbatim, never as success.
 */
export function AdminOutboxDlq({
  locale = "zh-CN",
  loader = (limit, offset) => api.adminOutboxDead(limit, offset),
  replayer = eventId => api.adminReplayOutboxDead(eventId)
}: {
  locale?: Locale;
  loader?: (limit: number, offset: number) => Promise<OutboxDeadLetterPage>;
  replayer?: (eventId: string) => Promise<OutboxReplayResult>;
}) {
  const t = COPY[locale];
  const [page, setPage] = useState<OutboxDeadLetterPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<Error | null>(null);
  const [offset, setOffset] = useState(0);
  const [refreshTick, setRefreshTick] = useState(0);
  const [replayingEventId, setReplayingEventId] = useState<string | null>(null);
  const [actionStatus, setActionStatus] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    loader(PAGE_SIZE, offset)
      .then(result => { if (!cancelled) setPage(result); })
      .catch((error: unknown) => { if (!cancelled) setLoadError(error instanceof Error ? error : new Error(String(error))); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [loader, offset, refreshTick]);

  // One replay at a time; a fresh page load clears the stale per-action message.
  const replay = useCallback(async (eventId: string) => {
    if (replayingEventId) return;
    setReplayingEventId(eventId);
    setActionStatus(null);
    try {
      const result = await replayer(eventId);
      setActionStatus({ ok: true, text: t.replayOkPrefix + (result.status ?? "") });
      // Refresh the list from the backend's real state: the replayed row should leave this page.
      setRefreshTick(value => value + 1);
    } catch (error) {
      // 409 (row no longer DEAD) / 404 (unknown eventId) arrive as ApiCodeError with the
      // backend's own message -- shown verbatim, never rephrased into a success.
      setActionStatus({
        ok: false,
        text: t.replayFailedPrefix + (error instanceof Error && error.message ? error.message : String(error))
      });
    } finally {
      setReplayingEventId(null);
    }
  }, [replayer, replayingEventId, t.replayOkPrefix, t.replayFailedPrefix]);

  if (loadError) {
    // Non-admin (401 UNAUTHORIZED) gets the honest permission message, not a fake empty queue.
    const unauthorized = loadError instanceof ApiCodeError && loadError.code === "UNAUTHORIZED";
    return <div className="admin-timeline" aria-label={t.aria}>
      <div className="admin-empty">
        <p className="admin-muted">{unauthorized ? t.unauthorized : `${t.loadFailed} ${loadError.message}`}</p>
        <button type="button" onClick={() => setRefreshTick(value => value + 1)}>{t.retry}</button>
      </div>
    </div>;
  }

  if (loading && !page) {
    return <div className="admin-timeline" aria-label={t.aria}><div className="admin-empty">{t.refreshing}</div></div>;
  }

  if (page && !page.enabled) {
    return <div className="admin-timeline" aria-label={t.aria}>
      <div className="admin-empty" data-dlq-state="disabled">
        <strong>{t.disabledTitle}</strong>
        <p className="admin-muted">{t.disabledNote}</p>
      </div>
    </div>;
  }

  const entries = page?.entries ?? [];
  const total = page?.total ?? 0;
  const currentOffset = page?.offset ?? offset;
  const rangeText = entries.length > 0
    ? t.range(currentOffset + 1, currentOffset + entries.length, total)
    : null;
  const canPrev = currentOffset > 0;
  const canNext = currentOffset + PAGE_SIZE < total;

  return <div className="admin-timeline" aria-label={t.aria} data-dlq-state="ready">
    <article className="admin-card">
      <strong>{t.heading}</strong>
      <p className="admin-muted">{t.note}</p>
      {actionStatus && <p className="admin-muted" role={actionStatus.ok ? "status" : "alert"}
        data-action={actionStatus.ok ? "ok" : "error"}>{actionStatus.text}</p>}
      {loading ? <p className="admin-muted" role="status">{t.refreshing}</p> :
        entries.length === 0 ? <div className="admin-empty" data-dlq-state="empty">
          <strong>{t.empty}</strong>
          <p className="admin-muted">{t.emptyNote}</p>
        </div> : <table className="admin-table">
          <thead>
            <tr>
              <th>{t.colEventId}</th><th>{t.colType}</th><th>{t.colSummary}</th>
              <th>{t.colAttempts}</th><th>{t.colLastError}</th><th>{t.colLastAttempt}</th>
              <th>{t.colAction}</th>
            </tr>
          </thead>
          <tbody>
            {entries.map(entry => <tr key={entry.eventId} data-event-id={entry.eventId}>
              <td title={entry.eventId}>{entry.eventId.slice(0, 8)}</td>
              <td>{entry.eventType}</td>
              <td>{entry.payloadSummary}</td>
              <td>{entry.attempts}</td>
              <td title={entry.lastError ?? undefined}>{entry.lastError ?? t.dash}</td>
              <td>{formatTimestamp(entry.lastAttemptAt, t.dash)}</td>
              <td><button type="button" disabled={replayingEventId !== null}
                onClick={() => void replay(entry.eventId)}>
                {replayingEventId === entry.eventId ? t.replayBusy : t.replay}
              </button></td>
            </tr>)}
          </tbody>
        </table>}
      {(canPrev || canNext || rangeText) && <div className="admin-pill-row" role="group" aria-label={t.aria}>
        <button type="button" disabled={!canPrev || loading}
          onClick={() => setOffset(Math.max(0, currentOffset - PAGE_SIZE))}>{t.prev}</button>
        {rangeText && <span className="admin-pill">{rangeText}</span>}
        <button type="button" disabled={!canNext || loading}
          onClick={() => setOffset(currentOffset + PAGE_SIZE)}>{t.next}</button>
      </div>}
    </article>
  </div>;
}
