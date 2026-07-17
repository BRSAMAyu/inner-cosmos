import { useState } from "react";
import type { ConnectionRequests, LetterThread, SlowLetter, SocialConnection } from "../api";
import { AsyncButton } from "../loading";

const repliable = new Set(["READ", "REPLIED"]);
const declinable = new Set(["DELIVERED", "READ"]);

// Human-readable status for a letter the user has SENT — the outbox is read-only, so it only
// needs to explain where each letter is in its slow journey.
const outboxStatusLabel: Record<string, string> = {
  DRAFT: "草稿", SENT: "已寄出", IN_FLIGHT: "飞行中", DELIVERED: "已抵达", READ: "对方已读",
  REPLIED: "对方回信了", DECLINED: "被婉拒", BLOCKED: "被屏蔽", ARCHIVED: "已归档"
};

export function LettersInbox({ letterInbox, letterOutbox = [], threads = [], threadLetters = [], selectedThreadId = null,
  draftBusy = false, replyDrafts, connectionRequests, friends,
  onReplyDraftChange, onReply, onActOnLetter, onReportLetter, onRequestConnection, onDecideConnection, onLeaveConnection,
  onSendDraft, onOpenThread }: {
  letterInbox: SlowLetter[]; letterOutbox?: SlowLetter[]; threads?: LetterThread[]; threadLetters?: SlowLetter[];
  selectedThreadId?: number | null; draftBusy?: boolean;
  replyDrafts: Record<number, string>; connectionRequests: ConnectionRequests; friends: SocialConnection[];
  onReplyDraftChange: (letterId: number, value: string) => void; onReply: (letter: SlowLetter) => void;
  onActOnLetter: (letter: SlowLetter, action: "read" | "decline" | "block" | "archive") => void;
  onReportLetter: (letter: SlowLetter) => void; onRequestConnection: (letter: SlowLetter) => void;
  onDecideConnection: (id: number, decision: "accept" | "decline") => void; onLeaveConnection: (id: number) => void;
  onSendDraft?: (id: number) => void; onOpenThread?: (threadId: number) => void;
}) {
  const [tab, setTab] = useState<"inbox" | "outbox" | "drafts" | "threads">("inbox");
  const drafts = letterOutbox.filter(l => l.status === "DRAFT");
  const sent = letterOutbox.filter(l => l.status !== "DRAFT");
  const counts: Record<string, string> = { inbox: `${letterInbox.length} 封已抵达`, outbox: `${sent.length} 封已寄出`, drafts: `${drafts.length} 封草稿`, threads: `${threads.length} 段往来` };
  return <section className="letter-inbox" aria-label="慢信收件箱与寄件箱">
    <div className="resonance-heading"><div><span className="eyebrow">LETTERS, ARRIVED</span><h2>只在抵达之后，才由你决定关系往哪里走</h2></div>
      <span>{counts[tab]}</span></div>
    <div className="letter-tabs" role="tablist" aria-label="慢信方向">
      <button type="button" role="tab" aria-selected={tab === "inbox"} className={tab === "inbox" ? "active" : ""} onClick={() => setTab("inbox")}>收到的</button>
      <button type="button" role="tab" aria-selected={tab === "outbox"} className={tab === "outbox" ? "active" : ""} onClick={() => setTab("outbox")}>寄出的</button>
      <button type="button" role="tab" aria-selected={tab === "drafts"} className={tab === "drafts" ? "active" : ""} onClick={() => setTab("drafts")}>草稿</button>
      <button type="button" role="tab" aria-selected={tab === "threads"} className={tab === "threads" ? "active" : ""} onClick={() => setTab("threads")}>往来</button>
    </div>

    {tab === "inbox" ? <>
      <p className="resonance-intro">飞行中的信不会提前泄露正文。抵达后你可以阅读、婉拒、举报或屏蔽；屏蔽会阻断同一来信者之后的慢信。</p>
      {letterInbox.length === 0 ? <div className="network-empty">此刻没有已经抵达的慢信。</div> : <div className="inbox-list">
        {letterInbox.map(letter => <article key={letter.id}><header><strong>{letter.title}</strong><span>{letter.status}</span></header>
          <p>{letter.letterBody}</p>
          {repliable.has(letter.status) && <div className="letter-reply"><textarea aria-label={`回复「${letter.title}」`}
            value={replyDrafts[letter.id] ?? ""} onChange={event => onReplyDraftChange(letter.id, event.target.value)}
            placeholder="写下你愿意负责的回应；它仍会慢慢抵达。" /><button disabled={!replyDrafts[letter.id]?.trim()} onClick={() => onReply(letter)}>让回复慢信启程</button></div>}
          <div>
            {letter.status === "DELIVERED" && <button onClick={() => onActOnLetter(letter, "read")}>标记已读</button>}
            {declinable.has(letter.status) && <button onClick={() => onActOnLetter(letter, "decline")}>温和婉拒</button>}
            {repliable.has(letter.status) && <button onClick={() => onRequestConnection(letter)}>愿意认识对方</button>}
            {letter.status !== "BLOCKED" && <button onClick={() => onActOnLetter(letter, "block")}>屏蔽后续来信</button>}
            <button onClick={() => onReportLetter(letter)}>举报这封信</button>
          </div></article>)}
      </div>}
    </> : tab === "outbox" ? <>
      <p className="resonance-intro">你写出去的信都在这里。它们会按各自的节奏抵达；对方是否回应由对方决定，你不会被催促，也不会看到假装的实时状态。</p>
      {sent.length === 0 ? <div className="network-empty">你还没有寄出任何慢信。</div> : <div className="inbox-list outbox-list">
        {sent.map(letter => <article key={letter.id}><header><strong>{letter.title}</strong>
          <span className="outbox-status">{outboxStatusLabel[letter.status] ?? letter.status}</span></header>
          <p>{letter.letterBody}</p>
          {letter.estimatedArrivalAt && (letter.status === "IN_FLIGHT" || letter.status === "SENT") &&
            <small>预计 {new Date(letter.estimatedArrivalAt).toLocaleString()} 抵达</small>}
        </article>)}
      </div>}
    </> : tab === "drafts" ? <>
      <p className="resonance-intro">还没寄出的信留在这里。你可以慢慢改，准备好了再让它启程——寄出后它会按慢信的节奏抵达。</p>
      {drafts.length === 0 ? <div className="network-empty">没有草稿。</div> : <div className="inbox-list outbox-list">
        {drafts.map(letter => <article key={letter.id}><header><strong>{letter.title || "未命名草稿"}</strong><span className="outbox-status">草稿</span></header>
          <p>{letter.letterBody}</p>
          <div><AsyncButton busy={draftBusy} busyText="正在寄出" onClick={() => onSendDraft?.(letter.id)}>让这封信启程</AsyncButton></div>
        </article>)}
      </div>}
    </> : <>
      <p className="resonance-intro">同一段关系里来回的慢信会聚成一条往来。点开看看你们之间慢慢积累的对话。</p>
      {threads.length === 0 ? <div className="network-empty">还没有形成往来的慢信线程。</div> : <div className="letter-threads">
        <ul className="thread-list" role="list">
          {threads.map(t => <li key={t.id}><button type="button" className={"thread-item" + (selectedThreadId === t.id ? " is-selected" : "")} aria-pressed={selectedThreadId === t.id} onClick={() => onOpenThread?.(t.id)}>
            <strong>往来 #{t.id}</strong><small>{t.status}{t.lastLetterAt ? ` · ${new Date(t.lastLetterAt).toLocaleDateString("zh-CN")}` : ""}</small>
          </button></li>)}
        </ul>
        <div className="thread-letters" aria-live="polite">
          {!selectedThreadId ? <div className="network-empty">选一段往来，看你们之间的慢信。</div>
            : threadLetters.length === 0 ? <div className="network-empty">正在读取这段往来…</div>
            : <div className="inbox-list">{threadLetters.map(letter => <article key={letter.id}><header><strong>{letter.title}</strong><span>{outboxStatusLabel[letter.status] ?? letter.status}</span></header><p>{letter.letterBody}</p></article>)}</div>}
        </div>
      </div>}
    </>}

    <div className="connection-consent" aria-label="双向连接同意">
      <div><strong>等待你决定</strong>{connectionRequests.incoming.length === 0 ? <small>没有新的连接邀请</small> : connectionRequests.incoming.map(item =>
        <article key={item.id}><span>{item.nickname} 想在慢信之后认识你</span><div><button onClick={() => onDecideConnection(item.id, "accept")}>我也愿意</button><button onClick={() => onDecideConnection(item.id, "decline")}>暂不连接</button></div></article>)}</div>
      <div><strong>等待对方决定</strong>{connectionRequests.outgoing.length === 0 ? <small>没有等待中的邀请</small> : connectionRequests.outgoing.map(item => <article key={item.id}><span>{item.nickname}</span><small>尚未同意，不会提前开放真人连接</small></article>)}</div>
      <div><strong>双方已同意</strong>{friends.length === 0 ? <small>还没有建立真人连接</small> : friends.map(item => <article key={item.id}><span>{item.nickname}</span><button onClick={() => onLeaveConnection(item.id)}>退出连接</button></article>)}</div>
    </div>
  </section>;
}
