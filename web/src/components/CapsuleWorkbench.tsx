import { useState } from "react";
import type { CapsuleBoundary, CapsuleFidelitySummary, CapsuleGenomeVersion, CapsulePreview, CapsuleSandbox, EchoCapsule, MemoryCard } from "../api";
import { AsyncButton } from "../loading";

const sandboxRatings: Array<[string, string]> = [
  ["LIKE_ME", "像我"], ["NOT_ME", "不像我"], ["FACT_WRONG", "事实不对"], ["TOO_EXPOSED", "太暴露"], ["TONE_WRONG", "语气不对"]
];
const blockedScopes = new Set(["LOCAL_ONLY", "NO_EXTERNAL_PROCESSING"]);
const privacyOptions: Array<[string, string]> = [["STRICT", "严格保护"], ["BALANCED", "均衡保护"], ["OPEN", "开放一点"]];

function fidelityLabel(summary: CapsuleFidelitySummary | undefined): string | null {
  if (!summary || summary.totalRatings === 0 || summary.fidelityScore === null) return null;
  return `${summary.totalRatings} 次反馈 · ${Math.round(summary.fidelityScore * 100)}% 像我`;
}

// Boundary topics are stored as a free string; the compiler seeds them as a JSON array
// (["自我观察",…]) while a hand-edited value is plain comma-separated text. Show either cleanly.
function topicsToText(value: string | null): string {
  if (!value) return "";
  const trimmed = value.trim();
  if (trimmed.startsWith("[")) {
    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) return parsed.map(String).join("，");
    } catch { /* not JSON — fall through and show as-is */ }
  }
  return value;
}

// Owner-private boundary editor for a selected capsule: what the public persona may/won't discuss,
// how strict its masking is, the daily turn cap, and whether visitors can request a slow letter.
// Local state is seeded from the loaded boundary and reset per capsule via key={capsuleId}.
function CapsuleBoundaryEditor({ boundary, boundaryBusy, onSaveBoundary }: {
  boundary: CapsuleBoundary | null; boundaryBusy: boolean;
  onSaveBoundary: (boundary: Partial<CapsuleBoundary>) => void;
}) {
  const [allowTopics, setAllowTopics] = useState(topicsToText(boundary?.allowTopics ?? null));
  const [blockedTopics, setBlockedTopics] = useState(topicsToText(boundary?.blockedTopics ?? null));
  const [maxTurns, setMaxTurns] = useState(boundary?.maxConversationTurns ?? 30);
  const [allowLetter, setAllowLetter] = useState(boundary?.allowLetterRequest ?? true);
  const [privacy, setPrivacy] = useState(boundary?.privacyLevel ?? "STRICT");
  return <>
    <div className="capsule-step"><span>3</span><div><strong>设定它在对话里的边界</strong><small>这些只有你能改：它可以谈什么、要避开什么、每天最多聊几轮、别人能否请求给你写慢信。</small></div></div>
    <div className="boundary-editor">
      <label>允许谈论的话题<input value={allowTopics} onChange={event => setAllowTopics(event.target.value)} placeholder="例如：自我观察, 日常支持, 温柔建议" /></label>
      <label>明确避开的话题<input value={blockedTopics} onChange={event => setBlockedTopics(event.target.value)} placeholder="例如：真实姓名, 诊断承诺, 强迫即时回应" /></label>
      <div className="boundary-row">
        <label>隐私等级<select value={privacy} onChange={event => setPrivacy(event.target.value)}>
          {privacyOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label>每日对话轮数<input type="number" min={2} max={50} value={maxTurns}
          onChange={event => setMaxTurns(Number(event.target.value))} /></label>
      </div>
      <label className="boundary-check"><input type="checkbox" checked={allowLetter}
        onChange={event => setAllowLetter(event.target.checked)} />允许访客读完后请求给你写一封慢信</label>
      <button type="button" className="resonance-secondary" disabled={boundaryBusy}
        onClick={() => onSaveBoundary({ allowTopics, blockedTopics, maxConversationTurns: maxTurns, allowLetterRequest: allowLetter, privacyLevel: privacy })}>
        {boundaryBusy ? "保存中…" : "保存边界设置"}</button>
    </div>
  </>;
}

export function CapsuleWorkbench({ capsules, selectedCapsuleId, selectedCapsule, selectableMemories, selectedMemoryIds,
  capsuleName, capsuleIntro, capsulePreview, capsuleBusy, genomeHistory, fidelitySummary, sandboxQuestion, sandboxResult, sandboxFeedback,
  onSelectCapsule, onToggleMemory, onCapsuleName, onCapsuleIntro, onPreviewNewCapsule, onCancelPreview, onCreateCapsule,
  onRecompile, onSandboxQuestion, onRunSandbox, onRateSandbox, onPublish, onPause, onArchive,
  boundary = null, boundaryBusy = false, onSaveBoundary }: {
  capsules: EchoCapsule[]; selectedCapsuleId: number | null; selectedCapsule: EchoCapsule | null;
  selectableMemories: MemoryCard[]; selectedMemoryIds: number[]; capsuleName: string; capsuleIntro: string;
  capsulePreview: CapsulePreview | null; capsuleBusy: boolean; genomeHistory: CapsuleGenomeVersion[];
  fidelitySummary: CapsuleFidelitySummary[]; sandboxQuestion: string; sandboxResult: CapsuleSandbox | null; sandboxFeedback: string | null;
  onSelectCapsule: (id: number | null) => void; onToggleMemory: (id: number) => void;
  onCapsuleName: (value: string) => void; onCapsuleIntro: (value: string) => void;
  onPreviewNewCapsule: () => void; onCancelPreview: () => void; onCreateCapsule: () => void;
  onRecompile: () => void; onSandboxQuestion: (value: string) => void; onRunSandbox: () => void;
  onRateSandbox: (rating: string) => void; onPublish: () => void; onPause: () => void; onArchive: () => void;
  boundary?: CapsuleBoundary | null; boundaryBusy?: boolean; onSaveBoundary?: (boundary: Partial<CapsuleBoundary>) => void;
}) {
  const activeFidelity = fidelityLabel(fidelitySummary.find(summary => summary.genomeVersionId === genomeHistory[0]?.id));
  return <section className="resonance-space" aria-label="共鸣体创建与像不像我沙盒">
    <div className="resonance-heading"><div><span className="eyebrow">YOUR RESONANCE</span><h2>先确认像不像你，再让别人遇见</h2></div>
      <span>{capsules.filter(capsule => capsule.visibilityStatus !== "ARCHIVED").length} 个共鸣体</span></div>
    <p className="resonance-intro">共鸣体是你明确授权的一个侧面，不是你的账号，也不会假装你正在实时回复。每次重编译都形成新版本。</p>

    {capsules.length > 0 && <div className="capsule-tabs" role="tablist" aria-label="我的共鸣体">
      {capsules.filter(capsule => capsule.visibilityStatus !== "ARCHIVED").map(capsule =>
        <button type="button" role="tab" aria-selected={selectedCapsuleId === capsule.id} className={selectedCapsuleId === capsule.id ? "active" : ""}
          key={capsule.id} onClick={() => onSelectCapsule(capsule.id)}>{capsule.pseudonym}<small>{capsule.visibilityStatus === "PUBLIC" ? "已公开" : capsule.visibilityStatus === "NEEDS_REVIEW" ? "需复核" : "仅自己"}</small></button>)}
      <button type="button" role="tab" aria-selected={selectedCapsuleId === null} className={selectedCapsuleId === null ? "active new" : "new"}
        onClick={() => onSelectCapsule(null)}>＋ 新建一个侧面</button>
    </div>}

    {!selectedCapsule ? <div className="capsule-create" role="region" aria-label="创建共鸣体">
      <div className="capsule-step"><span>1</span><div><strong>你愿意让它使用哪些记忆？</strong><small>这里的选择不会自动公开；LOCAL_ONLY 与禁止外部处理的内容不能进入 Genome。</small></div></div>
      <div className="memory-consent-list">{selectableMemories.length === 0 ? <p>还没有可选择的当前记忆。你也可以创建一个不读取记忆的通用侧面。</p> : selectableMemories.slice(0, 10).map(memory => {
        const blocked = blockedScopes.has((memory.consentScope ?? "").toUpperCase());
        return <label className={blocked ? "blocked" : ""} key={memory.id}><input type="checkbox" disabled={blocked || capsuleBusy}
          checked={selectedMemoryIds.includes(memory.id)} onChange={() => onToggleMemory(memory.id)} /><span><strong>{memory.title}</strong><small>{blocked ? "不会用于共鸣体" : `${memory.memoryLayer ?? "记忆"} · v${memory.versionNo}`}</small></span></label>;
      })}</div>
      <div className="capsule-step"><span>2</span><div><strong>它表达你的哪一部分？</strong><small>名字和说明面向访客，但创建后仍保持私密，直到你主动发布。</small></div></div>
      <div className="capsule-fields"><label>共鸣体名字<input value={capsuleName} onChange={event => onCapsuleName(event.target.value)} placeholder="例如：雨后仍愿意开口的人" /></label>
        <label>希望它保留的侧面<textarea value={capsuleIntro} onChange={event => onCapsuleIntro(event.target.value)} placeholder="例如：面对关系误解时，我会先沉默整理，再清楚说出边界。" /></label></div>
      {!capsulePreview ? <AsyncButton className="resonance-primary" busy={capsuleBusy} busyText="正在脱敏" onClick={onPreviewNewCapsule}>先看严格脱敏预览</AsyncButton> :
        <div className="capsule-preview" aria-label="共鸣体授权预览"><span className="eyebrow">WHAT IT MAY USE</span><p>{capsulePreview.abstractSummary}</p>
          <div className="preview-tags">{capsulePreview.publicTags.map(tag => <span key={tag}>{tag}</span>)}</div>
          {capsulePreview.removedSensitiveItems.length > 0 && <small>已移除：{capsulePreview.removedSensitiveItems.join("、")}</small>}
          {capsulePreview.riskWarnings.map(warning => <p className="preview-warning" key={warning}>{warning}</p>)}
          <div className="resonance-actions"><button type="button" disabled={capsuleBusy} onClick={onCancelPreview}>返回修改</button><AsyncButton className="resonance-primary" busy={capsuleBusy} busyText="正在编译" onClick={onCreateCapsule}>编译为私密版本</AsyncButton></div>
        </div>}
    </div> : <div className="capsule-workbench">
      <div className="capsule-summary"><div><span className="capsule-status">{selectedCapsule.visibilityStatus === "PUBLIC" ? "公开中" : selectedCapsule.visibilityStatus === "NEEDS_REVIEW" ? "授权变化，等待复核" : "仅自己可见"}</span>
        <h3>{selectedCapsule.pseudonym}</h3><p>{selectedCapsule.intro}</p></div>
        <div className="genome-badge"><strong>v{genomeHistory[0]?.versionNo ?? "–"}</strong><small>{genomeHistory[0]?.status ?? "读取中"}</small></div></div>
      {activeFidelity && <p className="fidelity-note">当前版本 · {activeFidelity}</p>}
      <details className="genome-history"><summary>Genome 版本与变化记录</summary>{genomeHistory.map(version => {
        const label = fidelityLabel(fidelitySummary.find(summary => summary.genomeVersionId === version.id));
        return <article key={version.id}><strong>v{version.versionNo} · {version.status}</strong><span>{version.changeReason}</span><small>{version.compilerVersion}{label ? ` · ${label}` : ""}</small></article>;
      })}</details>

      <div className="capsule-step"><span>1</span><div><strong>复核这个版本可以使用的记忆</strong><small>取消选择或修正来源后，必须重新编译；历史版本不会被悄悄改写。</small></div></div>
      <div className="memory-consent-list compact">{selectableMemories.slice(0, 10).map(memory => {
        const blocked = blockedScopes.has((memory.consentScope ?? "").toUpperCase());
        return <label className={blocked ? "blocked" : ""} key={memory.id}><input type="checkbox" disabled={blocked || capsuleBusy}
          checked={selectedMemoryIds.includes(memory.id)} onChange={() => onToggleMemory(memory.id)} /><span><strong>{memory.title}</strong><small>{blocked ? "不能进入共鸣体" : `v${memory.versionNo}`}</small></span></label>;
      })}</div>
      <AsyncButton className="resonance-secondary" busy={capsuleBusy} busyText="正在生成新版本" onClick={onRecompile}>用当前选择生成新版本</AsyncButton>

      <div className="capsule-step"><span>2</span><div><strong>在只有你能看的沙盒里试聊</strong><small>反馈只成为下一版的改进信号，不会让公开人格暗中漂移。</small></div></div>
      <div className="sandbox-composer"><textarea value={sandboxQuestion} onChange={event => onSandboxQuestion(event.target.value)} aria-label="问自己的共鸣体" />
        <AsyncButton className="resonance-primary" busy={capsuleBusy} disabled={!sandboxQuestion.trim()} busyText="正在生成" onClick={onRunSandbox}>看看它会怎么说</AsyncButton></div>
      {sandboxResult && <article className="sandbox-response"><span>v{sandboxResult.genomeVersionNo} 的回答 · {sandboxResult.identityNotice}</span><p>{sandboxResult.reply}</p>
        {sandboxResult.boundaryNotice && <small>{sandboxResult.boundaryNotice}</small>}
        {sandboxResult.providerAvailable ? <div className="sandbox-ratings" aria-label="这段回应像不像我">
          {sandboxRatings.map(([value, label]) =>
            <button type="button" className={sandboxFeedback === value ? "active" : ""} disabled={capsuleBusy} key={value} onClick={() => onRateSandbox(value)}>{label}</button>)}</div> :
          <p className="preview-warning">真实模型暂时不可用，这次回应不会被当作拟真证据。</p>}
      </article>}

      {onSaveBoundary && <CapsuleBoundaryEditor key={`${selectedCapsule.id}:${boundary?.capsuleId ?? "loading"}`}
        boundary={boundary} boundaryBusy={boundaryBusy} onSaveBoundary={onSaveBoundary} />}

      <div className="capsule-step"><span>4</span><div><strong>决定它是否可以被别人遇见</strong><small>发布不会开放真实身份、联系方式或未授权记忆；撤回会立即阻止新旧会话继续代表你。</small></div></div>
      <div className="resonance-actions">{selectedCapsule.visibilityStatus !== "PUBLIC" && <AsyncButton className="resonance-primary" busy={capsuleBusy} disabled={genomeHistory[0]?.status !== "ACTIVE"} busyText="正在发布" onClick={onPublish}>确认并发布当前版本</AsyncButton>}
        {selectedCapsule.visibilityStatus === "PUBLIC" && <AsyncButton busy={capsuleBusy} busyText="正在暂停" onClick={onPause}>暂停公开</AsyncButton>}
        <AsyncButton className="danger-quiet" busy={capsuleBusy} busyText="正在撤回" onClick={onArchive}>撤回这个共鸣体</AsyncButton></div>
    </div>}
  </section>;
}
