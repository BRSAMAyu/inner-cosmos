import { useEffect, useId, useState } from "react";
import type {
  ConnectionRequests, DeliveryPreset, DeliverySchedule, LetterThread, LiveChatInvites, LiveChatMessage,
  LiveChatSession, RelationCorrectionProposal, SlowLetter, SlowLetterOutboxRow, SocialConnection
} from "../api";
import type { Locale } from "../i18n";
import { AsyncButton, LoadingText } from "../loading";
import { formatSlowLetterInstant, secondsUntilSlowLetterArrival, toLocalDateTimeInputValue } from "../slowLetterTime";
import { InlineAudioPlayer } from "./shared/InlineAudioPlayer";
import { LiveChatPanel } from "./LiveChatPanel";

const repliable = new Set(["READ", "REPLIED"]);
const declinable = new Set(["DELIVERED", "READ"]);
// DECLINED/BLOCKED arrive folded as CLOSED in the sender projection.
const archivableFromOutbox = new Set(["READ", "REPLIED", "CLOSED"]);

/**
 * CP-33 §2-6: the recipient's per-letter read-receipt choice, carried by the backend letter
 * payload. Absent/null means the persisted default NEVER -- the sender is not told anything.
 */
type ReceiptPolicy = "ALWAYS" | "NEVER";
type ReceiptAwareLetter = SlowLetter & { receiptPolicy?: ReceiptPolicy | null };

/** CP-33 §2-6: honest default -- no field (or null) means no receipt is ever emitted. */
function receiptPolicyOf(letter: SlowLetter): ReceiptPolicy {
  return (letter as ReceiptAwareLetter).receiptPolicy === "ALWAYS" ? "ALWAYS" : "NEVER";
}

const COPY: Record<Locale, {
  outboxStatus: Record<string, string>;
  counts: { inbox: (n: number) => string; outbox: (n: number) => string; drafts: (n: number) => string; threads: (n: number) => string };
  aria: string; heading: string; tabsAria: string; tabInbox: string; tabOutbox: string; tabDrafts: string; tabThreads: string;
  inboxIntro: string; inboxEmpty: string; replyAria: (title: string) => string; replyPlaceholder: string; replyBusy: string; replySend: string;
  markRead: string; markReadBusy: string; decline: string; declineBusy: string;
  playLetterVoice: string; letterVoiceBusy: string; letterVoiceAria: string;
  willKnow: string; willKnowBusy: string; block: string; blockBusy: string; report: string; reportBusy: string;
  receiptControl: string; receiptOffAction: string; receiptOnAction: string;
  receiptOffHint: string; receiptOnHint: string; receiptBusy: string; outboxReadReceipt: string;
  outboxIntro: string; outboxEmpty: string; arrivalEta: (time: string) => string; archiveLetter: string; archiveBusy: string;
  draftsIntro: string; draftsEmpty: string; untitledDraft: string; draftStatus: string; sendDraftBusy: string; sendDraft: string;
  threadsIntro: string; threadsEmpty: string; threadItem: (id: number) => string; threadItemAria: (label: string, statusText: string) => string;
  threadPickPrompt: string; threadLoading: string;
  threadLettersEmpty: string; threadLettersError: string;
  refresh: string; refreshBusy: string; autoRefreshNote: string;
  composeDirect: string; composeDirectHint: string; composeDiscover: string; composeDiscoverHint: string;
  safetyActions: string;
  directTo: string; directPick: string; directTitle: string; directBody: string; directSend: string; directBusy: string; directCancel: string;
  deliveryRhythm: string; deliveryHint: string; customArrival: string;
  deliveryOptions: Record<DeliveryPreset, string>; sealNote: string; countdown: (value: string) => string;
  consentAria: string; awaitingYou: string; noIncoming: string; wantsToKnow: (name: string) => string; accept: string; acceptBusy: string; declineConn: string; declineConnBusy: string;
  awaitingThem: string; noOutgoing: string; notYetAgreed: string; bothAgreed: string; noFriends: string; leave: string; leaveBusy: string;
  // CP-34 both-party-consent corrections on a shared letter thread.
  correctionsAria: string; correctionsHeading: string; correctionsIntro: string;
  correctionsIncoming: string; correctionsOutgoing: string; correctionsEmpty: string;
  correctionsLoading: string; correctionsError: string;
  correctionFieldLabel: (field: string) => string; correctionNote: string;
  correctionStatus: Record<string, string>;
  correctionsAccept: string; correctionsAcceptBusy: string;
  correctionsReject: string; correctionsRejectBusy: string; correctionsWithdraw: string; correctionsWithdrawBusy: string;
  correctionsProposeSummary: string; correctionsProposeField: string; correctionsProposeValue: string;
  correctionsProposeNote: string; correctionsProposeSend: string; correctionsProposeBusy: string;
  correctionsRejectedReason: (reason: string) => string;
}> = {
  "zh-CN": {
    outboxStatus: { DRAFT: "草稿", SENT: "已寄出", FLYING: "飞行中", DELIVERED: "已抵达", READ: "对方已读", REPLIED: "对方回信了", CLOSED: "已结束", ARCHIVED: "已归档" },
    counts: { inbox: n => `${n} 封已抵达`, outbox: n => `${n} 封已寄出`, drafts: n => `${n} 封草稿`, threads: n => `${n} 段往来` },
    aria: "慢信收件箱与寄件箱", heading: "只在抵达之后，才由你决定关系往哪里走", tabsAria: "慢信方向",
    tabInbox: "收到的", tabOutbox: "寄出的", tabDrafts: "草稿", tabThreads: "往来",
    inboxIntro: "飞行中的信不会提前泄露正文。抵达后你可以阅读、婉拒、举报或屏蔽；屏蔽会阻断同一来信者之后的慢信。", inboxEmpty: "此刻没有已经抵达的慢信。",
    replyAria: title => `回复「${title}」`, replyPlaceholder: "写下你愿意负责的回应；它仍会慢慢抵达。", replyBusy: "正在启程", replySend: "让回复慢信启程",
    markRead: "标记已读", markReadBusy: "正在标记", decline: "温和婉拒", declineBusy: "正在婉拒",
    playLetterVoice: "▶ 朗读这封信", letterVoiceBusy: "正在合成…", letterVoiceAria: "听这封慢信被朗读出来",
    willKnow: "愿意认识对方", willKnowBusy: "正在发出", block: "屏蔽后续来信", blockBusy: "正在屏蔽", report: "举报这封信", reportBusy: "正在提交",
    receiptControl: "已读回执", receiptOffAction: "不告知对方（默认）", receiptOnAction: "愿意告知对方",
    receiptOffHint: "对方不会收到已读回执；你读没读，寄件人都无从知道。", receiptOnHint: "对方会看到这封信已读；这只影响这一封信。",
    receiptBusy: "正在设置", outboxReadReceipt: "对方已读（对方选择告知）",
    outboxIntro: "你写出去的信都在这里。它们会按各自的节奏抵达；对方是否回应由对方决定，你不会被催促，也不会看到假装的实时状态。", outboxEmpty: "你还没有寄出任何慢信。",
    arrivalEta: t => `预计 ${t} 抵达`, archiveLetter: "归档", archiveBusy: "正在归档",
    draftsIntro: "还没寄出的信留在这里。你可以慢慢改，准备好了再让它启程——寄出后它会按慢信的节奏抵达。", draftsEmpty: "没有草稿。", untitledDraft: "未命名草稿", draftStatus: "草稿",
    sendDraftBusy: "正在寄出", sendDraft: "让这封信启程",
    threadsIntro: "同一段关系里来回的慢信会聚成一条往来。点开看看你们之间慢慢积累的对话。", threadsEmpty: "还没有形成往来的慢信线程。",
    threadItem: id => `往来 #${id}`, threadItemAria: (label, statusText) => `${label} · ${statusText}`,
    threadPickPrompt: "选一段往来，看你们之间的慢信。", threadLoading: "正在读取这段往来…",
    threadLettersEmpty: "这段往来里还没有信件。", threadLettersError: "暂时读不到这段往来，请稍后再试。",
    refresh: "刷新慢信", refreshBusy: "正在刷新", autoRefreshNote: "停留在这里时会自动同步抵达与回信。",
    correctionsAria: "往来理解纠错", correctionsHeading: "这段往来的共同理解，可以一起改",
    correctionsIntro: "往来里的关系理解来自你们双方；任何一方都不能单独改写。提案只有对方接受后才生效，被婉拒或撤回都如实留痕。",
    correctionsIncoming: "等你确认的提案", correctionsOutgoing: "你提出的提案", correctionsEmpty: "这段往来还没有纠错提案。",
    correctionsLoading: "正在读取纠错提案…", correctionsError: "暂时读不到纠错提案，请稍后再试。",
    correctionFieldLabel: field => field === "relationLabel" ? "关系称呼"
      : field === "threadTitle" ? "往来题目" : field,
    correctionNote: "提案说明",
    correctionStatus: { PROPOSED: "待对方确认", APPLIED: "已应用", REJECTED: "被婉拒", WITHDRAWN: "已撤回" },
    correctionsAccept: "接受", correctionsAcceptBusy: "正在接受",
    correctionsReject: "婉拒", correctionsRejectBusy: "正在婉拒",
    correctionsWithdraw: "撤回", correctionsWithdrawBusy: "正在撤回",
    correctionsProposeSummary: "对这段往来的理解提出一处修改",
    correctionsProposeField: "要修改的字段", correctionsProposeValue: "你认为正确的内容",
    correctionsProposeNote: "补充说明（可选）", correctionsProposeSend: "送出提案", correctionsProposeBusy: "正在送出",
    correctionsRejectedReason: reason => `婉拒理由：${reason}`,
    composeDirect: "写给已连接的好友", composeDirectHint: "在当前页面打开写信表单，收信人只来自双方同意的连接。",
    composeDiscover: "先去遇见可以写信的人", composeDiscoverHint: "还没有可直接写信的连接；先去共鸣相遇，建立连接后再写。",
    safetyActions: "边界与安全",
    directTo: "写给已连接的好友", directPick: "选择一位好友", directTitle: "信的标题",
    directBody: "写下你真正想说的话…", directSend: "让慢信启程", directBusy: "正在启程", directCancel: "取消",
    deliveryRhythm: "选择抵达的节奏", deliveryHint: "Demo 可选 30 秒或 3 分钟；正式节奏仍由服务端锁定，不会用前端假装抵达。自定义时间按你当前设备时区填写。", customArrival: "自定义抵达时间（当前时区）",
    deliveryOptions: { DEMO_30S: "演示片刻后 · 30 秒", DEMO_3M: "稍后抵达 · 3 分钟", TONIGHT: "今晚抵达", TOMORROW: "明天此时", CUSTOM: "自定义时间" },
    sealNote: "寄出时会短暂封缄，然后进入旅途。动画不会阻碍你继续浏览。", countdown: value => `还有 ${value} 抵达`,
    consentAria: "双向连接同意", awaitingYou: "等待你决定", noIncoming: "没有新的连接邀请", wantsToKnow: name => `${name} 想在慢信之后认识你`, accept: "我也愿意", acceptBusy: "正在同意", declineConn: "暂不连接", declineConnBusy: "正在婉拒",
    awaitingThem: "等待对方决定", noOutgoing: "没有等待中的邀请", notYetAgreed: "尚未同意，不会提前开放真人连接", bothAgreed: "双方已同意", noFriends: "还没有建立真人连接", leave: "退出连接", leaveBusy: "正在退出"
  },
  "en-SG": {
    outboxStatus: { DRAFT: "Draft", SENT: "Sent", FLYING: "In flight", DELIVERED: "Delivered", READ: "Read", REPLIED: "Replied", CLOSED: "Closed", ARCHIVED: "Archived" },
    counts: { inbox: n => `${n} arrived`, outbox: n => `${n} sent`, drafts: n => `${n} draft${n === 1 ? "" : "s"}`, threads: n => `${n} thread${n === 1 ? "" : "s"}` },
    aria: "Slow-letter inbox and outbox", heading: "Only after it arrives do you decide where the relationship goes", tabsAria: "Slow-letter direction",
    tabInbox: "Received", tabOutbox: "Sent", tabDrafts: "Drafts", tabThreads: "Threads",
    inboxIntro: "A letter in flight never reveals its body early. Once it arrives you can read, decline, report or block; blocking stops future letters from the same sender.", inboxEmpty: "No slow letters have arrived just now.",
    replyAria: title => `Reply to "${title}"`, replyPlaceholder: "Write a response you're willing to stand behind; it still arrives slowly.", replyBusy: "Sending", replySend: "Send the reply slow letter",
    markRead: "Mark read", markReadBusy: "Marking", decline: "Gently decline", declineBusy: "Declining",
    playLetterVoice: "▶ Read this letter aloud", letterVoiceBusy: "Synthesizing…", letterVoiceAria: "Hear this slow letter read aloud",
    willKnow: "Willing to know them", willKnowBusy: "Sending", block: "Block future letters", blockBusy: "Blocking", report: "Report this letter", reportBusy: "Submitting",
    receiptControl: "Read receipt", receiptOffAction: "Not telling the sender (default)", receiptOnAction: "Tell the sender",
    receiptOffHint: "The sender won't be told you read this letter.", receiptOnHint: "The sender will see this letter as read. Applies to this letter only.",
    receiptBusy: "Setting", outboxReadReceipt: "Read (they chose to share)",
    outboxIntro: "Every letter you've sent is here. Each arrives at its own pace; whether they reply is theirs to decide — you're never rushed, and never shown a fake live status.", outboxEmpty: "You haven't sent any slow letters yet.",
    arrivalEta: t => `Arrives ~${t}`, archiveLetter: "Archive", archiveBusy: "Archiving",
    draftsIntro: "Letters not yet sent stay here. Revise slowly and send when ready — once sent, it arrives at a slow letter's pace.", draftsEmpty: "No drafts.", untitledDraft: "Untitled draft", draftStatus: "Draft",
    sendDraftBusy: "Sending", sendDraft: "Send this letter",
    threadsIntro: "Letters back and forth in one relationship gather into a thread. Open one to see the conversation you've slowly built.", threadsEmpty: "No slow-letter threads yet.",
    threadItem: id => `Thread #${id}`, threadItemAria: (label, statusText) => `${label} · ${statusText}`,
    threadPickPrompt: "Pick a thread to see the letters between you.", threadLoading: "Loading this thread…",
    threadLettersEmpty: "No letters in this thread yet.", threadLettersError: "Couldn't load this thread right now -- try again shortly.",
    refresh: "Refresh letters", refreshBusy: "Refreshing", autoRefreshNote: "Arrivals and replies sync automatically while you stay here.",
    correctionsAria: "Thread understanding corrections", correctionsHeading: "The shared understanding of this exchange can change together",
    correctionsIntro: "A thread's relationship understanding comes from both of you; neither side rewrites it alone. A proposal applies only after the other side accepts, and a declined or withdrawn one stays on record as such.",
    correctionsIncoming: "Awaiting your decision", correctionsOutgoing: "Your proposals", correctionsEmpty: "No corrections on this exchange yet.",
    correctionsLoading: "Loading corrections…", correctionsError: "Couldn't load corrections right now -- try again shortly.",
    correctionFieldLabel: field => field === "relationLabel" ? "relationship label"
      : field === "threadTitle" ? "thread title" : field,
    correctionNote: "Proposer's note",
    correctionStatus: { PROPOSED: "Waiting on them", APPLIED: "Applied", REJECTED: "Declined", WITHDRAWN: "Withdrawn" },
    correctionsAccept: "Accept", correctionsAcceptBusy: "Accepting",
    correctionsReject: "Decline", correctionsRejectBusy: "Declining",
    correctionsWithdraw: "Withdraw", correctionsWithdrawBusy: "Withdrawing",
    correctionsProposeSummary: "Propose one change to this exchange's understanding",
    correctionsProposeField: "Field to correct", correctionsProposeValue: "What you believe is right",
    correctionsProposeNote: "A short note (optional)", correctionsProposeSend: "Send proposal", correctionsProposeBusy: "Sending",
    correctionsRejectedReason: reason => `Their reason: ${reason}`,
    composeDirect: "Write to a connection", composeDirectHint: "Opens the composer here. Recipients are limited to mutual connections.",
    composeDiscover: "Meet someone you can write to", composeDiscoverHint: "No direct recipient yet. Meet through resonance and connect before writing.",
    safetyActions: "Boundaries & safety",
    directTo: "Write to a connection", directPick: "Choose a connection", directTitle: "Letter title",
    directBody: "Write what you genuinely want to say…", directSend: "Send slow letter", directBusy: "Sending", directCancel: "Cancel",
    deliveryRhythm: "Choose its arrival rhythm", deliveryHint: "Use 30 seconds or 3 minutes for the demo. The server still locks the real arrival time. Custom times use your current device time zone.", customArrival: "Custom arrival (current time zone)",
    deliveryOptions: { DEMO_30S: "Demo moment · 30 seconds", DEMO_3M: "A little later · 3 minutes", TONIGHT: "Tonight", TOMORROW: "This time tomorrow", CUSTOM: "Custom time" },
    sealNote: "Sending briefly seals the letter before its journey. The animation never blocks the rest of the app.", countdown: value => `Arrives in ${value}`,
    consentAria: "Mutual connection consent", awaitingYou: "Awaiting your decision", noIncoming: "No new connection invitations", wantsToKnow: name => `${name} would like to know you after the letters`, accept: "I'd like to too", acceptBusy: "Accepting", declineConn: "Not yet", declineConnBusy: "Declining",
    awaitingThem: "Awaiting their decision", noOutgoing: "No pending invitations", notYetAgreed: "Not yet agreed — a real connection won't open early", bothAgreed: "Both agreed", noFriends: "No real connections yet", leave: "Leave connection", leaveBusy: "Leaving"
  }
};

// CP-34 both-party-consent corrections on one shared letter thread. Local form state lives in
// this sub-component and resets per thread via key={threadId} (the CapsuleBoundaryEditor
// pattern). Everything is optional at the LettersInbox level: callers not passing the props
// (including existing tests) render no correction block at all.
function ThreadCorrections({ threadId, corrections, status, locale, t,
  isCorrectionBusy = () => false, isCorrectionProposeBusy = () => false,
  onProposeCorrection, onAcceptCorrection, onRejectCorrection, onWithdrawCorrection }: {
  threadId: number;
  corrections: { incoming: RelationCorrectionProposal[]; outgoing: RelationCorrectionProposal[] };
  status: "idle" | "loading" | "success" | "error";
  locale: Locale;
  t: typeof COPY["zh-CN"];
  isCorrectionBusy?: (proposalId: number) => boolean;
  isCorrectionProposeBusy?: (threadId: number) => boolean;
  onProposeCorrection?: (threadId: number, correctionField: string, proposedValue: string, note?: string) => void;
  onAcceptCorrection?: (proposalId: number) => void;
  onRejectCorrection?: (proposalId: number) => void;
  onWithdrawCorrection?: (proposalId: number) => void;
}) {
  const [proposeField, setProposeField] = useState("relationLabel");
  const [proposeValue, setProposeValue] = useState("");
  const [proposeNote, setProposeNote] = useState("");
  const fieldOptions = ["relationLabel", "threadTitle"] as const;
  const statusLabel = (value: string) => t.correctionStatus[value] ?? value;
  // Both lists are user-scoped; only this thread's proposals belong in this thread's view.
  const incoming = corrections.incoming.filter(item => item.threadId === threadId && item.status === "PROPOSED");
  const outgoing = corrections.outgoing.filter(item => item.threadId === threadId);
  const nothingHere = incoming.length === 0 && outgoing.length === 0;
  return <section className="thread-corrections" aria-label={t.correctionsAria}>
    <h4>{t.correctionsHeading}</h4>
    <p className="thread-corrections-intro">{t.correctionsIntro}</p>
    {status === "loading" && <p className="muted" role="status">{t.correctionsLoading}</p>}
    {status === "error" && <p className="network-empty" role="alert">{t.correctionsError}</p>}
    {status === "success" && nothingHere && <p className="muted">{t.correctionsEmpty}</p>}
    {status === "success" && incoming.length > 0 && <div className="correction-list">
      <strong>{t.correctionsIncoming}</strong>
      {incoming.map(item => <article key={item.id} className="correction-card" data-status={item.status}>
        <span>{t.correctionFieldLabel(item.correctionField)} → {item.proposedValue}</span>
        {item.note && <small className="ugc-text">{item.note}</small>}
        <div>
          {onAcceptCorrection && <AsyncButton busy={isCorrectionBusy(item.id)} busyText={t.correctionsAcceptBusy}
            onClick={() => onAcceptCorrection(item.id)}>{t.correctionsAccept}</AsyncButton>}
          {onRejectCorrection && <AsyncButton busy={isCorrectionBusy(item.id)} busyText={t.correctionsRejectBusy}
            onClick={() => onRejectCorrection(item.id)}>{t.correctionsReject}</AsyncButton>}
        </div>
      </article>)}
    </div>}
    {status === "success" && outgoing.length > 0 && <div className="correction-list">
      <strong>{t.correctionsOutgoing}</strong>
      {outgoing.map(item => <article key={item.id} className="correction-card" data-status={item.status}>
        <span>{t.correctionFieldLabel(item.correctionField)} → {item.proposedValue}</span>
        <small>{statusLabel(item.status)}</small>
        {item.status === "REJECTED" && item.decisionReason
          && <small className="correction-reason">{t.correctionsRejectedReason(item.decisionReason)}</small>}
        {item.status === "PROPOSED" && onWithdrawCorrection && <AsyncButton className="quiet"
          busy={isCorrectionBusy(item.id)} busyText={t.correctionsWithdrawBusy}
          onClick={() => onWithdrawCorrection(item.id)}>{t.correctionsWithdraw}</AsyncButton>}
      </article>)}
    </div>}
    {onProposeCorrection && <details className="correction-propose">
      <summary>{t.correctionsProposeSummary}</summary>
      <label>{t.correctionsProposeField}<select value={proposeField}
          onChange={event => setProposeField(event.target.value)}>
          {fieldOptions.map(field => <option key={field} value={field}>{t.correctionFieldLabel(field)}</option>)}
        </select></label>
      <label>{t.correctionsProposeValue}<input value={proposeValue}
          onChange={event => setProposeValue(event.target.value)} /></label>
      <label>{t.correctionsProposeNote}<input value={proposeNote}
          onChange={event => setProposeNote(event.target.value)} /></label>
      <AsyncButton busy={isCorrectionProposeBusy(threadId)} busyText={t.correctionsProposeBusy}
        disabled={!proposeValue.trim()}
        onClick={() => {
          onProposeCorrection(threadId, proposeField, proposeValue.trim(), proposeNote.trim() || undefined);
          setProposeValue(""); setProposeNote("");
        }}>{t.correctionsProposeSend}</AsyncButton>
    </details>}
  </section>;
}

export function LettersInbox({ letterInbox, letterOutbox = [], threads = [], threadLetters = [], threadLettersStatus = "idle", selectedThreadId = null,
  isDraftBusy, replyBusyId = null, isLetterActionBusy, isConnectionDecisionBusy, isConnectionLeaveBusy, isLetterConnectionBusy,
  replyDrafts, connectionRequests, friends,
  onReplyDraftChange, onReply, onActOnLetter, onReportLetter, onRequestConnection, onDecideConnection, onLeaveConnection,
  onSendDraft, onOpenThread, locale = "zh-CN",
  onSetReceiptPolicy, isReceiptPolicyBusy,
  letterVoiceLetterId = null, letterVoiceAudio = null, letterVoiceError = null,
  isLetterVoiceBusy = () => false, onPlayLetterVoice, refreshBusy = false, onRefresh, onComposeNew,
  directLetterBusy = false, onSendDirectLetter,
  threadCorrections = { incoming: [], outgoing: [] }, threadCorrectionsStatus = "idle",
  isCorrectionBusy = () => false, isCorrectionProposeBusy = () => false,
  onProposeCorrection, onAcceptCorrection, onRejectCorrection, onWithdrawCorrection,
  liveChatInvites = { incoming: [], outgoing: [] }, liveChatSessions = [], selectedLiveChatSessionId = null,
  liveChatMessages = [], liveChatStatus = "idle", currentUserId = null,
  isLiveChatInviteBusy = () => false, isLiveChatDecisionBusy = () => false,
  isLiveChatMessageBusy = () => false, isLiveChatEndBusy = () => false,
  onInviteLiveChat, onRespondLiveChatInvite, onSelectLiveChatSession, onSendLiveChatMessage, onEndLiveChatSession }: {
  letterInbox: SlowLetter[]; letterOutbox?: SlowLetterOutboxRow[]; threads?: LetterThread[]; threadLetters?: SlowLetter[];
  threadLettersStatus?: "idle" | "loading" | "success" | "error";
  selectedThreadId?: number | null; replyBusyId?: number | null;
  // Gemini audit 4.8 (CONFIRMED/P1): every busy check here is keyed by the SPECIFIC letter/
  // connection/draft it targets -- markRead/decline/block/report on one letter, or accept/decline
  // on one connection request, must never disable the equivalent button for an unrelated one.
  isDraftBusy: (draftId: number) => boolean; isLetterActionBusy: (letterId: number) => boolean;
  isConnectionDecisionBusy: (requestId: number) => boolean; isConnectionLeaveBusy: (connectionId: number) => boolean;
  isLetterConnectionBusy: (letterId: number) => boolean;
  replyDrafts: Record<number, string>; connectionRequests: ConnectionRequests; friends: SocialConnection[];
  onReplyDraftChange: (letterId: number, value: string) => void; onReply: (letter: SlowLetter) => void;
  // Accepts inbox SlowLetter rows AND outbox projection rows -- only the id reaches the API.
  onActOnLetter: (letter: { id: number }, action: "read" | "decline" | "block" | "archive") => void;
  onReportLetter: (letter: SlowLetter) => void; onRequestConnection: (letter: SlowLetter) => void;
  onDecideConnection: (id: number, decision: "accept" | "decline") => void; onLeaveConnection: (id: number) => void;
  onSendDraft?: (id: number) => void; onOpenThread?: (threadId: number) => void; locale?: Locale;
  // W1 slow-letter voice reuse (optional -- defaults to "no play affordance" so callers not passing
  // these props, including existing tests, render unchanged). All four are wired together by
  // useConnectionsAndLetters.playLetterVoice and bound to one active clip at a time.
  letterVoiceLetterId?: number | null; letterVoiceAudio?: string | null; letterVoiceError?: string | null;
  isLetterVoiceBusy?: (letterId: number) => boolean; onPlayLetterVoice?: (letter: SlowLetter) => void;
  refreshBusy?: boolean; onRefresh?: () => void; onComposeNew?: () => void;
  // CP-33 §2-6: the recipient's per-letter read-receipt switch. Optional exactly like the
  // voice props above -- callers not passing it (including existing tests) render unchanged,
  // i.e. NO receipt control appears. Default state is ALWAYS off (NEVER): the sender is not
  // told anything unless the recipient opts in, and the copy says so explicitly.
  onSetReceiptPolicy?: (letter: SlowLetter, policy: ReceiptPolicy) => void;
  isReceiptPolicyBusy?: (letterId: number) => boolean;
  directLetterBusy?: boolean;
  onSendDirectLetter?: (receiverUserId: number, title: string, body: string, delivery: DeliverySchedule) => Promise<boolean>;
  // CP-34 both-party-consent corrections (all optional; absent props render no correction block).
  threadCorrections?: { incoming: RelationCorrectionProposal[]; outgoing: RelationCorrectionProposal[] };
  threadCorrectionsStatus?: "idle" | "loading" | "success" | "error";
  isCorrectionBusy?: (proposalId: number) => boolean;
  isCorrectionProposeBusy?: (threadId: number) => boolean;
  onProposeCorrection?: (threadId: number, correctionField: string, proposedValue: string, note?: string) => void;
  onAcceptCorrection?: (proposalId: number) => void;
  onRejectCorrection?: (proposalId: number) => void;
  onWithdrawCorrection?: (proposalId: number) => void;
  liveChatInvites?: LiveChatInvites; liveChatSessions?: LiveChatSession[];
  selectedLiveChatSessionId?: number | null; liveChatMessages?: LiveChatMessage[];
  liveChatStatus?: "idle" | "loading" | "success" | "error"; currentUserId?: number | null;
  isLiveChatInviteBusy?: (userId: number) => boolean; isLiveChatDecisionBusy?: (inviteId: number) => boolean;
  isLiveChatMessageBusy?: (sessionId: number) => boolean; isLiveChatEndBusy?: (sessionId: number) => boolean;
  onInviteLiveChat?: (userId: number, duration: 10 | 15) => void;
  onRespondLiveChatInvite?: (inviteId: number, decision: "accept" | "decline") => void;
  onSelectLiveChatSession?: (sessionId: number) => void;
  onSendLiveChatMessage?: (sessionId: number, body: string) => Promise<boolean>;
  onEndLiveChatSession?: (sessionId: number) => void;
}) {
  const t = COPY[locale];
  const [tab, setTab] = useState<"inbox" | "outbox" | "drafts" | "threads">("inbox");
  const [directComposeOpen, setDirectComposeOpen] = useState(false);
  const [directReceiverId, setDirectReceiverId] = useState("");
  const [directTitle, setDirectTitle] = useState("");
  const [directBody, setDirectBody] = useState("");
  const [deliveryPreset, setDeliveryPreset] = useState<DeliveryPreset>("DEMO_30S");
  const [customArrival, setCustomArrival] = useState("");
  const [now, setNow] = useState(Date.now());
  const composeHintId = useId();
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1_000);
    return () => window.clearInterval(timer);
  }, []);
  const drafts = letterOutbox.filter(l => l.senderStatus === "DRAFT");
  const sent = letterOutbox.filter(l => l.senderStatus !== "DRAFT");
  const counts: Record<string, string> = { inbox: t.counts.inbox(letterInbox.length), outbox: t.counts.outbox(sent.length), drafts: t.counts.drafts(drafts.length), threads: t.counts.threads(threads.length) };
  const status = (s: string) => t.outboxStatus[s] ?? s;
  const canComposeDirect = friends.length > 0 && Boolean(onSendDirectLetter);
  const canDiscoverRecipient = Boolean(onComposeNew);
  const showComposeEntry = canComposeDirect || canDiscoverRecipient;
  return <section className="letter-inbox" aria-label={t.aria}>
    <div className="resonance-heading"><div><span className="eyebrow">{locale === "en-SG" ? "LETTERS, ARRIVED" : "慢信抵达"}</span><h2>{t.heading}</h2></div>
      <div className="letter-sync"><span>{counts[tab]}</span>
        {showComposeEntry && <div className="letter-compose-entry-group">
          <button type="button" className="letter-compose-entry" aria-describedby={composeHintId}
            onClick={() => canComposeDirect ? setDirectComposeOpen(true) : onComposeNew?.()}>
            {canComposeDirect ? t.composeDirect : t.composeDiscover}
          </button>
          <small id={composeHintId} className="letter-compose-hint">
            {canComposeDirect ? t.composeDirectHint : t.composeDiscoverHint}
          </small>
        </div>}
        {onRefresh && <AsyncButton className="quiet" busy={refreshBusy} busyText={t.refreshBusy}
          onClick={onRefresh}>{t.refresh}</AsyncButton>}
      </div></div>
    <small className="letter-sync-note">{t.autoRefreshNote}</small>
    {directComposeOpen && onSendDirectLetter && <div className="direct-letter-compose" aria-label={t.directTo}>
      <strong>{t.directTo}</strong>
      <select aria-label={t.directPick} value={directReceiverId} onChange={event => setDirectReceiverId(event.target.value)}>
        <option value="">{t.directPick}</option>
        {friends.map(friend => <option key={friend.userId} value={friend.userId}>{friend.nickname}</option>)}
      </select>
      <input aria-label={t.directTitle} placeholder={t.directTitle} value={directTitle}
        onChange={event => setDirectTitle(event.target.value)} />
      <textarea aria-label={t.directBody} placeholder={t.directBody} value={directBody}
        onChange={event => setDirectBody(event.target.value)} />
      <fieldset className="letter-delivery-rhythm">
        <legend>{t.deliveryRhythm}</legend>
        <div>{(Object.keys(t.deliveryOptions) as DeliveryPreset[]).map(option =>
          <button type="button" key={option} aria-pressed={deliveryPreset === option}
            onClick={() => setDeliveryPreset(option)}>{t.deliveryOptions[option]}</button>)}</div>
        {deliveryPreset === "CUSTOM" && <label>{t.customArrival}
          <input type="datetime-local" value={customArrival} min={toLocalDateTimeInputValue(new Date(Date.now() + 60_000))}
            onChange={event => setCustomArrival(event.target.value)} />
        </label>}
        <small>{t.deliveryHint}</small>
      </fieldset>
      <div className="letter-seal-preview" aria-hidden="true"><span>✦</span><i /></div>
      <small className="letter-seal-note">{t.sealNote}</small>
      <div>
        <button type="button" className="quiet" onClick={() => setDirectComposeOpen(false)}>{t.directCancel}</button>
        <AsyncButton busy={directLetterBusy} busyText={t.directBusy}
          disabled={!directReceiverId || !directTitle.trim() || !directBody.trim() || (deliveryPreset === "CUSTOM" && !customArrival)}
          onClick={() => {
            const delivery: DeliverySchedule = {
              deliveryPreset,
              timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Shanghai",
              ...(deliveryPreset === "CUSTOM" ? { customArrivalAt: new Date(customArrival).toISOString() } : {})
            };
            void onSendDirectLetter(Number(directReceiverId), directTitle, directBody, delivery).then(sent => {
              if (!sent) return;
              setDirectComposeOpen(false); setDirectReceiverId(""); setDirectTitle(""); setDirectBody("");
              setTab("outbox");
            });
          }}>{t.directSend}</AsyncButton>
      </div>
    </div>}
    <div className="letter-tabs" role="tablist" aria-label={t.tabsAria}>
      <button type="button" role="tab" aria-selected={tab === "inbox"} className={tab === "inbox" ? "active" : ""} onClick={() => setTab("inbox")}>{t.tabInbox}</button>
      <button type="button" role="tab" aria-selected={tab === "outbox"} className={tab === "outbox" ? "active" : ""} onClick={() => setTab("outbox")}>{t.tabOutbox}</button>
      <button type="button" role="tab" aria-selected={tab === "drafts"} className={tab === "drafts" ? "active" : ""} onClick={() => setTab("drafts")}>{t.tabDrafts}</button>
      <button type="button" role="tab" aria-selected={tab === "threads"} className={tab === "threads" ? "active" : ""} onClick={() => setTab("threads")}>{t.tabThreads}</button>
    </div>

    {tab === "inbox" ? <>
      <p className="resonance-intro">{t.inboxIntro}</p>
      {letterInbox.length === 0 ? <div className="network-empty">{t.inboxEmpty}</div> : <div className="inbox-list">
        {letterInbox.map(letter => <article key={letter.id}><header><strong>{letter.title}</strong><span>{status(letter.status)}</span></header>
          <p className="ugc-text">{letter.letterBody}</p>
          {onPlayLetterVoice && <div className="letter-voice">
            {/* W1 slow-letter voice reuse: tap-to-play the delivered body read aloud. Every inbox
                letter has already arrived (the inbox query only returns delivered-or-later statuses),
                so the affordance is shown on each. autoPlay on arrival is safe: the tap below is the
                user gesture that authorizes playback (same reasoning as the capsule-voice bubble). */}
            {letterVoiceLetterId === letter.id && letterVoiceAudio
              ? <InlineAudioPlayer audio={letterVoiceAudio} autoPlay locale={locale} ariaLabel={t.letterVoiceAria} />
              : <AsyncButton className="quiet" busy={isLetterVoiceBusy?.(letter.id) ?? false} busyText={t.letterVoiceBusy}
                  onClick={() => onPlayLetterVoice(letter)}>{t.playLetterVoice}</AsyncButton>}
            {letterVoiceLetterId === letter.id && letterVoiceError && <span className="voice-error" role="alert">{letterVoiceError}</span>}
          </div>}
          {repliable.has(letter.status) && <div className="letter-reply"><textarea aria-label={t.replyAria(letter.title)}
            value={replyDrafts[letter.id] ?? ""} onChange={event => onReplyDraftChange(letter.id, event.target.value)}
            placeholder={t.replyPlaceholder} /><AsyncButton busy={replyBusyId === letter.id} busyText={t.replyBusy} disabled={!replyDrafts[letter.id]?.trim()} onClick={() => onReply(letter)}>{t.replySend}</AsyncButton></div>}
          {onSetReceiptPolicy && (() => {
            // CP-33 §2-6: honest default -- absent/null policy means NEVER (no receipt is
            // emitted). The hint states plainly what the sender currently learns, so "off"
            // can never masquerade as "they just haven't read it yet".
            const policy = receiptPolicyOf(letter);
            return <div className="letter-receipt-preference">
              <span>{t.receiptControl}</span>
              <AsyncButton className="quiet" aria-pressed={policy === "ALWAYS"}
                busy={isReceiptPolicyBusy?.(letter.id) ?? false} busyText={t.receiptBusy}
                onClick={() => onSetReceiptPolicy(letter, policy === "ALWAYS" ? "NEVER" : "ALWAYS")}>
                {policy === "ALWAYS" ? t.receiptOnAction : t.receiptOffAction}
              </AsyncButton>
              <small>{policy === "ALWAYS" ? t.receiptOnHint : t.receiptOffHint}</small>
            </div>;
          })()}
          <div className="letter-primary-actions">
            {letter.status === "DELIVERED" && <AsyncButton busy={isLetterActionBusy(letter.id)} busyText={t.markReadBusy} onClick={() => onActOnLetter(letter, "read")}>{t.markRead}</AsyncButton>}
            {declinable.has(letter.status) && <AsyncButton busy={isLetterActionBusy(letter.id)} busyText={t.declineBusy} onClick={() => onActOnLetter(letter, "decline")}>{t.decline}</AsyncButton>}
            {repliable.has(letter.status) && <AsyncButton busy={isLetterConnectionBusy(letter.id)} busyText={t.willKnowBusy} onClick={() => onRequestConnection(letter)}>{t.willKnow}</AsyncButton>}
          </div>
          <details className="letter-secondary-actions">
            <summary>{t.safetyActions}</summary>
            <div>
              {letter.status !== "BLOCKED" && <AsyncButton busy={isLetterActionBusy(letter.id)} busyText={t.blockBusy} onClick={() => onActOnLetter(letter, "block")}>{t.block}</AsyncButton>}
              <AsyncButton busy={isLetterActionBusy(letter.id)} busyText={t.reportBusy} onClick={() => onReportLetter(letter)}>{t.report}</AsyncButton>
            </div>
          </details>
        </article>)}
      </div>}
    </> : tab === "outbox" ? <>
      <p className="resonance-intro">{t.outboxIntro}</p>
      {sent.length === 0 ? <div className="network-empty">{t.outboxEmpty}</div> : <div className="inbox-list outbox-list">
        {sent.map(letter => {
          const eta = letter.scheduledArrivalAt;
          const remainingSeconds = eta ? secondsUntilSlowLetterArrival(eta, now) : 0;
          const remaining = `${String(Math.floor(remainingSeconds / 60)).padStart(2, "0")}:${String(remainingSeconds % 60).padStart(2, "0")}`;
          const stage = letter.senderStatus === "READ" || letter.senderStatus === "REPLIED" ? 3
            : letter.senderStatus === "DELIVERED" ? 2 : letter.senderStatus === "FLYING" || letter.senderStatus === "SENT" ? 1 : 0;
          return <article key={letter.id} className={`letter-ritual-card stage-${stage}`}><header><strong>{letter.title}</strong>
          {/* CP-33 §2-6: the backend only ever reveals READ when the recipient opted in, so the
              label says who made that call -- never a passive "已读" that hides the choice. */}
          <span className="outbox-status">{letter.senderStatus === "READ" ? t.outboxReadReceipt : status(letter.senderStatus)}</span></header>
          {/* CP-33: sent bodies are not echoed by the list endpoint (privacy projection);
              a DRAFT still shows its own composing text. */}
          <p className="ugc-text">{letter.letterBody ?? letter.statusExplanation}</p>
          <div className="letter-ritual-steps" aria-label={`${status(letter.senderStatus)} · ${eta ? t.arrivalEta(formatSlowLetterInstant(eta, { locale })) : ""}`}>
            {["封缄", "旅途", "抵达", "开启"].map((label, index) =>
              <span key={label} className={index <= stage ? "is-reached" : ""}><i />{locale === "en-SG" ? ["Sealed", "Journey", "Arrived", "Opened"][index] : label}</span>)}
          </div>
          {letter.senderStatus === "FLYING" && <div className="letter-flying-transit" aria-hidden="true"><span className="letter-flying-point" /></div>}
          {eta && (letter.senderStatus === "FLYING" || letter.senderStatus === "SENT") &&
            <div className="letter-arrival-clock"><strong>{t.countdown(remaining)}</strong><small>{t.arrivalEta(formatSlowLetterInstant(eta, { locale }))}</small></div>}
          {archivableFromOutbox.has(letter.senderStatus) && <AsyncButton busy={isLetterActionBusy(letter.id)} busyText={t.archiveBusy} onClick={() => onActOnLetter(letter, "archive")}>{t.archiveLetter}</AsyncButton>}
        </article>;})}
      </div>}
    </> : tab === "drafts" ? <>
      <p className="resonance-intro">{t.draftsIntro}</p>
      {drafts.length === 0 ? <div className="network-empty">{t.draftsEmpty}</div> : <div className="inbox-list outbox-list">
        {drafts.map(letter => <article key={letter.id}><header><strong>{letter.title || t.untitledDraft}</strong><span className="outbox-status">{t.draftStatus}</span></header>
          <p className="ugc-text">{letter.letterBody}</p>
          <div><AsyncButton busy={isDraftBusy(letter.id)} busyText={t.sendDraftBusy} onClick={() => onSendDraft?.(letter.id)}>{t.sendDraft}</AsyncButton></div>
        </article>)}
      </div>}
    </> : <>
      <p className="resonance-intro">{t.threadsIntro}</p>
      {threads.length === 0 ? <div className="network-empty">{t.threadsEmpty}</div> : <div className="letter-threads">
        <ul className="thread-list" role="list">
          {threads.map(thread => {
            const label = t.threadItem(thread.id);
            const statusText = `${thread.status}${thread.lastLetterAt ? ` · ${new Date(thread.lastLetterAt).toLocaleDateString(locale)}` : ""}`;
            // W2 UIUX audit: same run-on-naming shape as ProductShellNavigation's five-space tabs --
            // <strong>label</strong><small>status</small> sit with no separator inside this button, so
            // its accessible name would concatenate into one run-on string (e.g. "往来 #3FLYING").
            // aria-hidden the visual duplicate and give the button a properly separated aria-label.
            return <li key={thread.id}><button type="button" className={"thread-item" + (selectedThreadId === thread.id ? " is-selected" : "")}
              aria-pressed={selectedThreadId === thread.id} aria-label={t.threadItemAria(label, statusText)}
              onClick={() => onOpenThread?.(thread.id)}>
              <strong aria-hidden="true">{label}</strong><small aria-hidden="true">{statusText}</small>
            </button></li>;
          })}
        </ul>
        <div className="thread-letters" aria-live="polite">
          {!selectedThreadId ? <div className="network-empty">{t.threadPickPrompt}</div>
            // Gemini audit 4.9 fix: an explicit status distinguishes "still loading" from a
            // genuinely empty successful response and from a failed fetch -- `threadLetters.length
            // === 0` alone used to mean all three, so a real empty thread was mislabeled "loading"
            // forever.
            : threadLettersStatus === "loading" ? <LoadingText busy className="network-empty">{t.threadLoading}</LoadingText>
            : threadLettersStatus === "error" ? <div className="network-empty" role="alert">{t.threadLettersError}</div>
            : threadLetters.length === 0 ? <div className="network-empty">{t.threadLettersEmpty}</div>
            : <div className="inbox-list">{threadLetters.map(letter => <article key={letter.id}><header><strong>{letter.title}</strong><span>{status(letter.status)}</span></header><p className="ugc-text">{letter.letterBody}</p></article>)}</div>}
          {selectedThreadId != null && (onAcceptCorrection || onProposeCorrection || onWithdrawCorrection || onRejectCorrection) && <ThreadCorrections
            key={selectedThreadId} threadId={selectedThreadId} corrections={threadCorrections}
            status={threadCorrectionsStatus} locale={locale} t={t}
            isCorrectionBusy={isCorrectionBusy} isCorrectionProposeBusy={isCorrectionProposeBusy}
            onProposeCorrection={onProposeCorrection} onAcceptCorrection={onAcceptCorrection}
            onRejectCorrection={onRejectCorrection} onWithdrawCorrection={onWithdrawCorrection} />}
        </div>
      </div>}
    </>}

    <div className="connection-consent" aria-label={t.consentAria}>
      <div><strong>{t.awaitingYou}</strong>{connectionRequests.incoming.length === 0 ? <small>{t.noIncoming}</small> : connectionRequests.incoming.map(item =>
        <article key={item.id}><span>{t.wantsToKnow(item.nickname)}</span><div>
          <AsyncButton busy={isConnectionDecisionBusy(item.id)} busyText={t.acceptBusy} onClick={() => onDecideConnection(item.id, "accept")}>{t.accept}</AsyncButton>
          <AsyncButton busy={isConnectionDecisionBusy(item.id)} busyText={t.declineConnBusy} onClick={() => onDecideConnection(item.id, "decline")}>{t.declineConn}</AsyncButton>
        </div></article>)}</div>
      <div><strong>{t.awaitingThem}</strong>{connectionRequests.outgoing.length === 0 ? <small>{t.noOutgoing}</small> : connectionRequests.outgoing.map(item => <article key={item.id}><span>{item.nickname}</span><small>{t.notYetAgreed}</small></article>)}</div>
      <div><strong>{t.bothAgreed}</strong>{friends.length === 0 ? <small>{t.noFriends}</small> : friends.map(item => <article key={item.id}><span>{item.nickname}</span><AsyncButton busy={isConnectionLeaveBusy(item.id)} busyText={t.leaveBusy} onClick={() => onLeaveConnection(item.id)}>{t.leave}</AsyncButton></article>)}</div>
    </div>
    {onInviteLiveChat && onRespondLiveChatInvite && onSelectLiveChatSession && onSendLiveChatMessage && onEndLiveChatSession &&
      <LiveChatPanel friends={friends} invites={liveChatInvites} sessions={liveChatSessions}
        selectedSessionId={selectedLiveChatSessionId} messages={liveChatMessages} status={liveChatStatus}
        currentUserId={currentUserId} isInviteBusy={isLiveChatInviteBusy} isDecisionBusy={isLiveChatDecisionBusy}
        isMessageBusy={isLiveChatMessageBusy} isEndBusy={isLiveChatEndBusy}
        onWriteLetter={userId => { setDirectReceiverId(String(userId)); setDirectComposeOpen(true); }}
        onInvite={onInviteLiveChat} onRespond={onRespondLiveChatInvite} onSelectSession={onSelectLiveChatSession}
        onSendMessage={onSendLiveChatMessage} onEndSession={onEndLiveChatSession} locale={locale} />}
  </section>;
}
