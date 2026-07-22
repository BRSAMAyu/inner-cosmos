import { useEffect, useState } from "react";
import type { Locale } from "../../i18n";
import { useAdminConsole } from "../../hooks/useAdminConsole";
import { AdminUsersTab } from "./AdminUsersTab";
import { AdminCapsulesTab } from "./AdminCapsulesTab";
import { AdminReportsTab } from "./AdminReportsTab";
import { AdminAbTestTab } from "./AdminAbTestTab";
import { AdminAiLogsTab } from "./AdminAiLogsTab";
import { AdminSafetyTab } from "./AdminSafetyTab";
import { AdminModelTab } from "./AdminModelTab";
import { AdminAuditTab } from "./AdminAuditTab";

// Port of the legacy static /pages/admin.html (689 lines, 8 tabs) into the AppShell. Deliberately a
// standalone route (see AuroraApp.tsx's `/admin` wiring), NOT a 6th ProductShell space -- moderation
// tooling is a fundamentally different information architecture from the five consumer spaces
// (aurora/cosmos/resonance/letters/me, see ProductShell.tsx). Reachable only for an ADMIN-role
// session; AuroraApp.tsx redirects any non-admin session away before this ever mounts. This
// component owns its own data fetching (useAdminConsole) so AuroraApp.tsx only needs the route
// wiring and a single top-level render call, matching SafetyHarborPage's standalone-route pattern.
const TABS = ["users", "capsules", "reports", "abtest", "ailogs", "safety", "model", "audit"] as const;
type TabKey = typeof TABS[number];

const COPY: Record<Locale, {
  aria: string; heading: string; sub: string; refresh: string; back: string; tabsAria: string;
  tabLabel: Record<TabKey, string>;
  metricUsers: string; metricPublicCapsules: string; metricLetters: string; metricPendingReports: string;
  metricAiLogs: string; metricSafetyEvents: string;
}> = {
  "zh-CN": {
    aria: "管理后台", heading: "管理后台", sub: "所有影响用户内容的操作都需要理由，并写入审计日志。",
    refresh: "刷新全部", back: "返回核心", tabsAria: "管理后台分区",
    tabLabel: { users: "用户", capsules: "共鸣体", reports: "举报", abtest: "A/B 测试", ailogs: "AI 日志", safety: "安全", model: "模型", audit: "审计" },
    metricUsers: "注册用户", metricPublicCapsules: "公开共鸣体", metricLetters: "慢信",
    metricPendingReports: "待处理举报", metricAiLogs: "AI 日志", metricSafetyEvents: "安全事件"
  },
  "en-SG": {
    aria: "Admin console", heading: "Admin console", sub: "Every action that affects a user's content needs a reason and is written to the audit log.",
    refresh: "Refresh all", back: "Back to today", tabsAria: "Admin console sections",
    tabLabel: { users: "Users", capsules: "Capsules", reports: "Reports", abtest: "A/B test", ailogs: "AI logs", safety: "Safety", model: "Model", audit: "Audit" },
    metricUsers: "Registered users", metricPublicCapsules: "Public capsules", metricLetters: "Slow letters",
    metricPendingReports: "Pending reports", metricAiLogs: "AI logs", metricSafetyEvents: "Safety events"
  }
};

export function AdminConsole({ locale = "zh-CN", onBack }: { locale?: Locale; onBack: () => void }) {
  const t = COPY[locale];
  const admin = useAdminConsole();
  const [tab, setTab] = useState<TabKey>("users");

  useEffect(() => { void admin.loadAll(); }, [admin.loadAll]);

  return <main className="admin-console" aria-label={t.aria} lang={locale}>
    <section className="admin-hero">
      <div>
        <h1>{t.heading}</h1>
        <p>{t.sub}</p>
      </div>
      <div className="admin-hero-actions">
        <button type="button" onClick={() => void admin.loadAll()}>{t.refresh}</button>
        <button type="button" className="quiet" onClick={onBack}>{t.back}</button>
      </div>
    </section>

    {admin.status && <div className="admin-status" role="status">{admin.status}</div>}

    {admin.overview && <section className="admin-metrics">
      <article><strong>{admin.overview.totalUsers}</strong><span>{t.metricUsers}</span></article>
      <article><strong>{admin.overview.publicCapsules}</strong><span>{t.metricPublicCapsules}</span></article>
      <article><strong>{admin.overview.totalLetters}</strong><span>{t.metricLetters}</span></article>
      <article className={admin.overview.pendingReports > 0 ? "is-alert" : ""}>
        <strong>{admin.overview.pendingReports}</strong><span>{t.metricPendingReports}</span></article>
      <article><strong>{admin.overview.totalAiLogs}</strong><span>{t.metricAiLogs}</span></article>
      <article><strong>{admin.overview.safetyEvents}</strong><span>{t.metricSafetyEvents}</span></article>
    </section>}

    <div className="admin-tabs" role="tablist" aria-label={t.tabsAria}>
      {TABS.map(key => <button key={key} type="button" role="tab" aria-selected={tab === key}
        className={tab === key ? "active" : ""} onClick={() => setTab(key)}>{t.tabLabel[key]}</button>)}
    </div>

    <section className="admin-tab-panel" role="tabpanel">
      {tab === "users" && <AdminUsersTab users={admin.users} locale={locale} />}
      {tab === "capsules" && <AdminCapsulesTab capsules={admin.capsules} busyId={admin.busyId}
        onHide={admin.hideCapsule} onRestore={admin.restoreCapsule} locale={locale} />}
      {tab === "reports" && <AdminReportsTab reports={admin.reports} statusFilter={admin.reportStatusFilter}
        busyId={admin.busyId} onChangeStatusFilter={admin.changeReportStatusFilter} onResolve={admin.resolveReport} locale={locale} />}
      {tab === "abtest" && <AdminAbTestTab config={admin.abtestConfig} stats={admin.abtestStats}
        busyId={admin.busyId} onToggle={admin.toggleAbTest} locale={locale} />}
      {tab === "ailogs" && <AdminAiLogsTab logs={admin.aiLogs} locale={locale} />}
      {tab === "safety" && <AdminSafetyTab events={admin.safetyEvents} locale={locale} />}
      {tab === "model" && <AdminModelTab health={admin.aiHealth} configs={admin.modelConfig} locale={locale} />}
      {tab === "audit" && <AdminAuditTab logs={admin.auditLogs} locale={locale} />}
    </section>
  </main>;
}
