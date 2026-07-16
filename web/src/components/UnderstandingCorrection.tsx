import { useState } from "react";
import type { CorrectionImpact, UnderstandingClaim, UserCorrection } from "../api";
import { AsyncButton } from "../loading";

export type CorrectionTarget = { id: number; label: string };

function formatTime(value: string): string {
  if (!value) return "";
  return value.replace("T", " ").slice(0, 16);
}

export function UnderstandingCorrection({ claims, oldValue, newValue, impact, busy, target, corrections = [],
  retiringId = null, onOldValue, onNewValue, onPreview, onCancelPreview, onConfirm, onClearTarget, onRetire }: {
  claims: UnderstandingClaim[]; oldValue: string; newValue: string; impact: CorrectionImpact | null; busy: boolean;
  target: CorrectionTarget | null; corrections?: UserCorrection[]; retiringId?: number | null;
  onOldValue: (value: string) => void; onNewValue: (value: string) => void;
  onPreview: () => void; onCancelPreview: () => void; onConfirm: () => void; onClearTarget: () => void;
  onRetire?: (id: number) => void;
}) {
  const activeClaims = claims.filter(claim => claim.status === "ACTIVE");
  const [confirmingId, setConfirmingId] = useState<number | null>(null);
  return <section className="understanding-space" aria-label="校准 Aurora 对我的理解">
    <div className="understanding-heading"><div><span className="eyebrow">YOUR INNER COSMOS</span><h2>如果这不太是你</h2></div>
      <span>{activeClaims.length} 条由你确认的理解</span></div>
    <p>先预览影响，再决定是否让 Aurora 记住。旧理解不会消失，只会退出“当前事实”。</p>
    {target && <div className="correction-target">
      <div><span>针对这条具体记忆</span><strong>{target.label}</strong></div>
      <button type="button" disabled={busy} onClick={onClearTarget}>改为整体理解</button>
    </div>}
    <div className="correction-fields">
      {!target && <label>Aurora 原先怎样理解（可选）<textarea value={oldValue} onChange={event => onOldValue(event.target.value)} placeholder="例如：你更喜欢独处" /></label>}
      <label style={target ? { gridColumn: "1 / -1" } : undefined}>更准确的你是<textarea value={newValue} onChange={event => onNewValue(event.target.value)} placeholder="例如：我不是喜欢独处，只是需要先恢复精力" /></label>
    </div>
    {!impact ? <AsyncButton className="understanding-action" busy={busy} disabled={!newValue.trim()} busyText="正在分析" onClick={onPreview}>预览会改变什么</AsyncButton> :
      <div className="impact-preview" role="region" aria-label="纠正影响预览">
        <strong>确认后会发生</strong>
        <ul>{impact.impacts.map((item, index) => <li key={`${item.kind}-${item.targetId ?? index}`}><span>{item.label}</span><small>{item.action}</small></li>)}</ul>
        <div className="impact-actions"><button type="button" disabled={busy} onClick={onCancelPreview}>返回修改</button><AsyncButton busy={busy} busyText="正在保存" onClick={onConfirm}>确认，这是更准确的我</AsyncButton></div>
      </div>}
    {activeClaims.slice(0, 3).map(claim => <article className="claim-card" key={claim.id}>
      <span>由你确认 · v{claim.version}</span><p>{claim.valueJson.replace(/^"|"$/g, "")}</p>
    </article>)}
    {corrections.length > 0 && <div className="correction-history" role="region" aria-label="我的更正历史">
      <h3>你确认过的更正</h3>
      <p className="correction-history-hint">每一条都会让 Aurora 重新理解你。如果某条已经过时或不再代表你，可以让它退休——被它替代的理解会重新成为当前事实。</p>
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
            <span className="new">{item.newValue || "（新理解）"}</span>
          </p>
          {item.reason && <p className="correction-record-reason">理由：{item.reason}</p>}
          <div className="correction-record-actions">
            {confirmingId === item.id ? <>
              <span className="correction-record-confirm-hint">确定让这条退休吗？</span>
              <button type="button" className="btn-retire-cancel" disabled={retiring}
                onClick={() => setConfirmingId(null)}>再想想</button>
              <button type="button" className="btn-retire-confirm" disabled={retiring}
                onClick={() => onRetire?.(item.id)}>{retiring ? "退休中…" : "确认退休"}</button>
            </> : <button type="button" className="btn-retire" disabled={retiring}
              onClick={() => setConfirmingId(item.id)}>{retiring ? "退休中…" : "让这条退休"}</button>}
          </div>
        </article>;
      })}
    </div>}
  </section>;
}
