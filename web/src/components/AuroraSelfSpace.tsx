import type { SelfEvolution } from "../api";

export function AuroraSelfSpace({ evolution, busy, onPropose, onEvaluate, onActivate, onRollback }: {
  evolution: SelfEvolution;
  busy: boolean;
  onPropose: (candidateId: number) => void;
  onEvaluate: (proposalId: number) => void;
  onActivate: (proposalId: number) => void;
  onRollback: (versionId: number, versionNo: number) => void;
}) {
  const active = evolution.versions.find(version => version.status === "ACTIVE");
  return <section className="self-space" aria-label="Aurora 的连续自我">
    <div className="self-heading"><div><span className="eyebrow">AURORA, BECOMING</span><h2>她最近学会了什么</h2></div>
      <span className="self-version">v{active?.versionNo ?? 1}</span></div>
    <p className="self-narrative">{active?.publicNarrative}</p>
    {evolution.candidates.filter(candidate => !evolution.proposals.some(proposal => proposal.sourceReflectionId === candidate.id)).map(candidate =>
      <article className="self-card candidate" key={candidate.id}>
        <span>正在形成的理解 · {Math.round(candidate.confidence * 100)}%</span><p>{candidate.proposedBelief}</p>
        <button disabled={busy} onClick={() => onPropose(candidate.id)}>预览这次变化</button>
      </article>)}
    {evolution.proposals.slice(0, 3).map(proposal => <article className={`self-card ${proposal.status.toLowerCase()}`} key={proposal.id}>
      <span>{proposal.status === "DRAFT" ? "等待沙盒评测" : proposal.status === "EVALUATED" ? "评测通过，等你确认" : proposal.status === "ACTIVATED" ? "已经成为 Aurora 的一部分" : "没有通过边界评测"}</span>
      <p>{proposal.proposedBelief}</p>
      {proposal.evaluation && <details><summary>为什么得到这个结果</summary>
        <p>{proposal.evaluation.sandboxBefore}</p><p>{proposal.evaluation.sandboxAfter}</p>
        <small>连续性 {Math.round(proposal.evaluation.continuityScore * 100)} · 质量 {Math.round(proposal.evaluation.qualityScore * 100)} · 安全 {proposal.evaluation.decision}</small>
      </details>}
      {proposal.status === "DRAFT" && <button disabled={busy} onClick={() => onEvaluate(proposal.id)}>运行变化评测</button>}
      {proposal.status === "EVALUATED" && <button disabled={busy} onClick={() => onActivate(proposal.id)}>允许她记住这次成长</button>}
    </article>)}
    {evolution.versions.filter(version => version.status === "RETIRED").slice(0, 2).map(version =>
      <button className="version-history" disabled={busy} key={version.id} onClick={() => onRollback(version.id, version.versionNo)}>
        回到 v{version.versionNo} · {version.publicNarrative}
      </button>)}
  </section>;
}
