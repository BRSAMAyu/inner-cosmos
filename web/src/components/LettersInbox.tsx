import { useEffect, useId, useState } from "react";
import type {
  ConnectionRequests, DeliveryPreset, DeliverySchedule, LetterThread, LiveChatInvites, LiveChatMessage,
  LiveChatSession, SlowLetter, SocialConnection
} from "../api";
import type { Locale } from "../i18n";
import { AsyncButton, LoadingText } from "../loading";
import { formatSlowLetterInstant, secondsUntilSlowLetterArrival, toLocalDateTimeInputValue } from "../slowLetterTime";
import { InlineAudioPlayer } from "./shared/InlineAudioPlayer";
import { LiveChatPanel } from "./LiveChatPanel";

const repliable = new Set(["READ", "REPLIED"]);
const declinable = new Set(["DELIVERED", "READ"]);
const archivableFromOutbox = new Set(["READ", "REPLIED", "DECLINED", "BLOCKED"]);

const COPY: Record<Locale, {
  outboxStatus: Record<string, string>;
  counts: { inbox: (n: number) => string; outbox: (n: number) => string; drafts: (n: number) => string; threads: (n: number) => string };
  aria: string; heading: string; tabsAria: string; tabInbox: string; tabOutbox: string; tabDrafts: string; tabThreads: string;
  inboxIntro: string; inboxEmpty: string; replyAria: (title: string) => string; replyPlaceholder: string; replyBusy: string; replySend: string;
  markRead: string; markReadBusy: string; decline: string; declineBusy: string;
  playLetterVoice: string; letterVoiceBusy: string; letterVoiceAria: string;
  willKnow: string; willKnowBusy: string; block: string; blockBusy: string; report: string; reportBusy: string;
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
}> = {
  "zh-CN": {
    outboxStatus: { DRAFT: "草稿", SENT: "已寄出", FLYING: "飞行中", DELIVERED: "已抵达", READ: "对方已读", REPLIED: "对方回信了", DECLINED: "被婉拒", BLOCKED: "被屏蔽", ARCHIVED: "已归档" },
    counts: { inbox: n => `${n} 封已抵达`, outbox: n => `${n} 封已寄出`, drafts: n => `${n} 封草稿`, threads: n => `${n} 段往来` },
    aria: "慢信收件箱与寄件箱", heading: "只在抵达之后，才由你决定关系往哪里走", tabsAria: "慢信方向",
    tabInbox: "收到的", tabOutbox: "寄出的", tabDrafts: "草稿", tabThreads: "往来",
    inboxIntro: "飞行中的信不会提前泄露正文。抵达后你可以阅读、婉拒、举报或屏蔽；屏蔽会阻断同一来信者之后的慢信。", inboxEmpty: "此刻没有已经抵达的慢信。",
    replyAria: title => `回复「${title}」`, replyPlaceholder: "写下你愿意负责的回应；它仍会慢慢抵达。", replyBusy: "正在启程", replySend: "让回复慢信启程",
    markRead: "标记已读", markReadBusy: "正在标记", decline: "温和婉拒", declineBusy: "正在婉拒",
    playLetterVoice: "▶ 朗读这封信", letterVoiceBusy: "正在合成…", letterVoiceAria: "听这封慢信被朗读出来",
    willKnow: "愿意认识对方", willKnowBusy: "正在发出", block: "屏蔽后续来信", blockBusy: "正在屏蔽", report: "举报这封信", reportBusy: "正在提交",
    outboxIntro: "你写出去的信都在这里。它们会按各自的节奏抵达；对方是否回应由对方决定，你不会被催促，也不会看到假装的实时状态。", outboxEmpty: "你还没有寄出任何慢信。",
    arrivalEta: t => `预计 ${t} 抵达`, archiveLetter: "归档", archiveBusy: "正在归档",
    draftsIntro: "还没寄出的信留在这里。你可以慢慢改，准备好了再让它启程——寄出后它会按慢信的节奏抵达。", draftsEmpty: "没有草稿。", untitledDraft: "未命名草稿", draftStatus: "草稿",
    sendDraftBusy: "正在寄出", sendDraft: "让这封信启程",
    threadsIntro: "同一段关系里来回的慢信会聚成一条往来。点开看看你们之间慢慢积累的对话。", threadsEmpty: "还没有形成往来的慢信线程。",
    threadItem: id => `往来 #${id}`, threadItemAria: (label, statusText) => `${label} · ${statusText}`,
    threadPickPrompt: "选一段往来，看你们之间的慢信。", threadLoading: "正在读取这段往来…",
    threadLettersEmpty: "这段往来里还没有信件。", threadLettersError: "暂时读不到这段往来，请稍后再试。",
    refresh: "刷新慢信", refreshBusy: "正在刷新", autoRefreshNote: "停留在这里时会自动同步抵达与回信。",
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
    outboxStatus: { DRAFT: "Draft", SENT: "Sent", FLYING: "In flight", DELIVERED: "Delivered", READ: "Read", REPLIED: "Replied", DECLINED: "Declined", BLOCKED: "Blocked", ARCHIVED: "Archived" },
    counts: { inbox: n => `${n} arrived`, outbox: n => `${n} sent`, drafts: n => `${n} draft${n === 1 ? "" : "s"}`, threads: n => `${n} thread${n === 1 ? "" : "s"}` },
    aria: "Slow-letter inbox and outbox", heading: "Only after it arrives do you decide where the relationship goes", tabsAria: "Slow-letter direction",
    tabInbox: "Received", tabOutbox: "Sent", tabDrafts: "Drafts", tabThreads: "Threads",
    inboxIntro: "A letter in flight never reveals its body early. Once it arrives you can read, decline, report or block; blocking stops future letters from the same sender.", inboxEmpty: "No slow letters have arrived just now.",
    replyAria: title => `Reply to "${title}"`, replyPlaceholder: "Write a response you're willing to stand behind; it still arrives slowly.", replyBusy: "Sending", replySend: "Send the reply slow letter",
    markRead: "Mark read", markReadBusy: "Marking", decline: "Gently decline", declineBusy: "Declining",
    playLetterVoice: "▶ Read this letter aloud", letterVoiceBusy: "Synthesizing…", letterVoiceAria: "Hear this slow letter read aloud",
    willKnow: "Willing to know them", willKnowBusy: "Sending", block: "Block future letters", blockBusy: "Blocking", report: "Report this letter", reportBusy: "Submitting",
    outboxIntro: "Every letter you've sent is here. Each arrives at its own pace; whether they reply is theirs to decide — you're never rushed, and never shown a fake live status.", outboxEmpty: "You haven't sent any slow letters yet.",
    arrivalEta: t => `Arrives ~${t}`, archiveLetter: "Archive", archiveBusy: "Archiving",
    draftsIntro: "Letters not yet sent stay here. Revise slowly and send when ready — once sent, it arrives at a slow letter's pace.", draftsEmpty: "No drafts.", untitledDraft: "Untitled draft", draftStatus: "Draft",
    sendDraftBusy: "Sending", sendDraft: "Send this letter",
    threadsIntro: "Letters back and forth in one relationship gather into a thread. Open one to see the conversation you've slowly built.", threadsEmpty: "No slow-letter threads yet.",
    threadItem: id => `Thread #${id}`, threadItemAria: (label, statusText) => `${label} · ${statusText}`,
    threadPickPrompt: "Pick a thread to see the letters between you.", threadLoading: "Loading this thread…",
    threadLettersEmpty: "No letters in this thread yet.", threadLettersError: "Couldn't load this thread right now -- try again shortly.",
    refresh: "Refresh letters", refreshBusy: "Refreshing", autoRefreshNote: "Arrivals and replies sync automatically while you stay here.",
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

export function LettersInbox({ letterInbox, letterOutbox = [], threads = [], threadLetters = [], threadLettersStatus = "idle", selectedThreadId = null,
  isDraftBusy, replyBusyId = null, isLetterActionBusy, isConnectionDecisionBusy, isConnectionLeaveBusy, isLetterConnectionBusy,
  replyDrafts, connectionRequests, friends,
  onReplyDraftChange, onReply, onActOnLetter, onReportLetter, onRequestConnection, onDecideConnection, onLeaveConnection,
  onSendDraft, onOpenThread, locale = "zh-CN",
  letterVoiceLetterId = null, letterVoiceAudio = null, letterVoiceError = null,
  isLetterVoiceBusy = () => false, onPlayLetterVoice, refreshBusy = false, onRefresh, onComposeNew,
  directLetterBusy = false, onSendDirectLetter,
  liveChatInvites = { incoming: [], outgoing: [] }, liveChatSessions = [], selectedLiveChatSessionId = null,
  liveChatMessages = [], liveChatStatus = "idle", currentUserId = null,
  isLiveChatInviteBusy = () => false, isLiveChatDecisionBusy = () => false,
  isLiveChatMessageBusy = () => false, isLiveChatEndBusy = () => false,
  onInviteLiveChat, onRespondLiveChatInvite, onSelectLiveChatSession, onSendLiveChatMessage, onEndLiveChatSession }: {
  letterInbox: SlowLetter[]; letterOutbox?: SlowLetter[]; threads?: LetterThread[]; threadLetters?: SlowLetter[];
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
  onActOnLetter: (letter: SlowLetter, action: "read" | "decline" | "block" | "archive") => void;
  onReportLetter: (letter: SlowLetter) => void; onRequestConnection: (letter: SlowLetter) => void;
  onDecideConnection: (id: number, decision: "accept" | "decline") => void; onLeaveConnection: (id: number) => void;
  onSendDraft?: (id: number) => void; onOpenThread?: (threadId: number) => void; locale?: Locale;
  // W1 slow-letter voice reuse (optional -- defaults to "no play affordance" so callers not passing
  // these props, including existing tests, render unchanged). All four are wired together by
  // useConnectionsAndLetters.playLetterVoice and bound to one active clip at a time.
  letterVoiceLetterId?: number | null; letterVoiceAudio?: string | null; letterVoiceError?: string | null;
  isLetterVoiceBusy?: (letterId: number) => boolean; onPlayLetterVoice?: (letter: SlowLetter) => void;
  refreshBusy?: boolean; onRefresh?: () => void; onComposeNew?: () => void;
  directLetterBusy?: boolean;
  onSendDirectLetter?: (receiverUserId: number, title: string, body: string, delivery: DeliverySchedule) => Promise<boolean>;
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
  const drafts = letterOutbox.filter(l => l.status === "DRAFT");
  const sent = letterOutbox.filter(l => l.status !== "DRAFT");
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
          const eta = letter.scheduledArrivalAt || letter.estimatedArrivalAt;
          const remainingSeconds = eta ? secondsUntilSlowLetterArrival(eta, now) : 0;
          const remaining = `${String(Math.floor(remainingSeconds / 60)).padStart(2, "0")}:${String(remainingSeconds % 60).padStart(2, "0")}`;
          const stage = letter.status === "READ" || letter.status === "REPLIED" ? 3
            : letter.status === "DELIVERED" ? 2 : letter.status === "FLYING" || letter.status === "SENT" ? 1 : 0;
          return <article key={letter.id} className={`letter-ritual-card stage-${stage}`}><header><strong>{letter.title}</strong>
          <span className="outbox-status">{status(letter.status)}</span></header>
          <p className="ugc-text">{letter.letterBody}</p>
          <div className="letter-ritual-steps" aria-label={`${status(letter.status)} · ${eta ? t.arrivalEta(formatSlowLetterInstant(eta, { locale })) : ""}`}>
            {["封缄", "旅途", "抵达", "开启"].map((label, index) =>
              <span key={label} className={index <= stage ? "is-reached" : ""}><i />{locale === "en-SG" ? ["Sealed", "Journey", "Arrived", "Opened"][index] : label}</span>)}
          </div>
          {letter.status === "FLYING" && <div className="letter-flying-transit" aria-hidden="true"><span className="letter-flying-point" /></div>}
          {eta && (letter.status === "FLYING" || letter.status === "SENT") &&
            <div className="letter-arrival-clock"><strong>{t.countdown(remaining)}</strong><small>{t.arrivalEta(formatSlowLetterInstant(eta, { locale }))}</small></div>}
          {archivableFromOutbox.has(letter.status) && <AsyncButton busy={isLetterActionBusy(letter.id)} busyText={t.archiveBusy} onClick={() => onActOnLetter(letter, "archive")}>{t.archiveLetter}</AsyncButton>}
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
