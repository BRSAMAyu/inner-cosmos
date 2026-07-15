import type { CorrectionImpact, UnderstandingClaim } from "../api";

export function UnderstandingCorrection({ claims, oldValue, newValue, impact, busy, onOldValue,
  onNewValue, onPreview, onCancelPreview, onConfirm }: {
  claims: UnderstandingClaim[]; oldValue: string; newValue: string; impact: CorrectionImpact | null; busy: boolean;
  onOldValue: (value: string) => void; onNewValue: (value: string) => void; onPreview: () => void;
  onCancelPreview: () => void; onConfirm: () => void;
}) {
  const activeClaims = claims.filter(claim => claim.status === "ACTIVE");
  return <section className="understanding-space" aria-label="校准 Aurora 对我的理解">
    <div className="understanding-heading"><div><span className="eyebrow">YOUR INNER COSMOS</span><h2>如果这不太是你</h2></div>
      <span>{activeClaims.length} 条由你确认的理解</span></div>
    <p>先预览影响，再决定是否让 Aurora 记住。旧理解不会消失，只会退出“当前事实”。</p>
    <div className="correction-fields">
      <label>Aurora 原先怎样理解（可选）<textarea value={oldValue} onChange={event => onOldValue(event.target.value)} placeholder="例如：你更喜欢独处" /></label>
      <label>更准确的你是<textarea value={newValue} onChange={event => onNewValue(event.target.value)} placeholder="例如：我不是喜欢独处，只是需要先恢复精力" /></label>
    </div>
    {!impact ? <button className="understanding-action" disabled={busy || !newValue.trim()} onClick={onPreview}>预览会改变什么</button> :
      <div className="impact-preview" role="region" aria-label="纠正影响预览">
        <strong>确认后会发生</strong>
        <ul>{impact.impacts.map((item, index) => <li key={`${item.kind}-${item.targetId ?? index}`}><span>{item.label}</span><small>{item.action}</small></li>)}</ul>
        <div className="impact-actions"><button disabled={busy} onClick={onCancelPreview}>返回修改</button><button disabled={busy} onClick={onConfirm}>确认，这是更准确的我</button></div>
      </div>}
    {activeClaims.slice(0, 3).map(claim => <article className="claim-card" key={claim.id}>
      <span>由你确认 · v{claim.version}</span><p>{claim.valueJson.replace(/^"|"$/g, "")}</p>
    </article>)}
  </section>;
}
