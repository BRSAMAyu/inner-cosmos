import type { MemoryOperation, StarfieldDetail, StarfieldScene, StarfieldStar } from "../api";

const modeOptions: Array<[StarfieldScene["mode"], string]> = [["TIME", "时间"], ["THEME", "主题"], ["PEOPLE", "人物"]];
const rollbackExcluded = new Set(["FORGET", "LINK", "NO_OP", "ROLLBACK"]);

export function MemoryStarfield({ starfield, starfieldBusy, onChangeMode, starfieldDetail, detailBusy,
  onRevealStar, onCloseDetail, memoryOperations, rollbackBusy, onRollback, onCorrectMemory }: {
  starfield: StarfieldScene; starfieldBusy: boolean; onChangeMode: (mode: StarfieldScene["mode"]) => void;
  starfieldDetail: StarfieldDetail | null; detailBusy: number | null; onRevealStar: (id: number) => void;
  onCloseDetail: () => void; memoryOperations: MemoryOperation[]; rollbackBusy: number | null;
  onRollback: (operation: MemoryOperation) => void; onCorrectMemory: (star: StarfieldStar) => void;
}) {
  return <section className="cosmos-space" aria-label="记忆星空">
    <div className="cosmos-heading"><div><span className="eyebrow">MEMORY, ALIVE</span><h2>你的记忆不是档案柜</h2></div>
      <span>{starfield.stars.length} 颗当前记忆</span></div>
    <div className="cosmos-modes" aria-label="星空视角">
      {modeOptions.map(([value, label]) =>
        <button type="button" disabled={starfieldBusy} aria-pressed={starfield.mode === value} key={value}
          className={starfield.mode === value ? "active" : ""} onClick={() => onChangeMode(value)}>{label}</button>)}
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
    <ol className="cosmos-list" aria-label="记忆星空可访问列表">
      {starfield.accessibleList.map(star => <li key={star.id}><div><strong>{star.title}</strong><span>{star.theme} · {star.memoryLayer}</span></div>
        <small>置信度 {Math.round(star.confidence * 100)}% · v{star.versionNo}</small><p>{star.summary}</p>
        <div className="cosmos-list-actions">
          <button type="button" disabled={detailBusy !== null} onClick={() => onRevealStar(star.id)}>{detailBusy === star.id ? "正在追溯…" : "查看来源与变化"}</button>
          <button type="button" className="quiet" onClick={() => onCorrectMemory(star)}>这条不准确了</button>
        </div></li>)}
    </ol>
    {starfieldDetail && <aside className="provenance-panel" aria-label="记忆来源与变化">
      <div><span className="eyebrow">WHY THIS STAR</span><button type="button" onClick={onCloseDetail} aria-label="关闭记忆来源">×</button></div>
      <h3>{starfieldDetail.card.title}</h3><p>{starfieldDetail.provenanceExplanation}</p>
      <dl><div><dt>当前版本</dt><dd>v{starfieldDetail.card.versionNo}</dd></div><div><dt>理解置信度</dt><dd>{Math.round(starfieldDetail.card.confidence * 100)}%</dd></div><div><dt>记忆层</dt><dd>{starfieldDetail.card.memoryLayer}</dd></div></dl>
      <details open><summary>为什么它在这里</summary><p>{starfieldDetail.gravityExplanation}</p></details>
      <details><summary>变化历史（{starfieldDetail.versionHistory.length}）</summary>{starfieldDetail.versionHistory.length === 0 ? <p>还没有后续改动。</p> : starfieldDetail.versionHistory.map(operation => <p key={operation.id}><strong>{operation.operationType}</strong> · v{operation.oldVersion} → v{operation.newVersion} · {operation.status}</p>)}</details>
      <details><summary>下游状态（{starfieldDetail.projectionReceipts.length}）</summary>{starfieldDetail.projectionReceipts.map(receipt => <p key={receipt.id}><strong>{receipt.projectionType}</strong> · {receipt.status}<br /><small>{receipt.detail}</small></p>)}</details>
    </aside>}
    {memoryOperations.length > 0 && <div className="memory-history" aria-label="记忆变更历史">
      <h3>最近的记忆变更</h3><p>撤回会生成一个新版本，不会抹掉发生过的历史。永久忘记不会恢复原文。</p>
      {memoryOperations.slice(0, 5).map(operation => <article key={operation.id}>
        <div><strong>{operation.operationType}</strong><span>v{operation.oldVersion} → v{operation.newVersion} · {operation.status === "ROLLED_BACK" ? "已撤回" : "已生效"}</span></div>
        {operation.status === "APPLIED" && !rollbackExcluded.has(operation.operationType) &&
          <button type="button" disabled={rollbackBusy !== null} onClick={() => onRollback(operation)}>{rollbackBusy === operation.id ? "正在撤回…" : "撤回这次变更"}</button>}
        {operation.operationType === "FORGET" && <small>原文已删除，不可恢复</small>}
      </article>)}
    </div>}
  </section>;
}
