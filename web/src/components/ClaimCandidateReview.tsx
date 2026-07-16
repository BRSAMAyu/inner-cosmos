import { useState } from "react";
import type { ClaimCandidate } from "../api";

// Friendly Chinese names for the doc-16 user-model dimensions so the user sees "看懂自己",
// not database enums.
const CLAIM_TYPE_LABEL: Record<string, string> = {
  FACT: "事实", PREFERENCE: "偏好", VALUE: "价值", RELATION: "关系",
  EMOTION_PATTERN: "情绪模式", HABIT: "习惯", EXPRESSION_STYLE: "表达风格",
  NEED: "需求", BOUNDARY: "边界", TREND: "变化趋势", UNCERTAINTY: "不确定"
};

const AUTHORITY_LABEL: Record<string, string> = {
  REPEATED_EXPLICIT: "你多次说过", REPEATED_BEHAVIOR: "反复出现", SINGLE_EXPLICIT: "你说过一次",
  MODEL_INFERENCE: "Aurora 的推测"
};

/**
 * Campaign B — lets the user see what Aurora automatically inferred about them (with the exact
 * evidence and how sure it is) and confirm it into an authoritative understanding or dismiss it.
 * Purely presentational: the parent owns the data and the confirm/dismiss side effects.
 */
export function ClaimCandidateReview({ candidates, busyId = null, onConfirm, onDismiss }: {
  candidates: ClaimCandidate[]; busyId?: number | null;
  onConfirm: (id: number) => void; onDismiss: (id: number) => void;
}) {
  const [dismissingId, setDismissingId] = useState<number | null>(null);
  if (candidates.length === 0) return null;
  return <section className="candidate-space" aria-label="Aurora 对你的新理解">
    <div className="candidate-heading">
      <div><span className="eyebrow">AURORA IS LEARNING YOU</span><h2>我最近好像读到了你</h2></div>
      <span>{candidates.length} 条待你确认</span>
    </div>
    <p>这些是我从对话里读到的你。它们还不是“事实”——只有你确认，它才会成为我认真记住、并影响以后每次对话的理解。</p>
    {candidates.map(candidate => {
      const busy = busyId === candidate.id;
      const confidencePct = Math.round(Math.max(0, Math.min(1, candidate.confidence)) * 100);
      return <article className="candidate-card" key={candidate.id} aria-label={`候选理解：${candidate.value}`}>
        <div className="candidate-card-head">
          <span className="candidate-type">{CLAIM_TYPE_LABEL[candidate.claimType] ?? candidate.claimType}</span>
          {candidate.uncertain && <span className="candidate-uncertain">你也还在确认</span>}
          {candidate.alreadyActive && <span className="candidate-known">已在你的理解中</span>}
          <span className="candidate-authority">{AUTHORITY_LABEL[candidate.authorityLevel] ?? candidate.authorityLevel}</span>
        </div>
        <p className="candidate-value">{candidate.value}</p>
        {candidate.evidenceText && <p className="candidate-evidence">依据：{candidate.evidenceText}</p>}
        <div className="candidate-confidence" role="img" aria-label={`把握 ${confidencePct}%`}>
          <span className="candidate-confidence-bar"><span style={{ width: `${confidencePct}%` }} /></span>
          <small>{confidencePct}% 把握 · 来自 {candidate.provenanceMessageIds.length} 处对话</small>
        </div>
        <div className="candidate-actions">
          {dismissingId === candidate.id ? <>
            <span className="candidate-confirm-hint">忽略这条理解吗？</span>
            <button type="button" className="btn-candidate-cancel" disabled={busy}
              onClick={() => setDismissingId(null)}>再想想</button>
            <button type="button" className="btn-candidate-dismiss-confirm" disabled={busy}
              onClick={() => onDismiss(candidate.id)}>{busy ? "处理中…" : "确认忽略"}</button>
          </> : <>
            <button type="button" className="btn-candidate-dismiss" disabled={busy}
              onClick={() => setDismissingId(candidate.id)}>不太是我</button>
            <button type="button" className="btn-candidate-confirm" disabled={busy}
              onClick={() => onConfirm(candidate.id)}>{busy ? "记住中…" : "对，就是我"}</button>
          </>}
        </div>
      </article>;
    })}
  </section>;
}
