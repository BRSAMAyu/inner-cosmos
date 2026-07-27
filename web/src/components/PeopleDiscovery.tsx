import { useState, type FormEvent } from "react";
import type { DiscoverablePerson } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

const COPY: Record<Locale, {
  aria: string; heading: string; count: (n: number) => string; intro: string; empty: string;
  inviteBusy: string; invite: string; searchLabel: string; searchPlaceholder: string;
  search: string; searching: string; clear: string; status: Record<string, string>;
}> = {
  "zh-CN": {
    aria: "发现可以慢慢认识的人", heading: "主动认识人，但不催促任何关系", count: n => `${n} 个可认识的人`,
    intro: "这里是愿意公开被认识的人。发出邀请只是表达意愿，对方同意前不会开放任何私密内容，也不会变成即时聊天。",
    empty: "此刻还没有可以认识的人。", inviteBusy: "正在邀请", invite: "想认识 ta",
    searchLabel: "按用户名精确寻找", searchPlaceholder: "输入用户名或昵称",
    search: "寻找", searching: "寻找中…", clear: "返回最近的人",
    status: { PENDING_OUT: "已发出邀请", ACCEPTED: "已连接", PENDING_IN: "ta 想认识你 · 到下面回应", DECLINED: "暂未连接", BLOCKED: "已屏蔽" }
  },
  "en-SG": {
    aria: "Discover people to get to know slowly", heading: "Reach out to people, without rushing any relationship", count: n => `${n} ${n === 1 ? "person" : "people"} to get to know`,
    intro: "These are people open to being met. Sending an invite only signals interest — nothing private opens and it never becomes instant chat until they agree.",
    empty: "No one to get to know just now.", inviteBusy: "Inviting", invite: "I'd like to know them",
    searchLabel: "Find by username", searchPlaceholder: "Username or nickname",
    search: "Find", searching: "Finding…", clear: "Back to recent people",
    status: { PENDING_OUT: "Invite sent", ACCEPTED: "Connected", PENDING_IN: "They'd like to know you · respond below", DECLINED: "Not connected yet", BLOCKED: "Blocked" }
  }
};

export function PeopleDiscovery({ people, isBusy, onRequest, onSearch, locale = "zh-CN" }: {
  // Gemini audit 4.8 (CONFIRMED/P1): isBusy is per-resource (keyed by the target person's userId),
  // not a single shared flag -- inviting person A must not disable person B's invite button too.
  people: DiscoverablePerson[]; isBusy: (userId: number) => boolean; onRequest: (userId: number) => void;
  onSearch?: (query: string) => Promise<number>; locale?: Locale;
}) {
  const t = COPY[locale];
  const [query, setQuery] = useState("");
  const [searching, setSearching] = useState(false);
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!onSearch) return;
    setSearching(true);
    try { await onSearch(query); }
    finally { setSearching(false); }
  };
  return <section className="people-discovery" aria-label={t.aria}>
    <div className="resonance-heading"><div><span className="eyebrow">{locale === "en-SG" ? "PEOPLE, SLOWLY" : "慢慢认识人"}</span><h2>{t.heading}</h2></div>
      <span>{t.count(people.length)}</span></div>
    <p className="resonance-intro">{t.intro}</p>
    {onSearch && <form className="people-search" onSubmit={event => void submit(event)}>
      <label><span>{t.searchLabel}</span>
        <input value={query} maxLength={120} placeholder={t.searchPlaceholder}
          onChange={event => setQuery(event.target.value)} /></label>
      <button type="submit" disabled={searching || !query.trim()}>{searching ? t.searching : t.search}</button>
      {query && <button type="button" className="quiet" onClick={() => {
        setQuery("");
        void onSearch("");
      }}>{t.clear}</button>}
    </form>}
    {people.length === 0 ? <div className="network-empty">{t.empty}</div> : <div className="people-list" role="list">
      {people.map(person => <article className="person-card" role="listitem" key={person.id}>
        <div><strong>{person.nickname}</strong><small>@{person.username}</small></div>
        {person.relationStatus === "NONE"
          ? <AsyncButton className="resonance-secondary" busy={isBusy(person.id)} busyText={t.inviteBusy} onClick={() => onRequest(person.id)}>{t.invite}</AsyncButton>
          : <span className="person-status">{t.status[person.relationStatus] ?? person.relationStatus}</span>}
      </article>)}
    </div>}
  </section>;
}
