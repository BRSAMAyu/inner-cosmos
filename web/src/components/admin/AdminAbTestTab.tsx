import type { AdminAbTestConfig, AdminAbTestStats } from "../../api";
import type { Locale } from "../../i18n";
import { AsyncButton } from "../../loading";

// Port of admin.html's "A/B 测试" tab (loadAbTests()/loadAbTestStats()/toggleAbTest()).
// ABTestController.activeConfig() returns a single active config or null, not a list -- unlike the
// legacy static page's JS, which mistakenly treated the response as an array. Real backend reality:
// at most one A/B test config is "active" at a time, so this renders that one config, not a list.
const COPY: Record<Locale, {
  aria: string; empty: string; running: string; paused: string; pause: string; enable: string;
  splitLabel: (mockPct: number, remotePct: number) => string; statsTitle: string; metric: string;
  calls: string; successRate: string; avgLatency: string; fallbackRate: string; noStats: string; busy: string;
}> = {
  "zh-CN": {
    aria: "A/B 测试看板", empty: "暂无活跃的 A/B 测试", running: "运行中", paused: "已暂停", pause: "暂停", enable: "启用",
    splitLabel: (m, r) => `流量分配：MOCK ${m}% / REMOTE ${r}%`, statsTitle: "累计指标对比", metric: "指标",
    calls: "调用次数", successRate: "成功率", avgLatency: "平均延迟", fallbackRate: "降级率", noStats: "数据收集中，请稍后再来查看。", busy: "处理中"
  },
  "en-SG": {
    aria: "A/B test dashboard", empty: "No active A/B test", running: "Running", paused: "Paused", pause: "Pause", enable: "Enable",
    splitLabel: (m, r) => `Traffic split: MOCK ${m}% / REMOTE ${r}%`, statsTitle: "Aggregate comparison", metric: "Metric",
    calls: "Calls", successRate: "Success rate", avgLatency: "Avg latency", fallbackRate: "Fallback rate", noStats: "Still collecting data, check back later.", busy: "Working"
  }
};

const fmtPct = (v: number | undefined) => typeof v === "number" ? `${(v * 100).toFixed(1)}%` : "-";
const fmtMs = (v: number | undefined) => typeof v === "number" ? `${v.toFixed(0)}ms` : "-";

export function AdminAbTestTab({ config, stats, busyId, onToggle, locale = "zh-CN" }: {
  config: AdminAbTestConfig | null; stats: AdminAbTestStats | null; busyId: number | null;
  onToggle: (id: number, enabled: boolean) => void | Promise<void>; locale?: Locale;
}) {
  const t = COPY[locale];
  if (!config) return <div className="admin-empty" aria-label={t.aria}>{t.empty}</div>;
  const mockPct = Math.round(config.mockPercentage ?? 50);
  const remotePct = 100 - mockPct;
  const mockGroup = stats?.MOCK ?? stats?.mock;
  const remoteGroup = stats?.REMOTE ?? stats?.remote;

  return <div className="admin-grid" aria-label={t.aria}>
    <article className="admin-card">
      <div className="admin-card-head">
        <strong>{config.testName}</strong>
        <span className={"admin-badge" + (config.enabled ? " is-resolved" : " is-pending")}>{config.enabled ? t.running : t.paused}</span>
      </div>
      {config.description && <p className="admin-muted">{config.description}</p>}
      <p className="admin-muted">{t.splitLabel(mockPct, remotePct)}</p>
      <div className="admin-actions">
        <AsyncButton busy={busyId === config.id} busyText={t.busy} onClick={() => onToggle(config.id, !config.enabled)}>
          {config.enabled ? t.pause : t.enable}
        </AsyncButton>
      </div>
      {mockGroup || remoteGroup ? <div className="admin-stage">
        <h3>{t.statsTitle}</h3>
        <table className="admin-table">
          <thead><tr><th>{t.metric}</th><th>MOCK</th><th>REMOTE</th></tr></thead>
          <tbody>
            <tr><td>{t.calls}</td><td>{mockGroup?.totalRequests ?? 0}</td><td>{remoteGroup?.totalRequests ?? 0}</td></tr>
            <tr><td>{t.successRate}</td><td>{fmtPct(mockGroup?.successRate)}</td><td>{fmtPct(remoteGroup?.successRate)}</td></tr>
            <tr><td>{t.avgLatency}</td><td>{fmtMs(mockGroup?.avgLatency)}</td><td>{fmtMs(remoteGroup?.avgLatency)}</td></tr>
            <tr><td>{t.fallbackRate}</td><td>{fmtPct(mockGroup?.fallbackCount && mockGroup.totalRequests ? mockGroup.fallbackCount / mockGroup.totalRequests : undefined)}</td>
              <td>{fmtPct(remoteGroup?.fallbackCount && remoteGroup.totalRequests ? remoteGroup.fallbackCount / remoteGroup.totalRequests : undefined)}</td></tr>
          </tbody>
        </table>
      </div> : <p className="admin-muted">{t.noStats}</p>}
    </article>
  </div>;
}
