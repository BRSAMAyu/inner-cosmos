import type { DataRetractionReceipt } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

// Track B side of Track A contract delta TA-DELTA-001: the owner-facing "what Aurora stopped using"
// audit trail. Turns the invisible G5 propagation (matching vectors / retrieval embeddings erased
// when you forget a memory, archive a capsule, revoke consent or correct an inference) into a
// visible, trust-building surface. Presentational only — data is loaded by AuroraApp. Bilingual via
// the shared Locale, following the same Record<Locale, dict> convention as ClaimCandidateReview.

const DERIVATIVE_LABEL: Record<Locale, Record<DataRetractionReceipt["derivativeType"], string>> = {
  "zh-CN": {
    CAPSULE_MATCH_INDEX: "共鸣体匹配向量",
    MEMORY_EMBEDDING: "记忆检索向量",
    CAPSULE_PERSONA: "共鸣体人格文本",
    GENOME: "共鸣体基因组"
  },
  "en-SG": {
    CAPSULE_MATCH_INDEX: "capsule matching vector",
    MEMORY_EMBEDDING: "memory retrieval vector",
    CAPSULE_PERSONA: "capsule persona text",
    GENOME: "capsule genome"
  }
};

const ACTION_LABEL: Record<Locale, Record<DataRetractionReceipt["action"], string>> = {
  "zh-CN": { ERASED: "已彻底清除", CLEARED: "已停用", REVIEW_REQUIRED: "已进入复核" },
  "en-SG": { ERASED: "fully erased", CLEARED: "stopped using", REVIEW_REQUIRED: "under review" }
};

const COPY: Record<Locale, {
  eyebrow: string; heading: string; intro: string; load: string; refresh: string; empty: string;
  items: (n: number) => string;
}> = {
  "zh-CN": {
    eyebrow: "YOUR DATA, ACCOUNTED FOR",
    heading: "Aurora 停止使用了什么",
    intro: "当你忘记一段记忆、归档共鸣体、撤回授权或纠正理解时，Aurora 会立刻停止使用由它派生出来的向量与画像，并在这里留下不含原文的回执。",
    load: "查看数据权利回执",
    refresh: "刷新回执",
    empty: "还没有任何回执。当你行使数据权利后，这里会出现可核对的记录。",
    items: n => `${n} 项`
  },
  "en-SG": {
    eyebrow: "YOUR DATA, ACCOUNTED FOR",
    heading: "What Aurora stopped using",
    intro: "When you forget a memory, archive a capsule, revoke consent or correct an understanding, Aurora immediately stops using the vectors and profile derived from it — and leaves a content-free receipt here.",
    load: "View data-rights receipts",
    refresh: "Refresh receipts",
    empty: "No receipts yet. Once you exercise a data right, a verifiable record appears here.",
    items: n => `${n} item${n === 1 ? "" : "s"}`
  }
};

function whenLabel(iso: string, locale: Locale): string {
  const parsed = new Date(iso);
  return Number.isNaN(parsed.getTime()) ? iso : parsed.toLocaleString(locale);
}

export function DataRightsPanel({ receipts, loading, loaded, onLoad, locale = "zh-CN" }: {
  receipts: DataRetractionReceipt[];
  loading: boolean;
  loaded: boolean;
  onLoad: () => void;
  locale?: Locale;
}) {
  const copy = COPY[locale];
  return <section className="data-rights" aria-label={copy.heading}>
    <span className="eyebrow">{copy.eyebrow}</span>
    <h3>{copy.heading}</h3>
    <p>{copy.intro}</p>
    <AsyncButton className="data-rights-refresh" busy={loading} busyText="…"
      onClick={onLoad}>{loaded ? copy.refresh : copy.load}</AsyncButton>
    {loaded && receipts.length === 0 && <p className="data-rights-empty">{copy.empty}</p>}
    {receipts.length > 0 && <ol className="data-rights-list">
      {receipts.map(receipt => <li key={receipt.id}>
        <div className="data-rights-head">
          <span className="data-rights-derivative">{DERIVATIVE_LABEL[locale][receipt.derivativeType] ?? receipt.derivativeType}</span>
          <span className={`data-rights-action ${receipt.action.toLowerCase()}`}>{ACTION_LABEL[locale][receipt.action] ?? receipt.action}</span>
        </div>
        <p className="data-rights-reason">{receipt.reason}</p>
        <small>{copy.items(receipt.affectedCount)} · {whenLabel(receipt.createdAt, locale)}</small>
      </li>)}
    </ol>}
  </section>;
}
