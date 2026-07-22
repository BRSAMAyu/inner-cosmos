import type { AdminAiLog } from "../../api";
import type { Locale } from "../../i18n";

// Port of admin.html's "AI 日志" tab (loadAiLogs()/#aiLogList). The legacy page capped the list at
// 30 entries client-side; kept here since /api/ai-logs already returns "recent" logs, not a full table.
const COPY: Record<Locale, { aria: string; empty: string; success: string; failure: string; fallback: string; realPath: string }> = {
  "zh-CN": { aria: "AI 日志列表", empty: "没有 AI 日志", success: "成功", failure: "失败", fallback: "fallback", realPath: "真实路径" },
  "en-SG": { aria: "AI log list", empty: "No AI logs", success: "Success", failure: "Failed", fallback: "fallback", realPath: "real path" }
};

export function AdminAiLogsTab({ logs, locale = "zh-CN" }: { logs: AdminAiLog[]; locale?: Locale }) {
  const t = COPY[locale];
  return <div className="admin-timeline" aria-label={t.aria}>
    {logs.length === 0 ? <div className="admin-empty">{t.empty}</div> : logs.slice(0, 30).map(log => <article className="admin-card" key={log.id}>
      <strong>{log.moduleName || "AI"}</strong>
      <div className="admin-pill-row">
        <span className="admin-pill">{log.provider}</span>
        <span className="admin-pill">{log.modelName}</span>
        <span className="admin-pill">{log.success ? t.success : t.failure}</span>
        <span className="admin-pill">{log.fallbackUsed ? t.fallback : t.realPath}</span>
      </div>
      {log.errorMessage && <p className="admin-muted">{log.errorMessage}</p>}
    </article>)}
  </div>;
}
