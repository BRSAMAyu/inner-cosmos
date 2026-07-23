import { useState } from "react";
import type { ConnectionRequests, LetterThread, SlowLetter, SocialConnection } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton, LoadingText } from "../loading";

const repliable = new Set(["READ", "REPLIED"]);
const declinable = new Set(["DELIVERED", "READ"]);
const archivableFromOutbox = new Set(["READ", "REPLIED", "DECLINED", "BLOCKED"]);

const COPY: Record<Locale, {
  outboxStatus: Record<string, string>;
  counts: { inbox: (n: number) => string; outbox: (n: number) => string; drafts: (n: number) => string; threads: (n: number) => string };
  aria: string; heading: string; tabsAria: string; tabInbox: string; tabOutbox: string; tabDrafts: string; tabThreads: string;
  inboxIntro: string; inboxEmpty: string; replyAria: (title: string) => string; replyPlaceholder: string; replyBusy: string; replySend: string;
  markRead: string; markReadBusy: string; decline: string; declineBusy: string;
  willKnow: string; willKnowBusy: string; block: string; blockBusy: string; report: string; reportBusy: string;
  outboxIntro: string; outboxEmpty: string; arrivalEta: (time: string) => string; archiveLetter: string; archiveBusy: string;
  draftsIntro: string; draftsEmpty: string; untitledDraft: string; draftStatus: string; sendDraftBusy: string; sendDraft: string;
  threadsIntro: string; threadsEmpty: string; threadItem: (id: number) => string; threadItemAria: (label: string, statusText: string) => string;
  threadPickPrompt: string; threadLoading: string;
  threadLettersEmpty: string; threadLettersError: string;
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
    willKnow: "愿意认识对方", willKnowBusy: "正在发出", block: "屏蔽后续来信", blockBusy: "正在屏蔽", report: "举报这封信", reportBusy: "正在提交",
    outboxIntro: "你写出去的信都在这里。它们会按各自的节奏抵达；对方是否回应由对方决定，你不会被催促，也不会看到假装的实时状态。", outboxEmpty: "你还没有寄出任何慢信。",
    arrivalEta: t => `预计 ${t} 抵达`, archiveLetter: "归档", archiveBusy: "正在归档",
    draftsIntro: "还没寄出的信留在这里。你可以慢慢改，准备好了再让它启程——寄出后它会按慢信的节奏抵达。", draftsEmpty: "没有草稿。", untitledDraft: "未命名草稿", draftStatus: "草稿",
    sendDraftBusy: "正在寄出", sendDraft: "让这封信启程",
    threadsIntro: "同一段关系里来回的慢信会聚成一条往来。点开看看你们之间慢慢积累的对话。", threadsEmpty: "还没有形成往来的慢信线程。",
    threadItem: id => `往来 #${id}`, threadItemAria: (label, statusText) => `${label} · ${statusText}`,
    threadPickPrompt: "选一段往来，看你们之间的慢信。", threadLoading: "正在读取这段往来…",
    threadLettersEmpty: "这段往来里还没有信件。", threadLettersError: "暂时读不到这段往来，请稍后再试。",
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
    willKnow: "Willing to know them", willKnowBusy: "Sending", block: "Block future letters", blockBusy: "Blocking", report: "Report this letter", reportBusy: "Submitting",
    outboxIntro: "Every letter you've sent is here. Each arrives at its own pace; whether they reply is theirs to decide — you're never rushed, and never shown a fake live status.", outboxEmpty: "You haven't sent any slow letters yet.",
    arrivalEta: t => `Arrives ~${t}`, archiveLetter: "Archive", archiveBusy: "Archiving",
    draftsIntro: "Letters not yet sent stay here. Revise slowly and send when ready — once sent, it arrives at a slow letter's pace.", draftsEmpty: "No drafts.", untitledDraft: "Untitled draft", draftStatus: "Draft",
    sendDraftBusy: "Sending", sendDraft: "Send this letter",
    threadsIntro: "Letters back and forth in one relationship gather into a thread. Open one to see the conversation you've slowly built.", threadsEmpty: "No slow-letter threads yet.",
    threadItem: id => `Thread #${id}`, threadItemAria: (label, statusText) => `${label} · ${statusText}`,
    threadPickPrompt: "Pick a thread to see the letters between you.", threadLoading: "Loading this thread…",
    threadLettersEmpty: "No letters in this thread yet.", threadLettersError: "Couldn't load this thread right now -- try again shortly.",
    consentAria: "Mutual connection consent", awaitingYou: "Awaiting your decision", noIncoming: "No new connection invitations", wantsToKnow: name => `${name} would like to know you after the letters`, accept: "I'd like to too", acceptBusy: "Accepting", declineConn: "Not yet", declineConnBusy: "Declining",
    awaitingThem: "Awaiting their decision", noOutgoing: "No pending invitations", notYetAgreed: "Not yet agreed — a real connection won't open early", bothAgreed: "Both agreed", noFriends: "No real connections yet", leave: "Leave connection", leaveBusy: "Leaving"
  }
};

export function LettersInbox({ letterInbox, letterOutbox = [], threads = [], threadLetters = [], threadLettersStatus = "idle", selectedThreadId = null,
  isDraftBusy, replyBusyId = null, isLetterActionBusy, isConnectionDecisionBusy, isConnectionLeaveBusy, isLetterConnectionBusy,
  replyDrafts, connectionRequests, friends,
  onReplyDraftChange, onReply, onActOnLetter, onReportLetter, onRequestConnection, onDecideConnection, onLeaveConnection,
  onSendDraft, onOpenThread, locale = "zh-CN" }: {
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
}) {
  const t = COPY[locale];
  const [tab, setTab] = useState<"inbox" | "outbox" | "drafts" | "threads">("inbox");
  const drafts = letterOutbox.filter(l => l.status === "DRAFT");
  const sent = letterOutbox.filter(l => l.status !== "DRAFT");
  const counts: Record<string, string> = { inbox: t.counts.inbox(letterInbox.length), outbox: t.counts.outbox(sent.length), drafts: t.counts.drafts(drafts.length), threads: t.counts.threads(threads.length) };
  const status = (s: string) => t.outboxStatus[s] ?? s;
  return <section className="letter-inbox" aria-label={t.aria}>
    <div className="resonance-heading"><div><span className="eyebrow">LETTERS, ARRIVED</span><h2>{t.heading}</h2></div>
      <span>{counts[tab]}</span></div>
    <div className="letter-tabs" role="tablist" aria-label={t.tabsAria}>
      <button type="button" role="tab" aria-selected={tab === "inbox"} className={tab === "inbox" ? "active" : ""} onClick={() => setTab("inbox")}>{t.tabInbox}</button>
      <button type="button" role="tab" aria-selected={tab === "outbox"} className={tab === "outbox" ? "active" : ""} onClick={() => setTab("outbox")}>{t.tabOutbox}</button>
      <button type="button" role="tab" aria-selected={tab === "drafts"} className={tab === "drafts" ? "active" : ""} onClick={() => setTab("drafts")}>{t.tabDrafts}</button>
      <button type="button" role="tab" aria-selected={tab === "threads"} className={tab === "threads" ? "active" : ""} onClick={() => setTab("threads")}>{t.tabThreads}</button>
    </div>

    {tab === "inbox" ? <>
      <p className="resonance-intro">{t.inboxIntro}</p>
      {letterInbox.length === 0 ? <div className="network-empty">{t.inboxEmpty}</div> : <div className="inbox-list">
        {letterInbox.map(letter => <article key={letter.id}><header><strong>{letter.title}</strong><span>{letter.status}</span></header>
          <p className="ugc-text">{letter.letterBody}</p>
          {repliable.has(letter.status) && <div className="letter-reply"><textarea aria-label={t.replyAria(letter.title)}
            value={replyDrafts[letter.id] ?? ""} onChange={event => onReplyDraftChange(letter.id, event.target.value)}
            placeholder={t.replyPlaceholder} /><AsyncButton busy={replyBusyId === letter.id} busyText={t.replyBusy} disabled={!replyDrafts[letter.id]?.trim()} onClick={() => onReply(letter)}>{t.replySend}</AsyncButton></div>}
          <div>
            {letter.status === "DELIVERED" && <AsyncButton busy={isLetterActionBusy(letter.id)} busyText={t.markReadBusy} onClick={() => onActOnLetter(letter, "read")}>{t.markRead}</AsyncButton>}
            {declinable.has(letter.status) && <AsyncButton busy={isLetterActionBusy(letter.id)} busyText={t.declineBusy} onClick={() => onActOnLetter(letter, "decline")}>{t.decline}</AsyncButton>}
            {repliable.has(letter.status) && <AsyncButton busy={isLetterConnectionBusy(letter.id)} busyText={t.willKnowBusy} onClick={() => onRequestConnection(letter)}>{t.willKnow}</AsyncButton>}
            {letter.status !== "BLOCKED" && <AsyncButton busy={isLetterActionBusy(letter.id)} busyText={t.blockBusy} onClick={() => onActOnLetter(letter, "block")}>{t.block}</AsyncButton>}
            <AsyncButton busy={isLetterActionBusy(letter.id)} busyText={t.reportBusy} onClick={() => onReportLetter(letter)}>{t.report}</AsyncButton>
          </div></article>)}
      </div>}
    </> : tab === "outbox" ? <>
      <p className="resonance-intro">{t.outboxIntro}</p>
      {sent.length === 0 ? <div className="network-empty">{t.outboxEmpty}</div> : <div className="inbox-list outbox-list">
        {sent.map(letter => <article key={letter.id}><header><strong>{letter.title}</strong>
          <span className="outbox-status">{status(letter.status)}</span></header>
          <p className="ugc-text">{letter.letterBody}</p>
          {letter.status === "FLYING" && <div className="letter-flying-transit" aria-hidden="true"><span className="letter-flying-point" /></div>}
          {letter.estimatedArrivalAt && (letter.status === "FLYING" || letter.status === "SENT") &&
            <small>{t.arrivalEta(new Date(letter.estimatedArrivalAt).toLocaleString(locale))}</small>}
          {archivableFromOutbox.has(letter.status) && <AsyncButton busy={isLetterActionBusy(letter.id)} busyText={t.archiveBusy} onClick={() => onActOnLetter(letter, "archive")}>{t.archiveLetter}</AsyncButton>}
        </article>)}
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
  </section>;
}
