import type { DataRetractionReceipt } from "../api";
import { AsyncButton } from "../loading";

// Track B side of Track A contract delta TA-DELTA-001: the owner-facing "what Aurora stopped using"
// audit trail. Turns the invisible G5 propagation (matching vectors / retrieval embeddings erased
// when you forget a memory, archive a capsule, revoke consent or correct an inference) into a
// visible, trust-building surface. Presentational only — data is loaded by AuroraApp.

const DERIVATIVE_LABEL: Record<DataRetractionReceipt["derivativeType"], string> = {
  CAPSULE_MATCH_INDEX: "共鸣体匹配向量",
  MEMORY_EMBEDDING: "记忆检索向量",
  CAPSULE_PERSONA: "共鸣体人格文本",
  GENOME: "共鸣体基因组"
};

const ACTION_LABEL: Record<DataRetractionReceipt["action"], string> = {
  ERASED: "已彻底清除",
  CLEARED: "已停用",
  REVIEW_REQUIRED: "已进入复核"
};

function whenLabel(iso: string): string {
  const parsed = new Date(iso);
  return Number.isNaN(parsed.getTime()) ? iso : parsed.toLocaleString();
}

export function DataRightsPanel({ receipts, loading, loaded, onLoad }: {
  receipts: DataRetractionReceipt[];
  loading: boolean;
  loaded: boolean;
  onLoad: () => void;
}) {
  return <section className="data-rights" aria-label="数据权利回执">
    <span className="eyebrow">YOUR DATA, ACCOUNTED FOR</span>
    <h3>Aurora 停止使用了什么</h3>
    <p>当你忘记一段记忆、归档共鸣体、撤回授权或纠正理解时，Aurora 会立刻停止使用由它派生出来的向量与画像，并在这里留下不含原文的回执。</p>
    <AsyncButton className="data-rights-refresh" busy={loading} busyText="正在读取回执…"
      onClick={onLoad}>{loaded ? "刷新回执" : "查看数据权利回执"}</AsyncButton>
    {loaded && receipts.length === 0 && <p className="data-rights-empty">还没有任何回执。当你行使数据权利后，这里会出现可核对的记录。</p>}
    {receipts.length > 0 && <ol className="data-rights-list">
      {receipts.map(receipt => <li key={receipt.id}>
        <div className="data-rights-head">
          <span className="data-rights-derivative">{DERIVATIVE_LABEL[receipt.derivativeType] ?? receipt.derivativeType}</span>
          <span className={`data-rights-action ${receipt.action.toLowerCase()}`}>{ACTION_LABEL[receipt.action] ?? receipt.action}</span>
        </div>
        <p className="data-rights-reason">{receipt.reason}</p>
        <small>{receipt.affectedCount} 项 · {whenLabel(receipt.createdAt)}</small>
      </li>)}
    </ol>}
  </section>;
}
