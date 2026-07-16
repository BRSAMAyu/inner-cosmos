import { useMemo, useState } from "react";
import type { PublicCapsule } from "../api";

type SortMode = "ENERGY" | "FRESH" | "RECENT";
const sortOptions: Array<[SortMode, string]> = [["ENERGY", "回声能量"], ["FRESH", "新鲜度"], ["RECENT", "最近活跃"]];

function parseTags(raw: string | null): string[] {
  if (!raw) return [];
  const trimmed = raw.trim();
  if (trimmed.startsWith("[")) {
    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) return parsed.map(String).filter(Boolean);
    } catch { /* fall through */ }
  }
  return trimmed ? trimmed.split(/[,，]/).map(tag => tag.trim()).filter(Boolean) : [];
}

export function PlazaDirectory({ capsules, activeCapsuleId, busy, onOpenCapsule }: {
  capsules: PublicCapsule[]; activeCapsuleId: number | null; busy: boolean;
  onOpenCapsule: (capsule: PublicCapsule) => void;
}) {
  const [query, setQuery] = useState("");
  const [tag, setTag] = useState<string | null>(null);
  const [sort, setSort] = useState<SortMode>("ENERGY");

  const allTags = useMemo(() => {
    const seen = new Set<string>();
    capsules.forEach(capsule => parseTags(capsule.publicTags).forEach(t => seen.add(t)));
    return [...seen];
  }, [capsules]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    const rank = (capsule: PublicCapsule) => sort === "ENERGY" ? capsule.echoEnergy
      : sort === "FRESH" ? capsule.freshnessScore
      : Date.parse(capsule.lastActivityAt ?? "") || 0;
    return capsules
      .filter(capsule => !tag || parseTags(capsule.publicTags).includes(tag))
      .filter(capsule => !q || `${capsule.pseudonym} ${capsule.intro}`.toLowerCase().includes(q))
      .slice()
      .sort((a, b) => rank(b) - rank(a));
  }, [capsules, query, tag, sort]);

  return <section className="plaza-directory" aria-label="共鸣广场 · 浏览所有公开共鸣体">
    <div className="resonance-heading"><div><span className="eyebrow">RESONANCE PLAZA</span><h2>主动走进广场，而不是只等推荐</h2></div>
      <span>{capsules.length} 个公开共鸣体</span></div>
    <p className="resonance-intro">这里是所有愿意被遇见的公开侧面。你可以按主题或状态自己找，而不是只看系统推给你的。每个都是授权 AI 共鸣体，不是真人实时在线。</p>

    <div className="plaza-controls">
      <input className="plaza-search" value={query} onChange={event => setQuery(event.target.value)}
        placeholder="搜索名字或它想表达的侧面" aria-label="搜索公开共鸣体" />
      <div className="plaza-sort" role="group" aria-label="排序方式">
        {sortOptions.map(([value, label]) =>
          <button type="button" key={value} aria-pressed={sort === value}
            className={sort === value ? "active" : ""} onClick={() => setSort(value)}>{label}</button>)}
      </div>
    </div>
    {allTags.length > 0 && <div className="plaza-tags" role="group" aria-label="按主题筛选">
      <button type="button" aria-pressed={tag === null} className={tag === null ? "active" : ""} onClick={() => setTag(null)}>全部</button>
      {allTags.map(t => <button type="button" key={t} aria-pressed={tag === t}
        className={tag === t ? "active" : ""} onClick={() => setTag(current => current === t ? null : t)}>{t}</button>)}
    </div>}

    {capsules.length === 0 ? <div className="network-empty">广场上还没有公开的共鸣体。当有人愿意被遇见时，它会出现在这里。</div> :
      visible.length === 0 ? <div className="network-empty">没有符合当前筛选的共鸣体。换个主题或清空搜索试试。</div> :
      <div className="plaza-grid" role="list">
        {visible.map(capsule => {
          const tags = parseTags(capsule.publicTags);
          return <article className={activeCapsuleId === capsule.id ? "plaza-card active" : "plaza-card"} role="listitem" key={capsule.id}>
            <div className="plaza-card-head"><strong>{capsule.pseudonym}</strong>
              <span className="plaza-energy" title="回声能量">✦ {Math.round(capsule.echoEnergy)}</span></div>
            <p>{capsule.intro}</p>
            {tags.length > 0 && <div className="plaza-card-tags">{tags.map(t => <span key={t}>{t}</span>)}</div>}
            <button type="button" className="resonance-secondary" disabled={busy}
              onClick={() => onOpenCapsule(capsule)}>开始对话</button>
          </article>;
        })}
      </div>}
  </section>;
}
