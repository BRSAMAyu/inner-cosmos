import type { CapsuleGenomeVersion, CapsulePreview, CapsuleSandbox, EchoCapsule, MemoryCard } from "../api";

const sandboxRatings: Array<[string, string]> = [
  ["LIKE_ME", "像我"], ["NOT_ME", "不像我"], ["FACT_WRONG", "事实不对"], ["TOO_EXPOSED", "太暴露"], ["TONE_WRONG", "语气不对"]
];
const blockedScopes = new Set(["LOCAL_ONLY", "NO_EXTERNAL_PROCESSING"]);

export function CapsuleWorkbench({ capsules, selectedCapsuleId, selectedCapsule, selectableMemories, selectedMemoryIds,
  capsuleName, capsuleIntro, capsulePreview, capsuleBusy, genomeHistory, sandboxQuestion, sandboxResult, sandboxFeedback,
  onSelectCapsule, onToggleMemory, onCapsuleName, onCapsuleIntro, onPreviewNewCapsule, onCancelPreview, onCreateCapsule,
  onRecompile, onSandboxQuestion, onRunSandbox, onRateSandbox, onPublish, onPause, onArchive }: {
  capsules: EchoCapsule[]; selectedCapsuleId: number | null; selectedCapsule: EchoCapsule | null;
  selectableMemories: MemoryCard[]; selectedMemoryIds: number[]; capsuleName: string; capsuleIntro: string;
  capsulePreview: CapsulePreview | null; capsuleBusy: boolean; genomeHistory: CapsuleGenomeVersion[];
  sandboxQuestion: string; sandboxResult: CapsuleSandbox | null; sandboxFeedback: string | null;
  onSelectCapsule: (id: number | null) => void; onToggleMemory: (id: number) => void;
  onCapsuleName: (value: string) => void; onCapsuleIntro: (value: string) => void;
  onPreviewNewCapsule: () => void; onCancelPreview: () => void; onCreateCapsule: () => void;
  onRecompile: () => void; onSandboxQuestion: (value: string) => void; onRunSandbox: () => void;
  onRateSandbox: (rating: string) => void; onPublish: () => void; onPause: () => void; onArchive: () => void;
}) {
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
      {!capsulePreview ? <button className="resonance-primary" disabled={capsuleBusy} onClick={onPreviewNewCapsule}>先看严格脱敏预览</button> :
        <div className="capsule-preview" aria-label="共鸣体授权预览"><span className="eyebrow">WHAT IT MAY USE</span><p>{capsulePreview.abstractSummary}</p>
          <div className="preview-tags">{capsulePreview.publicTags.map(tag => <span key={tag}>{tag}</span>)}</div>
          {capsulePreview.removedSensitiveItems.length > 0 && <small>已移除：{capsulePreview.removedSensitiveItems.join("、")}</small>}
          {capsulePreview.riskWarnings.map(warning => <p className="preview-warning" key={warning}>{warning}</p>)}
          <div className="resonance-actions"><button disabled={capsuleBusy} onClick={onCancelPreview}>返回修改</button><button className="resonance-primary" disabled={capsuleBusy} onClick={onCreateCapsule}>编译为私密版本</button></div>
        </div>}
    </div> : <div className="capsule-workbench">
      <div className="capsule-summary"><div><span className="capsule-status">{selectedCapsule.visibilityStatus === "PUBLIC" ? "公开中" : selectedCapsule.visibilityStatus === "NEEDS_REVIEW" ? "授权变化，等待复核" : "仅自己可见"}</span>
        <h3>{selectedCapsule.pseudonym}</h3><p>{selectedCapsule.intro}</p></div>
        <div className="genome-badge"><strong>v{genomeHistory[0]?.versionNo ?? "–"}</strong><small>{genomeHistory[0]?.status ?? "读取中"}</small></div></div>
      <details className="genome-history"><summary>Genome 版本与变化记录</summary>{genomeHistory.map(version => <article key={version.id}><strong>v{version.versionNo} · {version.status}</strong><span>{version.changeReason}</span><small>{version.compilerVersion}</small></article>)}</details>

      <div className="capsule-step"><span>1</span><div><strong>复核这个版本可以使用的记忆</strong><small>取消选择或修正来源后，必须重新编译；历史版本不会被悄悄改写。</small></div></div>
      <div className="memory-consent-list compact">{selectableMemories.slice(0, 10).map(memory => {
        const blocked = blockedScopes.has((memory.consentScope ?? "").toUpperCase());
        return <label className={blocked ? "blocked" : ""} key={memory.id}><input type="checkbox" disabled={blocked || capsuleBusy}
          checked={selectedMemoryIds.includes(memory.id)} onChange={() => onToggleMemory(memory.id)} /><span><strong>{memory.title}</strong><small>{blocked ? "不能进入共鸣体" : `v${memory.versionNo}`}</small></span></label>;
      })}</div>
      <button className="resonance-secondary" disabled={capsuleBusy} onClick={onRecompile}>用当前选择生成新版本</button>

      <div className="capsule-step"><span>2</span><div><strong>在只有你能看的沙盒里试聊</strong><small>反馈只成为下一版的改进信号，不会让公开人格暗中漂移。</small></div></div>
      <div className="sandbox-composer"><textarea value={sandboxQuestion} onChange={event => onSandboxQuestion(event.target.value)} aria-label="问自己的共鸣体" />
        <button className="resonance-primary" disabled={capsuleBusy || !sandboxQuestion.trim()} onClick={onRunSandbox}>看看它会怎么说</button></div>
      {sandboxResult && <article className="sandbox-response"><span>v{sandboxResult.genomeVersionNo} 的回答 · {sandboxResult.identityNotice}</span><p>{sandboxResult.reply}</p>
        {sandboxResult.boundaryNotice && <small>{sandboxResult.boundaryNotice}</small>}
        {sandboxResult.providerAvailable ? <div className="sandbox-ratings" aria-label="这段回应像不像我">
          {sandboxRatings.map(([value, label]) =>
            <button type="button" className={sandboxFeedback === value ? "active" : ""} disabled={capsuleBusy} key={value} onClick={() => onRateSandbox(value)}>{label}</button>)}</div> :
          <p className="preview-warning">真实模型暂时不可用，这次回应不会被当作拟真证据。</p>}
      </article>}

      <div className="capsule-step"><span>3</span><div><strong>决定它是否可以被别人遇见</strong><small>发布不会开放真实身份、联系方式或未授权记忆；撤回会立即阻止新旧会话继续代表你。</small></div></div>
      <div className="resonance-actions">{selectedCapsule.visibilityStatus !== "PUBLIC" && <button className="resonance-primary" disabled={capsuleBusy || genomeHistory[0]?.status !== "ACTIVE"} onClick={onPublish}>确认并发布当前版本</button>}
        {selectedCapsule.visibilityStatus === "PUBLIC" && <button disabled={capsuleBusy} onClick={onPause}>暂停公开</button>}
        <button className="danger-quiet" disabled={capsuleBusy} onClick={onArchive}>撤回这个共鸣体</button></div>
    </div>}
  </section>;
}
