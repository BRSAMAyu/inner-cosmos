import { useState } from "react";
import type { AdminReport } from "../../api";
import type { Locale } from "../../i18n";
import { AsyncButton } from "../../loading";

// Port of admin.html's "举报" tab (loadReports()/#reportList + resolveReport()/confirmResolve()).
// The legacy page used a global modal (IC.showModal) to collect a resolution reason; this inline
// per-card expand/reason-input achieves the same "a reason is required before an action that affects
// another user's content" behavior without a new modal subsystem.
const COPY: Record<Locale, {
  aria: string; empty: string; statusFilterLabel: string; statusAll: string; statusPending: string;
  reportLabel: (id: number) => string; targetLabel: (type: string, id: number) => string;
  resolutionLabel: string; dismiss: string; warn: string; ban: string; reasonPlaceholder: string;
  confirm: string; cancel: string; busy: string;
}> = {
  "zh-CN": {
    aria: "举报列表", empty: "没有举报记录", statusFilterLabel: "举报状态", statusAll: "全部举报", statusPending: "待处理",
    reportLabel: id => `举报 #${id}`, targetLabel: (type, id) => `对象：${type} #${id}`,
    resolutionLabel: "处理结果", dismiss: "忽略", warn: "警告", ban: "封禁/隐藏", reasonPlaceholder: "填写处理理由...",
    confirm: "确认处理", cancel: "取消", busy: "处理中"
  },
  "en-SG": {
    aria: "Report list", empty: "No reports", statusFilterLabel: "Report status", statusAll: "All reports", statusPending: "Pending",
    reportLabel: id => `Report #${id}`, targetLabel: (type, id) => `Target: ${type} #${id}`,
    resolutionLabel: "Resolution", dismiss: "Dismiss", warn: "Warn", ban: "Ban/hide", reasonPlaceholder: "Write a resolution reason...",
    confirm: "Confirm", cancel: "Cancel", busy: "Working"
  }
};

const ACTIONS = ["dismiss", "warn", "ban"] as const;
type ResolveAction = typeof ACTIONS[number];

export function AdminReportsTab({ reports, statusFilter, busyId, onChangeStatusFilter, onResolve, locale = "zh-CN" }: {
  reports: AdminReport[]; statusFilter: string; busyId: number | null;
  onChangeStatusFilter: (status: string) => void;
  onResolve: (id: number, action: ResolveAction, reason: string) => void | Promise<void>;
  locale?: Locale;
}) {
  const t = COPY[locale];
  const [pending, setPending] = useState<{ id: number; action: ResolveAction } | null>(null);
  const [reason, setReason] = useState("");

  const startResolve = (id: number, action: ResolveAction) => { setPending({ id, action }); setReason(""); };
  const confirm = async () => {
    if (!pending) return;
    await onResolve(pending.id, pending.action, reason.trim() || t.dismiss);
    setPending(null);
  };

  return <div aria-label={t.aria}>
    <div className="admin-toolbar">
      <label>
        {t.statusFilterLabel}
        <select value={statusFilter} onChange={event => onChangeStatusFilter(event.target.value)}>
          <option value="">{t.statusAll}</option>
          <option value="PENDING">{t.statusPending}</option>
        </select>
      </label>
    </div>
    {reports.length === 0 ? <div className="admin-empty">{t.empty}</div> : <div className="admin-timeline">
      {reports.map(rep => <article className="admin-card" key={rep.id}>
        <div className="admin-card-head">
          <strong>{t.reportLabel(rep.id)}</strong>
          <span className={"admin-badge" + (rep.status === "RESOLVED" ? " is-resolved" : " is-pending")}>{rep.status}</span>
        </div>
        <p className="admin-muted">{rep.reason}</p>
        {rep.targetType && <p className="admin-muted">{t.targetLabel(rep.targetType, rep.targetId)}</p>}
        {rep.status !== "RESOLVED" && <div className="admin-actions">
          {pending?.id === rep.id ? <div className="admin-reason-form">
            <input aria-label={t.reasonPlaceholder} placeholder={t.reasonPlaceholder}
              value={reason} onChange={event => setReason(event.target.value)} />
            <AsyncButton busy={busyId === rep.id} busyText={t.busy} onClick={confirm}>{t.confirm}</AsyncButton>
            <button type="button" onClick={() => setPending(null)}>{t.cancel}</button>
          </div> : ACTIONS.map(action => <button key={action} type="button" onClick={() => startResolve(rep.id, action)}>{t[action]}</button>)}
        </div>}
      </article>)}
    </div>}
  </div>;
}
