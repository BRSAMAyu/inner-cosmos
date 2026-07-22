import type { AdminSafetyEvent } from "../../api";
import type { Locale } from "../../i18n";

// Port of admin.html's "安全" tab (loadSafetyEvents()/#safetyList).
const COPY: Record<Locale, { aria: string; empty: string; eventLabel: (id: number) => string }> = {
  "zh-CN": { aria: "安全事件列表", empty: "没有安全事件", eventLabel: id => `安全事件 #${id}` },
  "en-SG": { aria: "Safety event list", empty: "No safety events", eventLabel: id => `Safety event #${id}` }
};

export function AdminSafetyTab({ events, locale = "zh-CN" }: { events: AdminSafetyEvent[]; locale?: Locale }) {
  const t = COPY[locale];
  return <div className="admin-timeline" aria-label={t.aria}>
    {events.length === 0 ? <div className="admin-empty">{t.empty}</div> : events.map(e => <article className="admin-card" key={e.id}>
      <strong>{t.eventLabel(e.id)}</strong>
      <p className="admin-muted">{e.riskType} &middot; {e.riskLevel}</p>
    </article>)}
  </div>;
}
