import { useState } from "react";
import type { CorrectionImpact, UnderstandingClaim, UserCorrection } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

export type CorrectionTarget = { id: number; label: string };

function formatTime(value: string): string {
  if (!value) return "";
  return value.replace("T", " ").slice(0, 16);
}

const COPY: Record<Locale, {
  aria: string; heading: string; confirmedCount: (n: number) => string; intro: string;
  targetLabel: string; switchToGlobal: string; oldLabel: string; oldPlaceholder: string;
  newLabel: string; newPlaceholder: string; previewBusy: string; previewBtn: string;
  impactAria: string; impactTitle: string; backToEdit: string; saveBusy: string; confirmBtn: string;
  claimTag: (v: number) => string; historyAria: string; historyTitle: string; historyHint: string;
  newUnderstanding: string; reasonPrefix: string; confirmRetireHint: string; rethink: string;
  retiringShort: string; confirmRetire: string; retireBtn: string;
}> = {
  "zh-CN": {
    aria: "校准 Aurora 对我的理解", heading: "如果这不太是你",
    confirmedCount: n => `${n} 条由你确认的理解`,
    intro: "先预览影响，再决定是否让 Aurora 记住。旧理解不会消失，只会退出“当前事实”。",
    targetLabel: "针对这条具体记忆", switchToGlobal: "改为整体理解",
    oldLabel: "Aurora 原先怎样理解（可选）", oldPlaceholder: "例如：你更喜欢独处",
    newLabel: "更准确的你是", newPlaceholder: "例如：我不是喜欢独处，只是需要先恢复精力",
    previewBusy: "正在分析", previewBtn: "预览会改变什么",
    impactAria: "纠正影响预览", impactTitle: "确认后会发生", backToEdit: "返回修改", saveBusy: "正在保存",
    confirmBtn: "确认，这是更准确的我", claimTag: v => `由你确认 · v${v}`,
    historyAria: "我的更正历史", historyTitle: "你确认过的更正",
    historyHint: "每一条都会让 Aurora 重新理解你。如果某条已经过时或不再代表你，可以让它退休——被它替代的理解会重新成为当前事实。",
    newUnderstanding: "（新理解）", reasonPrefix: "理由：", confirmRetireHint: "确定让这条退休吗？",
    rethink: "再想想", retiringShort: "退休中…", confirmRetire: "确认退休", retireBtn: "让这条退休"
  },
  "en-SG": {
    aria: "Calibrate Aurora's understanding of me", heading: "If this isn't quite you",
    confirmedCount: n => `${n} understanding${n === 1 ? "" : "s"} you've confirmed`,
    intro: "Preview the impact first, then decide whether Aurora should remember it. Old understandings don't disappear — they just step out of “current fact”.",
    targetLabel: "About this specific memory", switchToGlobal: "Switch to overall understanding",
    oldLabel: "How Aurora understood it before (optional)", oldPlaceholder: "e.g. you prefer being alone",
    newLabel: "The more accurate you is", newPlaceholder: "e.g. I don't prefer solitude — I just need to recharge first",
    previewBusy: "Analyzing", previewBtn: "Preview what changes",
    impactAria: "Correction impact preview", impactTitle: "After you confirm", backToEdit: "Back to edit", saveBusy: "Saving",
    confirmBtn: "Confirm — this is the more accurate me", claimTag: v => `Confirmed by you · v${v}`,
    historyAria: "My correction history", historyTitle: "Corrections you've confirmed",
    historyHint: "Each one makes Aurora re-understand you. If one is outdated or no longer represents you, you can retire it — the understanding it replaced becomes current fact again.",
    newUnderstanding: "(new understanding)", reasonPrefix: "Reason: ", confirmRetireHint: "Retire this one?",
    rethink: "Reconsider", retiringShort: "Retiring…", confirmRetire: "Confirm retire", retireBtn: "Retire this one"
  }
};

export function UnderstandingCorrection({ claims, oldValue, newValue, impact, busy, target, corrections = [],
  retiringId = null, onOldValue, onNewValue, onPreview, onCancelPreview, onConfirm, onClearTarget, onRetire, locale = "zh-CN" }: {
  claims: UnderstandingClaim[]; oldValue: string; newValue: string; impact: CorrectionImpact | null; busy: boolean;
  target: CorrectionTarget | null; corrections?: UserCorrection[]; retiringId?: number | null;
  onOldValue: (value: string) => void; onNewValue: (value: string) => void;
  onPreview: () => void; onCancelPreview: () => void; onConfirm: () => void; onClearTarget: () => void;
  onRetire?: (id: number) => void; locale?: Locale;
}) {
  const t = COPY[locale];
  const activeClaims = claims.filter(claim => claim.status === "ACTIVE");
  const [confirmingId, setConfirmingId] = useState<number | null>(null);
  return <section className="understanding-space" aria-label={t.aria}>
    <div className="understanding-heading"><div><span className="eyebrow">YOUR INNER COSMOS</span><h2>{t.heading}</h2></div>
      <span>{t.confirmedCount(activeClaims.length)}</span></div>
    <p>{t.intro}</p>
    {target && <div className="correction-target">
      <div><span>{t.targetLabel}</span><strong>{target.label}</strong></div>
      <button type="button" disabled={busy} onClick={onClearTarget}>{t.switchToGlobal}</button>
    </div>}
    <div className="correction-fields">
      {!target && <label>{t.oldLabel}<textarea value={oldValue} onChange={event => onOldValue(event.target.value)} placeholder={t.oldPlaceholder} /></label>}
      <label style={target ? { gridColumn: "1 / -1" } : undefined}>{t.newLabel}<textarea value={newValue} onChange={event => onNewValue(event.target.value)} placeholder={t.newPlaceholder} /></label>
    </div>
    {!impact ? <AsyncButton className="understanding-action" busy={busy} disabled={!newValue.trim()} busyText={t.previewBusy} onClick={onPreview}>{t.previewBtn}</AsyncButton> :
      <div className="impact-preview" role="region" aria-label={t.impactAria}>
        <strong>{t.impactTitle}</strong>
        <ul>{impact.impacts.map((item, index) => <li key={`${item.kind}-${item.targetId ?? index}`}><span>{item.label}</span><small>{item.action}</small></li>)}</ul>
        <div className="impact-actions"><button type="button" disabled={busy} onClick={onCancelPreview}>{t.backToEdit}</button><AsyncButton busy={busy} busyText={t.saveBusy} onClick={onConfirm}>{t.confirmBtn}</AsyncButton></div>
      </div>}
    {activeClaims.slice(0, 3).map(claim => <article className="claim-card" key={claim.id}>
      <span>{t.claimTag(claim.version)}</span><p>{claim.valueJson.replace(/^"|"$/g, "")}</p>
    </article>)}
    {corrections.length > 0 && <div className="correction-history" role="region" aria-label={t.historyAria}>
      <h3>{t.historyTitle}</h3>
      <p className="correction-history-hint">{t.historyHint}</p>
      {corrections.map(item => {
        const retiring = retiringId === item.id;
        return <article className="correction-record" key={item.id}>
          <div className="correction-record-head">
            <span className="correction-record-field">{item.fieldName || "self_understanding"}</span>
            {item.targetType && <span className="correction-record-type">{item.targetType}</span>}
            {item.createdAt && <span className="correction-record-time">{formatTime(item.createdAt)}</span>}
          </div>
          <p className="correction-record-change">
            {item.oldValue && <span className="old">{item.oldValue}</span>}
            {item.oldValue && <span className="arrow">→</span>}
            <span className="new">{item.newValue || t.newUnderstanding}</span>
          </p>
          {item.reason && <p className="correction-record-reason">{t.reasonPrefix}{item.reason}</p>}
          <div className="correction-record-actions">
            {confirmingId === item.id ? <>
              <span className="correction-record-confirm-hint">{t.confirmRetireHint}</span>
              <button type="button" className="btn-retire-cancel" disabled={retiring}
                onClick={() => setConfirmingId(null)}>{t.rethink}</button>
              <AsyncButton className="btn-retire-confirm" busy={retiring} busyText={t.retiringShort}
                onClick={() => onRetire?.(item.id)}>{t.confirmRetire}</AsyncButton>
            </> : <button type="button" className="btn-retire" disabled={retiring}
              onClick={() => setConfirmingId(item.id)}>{retiring ? t.retiringShort : t.retireBtn}</button>}
          </div>
        </article>;
      })}
    </div>}
  </section>;
}
