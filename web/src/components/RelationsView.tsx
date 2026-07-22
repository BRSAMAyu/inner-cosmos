import type { RelationMention, RelationTimelinePoint, RelationHealth } from "../api";
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
  temperature: string; temp: { warm: string; stable: string; cool: string; needsCare: string };
  timelineTitle: (label: string) => string; timelineEmpty: string;
}> = {
  "zh-CN": {
    aria: "关系温度与时间线", heading: "关系的温度，慢慢看清", count: n => `${n} 段被你提到的关系`,
    intro: "这些是你在对话里自然提到的人。这里不催促你联系谁，只帮你看清每段关系此刻的温度与走向。",
    empty: "还没有从对话里浮现的关系。多和 Aurora 聊聊你在意的人，这里会慢慢亮起来。",
    pickPrompt: "选一段关系，看它的温度与时间线。", loadingTimeline: label => `正在读取「${label}」的时间线…`,
    temperature: "关系温度", temp: { warm: "温暖", stable: "稳定", cool: "微凉", needsCare: "需要关照" },
    timelineTitle: label => `「${label}」的时间线`, timelineEmpty: "这段关系还没有足够的时间线记录。"
  },
  "en-SG": {
    aria: "Relationship warmth and timeline", heading: "Relationship warmth, seen slowly",
    count: n => `${n} relationship${n === 1 ? "" : "s"} you've mentioned`,
    intro: "These are people you've naturally mentioned in conversation. Nothing here pushes you to reach out — it just helps you see each relationship's current warmth and direction.",
    empty: "No relationships have surfaced from conversation yet. Talk with Aurora about people who matter to you, and this will slowly light up.",
    pickPrompt: "Pick a relationship to see its warmth and timeline.", loadingTimeline: label => `Loading ${label}'s timeline…`,
    temperature: "Relationship warmth", temp: { warm: "Warm", stable: "Stable", cool: "Cooling", needsCare: "Needs care" },
    timelineTitle: label => `${label}'s timeline`, timelineEmpty: "Not enough timeline records for this relationship yet."
  }
};

export function RelationsView({ relations, selected, timeline, health, busy, onSelect, locale = "zh-CN" }: {
  relations: RelationMention[];
  selected: string | null;
  timeline: RelationTimelinePoint[];
  health: RelationHealth | null;
  busy: boolean;
  onSelect: (label: string) => void;
  locale?: Locale;
}) {
  const t = COPY[locale];
  // Health score (0..1) -> a warm "relationship temperature" label.
  const temperature = (score: number): { label: string; pct: number } => {
    const pct = Math.max(0, Math.min(100, Math.round(score * 100)));
    const label = pct >= 75 ? t.temp.warm : pct >= 50 ? t.temp.stable : pct >= 25 ? t.temp.cool : t.temp.needsCare;
    return { label, pct };
  };
  return <section className="relations-view" aria-label={t.aria}>
    <div className="resonance-heading">
      <div><span className="eyebrow">RELATIONSHIPS</span><h2>{t.heading}</h2></div>
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
                {r.triggerSummary && <p className="relation-summary">{r.triggerSummary}</p>}
              </button>
            </li>)}
          </ul>
          <div className="relation-detail" aria-live="polite">
            {!selected
              ? <div className="network-empty">{t.pickPrompt}</div>
              : busy
                ? <LoadingText busy className="network-empty">{t.loadingTimeline(selected)}</LoadingText>
                : <>
                    {health && <div className="relation-temperature">
                      <div className="relation-temp-row">
                        <span>{t.temperature}</span>
                        <strong>{temperature(health.healthScore).label} · {temperature(health.healthScore).pct}%</strong>
                      </div>
                      <div className="relation-temp-bar"><span style={{ width: `${temperature(health.healthScore).pct}%` }} /></div>
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
                            {p.summary && <p className="relation-summary">{p.summary}</p>}
                          </li>)}
                        </ol>}
                  </>}
          </div>
        </div>}
  </section>;
}
