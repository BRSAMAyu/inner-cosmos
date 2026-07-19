import { useState } from "react";
import type { MemoryOperation, StarfieldDetail, StarfieldScene, StarfieldStar } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

const modeOptions: Array<StarfieldScene["mode"]> = ["TIME", "THEME", "PEOPLE"];
const rollbackExcluded = new Set(["FORGET", "LINK", "NO_OP", "ROLLBACK"]);

const COPY: Record<Locale, {
  aria: string; heading: string; count: (n: number) => string; modesAria: string; modeLabel: Record<StarfieldScene["mode"], string>;
  listAria: string; confidence: (pct: number, v: number) => string; revealBusy: string; revealBtn: string; inaccurate: string;
  provAria: string; closeProv: string; curVersion: string; confidenceLabel: string; memLayer: string; whyHere: string;
  changeHistory: (n: number) => string; noChanges: string; downstream: (n: number) => string;
  importance: string; saveBusy: string; saveImportance: string; archiveBusy: string; archiveBtn: string;
  historyAria: string; recentChanges: string; historyHint: string; rolledBack: string; applied: string;
  rollbackBusy: string; rollbackBtn: string; forgetNote: string;
}> = {
  "zh-CN": {
    aria: "记忆星空", heading: "你的记忆不是档案柜", count: n => `${n} 颗当前记忆`, modesAria: "星空视角",
    modeLabel: { TIME: "时间", THEME: "主题", PEOPLE: "人物" },
    listAria: "记忆星空可访问列表", confidence: (p, v) => `置信度 ${p}% · v${v}`, revealBusy: "正在追溯…",
    revealBtn: "查看来源与变化", inaccurate: "这条不准确了", provAria: "记忆来源与变化", closeProv: "关闭记忆来源",
    curVersion: "当前版本", confidenceLabel: "理解置信度", memLayer: "记忆层", whyHere: "为什么它在这里",
    changeHistory: n => `变化历史（${n}）`, noChanges: "还没有后续改动。", downstream: n => `下游状态（${n}）`,
    importance: "重要度", saveBusy: "保存中…", saveImportance: "保存重要度", archiveBusy: "归档中…", archiveBtn: "归档这颗记忆",
    historyAria: "记忆变更历史", recentChanges: "最近的记忆变更",
    historyHint: "撤回会生成一个新版本，不会抹掉发生过的历史。永久忘记不会恢复原文。",
    rolledBack: "已撤回", applied: "已生效", rollbackBusy: "正在撤回…", rollbackBtn: "撤回这次变更",
    forgetNote: "原文已删除，不可恢复"
  },
  "en-SG": {
    aria: "Memory starfield", heading: "Your memory isn't a filing cabinet", count: n => `${n} current memor${n === 1 ? "y" : "ies"}`,
    modesAria: "Starfield view", modeLabel: { TIME: "Time", THEME: "Theme", PEOPLE: "People" },
    listAria: "Memory starfield accessible list", confidence: (p, v) => `Confidence ${p}% · v${v}`, revealBusy: "Tracing…",
    revealBtn: "View source & changes", inaccurate: "This isn't accurate", provAria: "Memory source & changes", closeProv: "Close memory source",
    curVersion: "Current version", confidenceLabel: "Understanding confidence", memLayer: "Memory layer", whyHere: "Why it's here",
    changeHistory: n => `Change history (${n})`, noChanges: "No further changes yet.", downstream: n => `Downstream status (${n})`,
    importance: "Importance", saveBusy: "Saving…", saveImportance: "Save importance", archiveBusy: "Archiving…", archiveBtn: "Archive this memory",
    historyAria: "Memory change history", recentChanges: "Recent memory changes",
    historyHint: "Undoing creates a new version — it never erases what happened. A permanent forget does not restore the original.",
    rolledBack: "Rolled back", applied: "Applied", rollbackBusy: "Rolling back…", rollbackBtn: "Undo this change",
    forgetNote: "The original is deleted and cannot be recovered"
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
      <input type="range" min={0.5} max={2} step={0.1} value={importance} disabled={saving}
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
  onUpdateImportance, onArchive, importanceBusy = null, archiveBusy = null, locale = "zh-CN" }: {
  starfield: StarfieldScene; starfieldBusy: boolean; onChangeMode: (mode: StarfieldScene["mode"]) => void;
  starfieldDetail: StarfieldDetail | null; detailBusy: number | null; onRevealStar: (id: number) => void;
  onCloseDetail: () => void; memoryOperations: MemoryOperation[]; rollbackBusy: number | null;
  onRollback: (operation: MemoryOperation) => void; onCorrectMemory: (star: StarfieldStar) => void;
  onUpdateImportance?: (id: number, importance: number) => void; onArchive?: (id: number) => void;
  importanceBusy?: number | null; archiveBusy?: number | null; locale?: Locale;
}) {
  const t = COPY[locale];
  return <section className="cosmos-space" aria-label={t.aria}>
    <div className="cosmos-heading"><div><span className="eyebrow">MEMORY, ALIVE</span><h2>{t.heading}</h2></div>
      <span>{t.count(starfield.stars.length)}</span></div>
    <div className="cosmos-modes" aria-label={t.modesAria}>
      {modeOptions.map(value =>
        <button type="button" disabled={starfieldBusy} aria-pressed={starfield.mode === value} key={value}
          className={starfield.mode === value ? "active" : ""} onClick={() => onChangeMode(value)}>{t.modeLabel[value]}</button>)}
    </div>
    <p className="cosmos-explanation">{starfield.modeExplanation}</p>
    <div className="cosmos-map" aria-hidden="true">
      {starfield.stars.map(star => <span className="cosmos-star" key={star.id} title={star.ariaLabel} style={{
        left: `${50 + Math.max(-46, Math.min(46, star.x / 2))}%`, top: `${50 + Math.max(-42, Math.min(42, star.y / 2))}%`,
        width: `${Math.max(8, Math.min(24, 8 + star.gravity * 3))}px`, height: `${Math.max(8, Math.min(24, 8 + star.gravity * 3))}px`,
        background: star.color, opacity: Math.max(.45, star.glow ?? .7)
      }} />)}
    </div>
    <div className="cosmos-legend">{Object.entries(starfield.legend).map(([key, value]) => <span key={key}><strong>{key}</strong>{value}</span>)}</div>
    <ol className="cosmos-list" aria-label={t.listAria}>
      {starfield.accessibleList.map(star => <li key={star.id}><div><strong>{star.title}</strong><span>{star.theme} · {star.memoryLayer}</span></div>
        <small>{t.confidence(Math.round(star.confidence * 100), star.versionNo)}</small><p>{star.summary}</p>
        <div className="cosmos-list-actions">
          <AsyncButton disabled={detailBusy !== null} busy={detailBusy === star.id} busyText={t.revealBusy}
            onClick={() => onRevealStar(star.id)}>{t.revealBtn}</AsyncButton>
          <button type="button" className="quiet" onClick={() => onCorrectMemory(star)}>{t.inaccurate}</button>
        </div></li>)}
    </ol>
    {starfieldDetail && <aside className="provenance-panel" aria-label={t.provAria}>
      <div><span className="eyebrow">WHY THIS STAR</span><button type="button" onClick={onCloseDetail} aria-label={t.closeProv}>×</button></div>
      <h3>{starfieldDetail.card.title}</h3><p>{starfieldDetail.provenanceExplanation}</p>
      <dl><div><dt>{t.curVersion}</dt><dd>v{starfieldDetail.card.versionNo}</dd></div><div><dt>{t.confidenceLabel}</dt><dd>{Math.round(starfieldDetail.card.confidence * 100)}%</dd></div><div><dt>{t.memLayer}</dt><dd>{starfieldDetail.card.memoryLayer}</dd></div></dl>
      <details open><summary>{t.whyHere}</summary><p>{starfieldDetail.gravityExplanation}</p></details>
      <details><summary>{t.changeHistory(starfieldDetail.versionHistory.length)}</summary>{starfieldDetail.versionHistory.length === 0 ? <p>{t.noChanges}</p> : starfieldDetail.versionHistory.map(operation => <p key={operation.id}><strong>{operation.operationType}</strong> · v{operation.oldVersion} → v{operation.newVersion} · {operation.status}</p>)}</details>
      <details><summary>{t.downstream(starfieldDetail.projectionReceipts.length)}</summary>{starfieldDetail.projectionReceipts.map(receipt => <p key={receipt.id}><strong>{receipt.projectionType}</strong> · {receipt.status}<br /><small>{receipt.detail}</small></p>)}</details>
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
