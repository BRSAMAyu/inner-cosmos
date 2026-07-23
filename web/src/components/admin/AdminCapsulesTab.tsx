import { useMemo, useState } from "react";
import type { AdminCapsuleRow } from "../../api";
import type { Locale } from "../../i18n";
import { AsyncButton } from "../../loading";

// Port of admin.html's "共鸣体" tab (loadCapsules()/filterCapsules() + hideCapsule()/restoreCapsule()).
// Filtering stays client-side over the full list, matching the legacy `allCapsules` cache behavior.
const COPY: Record<Locale, {
  aria: string; empty: string; searchPlaceholder: string; statusAll: string;
  statusLabel: Record<string, string>; visibilityPublic: string; visibilityPrivate: string;
  energyLabel: (v: number) => string; hide: string; restore: string; reasonPlaceholder: string;
  confirm: string; cancel: string; busy: string;
}> = {
  "zh-CN": {
    aria: "共鸣体列表", empty: "没有匹配的共鸣体", searchPlaceholder: "搜索共鸣体...", statusAll: "全部状态",
    statusLabel: { PUBLIC: "公开", HIDDEN: "已隐藏", ARCHIVED: "已归档", PRIVATE: "私有", NEEDS_REVIEW: "待审核" },
    visibilityPublic: "公开", visibilityPrivate: "私有", energyLabel: v => `能量 ${v.toFixed(2)}`,
    hide: "隐藏", restore: "恢复", reasonPlaceholder: "填写原因...", confirm: "确认", cancel: "取消", busy: "处理中"
  },
  "en-SG": {
    aria: "Capsule list", empty: "No matching capsules", searchPlaceholder: "Search capsules...", statusAll: "All statuses",
    statusLabel: { PUBLIC: "Public", HIDDEN: "Hidden", ARCHIVED: "Archived", PRIVATE: "Private", NEEDS_REVIEW: "Needs review" },
    visibilityPublic: "Public", visibilityPrivate: "Private", energyLabel: v => `Energy ${v.toFixed(2)}`,
    hide: "Hide", restore: "Restore", reasonPlaceholder: "Write a reason...", confirm: "Confirm", cancel: "Cancel", busy: "Working"
  }
};

export function AdminCapsulesTab({ capsules, busyId, onHide, onRestore, locale = "zh-CN" }: {
  capsules: AdminCapsuleRow[]; busyId: number | null;
  onHide: (id: number, reason: string) => void | Promise<void>;
  onRestore: (id: number, reason: string) => void | Promise<void>;
  locale?: Locale;
}) {
  const t = COPY[locale];
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [pending, setPending] = useState<{ id: number; kind: "hide" | "restore" } | null>(null);
  const [reason, setReason] = useState("");

  const filtered = useMemo(() => capsules
    .filter(c => !search || c.pseudonym.toLowerCase().includes(search.toLowerCase()))
    .filter(c => !statusFilter || c.visibilityStatus === statusFilter), [capsules, search, statusFilter]);

  const start = (id: number, kind: "hide" | "restore") => { setPending({ id, kind }); setReason(kind === "restore" ? "管理员恢复" : ""); };
  const confirm = async () => {
    if (!pending) return;
    if (pending.kind === "hide") await onHide(pending.id, reason.trim() || "管理员隐藏");
    else await onRestore(pending.id, reason.trim() || "管理员恢复");
    setPending(null);
  };

  return <div aria-label={t.aria}>
    <div className="admin-toolbar">
      <input placeholder={t.searchPlaceholder} value={search} onChange={event => setSearch(event.target.value)} />
      <select value={statusFilter} onChange={event => setStatusFilter(event.target.value)}>
        <option value="">{t.statusAll}</option>
        <option value="PUBLIC">{t.statusLabel.PUBLIC}</option>
        <option value="HIDDEN">{t.statusLabel.HIDDEN}</option>
      </select>
    </div>
    {filtered.length === 0 ? <div className="admin-empty">{t.empty}</div> : <div className="admin-grid">
      {filtered.map(c => <article className="admin-card" key={c.id}>
        <div className="admin-card-head">
          <strong>{c.pseudonym || "未命名"}</strong>
          <span className="admin-badge">{t.statusLabel[c.visibilityStatus] ?? c.visibilityStatus}</span>
        </div>
        <p className="admin-muted ugc-text">{c.intro}</p>
        <div className="admin-pill-row">
          <span className="admin-pill">{c.isPublic ? t.visibilityPublic : t.visibilityPrivate}</span>
          {c.capsuleType && <span className="admin-pill">{c.capsuleType}</span>}
          {c.echoEnergy != null && <span className="admin-pill">{t.energyLabel(c.echoEnergy)}</span>}
        </div>
        {pending?.id === c.id ? <div className="admin-reason-form">
          <input aria-label={t.reasonPlaceholder} placeholder={t.reasonPlaceholder}
            value={reason} onChange={event => setReason(event.target.value)} />
          <AsyncButton busy={busyId === c.id} busyText={t.busy} onClick={confirm}>{t.confirm}</AsyncButton>
          <button type="button" onClick={() => setPending(null)}>{t.cancel}</button>
        </div> : <div className="admin-actions">
          <button type="button" onClick={() => start(c.id, "hide")}>{t.hide}</button>
          <button type="button" onClick={() => start(c.id, "restore")}>{t.restore}</button>
        </div>}
      </article>)}
    </div>}
  </div>;
}
