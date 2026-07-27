import type { DailyRecordEntry, MemoryThemeRow, StarfieldScene } from "../api";
import type { Locale } from "../i18n";
import { demoContentText } from "../demoContentLocale";
import { emotionWeatherPresentation } from "../emotionWeather";

export function InnerCosmosOverview({ starfield, dailyRecords, themes, locale = "zh-CN",
  onOpenMemory, onOpenDaily, onOpenWeekly, onOpenBeliefs }: {
  starfield: StarfieldScene;
  dailyRecords: DailyRecordEntry[];
  themes: MemoryThemeRow[];
  locale?: Locale;
  onOpenMemory: (id: number) => void;
  onOpenDaily: () => void;
  onOpenWeekly: () => void;
  onOpenBeliefs: () => void;
}) {
  const en = locale === "en-SG";
  const latest = [...dailyRecords].sort((a, b) => b.recordDate.localeCompare(a.recordDate))[0] ?? null;
  const weather = emotionWeatherPresentation(latest?.emotionWeather, locale);
  const mainThemes = [...themes]
    .filter(theme => theme.status == null || theme.status === "ACTIVE")
    .sort((a, b) => (b.averageGravity ?? 0) - (a.averageGravity ?? 0))
    .slice(0, 2);
  const unresolved = [...starfield.stars]
    .filter(star => /推进|关系|理解|TODO|RELATION/i.test(`${star.theme} ${star.memoryLayer}`))
    .sort((a, b) => b.gravity - a.gravity)[0]
    ?? [...starfield.stars].sort((a, b) => b.gravity - a.gravity)[0]
    ?? null;

  return <section className="inner-cosmos-overview" aria-label={en ? "Your memory cosmos" : "你的记忆宇宙"}>
    <header>
      <div><span className="eyebrow">{en ? "YOUR MEMORY COSMOS" : "你的记忆宇宙"}</span>
        <h1>{en ? "See what Aurora remembered — and correct it when needed." : "看见 Aurora 记住了什么，也随时纠正它"}</h1></div>
      <p>{en
        ? "Each star comes from a conversation or record. Larger, brighter stars are affecting you more strongly now. Open any star to inspect its source."
        : "每颗星来自一次对话或记录；越大越亮，表示它最近对你影响越强。点开任何星星，都能查看来源、修改理解或归档。"}</p>
    </header>
    <div className="inner-cosmos-overview-grid">
      <button type="button" onClick={onOpenDaily}>
        <span>{en ? "Emotional weather" : "此刻的情绪天气"}</span>
        <strong><i aria-hidden="true">{weather.mark}</i>{weather.label}</strong>
        <small>{latest ? demoContentText(latest.theme, locale) : (en ? "One honest check-in will name it." : "完成一次真实记录后，它会在这里成形")}</small>
        <em>{en ? "Open today's record" : "查看今日记录 →"}</em>
      </button>
      <button type="button" onClick={onOpenBeliefs}>
        <span>{en ? "Recurring themes" : "反复出现的主题"}</span>
        <strong>{mainThemes.length || "·"}</strong>
        <small>{mainThemes.length
          ? mainThemes.map(theme => demoContentText(theme.themeName, locale)).filter(Boolean).join(" · ")
          : (en ? "Themes form only after repeated evidence." : "有反复证据后，主题才会成形")}</small>
        <em>{en ? "Review Aurora's understanding" : "查看 Aurora 的理解 →"}</em>
      </button>
      <button type="button" disabled={!unresolved} onClick={() => unresolved && onOpenMemory(unresolved.id)}>
        <span>{en ? "Most active memory" : "最近影响最强的记忆"}</span>
        <strong>{unresolved ? unresolved.gravity.toFixed(1) : "·"}</strong>
        <small>{unresolved ? demoContentText(unresolved.title, locale) : (en ? "Nothing is pulling strongly right now." : "此刻没有明显牵引你的主题")}</small>
        <em>{en ? "Open this memory" : "打开这颗记忆 →"}</em>
      </button>
      <button type="button" onClick={onOpenWeekly}>
        <span>{en ? "Recent change" : "最近发生的变化"}</span>
        <strong>{dailyRecords.length}</strong>
        <small>{latest
          ? demoContentText(latest.cognitiveSummary ?? latest.auroraSummary, locale)
          : (en ? "A week of records will reveal movement." : "一周记录会让变化变得可见")}</small>
        <em>{en ? "Open weekly review" : "进入周报与变化 →"}</em>
      </button>
    </div>
  </section>;
}
