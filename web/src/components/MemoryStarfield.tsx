import { useState } from "react";
import type { MemoryOperation, StarfieldDetail, StarfieldScene, StarfieldStar } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

const modeOptions: Array<StarfieldScene["mode"]> = ["TIME", "THEME", "PEOPLE"];
const rollbackExcluded = new Set(["FORGET", "LINK", "NO_OP", "ROLLBACK"]);

type StarPosition = { left: number; top: number };

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value));

/**
 * Turns the service's -100…100 projection into label-safe percentages. Time remains horizontal,
 * but records without an exact occurrence time form a small, deterministic constellation instead
 * of all inheriting the service's "now" coordinate at the far right.
 */
export function layoutMemoryStars(stars: StarfieldStar[], mode: StarfieldScene["mode"]): Map<number, StarPosition> {
  const positions = new Map<number, StarPosition>();
  const occupied: StarPosition[] = [];
  const unknownTime = mode === "TIME"
    ? [...stars].filter(star => !star.occurredAt).sort((a, b) => a.id - b.id)
    : [];
  const unknownIndex = new Map(unknownTime.map((star, index) => [star.id, index]));

  [...stars].sort((a, b) => a.id - b.id).forEach(star => {
    const floatingIndex = unknownIndex.get(star.id);
    let anchor: StarPosition;
    if (floatingIndex !== undefined) {
      const angle = -Math.PI * .82 + floatingIndex * 2.399963229728653;
      const radius = unknownTime.length === 1 ? 0 : 18 + (floatingIndex % 3) * 4;
      anchor = { left: 48 + Math.cos(angle) * radius, top: 50 + Math.sin(angle) * radius * .78 };
    } else {
      anchor = {
        left: 46 + clamp(star.x, -100, 100) * .32,
        top: 50 + clamp(star.y, -100, 100) * .34
      };
    }

    const candidates = mode === "TIME"
      // A classroom burst often settles many memories at nearly the same "now" coordinate.
      // Fan those collisions back across the timeline instead of stacking two label columns
      // against the right edge. The offsets deliberately exceed one 140px label width.
      ? [[0, 0], [-18, -18], [-36, 18], [-18, 28], [-36, -28], [-54, 0],
        [-54, -36], [-54, 36], [4, -30], [4, 30], [-36, 42], [-36, -42]]
      : [[0, 0], [0, -14], [0, 14], [-12, -8], [12, 8], [-12, 8], [12, -8]];
    const candidate = candidates
      .map(([dx, dy]) => ({ left: clamp(anchor.left + dx, 12, 82), top: clamp(anchor.top + dy, 14, 86) }))
      .find(point => occupied.every(other => Math.abs(point.left - other.left) >= 17 || Math.abs(point.top - other.top) >= 15))
      ?? { left: clamp(anchor.left - 15, 12, 82), top: clamp(anchor.top + 16, 14, 86) };
    occupied.push(candidate);
    positions.set(star.id, candidate);
  });
  return positions;
}

const COPY: Record<Locale, {
  aria: string; heading: string; count: (n: number) => string; modesAria: string; modeLabel: Record<StarfieldScene["mode"], string>;
  listAria: string; confidence: (pct: number, v: number) => string; revealBusy: string; revealBtn: string; inaccurate: string;
  provAria: string; closeProv: string; curVersion: string; confidenceLabel: string; memLayer: string; whyHere: string;
  changeHistory: (n: number) => string; noChanges: string; downstream: (n: number) => string;
  observation: string; noDownstream: string; moreMemories: (n: number) => string;
  importance: string; saveBusy: string; saveImportance: string; archiveBusy: string; archiveBtn: string;
  historyAria: string; recentChanges: string; historyHint: string; rolledBack: string; applied: string;
  rollbackBusy: string; rollbackBtn: string; forgetNote: string;
  emptyTitle: Record<StarfieldScene["mode"], string>; emptyBody: Record<StarfieldScene["mode"], string>;
  emptyAction: string; openStar: (title: string) => string;
  modeExplanation: Record<StarfieldScene["mode"], string>;
  legend: Array<[string, string]>;
}> = {
  "zh-CN": {
    aria: "记忆星空", heading: "你的记忆不是档案柜", count: n => `${n} 颗当前记忆`, modesAria: "星空视角",
    modeLabel: { TIME: "时间", THEME: "主题", PEOPLE: "人物" },
    listAria: "记忆星空可访问列表", confidence: (p, v) => `置信度 ${p}% · v${v}`, revealBusy: "正在追溯…",
    revealBtn: "查看来源与变化", inaccurate: "这条不准确了", provAria: "记忆来源与变化", closeProv: "关闭记忆来源",
    curVersion: "当前版本", confidenceLabel: "理解置信度", memLayer: "记忆层", whyHere: "为什么它在这里",
    changeHistory: n => `变化历史（${n}）`, noChanges: "还没有后续改动。", downstream: n => `下游状态（${n}）`,
    observation: "Aurora 目前怎样理解它", noDownstream: "它还没有因为一次修改触发下游重建；被 Aurora 实际调用时，会在对话旁显示“记忆回声”。",
    moreMemories: n => `展开其余 ${n} 颗记忆`,
    importance: "重要度", saveBusy: "保存中…", saveImportance: "保存重要度", archiveBusy: "归档中…", archiveBtn: "归档这颗记忆",
    historyAria: "记忆变更历史", recentChanges: "最近的记忆变更",
    historyHint: "撤回会生成一个新版本，不会抹掉发生过的历史。永久忘记不会恢复原文。",
    rolledBack: "已撤回", applied: "已生效", rollbackBusy: "正在撤回…", rollbackBtn: "撤回这次变更",
    forgetNote: "原文已删除，不可恢复",
    emptyTitle: { TIME: "这里还没有第一颗星", THEME: "还没有足够的记忆形成主题", PEOPLE: "还没有被你确认的人物线索" },
    emptyBody: {
      TIME: "和 Aurora 说完一个真实片段后，点一次“沉淀今天”，这一刻会立即出现在这里。",
      THEME: "当两段以上的记忆出现相似线索，主题视角会把它们聚在一起，而不是重复堆放。",
      PEOPLE: "只有你确认过的关系线索才会进入人物视角；原始对话不会公开。"
    },
    emptyAction: "回到 Aurora 留下第一颗星", openStar: title => `打开记忆：${title}`,
    modeExplanation: {
      TIME: "从左到右沿时间展开；同一时刻会错落排开，时间未定的记忆停留在中央柔和轨道。",
      THEME: "相似线索会聚成主题，帮助你看见反复出现的关注与变化。",
      PEOPLE: "只展示你确认过的人物线索；原始对话不会进入这里。"
    },
    legend: [["尺寸", "情感重力与长期重要性"], ["亮度", "近期活跃程度"], ["边缘", "理解置信度"],
      ["连线", "合并、延续或人物关联"], ["距离", "从右侧列表打开可访问详情"]]
  },
  "en-SG": {
    aria: "Memory starfield", heading: "Your memory isn't a filing cabinet", count: n => `${n} current memor${n === 1 ? "y" : "ies"}`,
    modesAria: "Starfield view", modeLabel: { TIME: "Time", THEME: "Theme", PEOPLE: "People" },
    listAria: "Memory starfield accessible list", confidence: (p, v) => `Confidence ${p}% · v${v}`, revealBusy: "Tracing…",
    revealBtn: "View source & changes", inaccurate: "This isn't accurate", provAria: "Memory source & changes", closeProv: "Close memory source",
    curVersion: "Current version", confidenceLabel: "Understanding confidence", memLayer: "Memory layer", whyHere: "Why it's here",
    changeHistory: n => `Change history (${n})`, noChanges: "No further changes yet.", downstream: n => `Downstream status (${n})`,
    observation: "How Aurora currently understands it", noDownstream: "No edit has triggered a downstream rebuild yet. When Aurora actually retrieves it, a Memory Echo appears beside the conversation.",
    moreMemories: n => `Show ${n} more memor${n === 1 ? "y" : "ies"}`,
    importance: "Importance", saveBusy: "Saving…", saveImportance: "Save importance", archiveBusy: "Archiving…", archiveBtn: "Archive this memory",
    historyAria: "Memory change history", recentChanges: "Recent memory changes",
    historyHint: "Undoing creates a new version — it never erases what happened. A permanent forget does not restore the original.",
    rolledBack: "Rolled back", applied: "Applied", rollbackBusy: "Rolling back…", rollbackBtn: "Undo this change",
    forgetNote: "The original is deleted and cannot be recovered",
    emptyTitle: { TIME: "Your first star has not appeared yet", THEME: "Not enough memories form a theme yet", PEOPLE: "No people cues have been confirmed yet" },
    emptyBody: {
      TIME: "Share one real moment with Aurora, then choose “Settle today” to place it here immediately.",
      THEME: "Once two or more memories share a cue, this view groups them into a theme instead of stacking duplicates.",
      PEOPLE: "Only relationship cues you confirm enter this view; raw conversations never become public."
    },
    emptyAction: "Return to Aurora and leave the first star", openStar: title => `Open memory: ${title}`,
    modeExplanation: {
      TIME: "Time flows left to right. Memories from the same moment fan apart, while those without an exact time rest in a gentle central orbit.",
      THEME: "Related cues gather into themes, revealing recurring concerns and change over time.",
      PEOPLE: "Only people cues you have confirmed appear here; raw conversations never enter this view."
    },
    legend: [["Size", "Emotional gravity and long-term importance"], ["Glow", "Recent activity"],
      ["Edge", "Understanding confidence"], ["Links", "Merge, continuity or people relationships"],
      ["Distance", "Open an accessible detail from the list on the right"]]
  }
};

// Importance/archive controls for a revealed star. Kept as its own component so the slider's
// local state resets cleanly per card (rendered with key={card.id}) instead of leaking a stale
// value from a previously-opened star.
function MemoryDetailActions({ card, importanceBusy, archiveBusy, onUpdateImportance, onArchive, locale }: {
  card: StarfieldDetail["card"]; importanceBusy: number | null; archiveBusy: number | null;
  onUpdateImportance?: (id: number, importance: number) => void; onArchive?: (id: number) => void; locale: Locale;
}) {
  const t = COPY[locale];
  const [importance, setImportance] = useState(card.userImportance ?? 1);
  const saving = importanceBusy === card.id;
  const archiving = archiveBusy === card.id;
  if (!onUpdateImportance && !onArchive) return null;
  return <div className="memory-detail-actions">
    {onUpdateImportance && <label className="importance-control">{t.importance}
      <input type="range" min={0.5} max={10} step={0.1} value={importance} disabled={saving}
        onChange={event => setImportance(Number(event.target.value))} />
      <span className="importance-value">{importance.toFixed(1)}</span>
    </label>}
    <div className="memory-detail-buttons">
      {onUpdateImportance && <AsyncButton busy={saving} busyText={t.saveBusy}
        onClick={() => onUpdateImportance(card.id, importance)}>{t.saveImportance}</AsyncButton>}
      {onArchive && <AsyncButton className="quiet" busy={archiving} busyText={t.archiveBusy}
        onClick={() => onArchive(card.id)}>{t.archiveBtn}</AsyncButton>}
    </div>
  </div>;
}

export function MemoryStarfield({ starfield, starfieldBusy, onChangeMode, starfieldDetail, detailBusy,
  onRevealStar, onCloseDetail, memoryOperations, rollbackBusy, onRollback, onCorrectMemory,
  onUpdateImportance, onArchive, onStartMemory, importanceBusy = null, archiveBusy = null, locale = "zh-CN" }: {
  starfield: StarfieldScene; starfieldBusy: boolean; onChangeMode: (mode: StarfieldScene["mode"]) => void;
  starfieldDetail: StarfieldDetail | null; detailBusy: number | null; onRevealStar: (id: number) => void;
  onCloseDetail: () => void; memoryOperations: MemoryOperation[]; rollbackBusy: number | null;
  onRollback: (operation: MemoryOperation) => void; onCorrectMemory: (star: StarfieldStar) => void;
  onUpdateImportance?: (id: number, importance: number) => void; onArchive?: (id: number) => void;
  onStartMemory?: () => void;
  importanceBusy?: number | null; archiveBusy?: number | null; locale?: Locale;
}) {
  const t = COPY[locale];
  const englishStarText = (value: string) => {
    if (locale !== "en-SG") return value;
    const fixed: Record<string, string> = {
      "关系里的回声": "Echoes in relationships",
      "正在成形的理解": "An understanding taking shape",
      "被命名的感受": "A feeling given a name",
      "需要被轻轻推进的事": "Something that needs a gentle next step",
      "今日沉淀": "Today's reflection",
      "情景记忆": "Episodic memory",
      "语义记忆": "Semantic memory",
      "程序记忆": "Procedural memory",
      "情绪记忆": "Emotional memory",
    };
    return fixed[value] ?? value;
  };
  const renderMemoryRow = (star: StarfieldStar) => <li key={star.id}><div><strong>{englishStarText(star.title)}</strong><span>{englishStarText(star.theme)} · {englishStarText(star.memoryLayer)}</span></div>
    <small>{t.confidence(Math.round(star.confidence * 100), star.versionNo)}</small><p className="ugc-text">{star.summary}</p>
    <div className="cosmos-list-actions">
      <AsyncButton disabled={detailBusy !== null} busy={detailBusy === star.id} busyText={t.revealBusy}
        onClick={() => onRevealStar(star.id)}>{t.revealBtn}</AsyncButton>
      <button type="button" className="quiet" onClick={() => onCorrectMemory(star)}>{t.inaccurate}</button>
    </div></li>;
  const visibleMemories = starfield.accessibleList.slice(0, 3);
  const foldedMemories = starfield.accessibleList.slice(3);
  const starPositions = layoutMemoryStars(starfield.stars, starfield.mode);
  return <section className="cosmos-space" aria-label={t.aria}>
    <div className="cosmos-heading"><div><span className="eyebrow">MEMORY, ALIVE</span><h2>{t.heading}</h2></div>
      <span>{t.count(starfield.stars.length)}</span></div>
    <div className="cosmos-modes" aria-label={t.modesAria}>
      {modeOptions.map(value =>
        <button type="button" disabled={starfieldBusy} aria-pressed={starfield.mode === value} key={value}
          className={starfield.mode === value ? "active" : ""} onClick={() => onChangeMode(value)}>{t.modeLabel[value]}</button>)}
    </div>
    <p className="cosmos-explanation">{t.modeExplanation[starfield.mode]}</p>
    <div className="cosmos-map" aria-label={t.listAria}>
      {starfield.stars.map(star => {
        const position = starPositions.get(star.id) ?? { left: 50, top: 50 };
        return <button type="button" className="cosmos-star" key={star.id}
        aria-label={t.openStar(englishStarText(star.title))}
        title={locale === "en-SG" ? t.openStar(englishStarText(star.title)) : star.ariaLabel} disabled={detailBusy !== null}
        onClick={() => onRevealStar(star.id)} style={{
        left: `${position.left}%`, top: `${position.top}%`,
        color: star.color, opacity: Math.max(.45, star.glow ?? .7)
      }}><span className="cosmos-star-core" aria-hidden="true" style={{
          width: `${Math.max(9, Math.min(26, 9 + star.gravity * 3))}px`,
          height: `${Math.max(9, Math.min(26, 9 + star.gravity * 3))}px`,
          background: star.color
        }} /><span className="cosmos-star-label" aria-hidden="true">{englishStarText(star.title)}</span></button>;
      })}
      {starfield.stars.length === 0 && <div className="cosmos-empty">
        <strong>{t.emptyTitle[starfield.mode]}</strong>
        <p>{t.emptyBody[starfield.mode]}</p>
        {onStartMemory && <button type="button" onClick={onStartMemory}>{t.emptyAction}</button>}
      </div>}
    </div>
    <div className="cosmos-legend">{(locale === "en-SG" ? t.legend : Object.entries(starfield.legend))
      .map(([key, value]) => <span key={key}><strong>{key}</strong>{value}</span>)}</div>
    <ol className="cosmos-list" aria-label={t.listAria}>
      {visibleMemories.map(renderMemoryRow)}
    </ol>
    {foldedMemories.length > 0 && <details className="cosmos-more-memories">
      <summary>{t.moreMemories(foldedMemories.length)}</summary>
      <ol className="cosmos-list">{foldedMemories.map(renderMemoryRow)}</ol>
    </details>}
    {starfieldDetail && <aside className="provenance-panel" aria-label={t.provAria}>
      <div><span className="eyebrow">WHY THIS STAR</span><button type="button" onClick={onCloseDetail} aria-label={t.closeProv}>×</button></div>
      <h3>{englishStarText(starfieldDetail.card.title)}</h3><p>{starfieldDetail.provenanceExplanation}</p>
      <dl><div><dt>{t.curVersion}</dt><dd>v{starfieldDetail.card.versionNo}</dd></div><div><dt>{t.confidenceLabel}</dt><dd>{Math.round(starfieldDetail.card.confidence * 100)}%</dd></div><div><dt>{t.memLayer}</dt><dd>{englishStarText(starfieldDetail.card.memoryLayer)}</dd></div></dl>
      <details open><summary>{t.observation}</summary><p>{starfieldDetail.auroraObservation}</p></details>
      <details open><summary>{t.whyHere}</summary><p>{starfieldDetail.gravityExplanation}</p></details>
      <details><summary>{t.changeHistory(starfieldDetail.versionHistory.length)}</summary>{starfieldDetail.versionHistory.length === 0 ? <p>{t.noChanges}</p> : starfieldDetail.versionHistory.map(operation => <p key={operation.id}><strong>{operation.operationType}</strong> · v{operation.oldVersion} → v{operation.newVersion} · {operation.status}</p>)}</details>
      <details><summary>{t.downstream(starfieldDetail.projectionReceipts.length)}</summary>{starfieldDetail.projectionReceipts.length === 0
        ? <p>{t.noDownstream}</p>
        : starfieldDetail.projectionReceipts.map(receipt => <p key={receipt.id}><strong>{receipt.projectionType}</strong> · {receipt.status}<br /><small>{receipt.detail}</small></p>)}</details>
      <MemoryDetailActions key={starfieldDetail.card.id} card={starfieldDetail.card}
        importanceBusy={importanceBusy} archiveBusy={archiveBusy}
        onUpdateImportance={onUpdateImportance} onArchive={onArchive} locale={locale} />
    </aside>}
    {memoryOperations.length > 0 && <div className="memory-history" aria-label={t.historyAria}>
      <h3>{t.recentChanges}</h3><p>{t.historyHint}</p>
      {memoryOperations.slice(0, 5).map(operation => <article key={operation.id}>
        <div><strong>{operation.operationType}</strong><span>v{operation.oldVersion} → v{operation.newVersion} · {operation.status === "ROLLED_BACK" ? t.rolledBack : t.applied}</span></div>
        {operation.status === "APPLIED" && !rollbackExcluded.has(operation.operationType) &&
          <AsyncButton disabled={rollbackBusy !== null} busy={rollbackBusy === operation.id} busyText={t.rollbackBusy}
            onClick={() => onRollback(operation)}>{t.rollbackBtn}</AsyncButton>}
        {operation.operationType === "FORGET" && <small>{t.forgetNote}</small>}
      </article>)}
    </div>}
  </section>;
}
