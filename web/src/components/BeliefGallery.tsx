import type { BeliefContradiction, BeliefPattern } from "../api";
import type { Locale } from "../i18n";
import type { BeliefFilter } from "../hooks/useBeliefGallery";

// Port of the belief-pattern-browsing half of src/main/resources/static/pages/beliefs.html (Phase 3,
// legacy batch B). See api.ts's BeliefPattern doc comment: the OTHER half of that legacy page (the M6
// Aurora Self Panel) is a separate, still-unported backend and out of scope here.

function strengthTier(score: number | null): "low" | "mid" | "high" {
  const value = score ?? 0;
  return value > 0.6 ? "high" : value > 0.3 ? "mid" : "low";
}

const COPY: Record<Locale, {
  aria: string; heading: string; intro: string; contradictionsAria: string; contradictionsHeading: string;
  contradictionsIntro: string; contradictionsFallback: string; vs: string; filtersAria: string;
  filterAll: string; filterStrong: string; filterByCategory: string; noCategory: string;
  emptyCategory: string; empty: string; loading: string; strength: (pct: number) => string;
  occurrences: (n: number) => string;
}> = {
  "zh-CN": {
    aria: "信念画廊", heading: "信念画廊",
    intro: "系统从你的记忆里识别出反复出现的潜在信念——那些你以为只是「想法」，但其实在悄悄影响你决策的模式。",
    contradictionsAria: "矛盾信念", contradictionsHeading: "矛盾信念",
    contradictionsIntro: "以下信念在你的表达中同时出现——它们在拉扯你。", contradictionsFallback: "这两个信念在拉扯你",
    vs: "⚡ vs ⚡", filtersAria: "信念筛选", filterAll: "全部", filterStrong: "强信念（强度 > 0.5）", filterByCategory: "按类别",
    noCategory: "还没有可分类的信念。", emptyCategory: "这个分类下暂时没有信念。",
    empty: "还没有识别出明显的信念模式。聊更多几次，Aurora 会逐渐看到你的底层想法。", loading: "正在加载…",
    strength: pct => `强度 ${pct}%`, occurrences: n => `出现 ${n} 次`
  },
  "en-SG": {
    aria: "Belief patterns", heading: "Belief patterns",
    intro: "Recurring underlying beliefs surfaced from your memories — patterns you might think of as just \"thoughts\", but that quietly shape your decisions.",
    contradictionsAria: "Contradicting beliefs", contradictionsHeading: "Contradicting beliefs",
    contradictionsIntro: "These beliefs show up together in what you've said — they're pulling you in different directions.",
    contradictionsFallback: "These beliefs are pulling you in different directions",
    vs: "⚡ vs ⚡", filtersAria: "Belief filters", filterAll: "All", filterStrong: "Strong beliefs (strength > 0.5)", filterByCategory: "By category",
    noCategory: "No categorized beliefs yet.", emptyCategory: "No beliefs in this category yet.",
    empty: "No clear belief patterns yet. Keep talking with Aurora and it will gradually see your underlying thinking.",
    loading: "Loading…", strength: pct => `Strength ${pct}%`, occurrences: n => `Seen ${n} time${n === 1 ? "" : "s"}`
  }
};

export function BeliefGallery({ beliefs, contradictions, filter, categories, selectedCategory, categoryBeliefs, busy,
  onSelectFilter, onSelectCategory, locale = "zh-CN" }: {
  beliefs: BeliefPattern[]; contradictions: BeliefContradiction[]; filter: BeliefFilter; categories: string[];
  selectedCategory: string | null; categoryBeliefs: BeliefPattern[]; busy: boolean;
  onSelectFilter: (filter: BeliefFilter) => void; onSelectCategory: (category: string) => void; locale?: Locale;
}) {
  const t = COPY[locale];

  const renderCard = (belief: BeliefPattern) => <article className={`belief-card tier-${strengthTier(belief.strengthScore)}`} key={belief.id}>
    <p className="belief-text">&ldquo;{belief.beliefContent}&rdquo;</p>
    <div className="belief-meta">
      <span className="belief-strength">{t.strength(Math.round((belief.strengthScore ?? 0) * 100))}</span>
      {belief.beliefCategory && <span className="belief-category">{belief.beliefCategory}</span>}
      {typeof belief.confirmationCount === "number" && belief.confirmationCount > 0
        && <span className="belief-count">{t.occurrences(belief.confirmationCount)}</span>}
    </div>
  </article>;

  return <section className="belief-gallery-space" aria-label={t.aria}>
    <span className="eyebrow">BELIEF PATTERNS</span>
    <h2>{t.heading}</h2>
    <p>{t.intro}</p>

    {contradictions.length > 0 && <div className="belief-contradictions" aria-label={t.contradictionsAria}>
      <h3>{t.contradictionsHeading}</h3>
      <p className="muted">{t.contradictionsIntro}</p>
      {contradictions.map((pair, index) => <div className="contradiction-pair" key={index}>
        <article className="belief-card contradiction"><p className="belief-text">&ldquo;{pair.beliefA.beliefContent}&rdquo;</p></article>
        <span className="vs">{t.vs}</span>
        <article className="belief-card contradiction"><p className="belief-text">&ldquo;{pair.beliefB.beliefContent}&rdquo;</p></article>
        <p className="contradiction-reason">{pair.contradictionReason || t.contradictionsFallback}</p>
      </div>)}
    </div>}

    <div className="belief-filter-tabs" role="tablist" aria-label={t.filtersAria}>
      <button type="button" role="tab" aria-selected={filter === "all"} className={filter === "all" ? "active" : ""}
        onClick={() => onSelectFilter("all")}>{t.filterAll}</button>
      <button type="button" role="tab" aria-selected={filter === "strong"} className={filter === "strong" ? "active" : ""}
        onClick={() => onSelectFilter("strong")}>{t.filterStrong}</button>
      <button type="button" role="tab" aria-selected={filter === "byCategory"} className={filter === "byCategory" ? "active" : ""}
        onClick={() => onSelectFilter("byCategory")}>{t.filterByCategory}</button>
    </div>

    {filter === "byCategory"
      ? (categories.length === 0 ? <p className="belief-empty">{t.noCategory}</p> : <>
          <div className="belief-category-picker">
            {categories.map(category => <button type="button" key={category}
              className={`belief-category-btn${selectedCategory === category ? " active" : ""}`}
              onClick={() => onSelectCategory(category)}>{category}</button>)}
          </div>
          {selectedCategory && (busy ? <p className="belief-loading">{t.loading}</p>
            : categoryBeliefs.length === 0 ? <p className="belief-empty">{t.emptyCategory}</p>
            : <div className="belief-list">{categoryBeliefs.map(renderCard)}</div>)}
        </>)
      : busy ? <p className="belief-loading">{t.loading}</p>
      : beliefs.length === 0 ? <p className="belief-empty">{t.empty}</p>
      : <div className="belief-list">{beliefs.map(renderCard)}</div>}
  </section>;
}
