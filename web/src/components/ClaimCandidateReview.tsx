import { useState } from "react";
import type { ClaimCandidate } from "../api";
import type { SkillLocale } from "./PsychologySkillStudio";
import { AsyncButton } from "../loading";

// Friendly names for the doc-16 user-model dimensions, per locale, so the user sees "看懂自己" /
// "understand yourself", not database enums.
const CLAIM_TYPE_LABEL: Record<SkillLocale, Record<string, string>> = {
  "zh-CN": {
    FACT: "事实", PREFERENCE: "偏好", VALUE: "价值", RELATION: "关系", EMOTION_PATTERN: "情绪模式",
    HABIT: "习惯", EXPRESSION_STYLE: "表达风格", NEED: "需求", BOUNDARY: "边界", TREND: "变化趋势", UNCERTAINTY: "不确定"
  },
  "en-SG": {
    FACT: "Fact", PREFERENCE: "Preference", VALUE: "Value", RELATION: "Relationship", EMOTION_PATTERN: "Emotion pattern",
    HABIT: "Habit", EXPRESSION_STYLE: "Expression style", NEED: "Need", BOUNDARY: "Boundary", TREND: "Trend", UNCERTAINTY: "Unresolved"
  }
};

const AUTHORITY_LABEL: Record<SkillLocale, Record<string, string>> = {
  "zh-CN": { REPEATED_EXPLICIT: "你多次说过", REPEATED_BEHAVIOR: "反复出现", SINGLE_EXPLICIT: "你说过一次", MODEL_INFERENCE: "Aurora 的推测" },
  "en-SG": { REPEATED_EXPLICIT: "You've said this more than once", REPEATED_BEHAVIOR: "Recurring", SINGLE_EXPLICIT: "You said this once", MODEL_INFERENCE: "Aurora's guess" }
};

const COPY = {
  "zh-CN": {
    aria: "Aurora 对你的新理解", eyebrow: "AURORA IS LEARNING YOU", heading: "我最近好像读到了你",
    pending: (n: number) => `${n} 条待你确认`,
    intro: "这些是我从对话里读到的你。它们还不是“事实”——只有你确认，它才会成为我认真记住、并影响以后每次对话的理解。",
    uncertain: "你也还在确认", known: "已在你的理解中", evidence: "依据：",
    confidence: (pct: number, n: number) => `${pct}% 把握 · 来自 ${n} 处对话`, sure: (pct: number) => `把握 ${pct}%`,
    dismissHint: "忽略这条理解吗？", rethink: "再想想", dismissConfirm: "确认忽略", dismiss: "不太是我",
    confirm: "对，就是我", confirming: "记住中…", dismissing: "处理中…"
  },
  "en-SG": {
    aria: "What Aurora has newly understood about you", eyebrow: "AURORA IS LEARNING YOU", heading: "I think I've been reading you lately",
    pending: (n: number) => `${n} to review`,
    intro: "These are things I picked up about you from our conversations. They aren't facts yet — only once you confirm one does it become something I truly remember and let shape every future chat.",
    uncertain: "You're still unsure too", known: "Already in your understanding", evidence: "Based on: ",
    confidence: (pct: number, n: number) => `${pct}% confidence · from ${n} moments`, sure: (pct: number) => `confidence ${pct}%`,
    dismissHint: "Ignore this?", rethink: "On second thought", dismissConfirm: "Yes, ignore it", dismiss: "Not quite me",
    confirm: "Yes, that's me", confirming: "Remembering…", dismissing: "Working…"
  }
} as const;

/**
 * Campaign B — lets the user see what Aurora automatically inferred about them (with the exact
 * evidence and how sure it is) and confirm it into an authoritative understanding or dismiss it.
 * Purely presentational; bilingual via the shared {@link SkillLocale}.
 */
export function ClaimCandidateReview({ candidates, locale = "zh-CN", busyId = null, onConfirm, onDismiss }: {
  candidates: ClaimCandidate[]; locale?: SkillLocale; busyId?: number | null;
  onConfirm: (id: number) => void; onDismiss: (id: number) => void;
}) {
  const [dismissingId, setDismissingId] = useState<number | null>(null);
  if (candidates.length === 0) return null;
  const t = COPY[locale];
  const typeLabel = CLAIM_TYPE_LABEL[locale];
  const authorityLabel = AUTHORITY_LABEL[locale];
  return <section className="candidate-space" aria-label={t.aria}>
    <div className="candidate-heading">
      <div><span className="eyebrow">{t.eyebrow}</span><h2>{t.heading}</h2></div>
      <span>{t.pending(candidates.length)}</span>
    </div>
    <p>{t.intro}</p>
    {candidates.map(candidate => {
      const busy = busyId === candidate.id;
      const confidencePct = Math.round(Math.max(0, Math.min(1, candidate.confidence)) * 100);
      return <article className="candidate-card" key={candidate.id} aria-label={candidate.value}>
        <div className="candidate-card-head">
          <span className="candidate-type">{typeLabel[candidate.claimType] ?? candidate.claimType}</span>
          {candidate.uncertain && <span className="candidate-uncertain">{t.uncertain}</span>}
          {candidate.alreadyActive && <span className="candidate-known">{t.known}</span>}
          <span className="candidate-authority">{authorityLabel[candidate.authorityLevel] ?? candidate.authorityLevel}</span>
        </div>
        <p className="candidate-value">{candidate.value}</p>
        {candidate.evidenceText && <p className="candidate-evidence">{t.evidence}{candidate.evidenceText}</p>}
        <div className="candidate-confidence" role="img" aria-label={t.sure(confidencePct)}>
          <span className="candidate-confidence-bar"><span style={{ width: `${confidencePct}%` }} /></span>
          <small>{t.confidence(confidencePct, candidate.provenanceMessageIds.length)}</small>
        </div>
        <div className="candidate-actions">
          {dismissingId === candidate.id ? <>
            <span className="candidate-confirm-hint">{t.dismissHint}</span>
            <button type="button" className="btn-candidate-cancel" disabled={busy}
              onClick={() => setDismissingId(null)}>{t.rethink}</button>
            <AsyncButton className="btn-candidate-dismiss-confirm" busy={busy} busyText={t.dismissing}
              onClick={() => onDismiss(candidate.id)}>{t.dismissConfirm}</AsyncButton>
          </> : <>
            <button type="button" className="btn-candidate-dismiss" disabled={busy}
              onClick={() => setDismissingId(candidate.id)}>{t.dismiss}</button>
            <AsyncButton className="btn-candidate-confirm" busy={busy} busyText={t.confirming}
              onClick={() => onConfirm(candidate.id)}>{t.confirm}</AsyncButton>
          </>}
        </div>
      </article>;
    })}
  </section>;
}
