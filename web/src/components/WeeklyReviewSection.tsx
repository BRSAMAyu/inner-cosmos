import type { WeeklyReviewV2 } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

// Phase 3 legacy-page port: src/main/resources/static/pages/weekly-review.html, wired to
// /api/daily-record/weekly/v2/* (see api.ts's WeeklyReviewV2 doc comment for why V2, not V1).
const COPY: Record<Locale, {
  routeHint: string; heading: string; intro: string; regenerate: string; empty: string;
  memoryCount: string; completedTodos: string; themeCount: string; dominantEmotion: string;
  trajectoryHeading: string; noTrajectory: string; observationLabel: string;
  recommendationHeading: string; noThemes: string;
}> = {
  "zh-CN": {
    routeHint: "一周的内在总结", heading: "成长周报",
    intro: "过去 7 天你的内在宇宙发生了什么。系统会从记忆、情绪、待办、主题等多个维度生成一份只属于你的总结。",
    regenerate: "重新生成这一周", empty: "还没有生成过周报。点击「重新生成」开始整理过去 7 天的内在轨迹。",
    memoryCount: "记忆数", completedTodos: "待办进度", themeCount: "主题数", dominantEmotion: "主导情绪",
    trajectoryHeading: "本周轨迹", noTrajectory: "这周还没有每日记录", observationLabel: "Aurora 的观察：",
    recommendationHeading: "下周的微小建议", noThemes: "这周还没有聚类出主题"
  },
  "en-SG": {
    routeHint: "A week's inner summary", heading: "Growth Weekly Review",
    intro: "What happened in your inner cosmos over the past 7 days. Aurora builds a summary just for you from memories, emotions, todos and themes.",
    regenerate: "Regenerate this week", empty: "No review has been generated yet. Click \"Regenerate\" to gather the past 7 days.",
    memoryCount: "Memories", completedTodos: "Todo progress", themeCount: "Themes", dominantEmotion: "Dominant emotion",
    trajectoryHeading: "This week's trajectory", noTrajectory: "No daily records yet this week", observationLabel: "Aurora's observation:",
    recommendationHeading: "A small suggestion for next week", noThemes: "No themes have clustered yet this week"
  }
};

function themeList(topThemes: string): string[] {
  return topThemes.split(/[,，]/).map(s => s.trim()).filter(Boolean);
}

export function WeeklyReviewSection({ review, busy, onGenerate, locale = "zh-CN" }: {
  review: WeeklyReviewV2 | null; busy: boolean; onGenerate: () => void; locale?: Locale;
}) {
  const t = COPY[locale];
  const themes = review ? themeList(review.topThemes) : [];

  return <section className="weekly-review-section" aria-label={t.heading}>
    <span className="route-hint">{t.routeHint}</span>
    <h2>{t.heading}</h2>
    <p className="muted">{t.intro}</p>

    <AsyncButton busy={busy} className="regenerate-btn mb-2" onClick={onGenerate}>{t.regenerate}</AsyncButton>

    {!review
      ? <p className="empty">{t.empty}</p>
      : <>
          <div className="wr-hero">
            <h3>{review.title}</h3>
            <p className="muted" style={{ margin: "0 0 12px" }}>{review.dateRange}</p>
            <div>
              {themes.length === 0
                ? <span className="muted" style={{ fontSize: ".84rem" }}>{t.noThemes}</span>
                : themes.map(theme => <span className="wr-theme" key={theme}>{theme}</span>)}
            </div>
          </div>

          <div className="wr-grid mb-2">
            <div className="wr-stat"><span className="muted">{t.memoryCount}</span><div className="num">{review.memoryCount}</div></div>
            <div className="wr-stat"><span className="muted">{t.completedTodos}</span><div className="num">{review.todoRatio}</div></div>
            <div className="wr-stat"><span className="muted">{t.themeCount}</span><div className="num">{themes.length}</div></div>
            <div className="wr-stat"><span className="muted">{t.dominantEmotion}</span><div className="num" style={{ fontSize: "1.2rem" }}>{review.dominantEmotion || "—"}</div></div>
          </div>

          <div className="wr-section">
            <h3>{t.trajectoryHeading}</h3>
            <div className="wr-timeline">
              {review.dailySnapshots.length === 0
                ? <p className="muted">{t.noTrajectory}</p>
                : review.dailySnapshots.map(day => <div className="wr-day" key={day.date}>
                    <div>
                      <div className="date">{day.date.slice(5)}</div>
                      <div className="weather">{day.emotionWeather ? "🌤️" : "·"}</div>
                    </div>
                    <div>
                      <strong>{day.theme || "—"}</strong>
                      <p className="muted" style={{ fontSize: ".84rem", margin: "4px 0 0" }}>{day.memorySummary || day.cognitiveSummary || ""}</p>
                    </div>
                  </div>)}
            </div>
          </div>

          {review.auroraObservation && <div className="wr-aurora-note">
            <strong>{t.observationLabel}</strong><br />
            {review.auroraObservation}
          </div>}

          {review.recommendation && <div className="wr-section">
            <h3>{t.recommendationHeading}</h3>
            <p>{review.recommendation}</p>
          </div>}
        </>}
  </section>;
}
