import { useMemo, useState } from "react";
import type { DailyRecordEntry, MemoryThemeRow } from "../api";
import type { Locale } from "../i18n";

// Phase 3 legacy-page port: src/main/resources/static/pages/timeline.html. Read-only merge of
// GET /api/memory/daily-records and GET /api/memory/themes into one growth timeline -- no writes.
const COPY: Record<Locale, {
  routeHint: string; heading: string; intro: string; all: string; dateLabel: string;
  themesHeading: string; noThemes: string; empty: string;
}> = {
  "zh-CN": {
    routeHint: "成长轨迹", heading: "成长时间轴", intro: "Aurora 和你的日记、对话、记忆共同沉淀出的时间轨迹。",
    all: "全部", dateLabel: "按日期查看", themesHeading: "主题迁移", noThemes: "还没有足够主题数据。",
    empty: "完成一次 Aurora 对话或心声日记后，时间轴会出现真实沉淀。"
  },
  "en-SG": {
    routeHint: "Growth trajectory", heading: "Growth Timeline", intro: "The trajectory that your diary entries, conversations, and memories settle into together with Aurora.",
    all: "All", dateLabel: "Filter by date", themesHeading: "Theme evolution", noThemes: "Not enough theme data yet.",
    empty: "After a conversation with Aurora or a diary entry, real sediment will appear here."
  }
};

const WEATHER_ICON: Record<string, string> = {
  SUNNY: "☀️", CLOUDY: "☁️", RAINY: "🌧️", STORMY: "⛈️", SNOWY: "❄️", FOGGY: "🌫️", WINDY: "🌬️"
};

function weatherIcon(type: string | null): string {
  if (!type) return "🌤️";
  return WEATHER_ICON[type] ?? "🌤️";
}

export function TimelineSection({ dailyRecords, themes, locale = "zh-CN" }: {
  dailyRecords: DailyRecordEntry[]; themes: MemoryThemeRow[]; locale?: Locale;
}) {
  const t = COPY[locale];
  const [date, setDate] = useState("");

  const visible = useMemo(
    () => date ? dailyRecords.filter(r => r.recordDate.slice(0, 10) === date) : dailyRecords,
    [dailyRecords, date]
  );

  const topThemes = themes.slice(0, 7);
  const maxCount = Math.max(1, ...topThemes.map(theme => theme.memoryCount ?? 1));

  return <section className="timeline-section" aria-label={t.heading}>
    <div className="flex-between" style={{ flexWrap: "wrap", gap: 12 }}>
      <div>
        <span className="route-hint">{t.routeHint}</span>
        <h2>{t.heading}</h2>
        <p className="muted">{t.intro}</p>
      </div>
      <div className="row gap-sm">
        <input type="date" aria-label={t.dateLabel} value={date} onChange={event => setDate(event.target.value)} />
        <button type="button" onClick={() => setDate("")}>{t.all}</button>
      </div>
    </div>

    <div className="panel mb-2">
      <h3>{t.themesHeading}</h3>
      {topThemes.length === 0
        ? <p className="muted">{t.noThemes}</p>
        : <>
            <div className="chart-bars mt-1 mb-1" style={{ height: 68 }}>
              {topThemes.map(theme => <div key={theme.id} className="chart-bar"
                title={theme.themeName ?? ""} style={{ height: `${Math.round(((theme.memoryCount ?? 1) / maxCount) * 100)}%` }} />)}
            </div>
            <p className="muted" style={{ fontSize: ".84rem" }}>
              {topThemes.map(theme => (theme.themeName ?? "").slice(0, 6)).join(" / ")}
            </p>
          </>}
    </div>

    {visible.length === 0
      ? <p className="empty">{t.empty}</p>
      : <div className="cosmos-timeline">
          {visible.map((record, index) => <article key={record.id} className="axis-item" style={{ animationDelay: `${index * 40}ms` }}>
            <div className="axis-date">{record.recordDate.slice(0, 10)}</div>
            <div className={`axis-node${(record.eventSummary?.length ?? 0) > 8 ? " major" : ""}`} />
            <div className="panel axis-card">
              <div className="flex-between mb-1">
                <h3>{record.theme || "—"}</h3>
                <span className="weather-icon">{weatherIcon(record.emotionWeather)}</span>
              </div>
              <p className="muted">{record.auroraSummary || record.eventSummary || record.cognitiveSummary || ""}</p>
              <div className="pill-row mt-1">
                {record.emotionWeather && <span className="pill">{record.emotionWeather}</span>}
                {record.todoSummary && <span className="pill">{record.todoSummary}</span>}
                {record.capsuleSuggested && <span className="pill">{locale === "en-SG" ? "Good fit for a capsule" : "适合编织共鸣体"}</span>}
              </div>
            </div>
          </article>)}
        </div>}
  </section>;
}
