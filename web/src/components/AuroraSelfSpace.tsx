import type { SelfEvolution } from "../api";
import type { Locale } from "../i18n";

const COPY: Record<Locale, {
  aria: string; heading: string; forming: (pct: number) => string; previewChange: string;
  statusDraft: string; statusEvaluated: string; statusActivated: string; statusRejected: string;
  whyResult: string; scores: (cont: number, qual: number, dec: string) => string;
  runEval: string; allowRemember: string; backTo: (v: number, narr: string) => string;
}> = {
  "zh-CN": {
    aria: "Aurora 的连续自我", heading: "她最近学会了什么", forming: p => `正在形成的理解 · ${p}%`,
    previewChange: "预览这次变化", statusDraft: "等待沙盒评测", statusEvaluated: "评测通过，等你确认",
    statusActivated: "已经成为 Aurora 的一部分", statusRejected: "没有通过边界评测", whyResult: "为什么得到这个结果",
    scores: (c, q, d) => `连续性 ${c} · 质量 ${q} · 安全 ${d}`, runEval: "运行变化评测",
    allowRemember: "允许她记住这次成长", backTo: (v, n) => `回到 v${v} · ${n}`
  },
  "en-SG": {
    aria: "Aurora's continuous self", heading: "What she's recently learned", forming: p => `An understanding taking shape · ${p}%`,
    previewChange: "Preview this change", statusDraft: "Awaiting sandbox evaluation", statusEvaluated: "Passed evaluation — awaiting your confirmation",
    statusActivated: "Now part of Aurora", statusRejected: "Did not pass the boundary evaluation", whyResult: "Why this result",
    scores: (c, q, d) => `Continuity ${c} · Quality ${q} · Safety ${d}`, runEval: "Run change evaluation",
    allowRemember: "Let her remember this growth", backTo: (v, n) => `Return to v${v} · ${n}`
  }
};

export function AuroraSelfSpace({ evolution, busy, onPropose, onEvaluate, onActivate, onRollback, locale = "zh-CN" }: {
  evolution: SelfEvolution;
  busy: boolean;
  onPropose: (candidateId: number) => void;
  onEvaluate: (proposalId: number) => void;
  onActivate: (proposalId: number) => void;
  onRollback: (versionId: number, versionNo: number) => void;
  locale?: Locale;
}) {
  const t = COPY[locale];
  const active = evolution.versions.find(version => version.status === "ACTIVE");
  const statusLabel = (status: string) => status === "DRAFT" ? t.statusDraft
    : status === "EVALUATED" ? t.statusEvaluated : status === "ACTIVATED" ? t.statusActivated : t.statusRejected;
  return <section className="self-space" aria-label={t.aria}>
    <div className="self-heading"><div><span className="eyebrow">AURORA, BECOMING</span><h2>{t.heading}</h2></div>
      <span className="self-version">v{active?.versionNo ?? 1}</span></div>
    <p className="self-narrative">{active?.publicNarrative}</p>
    {evolution.candidates.filter(candidate => !evolution.proposals.some(proposal => proposal.sourceReflectionId === candidate.id)).map(candidate =>
      <article className="self-card candidate" key={candidate.id}>
        <span>{t.forming(Math.round(candidate.confidence * 100))}</span><p>{candidate.proposedBelief}</p>
        <button disabled={busy} onClick={() => onPropose(candidate.id)}>{t.previewChange}</button>
      </article>)}
    {evolution.proposals.slice(0, 3).map(proposal => <article className={`self-card ${proposal.status.toLowerCase()}`} key={proposal.id}>
      <span>{statusLabel(proposal.status)}</span>
      <p>{proposal.proposedBelief}</p>
      {proposal.evaluation && <details><summary>{t.whyResult}</summary>
        <p>{proposal.evaluation.sandboxBefore}</p><p>{proposal.evaluation.sandboxAfter}</p>
        <small>{t.scores(Math.round(proposal.evaluation.continuityScore * 100), Math.round(proposal.evaluation.qualityScore * 100), proposal.evaluation.decision)}</small>
      </details>}
      {proposal.status === "DRAFT" && <button disabled={busy} onClick={() => onEvaluate(proposal.id)}>{t.runEval}</button>}
      {proposal.status === "EVALUATED" && <button disabled={busy} onClick={() => onActivate(proposal.id)}>{t.allowRemember}</button>}
    </article>)}
    {evolution.versions.filter(version => version.status === "RETIRED").slice(0, 2).map(version =>
      <button className="version-history" disabled={busy} key={version.id} onClick={() => onRollback(version.id, version.versionNo)}>
        {t.backTo(version.versionNo, version.publicNarrative)}
      </button>)}
  </section>;
}
