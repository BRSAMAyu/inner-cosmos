import { useEffect, useMemo, useState } from "react";
import type { LiveChatInvites, LiveChatMessage, LiveChatSession, SocialConnection } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton, LoadingText } from "../loading";

type Props = {
  friends: SocialConnection[];
  invites: LiveChatInvites;
  sessions: LiveChatSession[];
  selectedSessionId: number | null;
  messages: LiveChatMessage[];
  status: "idle" | "loading" | "success" | "error";
  currentUserId: number | null;
  isInviteBusy: (userId: number) => boolean;
  isDecisionBusy: (inviteId: number) => boolean;
  isMessageBusy: (sessionId: number) => boolean;
  isEndBusy: (sessionId: number) => boolean;
  onWriteLetter: (userId: number) => void;
  onInvite: (userId: number, duration: 10 | 15) => void;
  onRespond: (inviteId: number, decision: "accept" | "decline") => void;
  onSelectSession: (sessionId: number) => void;
  onSendMessage: (sessionId: number, body: string) => Promise<boolean>;
  onEndSession: (sessionId: number) => void;
  locale?: Locale;
};

const COPY = {
  "zh-CN": {
    eyebrow: "此刻聊聊", heading: "慢信之外，也可以郑重地在此刻见面",
    intro: "它不是永久在线的聊天框。先发出邀请，双方都愿意后开启 10 或 15 分钟的实时会话；时间结束后，可以安静离开，或把想说的话写成慢信。",
    duration: "会面时长", ten: "10 分钟", fifteen: "15 分钟", write: "写慢信", invite: "邀请此刻聊聊", inviting: "正在邀请",
    incoming: "有人想在此刻见你", accept: "现在见面", accepting: "正在开启", decline: "稍后再说", declining: "正在回应",
    outgoing: "等待对方回应", active: "正在发生的会面", noActive: "还没有正在进行的会面。",
    remaining: (value: string) => `还可以聊 ${value}`, ended: "时间已到", messages: "此刻的对话",
    empty: "你们都已来到这里。可以从一句简单的“我在”开始。", loading: "正在同步此刻的对话…",
    error: "暂时无法同步，请稍后再试。", placeholder: "写下此刻想说的话…", send: "发送", sending: "正在发送",
    end: "结束这次相聚", ending: "正在结束", live: "双方都在时才开启 · 自动短时同步"
  },
  "en-SG": {
    eyebrow: "HERE, NOW", heading: "Beyond slow letters, meet deliberately in the present",
    intro: "This is not an always-on chat box. Invite each other into a 10- or 15-minute live conversation; when it ends, leave quietly or continue with a slow letter.",
    duration: "Meeting length", ten: "10 minutes", fifteen: "15 minutes", write: "Write slowly", invite: "Invite to talk now", inviting: "Inviting",
    incoming: "Someone would like to meet now", accept: "Meet now", accepting: "Opening", decline: "Not now", declining: "Responding",
    outgoing: "Waiting for their response", active: "Meetings happening now", noActive: "No live meeting right now.",
    remaining: (value: string) => `${value} remaining`, ended: "Time is up", messages: "Conversation now",
    empty: "You're both here. Begin with a simple “I'm here.”", loading: "Syncing this conversation…",
    error: "Couldn't sync just now. Try again shortly.", placeholder: "Write what you want to say now…", send: "Send", sending: "Sending",
    end: "End this meeting", ending: "Ending", live: "Opens only with mutual consent · short live sync"
  }
} as const;

function remainingLabel(endsAt: string, now: number) {
  const seconds = Math.max(0, Math.ceil((new Date(endsAt).getTime() - now) / 1000));
  const minutes = Math.floor(seconds / 60);
  return `${String(minutes).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
}

export function LiveChatPanel({ friends, invites, sessions, selectedSessionId, messages, status,
  currentUserId, isInviteBusy, isDecisionBusy, isMessageBusy, isEndBusy,
  onWriteLetter, onInvite, onRespond, onSelectSession, onSendMessage, onEndSession,
  locale = "zh-CN" }: Props) {
  const t = COPY[locale];
  const [duration, setDuration] = useState<10 | 15>(10);
  const [messageBody, setMessageBody] = useState("");
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1_000);
    return () => window.clearInterval(timer);
  }, []);
  const selected = sessions.find(session => session.id === selectedSessionId) ?? sessions[0] ?? null;
  const pendingIncoming = invites.incoming.filter(invite => invite.status === "PENDING");
  const pendingOutgoing = invites.outgoing.filter(invite => invite.status === "PENDING");
  const peerName = useMemo(() => {
    if (!selected) return "";
    return selected.participantOneId === currentUserId
      ? selected.participantTwoNickname : selected.participantOneNickname;
  }, [currentUserId, selected]);

  return <section className="live-chat-panel" aria-label={t.eyebrow}>
    <header>
      <div><span className="eyebrow">{t.eyebrow}</span><h3>{t.heading}</h3></div>
      <span className="live-chat-signal"><i />{t.live}</span>
    </header>
    <p>{t.intro}</p>

    {friends.length > 0 && <div className="live-chat-connections">
      <label>{t.duration}<select value={duration} onChange={event => setDuration(Number(event.target.value) as 10 | 15)}>
        <option value={10}>{t.ten}</option><option value={15}>{t.fifteen}</option>
      </select></label>
      {friends.map(friend => <article key={friend.id}>
        <strong>{friend.nickname}</strong>
        <div>
          <button type="button" className="quiet" onClick={() => onWriteLetter(friend.userId)}>{t.write}</button>
          <AsyncButton busy={isInviteBusy(friend.userId)} busyText={t.inviting}
            onClick={() => onInvite(friend.userId, duration)}>{t.invite}</AsyncButton>
        </div>
      </article>)}
    </div>}

    {pendingIncoming.length > 0 && <div className="live-chat-invites">
      <strong>{t.incoming}</strong>
      {pendingIncoming.map(invite => <article key={invite.id}>
        <span><b>{invite.inviterNickname}</b> · {invite.durationMinutes} min</span>
        <div>
          <AsyncButton busy={isDecisionBusy(invite.id)} busyText={t.accepting}
            onClick={() => onRespond(invite.id, "accept")}>{t.accept}</AsyncButton>
          <AsyncButton className="quiet" busy={isDecisionBusy(invite.id)} busyText={t.declining}
            onClick={() => onRespond(invite.id, "decline")}>{t.decline}</AsyncButton>
        </div>
      </article>)}
    </div>}
    {pendingOutgoing.length > 0 && <div className="live-chat-outgoing">
      <strong>{t.outgoing}</strong>
      {pendingOutgoing.map(invite => <span key={invite.id}>{invite.inviteeNickname} · {invite.durationMinutes} min</span>)}
    </div>}

    <div className="live-chat-room">
      <div className="live-chat-session-tabs">
        <strong>{t.active}</strong>
        {sessions.length === 0 ? <small>{t.noActive}</small> : sessions.map(session => {
          const name = session.participantOneId === currentUserId ? session.participantTwoNickname : session.participantOneNickname;
          return <button type="button" key={session.id} aria-pressed={session.id === selected?.id}
            onClick={() => onSelectSession(session.id)}>{name}</button>;
        })}
      </div>
      {selected && <div className="live-chat-stage">
        <header><div><span className="live-presence-dot" /><strong>{peerName}</strong></div>
          <time>{new Date(selected.endsAt).getTime() <= now ? t.ended : t.remaining(remainingLabel(selected.endsAt, now))}</time></header>
        <h4>{t.messages}</h4>
        <div className="live-chat-messages" aria-live="polite">
          {status === "loading" ? <LoadingText busy>{t.loading}</LoadingText>
            : status === "error" ? <p role="alert">{t.error}</p>
            : messages.length === 0 ? <p>{t.empty}</p>
            : messages.map(message => <article key={message.id} className={message.senderUserId === currentUserId ? "is-mine" : ""}>
              <small>{message.senderNickname}</small><p className="ugc-text">{message.messageBody}</p>
            </article>)}
        </div>
        <div className="live-chat-compose">
          <textarea aria-label={t.placeholder} placeholder={t.placeholder} maxLength={2000}
            value={messageBody} onChange={event => setMessageBody(event.target.value)} />
          <AsyncButton busy={isMessageBusy(selected.id)} busyText={t.sending} disabled={!messageBody.trim()}
            onClick={() => void onSendMessage(selected.id, messageBody).then(sent => { if (sent) setMessageBody(""); })}>
            {t.send}
          </AsyncButton>
        </div>
        <AsyncButton className="quiet live-chat-end" busy={isEndBusy(selected.id)} busyText={t.ending}
          onClick={() => onEndSession(selected.id)}>{t.end}</AsyncButton>
      </div>}
    </div>
  </section>;
}
