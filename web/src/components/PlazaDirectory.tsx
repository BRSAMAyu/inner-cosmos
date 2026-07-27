import { useMemo, useState } from "react";
import type { PublicCapsule } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

type SortMode = "ENERGY" | "FRESH" | "RECENT";
const sortModes: SortMode[] = ["ENERGY", "FRESH", "RECENT"];

const COPY: Record<Locale, {
  aria: string; heading: string; count: (n: number) => string; intro: string;
  searchPlaceholder: string; searchAria: string; sortAria: string; sort: Record<SortMode, string>;
  tagAria: string; all: string; emptyNone: string; emptyFilter: string; energyTitle: string;
  openBusy: string; open: string; showMoreTags: (n: number) => string; showFewerTags: string;
  realPersonPath: string; practiceCapsule: string; showMoreCapsules: (n: number) => string; showFewerCapsules: string;
}> = {
  "zh-CN": {
    aria: "共鸣广场 · 浏览所有公开共鸣体", heading: "选择一个共鸣体开始对话", count: n => `${n} 个公开共鸣体`,
    intro: "共鸣体是由创建者授权资料生成的 AI 对话角色。官方练习共鸣体可以直接体验；用户共鸣体聊过后可以写信给创建者。",
    searchPlaceholder: "搜索名称或话题", searchAria: "搜索公开共鸣体", sortAria: "排序方式",
    sort: { ENERGY: "推荐", FRESH: "内容新鲜度", RECENT: "最近更新" }, tagAria: "按主题筛选", all: "全部",
    emptyNone: "目前没有公开共鸣体。",
    emptyFilter: "没有符合当前筛选的共鸣体。换个主题或清空搜索试试。", energyTitle: "回声能量",
    openBusy: "正在打开", open: "开始对话", showMoreTags: n => `展开另外 ${n} 个主题`, showFewerTags: "收起主题",
    realPersonPath: "用户创建 · 可写慢信", practiceCapsule: "官方练习共鸣体",
    showMoreCapsules: n => `再显示 ${n} 个共鸣体`, showFewerCapsules: "只显示前 6 个"
  },
  "en-SG": {
    aria: "Resonance plaza · browse all public capsules", heading: "Walk into the plaza yourself, don't only wait for recommendations", count: n => `${n} public capsule${n === 1 ? "" : "s"}`,
    intro: "These are all the public facets open to being met. Search by theme or status yourself, instead of only what the system pushes. Each is an authorized AI capsule, not a real person online.",
    searchPlaceholder: "Search a name or the facet it expresses", searchAria: "Search public capsules", sortAria: "Sort by",
    sort: { ENERGY: "Echo energy", FRESH: "Freshness", RECENT: "Recently active" }, tagAria: "Filter by theme", all: "All",
    emptyNone: "No public capsules in the plaza yet. When someone is open to being met, it appears here.",
    emptyFilter: "No capsules match the current filter. Try another theme or clear the search.", energyTitle: "Echo energy",
    openBusy: "Opening", open: "Start a conversation", showMoreTags: n => `Show ${n} more themes`, showFewerTags: "Show fewer themes",
    realPersonPath: "You can write to the person", practiceCapsule: "Official practice facet",
    showMoreCapsules: n => `Browse ${n} more facet${n === 1 ? "" : "s"}`, showFewerCapsules: "Show only the best 6 starting points"
  }
};

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

export function PlazaDirectory({ capsules, activeCapsuleId, busy, onOpenCapsule, locale = "zh-CN" }: {
  capsules: PublicCapsule[]; activeCapsuleId: number | null; busy: boolean;
  onOpenCapsule: (capsule: PublicCapsule) => void; locale?: Locale;
}) {
  const t = COPY[locale];
  const [query, setQuery] = useState("");
  const [tag, setTag] = useState<string | null>(null);
  const [sort, setSort] = useState<SortMode>("ENERGY");
  const [tagsExpanded, setTagsExpanded] = useState(false);
  const [capsulesExpanded, setCapsulesExpanded] = useState(false);

  const allTags = useMemo(() => {
    const frequency = new Map<string, number>();
    capsules.forEach(capsule => parseTags(capsule.publicTags)
      .forEach(name => frequency.set(name, (frequency.get(name) ?? 0) + 1)));
    return [...frequency.entries()]
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], locale))
      .map(([name]) => name);
  }, [capsules, locale]);

  const visibleTags = useMemo(() => {
    if (tagsExpanded || allTags.length <= 12) return allTags;
    const compact = allTags.slice(0, 12);
    return tag && !compact.includes(tag) ? [...compact.slice(0, 11), tag] : compact;
  }, [allTags, tag, tagsExpanded]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    const rank = (capsule: PublicCapsule) => sort === "ENERGY" ? capsule.echoEnergy
      : sort === "FRESH" ? capsule.freshnessScore
      : Date.parse(capsule.lastActivityAt ?? "") || 0;
    return capsules
      .filter(capsule => !tag || parseTags(capsule.publicTags).includes(tag))
      .filter(capsule => !q || `${capsule.pseudonym} ${capsule.intro}`.toLowerCase().includes(q))
      .slice()
      .sort((a, b) => rank(b) - rank(a) || a.pseudonym.localeCompare(b.pseudonym, locale));
  }, [capsules, query, tag, sort]);
  const displayed = capsulesExpanded || visible.length <= 6 ? visible : visible.slice(0, 6);
  const plazaHeading = locale === "en-SG"
    ? "Choose a capsule and start a conversation"
    : "选择一个共鸣体开始对话";
  const plazaIntro = locale === "en-SG"
    ? "A capsule is an AI conversation role built from information its creator chose to share. Official practice capsules are ready to try; user capsules can lead to a slow letter."
    : "共鸣体只使用创建者主动授权的资料。官方练习共鸣体可直接体验；用户共鸣体聊过后可以写慢信。";

  return <section className="plaza-directory" aria-label={t.aria}>
    <div className="resonance-heading"><div><span className="eyebrow">{locale === "en-SG" ? "RESONANCE PLAZA" : "共鸣广场"}</span><h2>{plazaHeading}</h2></div>
      <span>{t.count(capsules.length)}</span></div>
    <p className="resonance-intro">{plazaIntro}</p>
    <div className="plaza-path" aria-label={locale === "en-SG" ? "Possible relationship path" : "可能的关系路径"}>
      <span>{locale === "en-SG" ? "Choose a capsule" : "选择共鸣体"}</span><i aria-hidden="true">→</i>
      <span>{locale === "en-SG" ? "Try a conversation" : "体验对话"}</span><i aria-hidden="true">→</i>
      <span>{locale === "en-SG" ? "Write a slow letter" : "需要时写慢信"}</span>
    </div>

    <div className="plaza-controls">
      <input className="plaza-search" value={query} onChange={event => setQuery(event.target.value)}
        placeholder={t.searchPlaceholder} aria-label={t.searchAria} />
      <div className="plaza-sort" role="group" aria-label={t.sortAria}>
        {sortModes.map(value =>
          <button type="button" key={value} aria-pressed={sort === value}
            className={sort === value ? "active" : ""} onClick={() => setSort(value)}>{t.sort[value]}</button>)}
      </div>
    </div>
    {allTags.length > 0 && <div className="plaza-tags" role="group" aria-label={t.tagAria}>
      <button type="button" aria-pressed={tag === null} className={tag === null ? "active" : ""} onClick={() => setTag(null)}>{t.all}</button>
      {visibleTags.map(tagName => <button type="button" key={tagName} aria-pressed={tag === tagName}
        className={tag === tagName ? "active" : ""} onClick={() => setTag(current => current === tagName ? null : tagName)}>{tagName}</button>)}
      {allTags.length > 12 && <button type="button" className="tag-disclosure"
        aria-expanded={tagsExpanded} onClick={() => setTagsExpanded(value => !value)}>
        {tagsExpanded ? t.showFewerTags : t.showMoreTags(allTags.length - 12)}
      </button>}
    </div>}

    {capsules.length === 0 ? <div className="network-empty">{t.emptyNone}</div> :
      visible.length === 0 ? <div className="network-empty">{t.emptyFilter}</div> :
      <>
      <div className="plaza-grid" role="list">
        {displayed.map(capsule => {
          const tags = parseTags(capsule.publicTags);
          return <article className={activeCapsuleId === capsule.id ? "plaza-card active" : "plaza-card"} role="listitem" key={capsule.id}>
            <div className="plaza-card-head"><strong>{capsule.pseudonym}</strong>
              <span className="plaza-energy" title={t.energyTitle}>✦ {Math.round(capsule.echoEnergy * 100)}%</span></div>
            <span className={capsule.capsuleType === "USER_CAPSULE" ? "plaza-kind real" : "plaza-kind practice"}>
              {capsule.capsuleType === "USER_CAPSULE" ? t.realPersonPath : t.practiceCapsule}
            </span>
            <p className="ugc-text">{capsule.intro}</p>
            <small className="plaza-topic-label">{locale === "en-SG" ? "Topics" : "可聊话题"}</small>
            {tags.length > 0 && <div className="plaza-card-tags">{tags.map(tagName => <span key={tagName}>{tagName}</span>)}</div>}
            <AsyncButton className="resonance-secondary" busy={busy} busyText={t.openBusy}
              onClick={() => onOpenCapsule(capsule)}>{locale === "en-SG" ? "Talk to this capsule" : "开始体验"}</AsyncButton>
          </article>;
        })}
      </div>
      {visible.length > 6 && <button type="button" className="match-rail-toggle"
        aria-expanded={capsulesExpanded} onClick={() => setCapsulesExpanded(value => !value)}>
        {capsulesExpanded ? t.showFewerCapsules : t.showMoreCapsules(visible.length - displayed.length)}
      </button>}
      </>}
  </section>;
}
