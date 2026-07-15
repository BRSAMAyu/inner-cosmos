import type { ConnectionRequests, SlowLetter, SocialConnection } from "../api";

const repliable = new Set(["READ", "REPLIED"]);
const declinable = new Set(["DELIVERED", "READ"]);

export function LettersInbox({ letterInbox, replyDrafts, connectionRequests, friends,
  onReplyDraftChange, onReply, onActOnLetter, onReportLetter, onRequestConnection, onDecideConnection, onLeaveConnection }: {
  letterInbox: SlowLetter[]; replyDrafts: Record<number, string>; connectionRequests: ConnectionRequests; friends: SocialConnection[];
  onReplyDraftChange: (letterId: number, value: string) => void; onReply: (letter: SlowLetter) => void;
  onActOnLetter: (letter: SlowLetter, action: "read" | "decline" | "block" | "archive") => void;
  onReportLetter: (letter: SlowLetter) => void; onRequestConnection: (letter: SlowLetter) => void;
  onDecideConnection: (id: number, decision: "accept" | "decline") => void; onLeaveConnection: (id: number) => void;
}) {
  return <section className="letter-inbox" aria-label="抵达我的慢信">
    <div className="resonance-heading"><div><span className="eyebrow">LETTERS, ARRIVED</span><h2>只在抵达之后，才由你决定关系往哪里走</h2></div><span>{letterInbox.length} 封已抵达</span></div>
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
    <div className="connection-consent" aria-label="双向连接同意">
      <div><strong>等待你决定</strong>{connectionRequests.incoming.length === 0 ? <small>没有新的连接邀请</small> : connectionRequests.incoming.map(item =>
        <article key={item.id}><span>{item.nickname} 想在慢信之后认识你</span><div><button onClick={() => onDecideConnection(item.id, "accept")}>我也愿意</button><button onClick={() => onDecideConnection(item.id, "decline")}>暂不连接</button></div></article>)}</div>
      <div><strong>等待对方决定</strong>{connectionRequests.outgoing.length === 0 ? <small>没有等待中的邀请</small> : connectionRequests.outgoing.map(item => <article key={item.id}><span>{item.nickname}</span><small>尚未同意，不会提前开放真人连接</small></article>)}</div>
      <div><strong>双方已同意</strong>{friends.length === 0 ? <small>还没有建立真人连接</small> : friends.map(item => <article key={item.id}><span>{item.nickname}</span><button onClick={() => onLeaveConnection(item.id)}>退出连接</button></article>)}</div>
    </div>
  </section>;
}
