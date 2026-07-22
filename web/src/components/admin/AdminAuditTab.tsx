import type { AdminAuditLog } from "../../api";
import type { Locale } from "../../i18n";

// Port of admin.html's "审计" tab (loadAudit()/#auditList).
const COPY: Record<Locale, { aria: string; empty: string; targetLabel: (type: string, id: number | null) => string }> = {
  "zh-CN": { aria: "审计日志列表", empty: "暂无审计日志", targetLabel: (type, id) => `${type} #${id ?? ""}` },
  "en-SG": { aria: "Audit log list", empty: "No audit logs yet", targetLabel: (type, id) => `${type} #${id ?? ""}` }
};

export function AdminAuditTab({ logs, locale = "zh-CN" }: { logs: AdminAuditLog[]; locale?: Locale }) {
  const t = COPY[locale];
  return <div className="admin-timeline" aria-label={t.aria}>
    {logs.length === 0 ? <div className="admin-empty">{t.empty}</div> : logs.map(a => <article className="admin-card" key={a.id}>
      <strong>{a.actionType}</strong>
      <p className="admin-muted">{t.targetLabel(a.targetType, a.targetId)} &middot; {a.detail}</p>
    </article>)}
  </div>;
}
