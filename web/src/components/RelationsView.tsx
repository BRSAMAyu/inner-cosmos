import type { RelationMention, RelationTimelinePoint, RelationHealth } from "../api";
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

// Health score (0..1) -> a warm "relationship temperature" label.
function temperature(score: number): { label: string; pct: number } {
  const pct = Math.max(0, Math.min(100, Math.round(score * 100)));
  const label = pct >= 75 ? "温暖" : pct >= 50 ? "稳定" : pct >= 25 ? "微凉" : "需要关照";
  return { label, pct };
}

export function RelationsView({ relations, selected, timeline, health, busy, onSelect }: {
  relations: RelationMention[];
  selected: string | null;
  timeline: RelationTimelinePoint[];
  health: RelationHealth | null;
  busy: boolean;
  onSelect: (label: string) => void;
}) {
  return <section className="relations-view" aria-label="关系温度与时间线">
    <div className="resonance-heading">
      <div><span className="eyebrow">RELATIONSHIPS</span><h2>关系的温度，慢慢看清</h2></div>
      <span>{relations.length} 段被你提到的关系</span>
    </div>
    <p className="resonance-intro">这些是你在对话里自然提到的人。这里不催促你联系谁，只帮你看清每段关系此刻的温度与走向。</p>
    {relations.length === 0
      ? <div className="network-empty">还没有从对话里浮现的关系。多和 Aurora 聊聊你在意的人，这里会慢慢亮起来。</div>
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
                  {parseTags(r.emotionTags).slice(0, 4).map((t, i) => <span className="relation-tag" key={i}>{t}</span>)}
                </div>}
                {r.triggerSummary && <p className="relation-summary">{r.triggerSummary}</p>}
              </button>
            </li>)}
          </ul>
          <div className="relation-detail" aria-live="polite">
            {!selected
              ? <div className="network-empty">选一段关系，看它的温度与时间线。</div>
              : busy
                ? <LoadingText busy className="network-empty">正在读取「{selected}」的时间线…</LoadingText>
                : <>
                    {health && <div className="relation-temperature">
                      <div className="relation-temp-row">
                        <span>关系温度</span>
                        <strong>{temperature(health.healthScore).label} · {temperature(health.healthScore).pct}%</strong>
                      </div>
                      <div className="relation-temp-bar"><span style={{ width: `${temperature(health.healthScore).pct}%` }} /></div>
                    </div>}
                    <h3 className="relation-timeline-title">「{selected}」的时间线</h3>
                    {timeline.length === 0
                      ? <div className="network-empty">这段关系还没有足够的时间线记录。</div>
                      : <ol className="relation-timeline" role="list">
                          {timeline.map((p, i) => <li className="relation-timeline-point" key={i}>
                            <time>{new Date(p.timestamp).toLocaleString("zh-CN", { hour12: false })}</time>
                            {p.emotions && <div className="relation-tags">
                              {parseTags(p.emotions).slice(0, 4).map((t, j) => <span className="relation-tag" key={j}>{t}</span>)}
                            </div>}
                            {p.summary && <p className="relation-summary">{p.summary}</p>}
                          </li>)}
                        </ol>}
                  </>}
          </div>
        </div>}
  </section>;
}
