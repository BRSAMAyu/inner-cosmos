import type { RelationMention, RelationTimelinePoint, RelationReview } from "../api";
import type { Locale } from "../i18n";
import { LoadingText } from "../loading";

// emotionTags may arrive as a comma-joined string or a JSON array string; normalize to chips.
function parseTags(raw: string | null): string[] {
  if (!raw) return [];
  const trimmed = raw.trim();
  if (trimmed.startsWith("[")) {
    try { const arr = JSON.parse(trimmed); if (Array.isArray(arr)) return arr.map(String).filter(Boolean); } catch { /* fall through */ }
  }
  return trimmed.replace(/^\[|\]$/g, "").split(/[,，、]/).map(s => s.replace(/["'\s]/g, "")).filter(Boolean);
}

const COPY: Record<Locale, {
  aria: string; heading: string; count: (n: number) => string; intro: string;
  empty: string; pickPrompt: string; loadingTimeline: (label: string) => string;
  review: string; reviewNote: string;
  mentions: (n: number) => string; activeWeeks: (n: number) => string; emotions: string;
  reviewEmpty: string; reviewDisclaimer: string;
  timelineTitle: (label: string) => string; timelineEmpty: string;
}> = {
  "zh-CN": {
    aria: "关系互动回顾与时间线", heading: "关系的互动，慢慢看清", count: n => `${n} 段被你提到的关系`,
    intro: "这些是你在对话里自然提到的人。这里不催促你联系谁，也不评判任何一段关系——只如实呈现这段关系在你记忆里出现的痕迹。",
    empty: "还没有从对话里浮现的关系。多和 Aurora 聊聊你在意的人，这里会慢慢亮起来。",
    pickPrompt: "选一段关系，看它的互动回顾与时间线。", loadingTimeline: label => `正在读取「${label}」的时间线…`,
    review: "互动回顾", reviewNote: "这是互动记录的回顾，不是关系好坏的评判。",
    mentions: n => `近 4 周被提及 ${n} 次`, activeWeeks: n => `分布在 ${n} 个不同的周`,
    emotions: "情绪痕迹", reviewEmpty: "近 4 周没有提及这段关系的记录。",
    reviewDisclaimer: "数据只来自你自己的记忆卡片，不会给关系打分。",
    timelineTitle: label => `「${label}」的时间线`, timelineEmpty: "这段关系还没有足够的时间线记录。"
  },
  "en-SG": {
    aria: "Relationship interaction review and timeline", heading: "Relationship interactions, seen slowly",
    count: n => `${n} relationship${n === 1 ? "" : "s"} you've mentioned`,
    intro: "These are people you've naturally mentioned in conversation. Nothing here pushes you to reach out, and nothing grades any relationship — it only shows where this person has appeared in your memories.",
    empty: "No relationships have surfaced from conversation yet. Talk with Aurora about people who matter to you, and this will slowly light up.",
    pickPrompt: "Pick a relationship to see its interaction review and timeline.", loadingTimeline: label => `Loading ${label}'s timeline…`,
    review: "Interaction review", reviewNote: "This is a record of interactions, not a verdict on the relationship.",
    mentions: n => `mentioned ${n} time${n === 1 ? "" : "s"} in the last 4 weeks`, activeWeeks: n => `across ${n} different week${n === 1 ? "" : "s"}`,
    emotions: "Emotional traces", reviewEmpty: "No mentions of this relationship in the last 4 weeks.",
    reviewDisclaimer: "Data comes only from your own memory cards; relationships are never scored.",
    timelineTitle: label => `${label}'s timeline`, timelineEmpty: "Not enough timeline records for this relationship yet."
  }
};

export function RelationsView({ relations, selected, timeline, review, busy, onSelect, locale = "zh-CN" }: {
  relations: RelationMention[];
  selected: string | null;
  timeline: RelationTimelinePoint[];
  review: RelationReview | null;
  busy: boolean;
  onSelect: (label: string) => void;
  locale?: Locale;
}) {
  const t = COPY[locale];
  return <section className="relations-view" aria-label={t.aria}>
    <div className="resonance-heading">
      <div><span className="eyebrow">{locale === "en-SG" ? "RELATIONSHIPS" : "关系"}</span><h2>{t.heading}</h2></div>
      <span>{t.count(relations.length)}</span>
    </div>
    <p className="resonance-intro">{t.intro}</p>
    {relations.length === 0
      ? <div className="network-empty">{t.empty}</div>
      : <div className="relations-layout">
          <ul className="relations-list" role="list">
            {relations.map(r => <li key={r.id}>
              <button
                type="button"
                className={"relation-card" + (selected === r.relationLabel ? " is-selected" : "")}
                aria-pressed={selected === r.relationLabel}
                onClick={() => onSelect(r.relationLabel)}>
                <div className="relation-card-head">
                  <strong>{r.relationLabel}</strong>
                  {r.relationType && <small>{r.relationType}</small>}
                </div>
                {parseTags(r.emotionTags).length > 0 && <div className="relation-tags">
                  {parseTags(r.emotionTags).slice(0, 4).map((tag, i) => <span className="relation-tag" key={i}>{tag}</span>)}
                </div>}
                {r.triggerSummary && <p className="relation-summary ugc-text">{r.triggerSummary}</p>}
              </button>
            </li>)}
          </ul>
          <div className="relation-detail" aria-live="polite">
            {!selected
              ? <div className="network-empty">{t.pickPrompt}</div>
              : busy
                ? <LoadingText busy className="network-empty">{t.loadingTimeline(selected)}</LoadingText>
                : <>
                    {review && <div className="relation-review">
                      <div className="relation-temp-row">
                        <span>{t.review}</span>
                        <strong>{review.mentionCount > 0
                          ? `${t.mentions(review.mentionCount)} · ${t.activeWeeks(review.weeksActive)}`
                          : t.reviewEmpty}</strong>
                      </div>
                      <p className="relation-review-note">{t.reviewNote} {t.reviewDisclaimer}</p>
                      {Object.keys(review.emotionSpectrum).length > 0 && <>
                        <span className="relation-review-label">{t.emotions}</span>
                        <div className="relation-tags">
                          {Object.entries(review.emotionSpectrum).slice(0, 6).map(([tag, count], i) =>
                            <span className="relation-tag" key={i}>{tag} ×{count}</span>)}
                        </div>
                      </>}
                    </div>}
                    <h3 className="relation-timeline-title">{t.timelineTitle(selected)}</h3>
                    {timeline.length === 0
                      ? <div className="network-empty">{t.timelineEmpty}</div>
                      : <ol className="relation-timeline" role="list">
                          {timeline.map((p, i) => <li className="relation-timeline-point" key={i}>
                            <time>{new Date(p.timestamp).toLocaleString(locale, { hour12: false })}</time>
                            {p.emotions && <div className="relation-tags">
                              {parseTags(p.emotions).slice(0, 4).map((tag, j) => <span className="relation-tag" key={j}>{tag}</span>)}
                            </div>}
                            {p.summary && <p className="relation-summary ugc-text">{p.summary}</p>}
                          </li>)}
                        </ol>}
                  </>}
          </div>
        </div>}
  </section>;
}
