import type { DailyRecordEntry, MemoryThemeRow, StarfieldScene } from "../api";
import type { Locale } from "../i18n";

const WEATHER: Record<string, { zh: string; en: string; mark: string }> = {
  CLEAR: { zh: "清朗", en: "Clear", mark: "◌" },
  SUNNY: { zh: "明亮", en: "Bright", mark: "☼" },
  CLOUDY: { zh: "有云", en: "Clouded", mark: "◒" },
  RAINY: { zh: "有雨", en: "Rainy", mark: "⌁" },
  STORMY: { zh: "风暴", en: "Stormy", mark: "≈" }
};

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
  const weather = WEATHER[latest?.emotionWeather ?? ""] ?? { zh: "尚未命名", en: "Not named yet", mark: "·" };
  const mainThemes = [...themes]
    .filter(theme => theme.status == null || theme.status === "ACTIVE")
    .sort((a, b) => (b.averageGravity ?? 0) - (a.averageGravity ?? 0))
    .slice(0, 2);
  const unresolved = [...starfield.stars]
    .filter(star => /推进|关系|理解|TODO|RELATION/i.test(`${star.theme} ${star.memoryLayer}`))
    .sort((a, b) => b.gravity - a.gravity)[0]
    ?? [...starfield.stars].sort((a, b) => b.gravity - a.gravity)[0]
    ?? null;

  return <section className="inner-cosmos-overview" aria-label={en ? "Current inner cosmos" : "此刻的内宇宙"}>
    <header>
      <div><span className="eyebrow">YOUR INNER COSMOS · NOW</span>
        <h1>{en ? "See what is alive before opening every memory." : "先看见此刻，再进入每一颗记忆"}</h1></div>
      <p>{en
        ? "A living reading of weather, recurring constellations, unfinished gravity and recent change."
        : "这里先呈现情绪天气、反复出现的星座、尚未松开的引力和最近变化；完整工具各自归位。"}</p>
    </header>
    <div className="inner-cosmos-overview-grid">
      <button type="button" onClick={onOpenDaily}>
        <span>{en ? "Emotional weather" : "此刻的情绪天气"}</span>
        <strong><i aria-hidden="true">{weather.mark}</i>{en ? weather.en : weather.zh}</strong>
        <small>{latest?.theme ?? (en ? "One honest check-in will name it." : "完成一次真实记录后，它会在这里成形")}</small>
        <em>{en ? "Open today's record" : "查看今日记录 →"}</em>
      </button>
      <button type="button" onClick={onOpenBeliefs}>
        <span>{en ? "Main constellations" : "反复出现的主星座"}</span>
        <strong>{mainThemes.length || "·"}</strong>
        <small>{mainThemes.length
          ? mainThemes.map(theme => theme.themeName).filter(Boolean).join(" · ")
          : (en ? "Themes form only after repeated evidence." : "有反复证据后，主题才会成形")}</small>
        <em>{en ? "Inspect understanding" : "查看理解与校准 →"}</em>
      </button>
      <button type="button" disabled={!unresolved} onClick={() => unresolved && onOpenMemory(unresolved.id)}>
        <span>{en ? "Unfinished gravity" : "尚未松开的引力"}</span>
        <strong>{unresolved ? unresolved.gravity.toFixed(1) : "·"}</strong>
        <small>{unresolved?.title ?? (en ? "Nothing is pulling strongly right now." : "此刻没有明显牵引你的主题")}</small>
        <em>{en ? "See why it is here" : "查看它为何仍在这里 →"}</em>
      </button>
      <button type="button" onClick={onOpenWeekly}>
        <span>{en ? "Recent change" : "最近发生的变化"}</span>
        <strong>{dailyRecords.length}</strong>
        <small>{latest?.cognitiveSummary ?? latest?.auroraSummary
          ?? (en ? "A week of records will reveal movement." : "一周记录会让变化变得可见")}</small>
        <em>{en ? "Open weekly review" : "进入周报与变化 →"}</em>
      </button>
    </div>
  </section>;
}
