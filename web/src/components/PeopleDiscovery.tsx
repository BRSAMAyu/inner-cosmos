import type { DiscoverablePerson } from "../api";

// Non-actionable relation states get a plain label; only NONE offers a request button. PENDING_IN
// is intentionally read-only here — the actual accept/decline lives in the connection-consent
// panel below, so this just points the user there.
const statusLabel: Record<string, string> = {
  PENDING_OUT: "已发出邀请", ACCEPTED: "已连接", PENDING_IN: "ta 想认识你 · 到下面回应",
  DECLINED: "暂未连接", BLOCKED: "已屏蔽"
};

export function PeopleDiscovery({ people, busy, onRequest }: {
  people: DiscoverablePerson[]; busy: boolean; onRequest: (userId: number) => void;
}) {
  return <section className="people-discovery" aria-label="发现可以慢慢认识的人">
    <div className="resonance-heading"><div><span className="eyebrow">PEOPLE, SLOWLY</span><h2>主动认识人，但不催促任何关系</h2></div>
      <span>{people.length} 个可认识的人</span></div>
    <p className="resonance-intro">这里是愿意公开被认识的人。发出邀请只是表达意愿，对方同意前不会开放任何私密内容，也不会变成即时聊天。</p>
    {people.length === 0 ? <div className="network-empty">此刻还没有可以认识的人。</div> : <div className="people-list" role="list">
      {people.map(person => <article className="person-card" role="listitem" key={person.id}>
        <div><strong>{person.nickname}</strong><small>@{person.username}</small></div>
        {person.relationStatus === "NONE"
          ? <button type="button" className="resonance-secondary" disabled={busy} onClick={() => onRequest(person.id)}>想认识 ta</button>
          : <span className="person-status">{statusLabel[person.relationStatus] ?? person.relationStatus}</span>}
      </article>)}
    </div>}
  </section>;
}
