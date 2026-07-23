import { useState } from "react";
import type { CapsuleBoundary, CapsuleFidelitySummary, CapsuleGenomeVersion, CapsulePreview, CapsuleSandbox, EchoCapsule, MemoryCard } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

const sandboxRatingOrder = ["LIKE_ME", "NOT_ME", "FACT_WRONG", "TOO_EXPOSED", "TONE_WRONG"] as const;
const privacyOrder = ["STRICT", "BALANCED", "OPEN"] as const;
const contactPolicyOrder = ["LETTER_ONLY", "STAND_IN_FIRST", "DIRECT_REQUEST", "NO_REAL_CONTACT"] as const;
const blockedScopes = new Set(["LOCAL_ONLY", "NO_EXTERNAL_PROCESSING"]);

type WorkbenchCopy = {
  aria: string; heading: string; count: (n: number) => string; intro: string; tabsAria: string;
  tabPublic: string; tabReview: string; tabPrivate: string; newTab: string; createAria: string;
  step1Title: string; step1Note: string; noMemories: string; blockedMem: string; memoryFallback: string;
  memoryCheckboxAria: (title: string, meta: string) => string;
  step2Title: string; step2Note: string; nameLabel: string; namePlaceholder: string; introLabel: string; introPlaceholder: string;
  previewBusy: string; previewBtn: string; previewAria: string; removedPrefix: string; backToEdit: string; compileBusy: string; compileBtn: string;
  statusPublic: string; statusReview: string; statusPrivate: string; genomeReading: string;
  fidelity: (n: number, pct: number) => string; fidelityCurrent: (label: string) => string; genomeHistorySummary: string;
  rvStep1Title: string; rvStep1Note: string; blockedMem2: string; recompileBusy: string; recompileBtn: string;
  rvStep2Title: string; rvStep2Note: string; askAria: string; sandboxRunBusy: string; sandboxRunBtn: string;
  sandboxAnswer: (v: number, notice: string) => string; ratingsAria: string; rating: Record<string, string>; providerUnavailable: string;
  step4Title: string; step4Note: string; publishBusy: string; publishBtn: string; pauseBusy: string; pauseBtn: string; archiveBusy: string; archiveBtn: string;
  // boundary editor
  step3Title: string; step3Note: string; allowLabel: string; allowPlaceholder: string; blockLabel: string; blockPlaceholder: string;
  privacyLabel: string; privacy: Record<string, string>; maxTurnsLabel: string; allowLetterCheck: string; boundarySaveBusy: string; boundarySave: string;
  ownerNoteLabel: string; ownerNotePlaceholder: string; standInCheck: string; contactPolicyLabel: string;
  contactPolicy: Record<string, string>; contextStepTitle: string; contextStepNote: string; contextSaveBusy: string; contextSave: string;
};

const COPY: Record<Locale, WorkbenchCopy> = {
  "zh-CN": {
    aria: "共鸣体创建与像不像我沙盒", heading: "先确认像不像你，再让别人遇见", count: n => `${n} 个共鸣体`,
    intro: "共鸣体是你明确授权的一个侧面，不是你的账号，也不会假装你正在实时回复。每次重编译都形成新版本。",
    tabsAria: "我的共鸣体", tabPublic: "已公开", tabReview: "需复核", tabPrivate: "仅自己", newTab: "＋ 新建一个侧面", createAria: "创建共鸣体",
    step1Title: "你愿意让它使用哪些记忆？", step1Note: "这里的选择不会自动公开；LOCAL_ONLY 与禁止外部处理的内容不能进入 Genome。",
    noMemories: "还没有可选择的当前记忆。你也可以创建一个不读取记忆的通用侧面。", blockedMem: "不会用于共鸣体", memoryFallback: "记忆",
    memoryCheckboxAria: (title, meta) => `${title} · ${meta}`,
    step2Title: "它表达你的哪一部分？", step2Note: "名字和说明面向访客，但创建后仍保持私密，直到你主动发布。",
    nameLabel: "共鸣体名字", namePlaceholder: "例如：雨后仍愿意开口的人", introLabel: "希望它保留的侧面",
    introPlaceholder: "例如：面对关系误解时，我会先沉默整理，再清楚说出边界。",
    previewBusy: "正在脱敏", previewBtn: "先看严格脱敏预览", previewAria: "共鸣体授权预览", removedPrefix: "已移除：",
    backToEdit: "返回修改", compileBusy: "正在编译", compileBtn: "编译为私密版本",
    statusPublic: "公开中", statusReview: "授权变化，等待复核", statusPrivate: "仅自己可见", genomeReading: "读取中",
    fidelity: (n, p) => `${n} 次反馈 · ${p}% 像我`, fidelityCurrent: l => `当前版本 · ${l}`, genomeHistorySummary: "Genome 版本与变化记录",
    rvStep1Title: "复核这个版本可以使用的记忆", rvStep1Note: "取消选择或修正来源后，必须重新编译；历史版本不会被悄悄改写。",
    blockedMem2: "不能进入共鸣体", recompileBusy: "正在生成新版本", recompileBtn: "用当前选择生成新版本",
    rvStep2Title: "在只有你能看的沙盒里试聊", rvStep2Note: "反馈只成为下一版的改进信号，不会让公开人格暗中漂移。",
    askAria: "问自己的共鸣体", sandboxRunBusy: "正在生成", sandboxRunBtn: "看看它会怎么说",
    sandboxAnswer: (v, notice) => `v${v} 的回答 · ${notice}`, ratingsAria: "这段回应像不像我",
    rating: { LIKE_ME: "像我", NOT_ME: "不像我", FACT_WRONG: "事实不对", TOO_EXPOSED: "太暴露", TONE_WRONG: "语气不对" },
    providerUnavailable: "真实模型暂时不可用，这次回应不会被当作拟真证据。",
    step4Title: "决定它是否可以被别人遇见", step4Note: "发布不会开放真实身份、联系方式或未授权记忆；撤回会立即阻止新旧会话继续代表你。",
    publishBusy: "正在发布", publishBtn: "确认并发布当前版本", pauseBusy: "正在暂停", pauseBtn: "暂停公开", archiveBusy: "正在撤回", archiveBtn: "撤回这个共鸣体",
    step3Title: "设定它在对话里的边界", step3Note: "这些只有你能改：它可以谈什么、要避开什么、每天最多聊几轮、别人能否请求给你写慢信。",
    allowLabel: "允许谈论的话题", allowPlaceholder: "例如：自我观察, 日常支持, 温柔建议", blockLabel: "明确避开的话题", blockPlaceholder: "例如：真实姓名, 诊断承诺, 强迫即时回应",
    privacyLabel: "隐私等级", privacy: { STRICT: "严格保护", BALANCED: "均衡保护", OPEN: "开放一点" }, maxTurnsLabel: "每日对话轮数",
    allowLetterCheck: "允许访客读完后请求给你写一封慢信", boundarySaveBusy: "保存中…", boundarySave: "保存边界设置",
    ownerNoteLabel: "给它的额外背景说明", ownerNotePlaceholder: "只有你能看到；帮助它更准确地表达这个侧面。",
    standInCheck: "允许它先作为回声代你回应", contactPolicyLabel: "真人联系方式",
    contactPolicy: { LETTER_ONLY: "只能引导慢信", STAND_IN_FIRST: "先作为回声回应", DIRECT_REQUEST: "可以请求真人连接", NO_REAL_CONTACT: "不开放真人联系" },
    contextStepTitle: "补充背景与联系方式偏好", contextStepNote: "这些设置只有你能改；共鸣体不会因为它们而假装是真人。",
    contextSaveBusy: "保存中…", contextSave: "保存背景与联系设置"
  },
  "en-SG": {
    aria: "Capsule creation and like-me sandbox", heading: "Confirm it's like you before others meet it", count: n => `${n} capsule${n === 1 ? "" : "s"}`,
    intro: "A capsule is one facet you explicitly authorized — not your account, and it never pretends you're replying live. Each recompile forms a new version.",
    tabsAria: "My capsules", tabPublic: "Public", tabReview: "Needs review", tabPrivate: "Private", newTab: "＋ New facet", createAria: "Create a capsule",
    step1Title: "Which memories are you willing to let it use?", step1Note: "Choices here aren't published automatically; LOCAL_ONLY and no-external-processing content can't enter the Genome.",
    noMemories: "No current memories to choose yet. You can also create a general facet that reads no memories.", blockedMem: "Won't be used by the capsule", memoryFallback: "Memory",
    memoryCheckboxAria: (title, meta) => `${title} · ${meta}`,
    step2Title: "Which part of you does it express?", step2Note: "The name and description face visitors, but stay private after creation until you publish.",
    nameLabel: "Capsule name", namePlaceholder: "e.g. someone still willing to speak after the rain", introLabel: "The facet you want it to keep",
    introPlaceholder: "e.g. facing a relationship misunderstanding, I go quiet to sort myself out, then clearly state my boundary.",
    previewBusy: "De-identifying", previewBtn: "See the strict de-identified preview", previewAria: "Capsule authorization preview", removedPrefix: "Removed: ",
    backToEdit: "Back to edit", compileBusy: "Compiling", compileBtn: "Compile as a private version",
    statusPublic: "Public", statusReview: "Authorization changed, awaiting review", statusPrivate: "Visible to you only", genomeReading: "Loading",
    fidelity: (n, p) => `${n} feedback · ${p}% like me`, fidelityCurrent: l => `Current version · ${l}`, genomeHistorySummary: "Genome versions & change log",
    rvStep1Title: "Review the memories this version may use", rvStep1Note: "After deselecting or correcting a source you must recompile; past versions are never quietly rewritten.",
    blockedMem2: "Can't enter the capsule", recompileBusy: "Generating new version", recompileBtn: "Generate a new version from the current selection",
    rvStep2Title: "Try chatting in a sandbox only you can see", rvStep2Note: "Feedback only becomes an improvement signal for the next version — it never drifts the public persona secretly.",
    askAria: "Ask your own capsule", sandboxRunBusy: "Generating", sandboxRunBtn: "See what it would say",
    sandboxAnswer: (v, notice) => `v${v}'s answer · ${notice}`, ratingsAria: "Does this response feel like me",
    rating: { LIKE_ME: "Like me", NOT_ME: "Not me", FACT_WRONG: "Fact wrong", TOO_EXPOSED: "Too exposed", TONE_WRONG: "Tone wrong" },
    providerUnavailable: "The real model is unavailable right now; this response won't count as fidelity evidence.",
    step4Title: "Decide whether others can meet it", step4Note: "Publishing never exposes your real identity, contact details or unauthorized memories; withdrawing immediately stops old and new sessions from representing you.",
    publishBusy: "Publishing", publishBtn: "Confirm and publish this version", pauseBusy: "Pausing", pauseBtn: "Pause publishing", archiveBusy: "Withdrawing", archiveBtn: "Withdraw this capsule",
    step3Title: "Set its boundaries in conversation", step3Note: "Only you can change these: what it may discuss, what to avoid, the daily turn cap, and whether others may request a slow letter to you.",
    allowLabel: "Topics it may discuss", allowPlaceholder: "e.g. self-observation, everyday support, gentle suggestions", blockLabel: "Topics to explicitly avoid", blockPlaceholder: "e.g. real name, diagnostic promises, forced instant replies",
    privacyLabel: "Privacy level", privacy: { STRICT: "Strict", BALANCED: "Balanced", OPEN: "More open" }, maxTurnsLabel: "Daily conversation turns",
    allowLetterCheck: "Let a visitor request a slow letter to you after reading", boundarySaveBusy: "Saving…", boundarySave: "Save boundary settings",
    ownerNoteLabel: "Extra background for it", ownerNotePlaceholder: "Only you see this; it helps the facet speak more accurately.",
    standInCheck: "Let it answer as a stand-in on your behalf first", contactPolicyLabel: "Real-contact policy",
    contactPolicy: { LETTER_ONLY: "Only guide to a slow letter", STAND_IN_FIRST: "Stand in first", DIRECT_REQUEST: "May request a real connection", NO_REAL_CONTACT: "No real contact" },
    contextStepTitle: "Background and contact-policy preferences", contextStepNote: "Only you can change these; the capsule never pretends to be a real person because of them.",
    contextSaveBusy: "Saving…", contextSave: "Save background & contact settings"
  }
};

function fidelityLabel(summary: CapsuleFidelitySummary | undefined, t: WorkbenchCopy): string | null {
  if (!summary || summary.totalRatings === 0 || summary.fidelityScore === null) return null;
  return t.fidelity(summary.totalRatings, Math.round(summary.fidelityScore * 100));
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

// Owner-private boundary editor for a selected capsule. Local state is seeded from the loaded
// boundary and reset per capsule via key={capsuleId}.
function CapsuleBoundaryEditor({ boundary, boundaryBusy, onSaveBoundary, t }: {
  boundary: CapsuleBoundary | null; boundaryBusy: boolean;
  onSaveBoundary: (boundary: Partial<CapsuleBoundary>) => void; t: WorkbenchCopy;
}) {
  const [allowTopics, setAllowTopics] = useState(topicsToText(boundary?.allowTopics ?? null));
  const [blockedTopics, setBlockedTopics] = useState(topicsToText(boundary?.blockedTopics ?? null));
  const [maxTurns, setMaxTurns] = useState(boundary?.maxConversationTurns ?? 30);
  const [allowLetter, setAllowLetter] = useState(boundary?.allowLetterRequest ?? true);
  const [privacy, setPrivacy] = useState(boundary?.privacyLevel ?? "STRICT");
  return <>
    <div className="capsule-step"><span>3</span><div><strong>{t.step3Title}</strong><small>{t.step3Note}</small></div></div>
    <div className="boundary-editor">
      <label>{t.allowLabel}<input value={allowTopics} onChange={event => setAllowTopics(event.target.value)} placeholder={t.allowPlaceholder} /></label>
      <label>{t.blockLabel}<input value={blockedTopics} onChange={event => setBlockedTopics(event.target.value)} placeholder={t.blockPlaceholder} /></label>
      <div className="boundary-row">
        <label>{t.privacyLabel}<select value={privacy} onChange={event => setPrivacy(event.target.value)}>
          {privacyOrder.map(value => <option key={value} value={value}>{t.privacy[value]}</option>)}</select></label>
        <label>{t.maxTurnsLabel}<input type="number" min={2} max={50} value={maxTurns}
          onChange={event => setMaxTurns(Number(event.target.value))} /></label>
      </div>
      <label className="boundary-check"><input type="checkbox" checked={allowLetter}
        onChange={event => setAllowLetter(event.target.checked)} />{t.allowLetterCheck}</label>
      <AsyncButton className="resonance-secondary" busy={boundaryBusy} busyText={t.boundarySaveBusy}
        onClick={() => onSaveBoundary({ allowTopics, blockedTopics, maxConversationTurns: maxTurns, allowLetterRequest: allowLetter, privacyLevel: privacy })}>
        {t.boundarySave}</AsyncButton>
    </div>
  </>;
}

// Owner-private context editor for an existing capsule: background note, stand-in permission
// and real-contact policy. Local state is seeded from the selected capsule and reset per
// capsule via key={capsuleId}, mirroring CapsuleBoundaryEditor above.
function CapsuleContextEditor({ capsule, capsuleBusy, onSaveContext, t }: {
  capsule: EchoCapsule; capsuleBusy: boolean;
  onSaveContext: (patch: { ownerContextNote: string; standInEnabled: boolean; realContactPolicy: string }) => void; t: WorkbenchCopy;
}) {
  const [ownerNote, setOwnerNote] = useState(capsule.ownerContextNote ?? "");
  const [standIn, setStandIn] = useState(capsule.standInEnabled ?? false);
  const [contactPolicy, setContactPolicy] = useState(capsule.realContactPolicy ?? "LETTER_ONLY");
  return <>
    <div className="capsule-step"><span>4</span><div><strong>{t.contextStepTitle}</strong><small>{t.contextStepNote}</small></div></div>
    <div className="boundary-editor">
      <label>{t.ownerNoteLabel}<textarea value={ownerNote} onChange={event => setOwnerNote(event.target.value)} placeholder={t.ownerNotePlaceholder} /></label>
      <label className="boundary-check"><input type="checkbox" checked={standIn}
        onChange={event => setStandIn(event.target.checked)} />{t.standInCheck}</label>
      <label>{t.contactPolicyLabel}<select value={contactPolicy} onChange={event => setContactPolicy(event.target.value)}>
        {contactPolicyOrder.map(value => <option key={value} value={value}>{t.contactPolicy[value]}</option>)}</select></label>
      <AsyncButton className="resonance-secondary" busy={capsuleBusy} busyText={t.contextSaveBusy}
        onClick={() => onSaveContext({ ownerContextNote: ownerNote, standInEnabled: standIn, realContactPolicy: contactPolicy })}>
        {t.contextSave}</AsyncButton>
    </div>
  </>;
}

export function CapsuleWorkbench({ capsules, selectedCapsuleId, selectedCapsule, selectableMemories, selectedMemoryIds,
  capsuleName, capsuleIntro, capsulePreview, capsuleBusy, genomeHistory, fidelitySummary, sandboxQuestion, sandboxResult, sandboxFeedback,
  onSelectCapsule, onToggleMemory, onCapsuleName, onCapsuleIntro, onPreviewNewCapsule, onCancelPreview, onCreateCapsule,
  onRecompile, onSandboxQuestion, onRunSandbox, onRateSandbox, onPublish, onPause, onArchive,
  boundary = null, boundaryBusy = false, onSaveBoundary,
  capsuleOwnerNote = "", onCapsuleOwnerNote, capsuleStandIn = false, onCapsuleStandIn,
  capsuleContactPolicy = "LETTER_ONLY", onCapsuleContactPolicy, onSaveContext, locale = "zh-CN" }: {
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
  capsuleOwnerNote?: string; onCapsuleOwnerNote?: (value: string) => void;
  capsuleStandIn?: boolean; onCapsuleStandIn?: (value: boolean) => void;
  capsuleContactPolicy?: string; onCapsuleContactPolicy?: (value: string) => void;
  onSaveContext?: (patch: { ownerContextNote: string; standInEnabled: boolean; realContactPolicy: string }) => void;
  locale?: Locale;
}) {
  const t = COPY[locale];
  const activeFidelity = fidelityLabel(fidelitySummary.find(summary => summary.genomeVersionId === genomeHistory[0]?.id), t);
  const tabStatus = (status: string) => status === "PUBLIC" ? t.tabPublic : status === "NEEDS_REVIEW" ? t.tabReview : t.tabPrivate;
  const summaryStatus = (status: string) => status === "PUBLIC" ? t.statusPublic : status === "NEEDS_REVIEW" ? t.statusReview : t.statusPrivate;
  return <section className="resonance-space" aria-label={t.aria}>
    <div className="resonance-heading"><div><span className="eyebrow">YOUR RESONANCE</span><h2>{t.heading}</h2></div>
      <span>{t.count(capsules.filter(capsule => capsule.visibilityStatus !== "ARCHIVED").length)}</span></div>
    <p className="resonance-intro">{t.intro}</p>

    {capsules.length > 0 && <div className="capsule-tabs" role="tablist" aria-label={t.tabsAria}>
      {capsules.filter(capsule => capsule.visibilityStatus !== "ARCHIVED").map(capsule =>
        <button type="button" role="tab" aria-selected={selectedCapsuleId === capsule.id} className={selectedCapsuleId === capsule.id ? "active" : ""}
          key={capsule.id} onClick={() => onSelectCapsule(capsule.id)}>{capsule.pseudonym}<small>{tabStatus(capsule.visibilityStatus)}</small></button>)}
      <button type="button" role="tab" aria-selected={selectedCapsuleId === null} className={selectedCapsuleId === null ? "active new" : "new"}
        onClick={() => onSelectCapsule(null)}>{t.newTab}</button>
    </div>}

    {!selectedCapsule ? <div className="capsule-create" role="region" aria-label={t.createAria}>
      <div className="capsule-step"><span>1</span><div><strong>{t.step1Title}</strong><small>{t.step1Note}</small></div></div>
      <div className="memory-consent-list">{selectableMemories.length === 0 ? <p>{t.noMemories}</p> : selectableMemories.slice(0, 10).map(memory => {
        const blocked = blockedScopes.has((memory.consentScope ?? "").toUpperCase());
        const meta = blocked ? t.blockedMem : `${memory.memoryLayer ?? t.memoryFallback} · v${memory.versionNo}`;
        // W2 UIUX audit: same run-on-naming shape as ProductShellNavigation's five-space tabs --
        // <strong>title</strong><small>meta</small> sit with no separator inside a <label>, so the
        // wrapped checkbox's accessible name concatenates into one run-on string (live-verified:
        // label.textContent === "今日沉淀EPISODIC · v1"). aria-hidden the visual duplicate and give
        // the checkbox itself a properly separated aria-label; the two-line visual layout is unchanged.
        return <label className={blocked ? "blocked" : ""} key={memory.id}><input type="checkbox" disabled={blocked || capsuleBusy}
          aria-label={t.memoryCheckboxAria(memory.title, meta)}
          checked={selectedMemoryIds.includes(memory.id)} onChange={() => onToggleMemory(memory.id)} /><span aria-hidden="true"><strong>{memory.title}</strong><small>{meta}</small></span></label>;
      })}</div>
      <div className="capsule-step"><span>2</span><div><strong>{t.step2Title}</strong><small>{t.step2Note}</small></div></div>
      <div className="capsule-fields"><label>{t.nameLabel}<input value={capsuleName} onChange={event => onCapsuleName(event.target.value)} placeholder={t.namePlaceholder} /></label>
        <label>{t.introLabel}<textarea value={capsuleIntro} onChange={event => onCapsuleIntro(event.target.value)} placeholder={t.introPlaceholder} /></label>
        <label>{t.ownerNoteLabel}<textarea value={capsuleOwnerNote} onChange={event => onCapsuleOwnerNote?.(event.target.value)} placeholder={t.ownerNotePlaceholder} /></label>
        <label>{t.contactPolicyLabel}<select value={capsuleContactPolicy} onChange={event => onCapsuleContactPolicy?.(event.target.value)}>
          {contactPolicyOrder.map(value => <option key={value} value={value}>{t.contactPolicy[value]}</option>)}</select></label></div>
      <label className="boundary-check"><input type="checkbox" checked={capsuleStandIn}
        onChange={event => onCapsuleStandIn?.(event.target.checked)} />{t.standInCheck}</label>
      {!capsulePreview ? <AsyncButton className="resonance-primary" busy={capsuleBusy} busyText={t.previewBusy} onClick={onPreviewNewCapsule}>{t.previewBtn}</AsyncButton> :
        <div className="capsule-preview" aria-label={t.previewAria}><span className="eyebrow">WHAT IT MAY USE</span><p>{capsulePreview.abstractSummary}</p>
          <div className="preview-tags">{capsulePreview.publicTags.map(tag => <span key={tag}>{tag}</span>)}</div>
          {capsulePreview.removedSensitiveItems.length > 0 && <small>{t.removedPrefix}{capsulePreview.removedSensitiveItems.join("、")}</small>}
          {capsulePreview.riskWarnings.map(warning => <p className="preview-warning" key={warning}>{warning}</p>)}
          <div className="resonance-actions"><button type="button" disabled={capsuleBusy} onClick={onCancelPreview}>{t.backToEdit}</button><AsyncButton className="resonance-primary" busy={capsuleBusy} busyText={t.compileBusy} onClick={onCreateCapsule}>{t.compileBtn}</AsyncButton></div>
        </div>}
    </div> : <div className="capsule-workbench">
      <div className="capsule-summary"><div><span className="capsule-status">{summaryStatus(selectedCapsule.visibilityStatus)}</span>
        <h3>{selectedCapsule.pseudonym}</h3><p className="ugc-text">{selectedCapsule.intro}</p></div>
        <div className="genome-badge"><strong>v{genomeHistory[0]?.versionNo ?? "–"}</strong><small>{genomeHistory[0]?.status ?? t.genomeReading}</small></div></div>
      {activeFidelity && <p className="fidelity-note">{t.fidelityCurrent(activeFidelity)}</p>}
      <details className="genome-history"><summary>{t.genomeHistorySummary}</summary>{genomeHistory.map(version => {
        const label = fidelityLabel(fidelitySummary.find(summary => summary.genomeVersionId === version.id), t);
        return <article key={version.id}><strong>v{version.versionNo} · {version.status}</strong><span>{version.changeReason}</span><small>{version.compilerVersion}{label ? ` · ${label}` : ""}</small></article>;
      })}</details>

      <div className="capsule-step"><span>1</span><div><strong>{t.rvStep1Title}</strong><small>{t.rvStep1Note}</small></div></div>
      <div className="memory-consent-list compact">{selectableMemories.slice(0, 10).map(memory => {
        const blocked = blockedScopes.has((memory.consentScope ?? "").toUpperCase());
        const meta = blocked ? t.blockedMem2 : `v${memory.versionNo}`;
        // W2 UIUX audit: same run-on-naming fix as the create-tab memory list above.
        return <label className={blocked ? "blocked" : ""} key={memory.id}><input type="checkbox" disabled={blocked || capsuleBusy}
          aria-label={t.memoryCheckboxAria(memory.title, meta)}
          checked={selectedMemoryIds.includes(memory.id)} onChange={() => onToggleMemory(memory.id)} /><span aria-hidden="true"><strong>{memory.title}</strong><small>{meta}</small></span></label>;
      })}</div>
      <AsyncButton className="resonance-secondary" busy={capsuleBusy} busyText={t.recompileBusy} onClick={onRecompile}>{t.recompileBtn}</AsyncButton>

      <div className="capsule-step"><span>2</span><div><strong>{t.rvStep2Title}</strong><small>{t.rvStep2Note}</small></div></div>
      <div className="sandbox-composer"><textarea value={sandboxQuestion} onChange={event => onSandboxQuestion(event.target.value)} aria-label={t.askAria} />
        <AsyncButton className="resonance-primary" busy={capsuleBusy} disabled={!sandboxQuestion.trim()} busyText={t.sandboxRunBusy} onClick={onRunSandbox}>{t.sandboxRunBtn}</AsyncButton></div>
      {sandboxResult && <article className="sandbox-response"><span>{t.sandboxAnswer(sandboxResult.genomeVersionNo, sandboxResult.identityNotice)}</span><p>{sandboxResult.reply}</p>
        {sandboxResult.boundaryNotice && <small>{sandboxResult.boundaryNotice}</small>}
        {sandboxResult.providerAvailable ? <div className="sandbox-ratings" aria-label={t.ratingsAria}>
          {sandboxRatingOrder.map(value =>
            <button type="button" className={sandboxFeedback === value ? "active" : ""} disabled={capsuleBusy} key={value} onClick={() => onRateSandbox(value)}>{t.rating[value]}</button>)}</div> :
          <p className="preview-warning">{t.providerUnavailable}</p>}
      </article>}

      {onSaveBoundary && <CapsuleBoundaryEditor key={`${selectedCapsule.id}:${boundary?.capsuleId ?? "loading"}`}
        boundary={boundary} boundaryBusy={boundaryBusy} onSaveBoundary={onSaveBoundary} t={t} />}
      {onSaveContext && <CapsuleContextEditor key={`context:${selectedCapsule.id}`}
        capsule={selectedCapsule} capsuleBusy={capsuleBusy} onSaveContext={onSaveContext} t={t} />}

      <div className="capsule-step"><span>5</span><div><strong>{t.step4Title}</strong><small>{t.step4Note}</small></div></div>
      <div className="resonance-actions">{selectedCapsule.visibilityStatus !== "PUBLIC" && <AsyncButton className="resonance-primary" busy={capsuleBusy} disabled={genomeHistory[0]?.status !== "ACTIVE"} busyText={t.publishBusy} onClick={onPublish}>{t.publishBtn}</AsyncButton>}
        {selectedCapsule.visibilityStatus === "PUBLIC" && <AsyncButton busy={capsuleBusy} busyText={t.pauseBusy} onClick={onPause}>{t.pauseBtn}</AsyncButton>}
        <AsyncButton className="danger-quiet" busy={capsuleBusy} busyText={t.archiveBusy} onClick={onArchive}>{t.archiveBtn}</AsyncButton></div>
    </div>}
  </section>;
}
