import { useState } from "react";
import type { ConnectionRequests, LetterThread, SlowLetter, SocialConnection } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

const repliable = new Set(["READ", "REPLIED"]);
const declinable = new Set(["DELIVERED", "READ"]);

const COPY: Record<Locale, {
  outboxStatus: Record<string, string>;
  counts: { inbox: (n: number) => string; outbox: (n: number) => string; drafts: (n: number) => string; threads: (n: number) => string };
  aria: string; heading: string; tabsAria: string; tabInbox: string; tabOutbox: string; tabDrafts: string; tabThreads: string;
  inboxIntro: string; inboxEmpty: string; replyAria: (title: string) => string; replyPlaceholder: string; replyBusy: string; replySend: string;
  markRead: string; decline: string; willKnow: string; block: string; report: string;
  outboxIntro: string; outboxEmpty: string; arrivalEta: (time: string) => string;
  draftsIntro: string; draftsEmpty: string; untitledDraft: string; draftStatus: string; sendDraftBusy: string; sendDraft: string;
  threadsIntro: string; threadsEmpty: string; threadItem: (id: number) => string; threadPickPrompt: string; threadLoading: string;
  consentAria: string; awaitingYou: string; noIncoming: string; wantsToKnow: (name: string) => string; accept: string; declineConn: string;
  awaitingThem: string; noOutgoing: string; notYetAgreed: string; bothAgreed: string; noFriends: string; leave: string;
}> = {
  "zh-CN": {
    outboxStatus: { DRAFT: "草稿", SENT: "已寄出", IN_FLIGHT: "飞行中", DELIVERED: "已抵达", READ: "对方已读", REPLIED: "对方回信了", DECLINED: "被婉拒", BLOCKED: "被屏蔽", ARCHIVED: "已归档" },
    counts: { inbox: n => `${n} 封已抵达`, outbox: n => `${n} 封已寄出`, drafts: n => `${n} 封草稿`, threads: n => `${n} 段往来` },
    aria: "慢信收件箱与寄件箱", heading: "只在抵达之后，才由你决定关系往哪里走", tabsAria: "慢信方向",
    tabInbox: "收到的", tabOutbox: "寄出的", tabDrafts: "草稿", tabThreads: "往来",
    inboxIntro: "飞行中的信不会提前泄露正文。抵达后你可以阅读、婉拒、举报或屏蔽；屏蔽会阻断同一来信者之后的慢信。", inboxEmpty: "此刻没有已经抵达的慢信。",
    replyAria: title => `回复「${title}」`, replyPlaceholder: "写下你愿意负责的回应；它仍会慢慢抵达。", replyBusy: "正在启程", replySend: "让回复慢信启程",
    markRead: "标记已读", decline: "温和婉拒", willKnow: "愿意认识对方", block: "屏蔽后续来信", report: "举报这封信",
    outboxIntro: "你写出去的信都在这里。它们会按各自的节奏抵达；对方是否回应由对方决定，你不会被催促，也不会看到假装的实时状态。", outboxEmpty: "你还没有寄出任何慢信。",
    arrivalEta: t => `预计 ${t} 抵达`,
    draftsIntro: "还没寄出的信留在这里。你可以慢慢改，准备好了再让它启程——寄出后它会按慢信的节奏抵达。", draftsEmpty: "没有草稿。", untitledDraft: "未命名草稿", draftStatus: "草稿",
    sendDraftBusy: "正在寄出", sendDraft: "让这封信启程",
    threadsIntro: "同一段关系里来回的慢信会聚成一条往来。点开看看你们之间慢慢积累的对话。", threadsEmpty: "还没有形成往来的慢信线程。",
    threadItem: id => `往来 #${id}`, threadPickPrompt: "选一段往来，看你们之间的慢信。", threadLoading: "正在读取这段往来…",
    consentAria: "双向连接同意", awaitingYou: "等待你决定", noIncoming: "没有新的连接邀请", wantsToKnow: name => `${name} 想在慢信之后认识你`, accept: "我也愿意", declineConn: "暂不连接",
    awaitingThem: "等待对方决定", noOutgoing: "没有等待中的邀请", notYetAgreed: "尚未同意，不会提前开放真人连接", bothAgreed: "双方已同意", noFriends: "还没有建立真人连接", leave: "退出连接"
  },
  "en-SG": {
    outboxStatus: { DRAFT: "Draft", SENT: "Sent", IN_FLIGHT: "In flight", DELIVERED: "Delivered", READ: "Read", REPLIED: "Replied", DECLINED: "Declined", BLOCKED: "Blocked", ARCHIVED: "Archived" },
    counts: { inbox: n => `${n} arrived`, outbox: n => `${n} sent`, drafts: n => `${n} draft${n === 1 ? "" : "s"}`, threads: n => `${n} thread${n === 1 ? "" : "s"}` },
    aria: "Slow-letter inbox and outbox", heading: "Only after it arrives do you decide where the relationship goes", tabsAria: "Slow-letter direction",
    tabInbox: "Received", tabOutbox: "Sent", tabDrafts: "Drafts", tabThreads: "Threads",
    inboxIntro: "A letter in flight never reveals its body early. Once it arrives you can read, decline, report or block; blocking stops future letters from the same sender.", inboxEmpty: "No slow letters have arrived just now.",
    replyAria: title => `Reply to "${title}"`, replyPlaceholder: "Write a response you're willing to stand behind; it still arrives slowly.", replyBusy: "Sending", replySend: "Send the reply slow letter",
    markRead: "Mark read", decline: "Gently decline", willKnow: "Willing to know them", block: "Block future letters", report: "Report this letter",
    outboxIntro: "Every letter you've sent is here. Each arrives at its own pace; whether they reply is theirs to decide — you're never rushed, and never shown a fake live status.", outboxEmpty: "You haven't sent any slow letters yet.",
    arrivalEta: t => `Arrives ~${t}`,
    draftsIntro: "Letters not yet sent stay here. Revise slowly and send when ready — once sent, it arrives at a slow letter's pace.", draftsEmpty: "No drafts.", untitledDraft: "Untitled draft", draftStatus: "Draft",
    sendDraftBusy: "Sending", sendDraft: "Send this letter",
    threadsIntro: "Letters back and forth in one relationship gather into a thread. Open one to see the conversation you've slowly built.", threadsEmpty: "No slow-letter threads yet.",
    threadItem: id => `Thread #${id}`, threadPickPrompt: "Pick a thread to see the letters between you.", threadLoading: "Loading this thread…",
    consentAria: "Mutual connection consent", awaitingYou: "Awaiting your decision", noIncoming: "No new connection invitations", wantsToKnow: name => `${name} would like to know you after the letters`, accept: "I'd like to too", declineConn: "Not yet",
    awaitingThem: "Awaiting their decision", noOutgoing: "No pending invitations", notYetAgreed: "Not yet agreed — a real connection won't open early", bothAgreed: "Both agreed", noFriends: "No real connections yet", leave: "Leave connection"
  }
};

export function LettersInbox({ letterInbox, letterOutbox = [], threads = [], threadLetters = [], selectedThreadId = null,
  draftBusy = false, replyBusyId = null, replyDrafts, connectionRequests, friends,
  onReplyDraftChange, onReply, onActOnLetter, onReportLetter, onRequestConnection, onDecideConnection, onLeaveConnection,
  onSendDraft, onOpenThread, locale = "zh-CN" }: {
  letterInbox: SlowLetter[]; letterOutbox?: SlowLetter[]; threads?: LetterThread[]; threadLetters?: SlowLetter[];
  selectedThreadId?: number | null; draftBusy?: boolean; replyBusyId?: number | null;
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
          <p>{letter.letterBody}</p>
          {repliable.has(letter.status) && <div className="letter-reply"><textarea aria-label={t.replyAria(letter.title)}
            value={replyDrafts[letter.id] ?? ""} onChange={event => onReplyDraftChange(letter.id, event.target.value)}
            placeholder={t.replyPlaceholder} /><AsyncButton busy={replyBusyId === letter.id} busyText={t.replyBusy} disabled={!replyDrafts[letter.id]?.trim()} onClick={() => onReply(letter)}>{t.replySend}</AsyncButton></div>}
          <div>
            {letter.status === "DELIVERED" && <button onClick={() => onActOnLetter(letter, "read")}>{t.markRead}</button>}
            {declinable.has(letter.status) && <button onClick={() => onActOnLetter(letter, "decline")}>{t.decline}</button>}
            {repliable.has(letter.status) && <button onClick={() => onRequestConnection(letter)}>{t.willKnow}</button>}
            {letter.status !== "BLOCKED" && <button onClick={() => onActOnLetter(letter, "block")}>{t.block}</button>}
            <button onClick={() => onReportLetter(letter)}>{t.report}</button>
          </div></article>)}
      </div>}
    </> : tab === "outbox" ? <>
      <p className="resonance-intro">{t.outboxIntro}</p>
      {sent.length === 0 ? <div className="network-empty">{t.outboxEmpty}</div> : <div className="inbox-list outbox-list">
        {sent.map(letter => <article key={letter.id}><header><strong>{letter.title}</strong>
          <span className="outbox-status">{status(letter.status)}</span></header>
          <p>{letter.letterBody}</p>
          {letter.estimatedArrivalAt && (letter.status === "IN_FLIGHT" || letter.status === "SENT") &&
            <small>{t.arrivalEta(new Date(letter.estimatedArrivalAt).toLocaleString(locale))}</small>}
        </article>)}
      </div>}
    </> : tab === "drafts" ? <>
      <p className="resonance-intro">{t.draftsIntro}</p>
      {drafts.length === 0 ? <div className="network-empty">{t.draftsEmpty}</div> : <div className="inbox-list outbox-list">
        {drafts.map(letter => <article key={letter.id}><header><strong>{letter.title || t.untitledDraft}</strong><span className="outbox-status">{t.draftStatus}</span></header>
          <p>{letter.letterBody}</p>
          <div><AsyncButton busy={draftBusy} busyText={t.sendDraftBusy} onClick={() => onSendDraft?.(letter.id)}>{t.sendDraft}</AsyncButton></div>
        </article>)}
      </div>}
    </> : <>
      <p className="resonance-intro">{t.threadsIntro}</p>
      {threads.length === 0 ? <div className="network-empty">{t.threadsEmpty}</div> : <div className="letter-threads">
        <ul className="thread-list" role="list">
          {threads.map(thread => <li key={thread.id}><button type="button" className={"thread-item" + (selectedThreadId === thread.id ? " is-selected" : "")} aria-pressed={selectedThreadId === thread.id} onClick={() => onOpenThread?.(thread.id)}>
            <strong>{t.threadItem(thread.id)}</strong><small>{thread.status}{thread.lastLetterAt ? ` · ${new Date(thread.lastLetterAt).toLocaleDateString(locale)}` : ""}</small>
          </button></li>)}
        </ul>
        <div className="thread-letters" aria-live="polite">
          {!selectedThreadId ? <div className="network-empty">{t.threadPickPrompt}</div>
            : threadLetters.length === 0 ? <div className="network-empty">{t.threadLoading}</div>
            : <div className="inbox-list">{threadLetters.map(letter => <article key={letter.id}><header><strong>{letter.title}</strong><span>{status(letter.status)}</span></header><p>{letter.letterBody}</p></article>)}</div>}
        </div>
      </div>}
    </>}

    <div className="connection-consent" aria-label={t.consentAria}>
      <div><strong>{t.awaitingYou}</strong>{connectionRequests.incoming.length === 0 ? <small>{t.noIncoming}</small> : connectionRequests.incoming.map(item =>
        <article key={item.id}><span>{t.wantsToKnow(item.nickname)}</span><div><button onClick={() => onDecideConnection(item.id, "accept")}>{t.accept}</button><button onClick={() => onDecideConnection(item.id, "decline")}>{t.declineConn}</button></div></article>)}</div>
      <div><strong>{t.awaitingThem}</strong>{connectionRequests.outgoing.length === 0 ? <small>{t.noOutgoing}</small> : connectionRequests.outgoing.map(item => <article key={item.id}><span>{item.nickname}</span><small>{t.notYetAgreed}</small></article>)}</div>
      <div><strong>{t.bothAgreed}</strong>{friends.length === 0 ? <small>{t.noFriends}</small> : friends.map(item => <article key={item.id}><span>{item.nickname}</span><button onClick={() => onLeaveConnection(item.id)}>{t.leave}</button></article>)}</div>
    </div>
  </section>;
}
