import { useEffect, useState } from "react";
import type { GroupInvite, GroupMember, GroupMessage, SocialConnection, SocialGroup } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton, LoadingText } from "../loading";

const COPY: Record<Locale, {
  aria: string; heading: string; count: (n: number) => string; intro: string; empty: string;
  namePlaceholder: string; createBusy: string; create: string;
  invitesHeading: string; acceptBusy: string; accept: string; declineBusy: string; decline: string;
  membersHeading: string; roleOwner: string; roleMember: string;
  inviteAria: string; invitePlaceholder: string; inviteBusy: string; invite: string;
  leaveBusy: string; leave: string; noMembers: string; noFriends: string;
  membersLoading: string; membersError: string;
  conversationHeading: string; conversationEmpty: string; conversationLoading: string; conversationError: string;
  messagePlaceholder: string; messageBusy: string; sendMessage: string;
  hearthHeading: string; hearthIntro: string; hearthStart: string; hearthClose: string;
  hearthDuration: string; hearthLocal: string; hearthRemaining: (value: string) => string;
}> = {
  "zh-CN": {
    aria: "慢群组", heading: "一小群人，也可以认真地聊在一起", count: n => `${n} 个群组`,
    intro: "只有明确接受邀请的成员才能进入小组。群聊保留每个人的原话与时间，不制造刷屏压力，也不会向组外泄露。",
    empty: "还没有加入任何群组。",
    namePlaceholder: "群组名字", createBusy: "正在创建", create: "创建群组",
    invitesHeading: "等待你回应的群组邀请", acceptBusy: "正在加入", accept: "接受", declineBusy: "正在婉拒", decline: "婉拒",
    membersHeading: "成员", roleOwner: "群主", roleMember: "成员",
    inviteAria: "邀请朋友加入", invitePlaceholder: "选择一位朋友", inviteBusy: "正在邀请", invite: "邀请",
    leaveBusy: "正在退出", leave: "退出群组", noMembers: "还没有成员。", noFriends: "还没有可邀请的朋友。",
    membersLoading: "正在读取成员…", membersError: "暂时读不到成员列表，请稍后再试。",
    conversationHeading: "群聊", conversationEmpty: "还没有消息。你可以先认真地说一句。",
    conversationLoading: "正在读取群聊…", conversationError: "暂时读不到群聊，请稍后再试。",
    messagePlaceholder: "写给小组成员…", messageBusy: "正在发送", sendMessage: "发送",
    hearthHeading: "围炉", hearthIntro: "把群聊暂时变成一段共同在场的专注时间。消息仍写真正的群聊；这一轮计时只保存在你的设备上，不会伪装其他成员在线。",
    hearthStart: "开启围炉", hearthClose: "结束围炉", hearthDuration: "围炉时长", hearthLocal: "本地仪式 · 不代表成员在线",
    hearthRemaining: value => `炉火还会亮 ${value}`
  },
  "en-SG": {
    aria: "Slow groups", heading: "A small circle can still talk meaningfully together", count: n => `${n} group${n === 1 ? "" : "s"}`,
    intro: "Only people who explicitly accept an invite enter the group. The conversation keeps each person's words and timing without creating feed pressure or exposing them outside the group.",
    empty: "You haven't joined any groups yet.",
    namePlaceholder: "Group name", createBusy: "Creating", create: "Create group",
    invitesHeading: "Group invites awaiting your response", acceptBusy: "Joining", accept: "Accept", declineBusy: "Declining", decline: "Decline",
    membersHeading: "Members", roleOwner: "Owner", roleMember: "Member",
    inviteAria: "Invite a friend to join", invitePlaceholder: "Choose a friend", inviteBusy: "Inviting", invite: "Invite",
    leaveBusy: "Leaving", leave: "Leave group", noMembers: "No members yet.", noFriends: "No friends to invite yet.",
    membersLoading: "Loading members…", membersError: "Couldn't load the member list right now -- try again shortly.",
    conversationHeading: "Group conversation", conversationEmpty: "No messages yet. You can begin with one considered note.",
    conversationLoading: "Loading the conversation…", conversationError: "Couldn't load the group conversation right now.",
    messagePlaceholder: "Write to the group…", messageBusy: "Sending", sendMessage: "Send",
    hearthHeading: "Hearth", hearthIntro: "Turn the group into a shared period of focused presence. Messages remain real group messages; this timer stays on your device and never pretends others are online.",
    hearthStart: "Light the hearth", hearthClose: "Close the hearth", hearthDuration: "Hearth length", hearthLocal: "Local ritual · not an online claim",
    hearthRemaining: value => `The hearth stays lit for ${value}`
  }
};

export function SocialGroupsView({ groups, invites, friends, selectedGroupId, members, membersStatus = "idle",
  messages = [], messagesStatus = "idle",
  createBusy, isInviteBusy, isInviteDecisionBusy, isLeaveBusy, currentUserId,
  isMessageBusy, onSelectGroup, onCreateGroup, onJoinClassroomGroup, onInvite, onRespondInvite, onLeaveGroup,
  onSendMessage, locale = "zh-CN" }: {
  groups: SocialGroup[]; invites: GroupInvite[]; friends: SocialConnection[];
  selectedGroupId: number | null; members: GroupMember[]; membersStatus?: "idle" | "loading" | "success" | "error";
  messages?: GroupMessage[]; messagesStatus?: "idle" | "loading" | "success" | "error";
  // Gemini audit 4.8 (CONFIRMED/P1): each busy check is keyed by the SPECIFIC resource the action
  // targets (memberId for an invite decision, groupId for invite-to-group/leave-group) -- a single
  // shared `busy` boolean used to disable every action for every group/invite/friend at once the
  // moment ANY one of them was in flight.
  createBusy: boolean; isInviteBusy: (groupId: number) => boolean;
  isInviteDecisionBusy: (memberId: number) => boolean; isLeaveBusy: (groupId: number) => boolean;
  isMessageBusy?: (groupId: number) => boolean;
  currentUserId: number | null;
  onSelectGroup: (id: number) => void; onCreateGroup: (name: string) => void;
  onJoinClassroomGroup?: () => Promise<boolean>;
  onInvite: (groupId: number, userId: number) => void;
  onRespondInvite: (memberId: number, decision: "accept" | "decline") => void;
  onLeaveGroup: (groupId: number) => void;
  onSendMessage?: (groupId: number, messageBody: string) => Promise<boolean>;
  locale?: Locale;
}) {
  const t = COPY[locale];
  const [name, setName] = useState("");
  const [inviteUserId, setInviteUserId] = useState("");
  const [messageBody, setMessageBody] = useState("");
  const [hearthMinutes, setHearthMinutes] = useState<10 | 15>(10);
  const [hearthEndsAt, setHearthEndsAt] = useState<number | null>(null);
  const [classroomBusy, setClassroomBusy] = useState(false);
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1_000);
    return () => window.clearInterval(timer);
  }, []);
  useEffect(() => setHearthEndsAt(null), [selectedGroupId]);
  const selectedGroup = groups.find(g => g.id === selectedGroupId) ?? null;
  // Regression (Gemini audit / remaining-work-handoff.md 2.2.4): the backend correctly rejects
  // OWNER leave-group (must transfer ownership or disband first), but this button rendered
  // unconditionally for any selected group -- an owner would always see a "Leave" action that is
  // guaranteed to fail with a 400.
  const isOwner = Boolean(selectedGroup && currentUserId !== null && selectedGroup.ownerUserId === currentUserId);

  return <section className="social-groups" aria-label={t.aria}>
    <div className="resonance-heading"><div><span className="eyebrow">{locale === "en-SG" ? "SLOW GROUPS" : "慢小组"}</span><h2>{t.heading}</h2></div>
      <span>{t.count(groups.length)}</span></div>
    <p className="resonance-intro">{t.intro}</p>
    {onJoinClassroomGroup && <section className="classroom-group-entry">
      <div><strong>{locale === "en-SG" ? "One room for everyone here" : "全场共同的小组"}</strong>
        <p>{locale === "en-SG"
          ? "Join with your registered username. Everyone in the room can read and reply; Aurora's private memory stays separate."
          : "用注册用户名自愿加入，全场都能看到并回复；这里的消息不会进入 Aurora 的私密记忆。"}</p></div>
      <button type="button" disabled={classroomBusy} onClick={() => {
        setClassroomBusy(true);
        void onJoinClassroomGroup().finally(() => setClassroomBusy(false));
      }}>{classroomBusy
        ? (locale === "en-SG" ? "Joining…" : "正在加入…")
        : (locale === "en-SG" ? "Join the live room" : "加入现场共同星球")}</button>
    </section>}

    {invites.length > 0 && <div className="group-invites">
      <strong>{t.invitesHeading}</strong>
      {invites.map(invite => <article key={invite.memberId}>
        <span>{invite.groupName}</span>
        <div>
          <AsyncButton busy={isInviteDecisionBusy(invite.memberId)} busyText={t.acceptBusy} onClick={() => onRespondInvite(invite.memberId, "accept")}>{t.accept}</AsyncButton>
          <AsyncButton busy={isInviteDecisionBusy(invite.memberId)} busyText={t.declineBusy} onClick={() => onRespondInvite(invite.memberId, "decline")}>{t.decline}</AsyncButton>
        </div>
      </article>)}
    </div>}

    <div className="group-create">
      <input value={name} onChange={event => setName(event.target.value)} placeholder={t.namePlaceholder} />
      <AsyncButton busy={createBusy} busyText={t.createBusy} disabled={!name.trim()} onClick={() => { onCreateGroup(name); setName(""); }}>{t.create}</AsyncButton>
    </div>

    {groups.length === 0 ? <div className="network-empty">{t.empty}</div> : <div className="group-layout">
      <ul className="group-list" role="list">
        {groups.map(group => <li key={group.id}>
          <button type="button" className={"group-item" + (selectedGroupId === group.id ? " is-selected" : "")}
            aria-pressed={selectedGroupId === group.id} onClick={() => onSelectGroup(group.id)}>{group.groupName}</button>
        </li>)}
      </ul>
      {selectedGroup && <div className="group-detail">
        <h3>{t.membersHeading}</h3>
        {/* Gemini audit 4.9 sibling fix: explicit status distinguishes loading from a genuinely
            empty member list -- `members.length === 0` alone used to show "no members" while the
            fetch was still in flight. */}
        {membersStatus === "loading" ? <LoadingText busy className="network-empty">{t.membersLoading}</LoadingText>
          : membersStatus === "error" ? <div className="network-empty" role="alert">{t.membersError}</div>
          : members.length === 0 ? <div className="network-empty">{t.noMembers}</div> : <ul role="list">
          {members.map(member => <li key={member.userId}>
            <span>{member.nickname}</span><small>{member.memberRole === "OWNER" ? t.roleOwner : t.roleMember}</small>
          </li>)}
        </ul>}
        {friends.length > 0 && <div className="group-invite-form">
          <label>{t.inviteAria}<select value={inviteUserId} onChange={event => setInviteUserId(event.target.value)}>
            <option value="">{t.invitePlaceholder}</option>
            {friends.map(friend => <option key={friend.userId} value={friend.userId}>{friend.nickname}</option>)}
          </select></label>
          <AsyncButton busy={isInviteBusy(selectedGroup.id)} busyText={t.inviteBusy} disabled={!inviteUserId}
            onClick={() => { onInvite(selectedGroup.id, Number(inviteUserId)); setInviteUserId(""); }}>{t.invite}</AsyncButton>
        </div>}
        {friends.length === 0 && <p className="muted">{t.noFriends}</p>}
        <section className={"group-hearth" + (hearthEndsAt && hearthEndsAt > now ? " is-lit" : "")}
          aria-label={t.hearthHeading}>
          <div className="group-hearth-flame" aria-hidden="true"><i /><i /><i /></div>
          <div><h3>{t.hearthHeading}</h3><p>{t.hearthIntro}</p><small>{t.hearthLocal}</small></div>
          {hearthEndsAt && hearthEndsAt > now
            ? <div className="group-hearth-active"><strong>{t.hearthRemaining(`${String(Math.floor((hearthEndsAt - now) / 60_000)).padStart(2, "0")}:${String(Math.floor((hearthEndsAt - now) / 1000) % 60).padStart(2, "0")}`)}</strong>
              <button type="button" className="quiet" onClick={() => setHearthEndsAt(null)}>{t.hearthClose}</button></div>
            : <div className="group-hearth-controls"><label>{t.hearthDuration}<select value={hearthMinutes}
                onChange={event => setHearthMinutes(Number(event.target.value) as 10 | 15)}>
                <option value={10}>10 min</option><option value={15}>15 min</option>
              </select></label>
              <button type="button" onClick={() => setHearthEndsAt(Date.now() + hearthMinutes * 60_000)}>{t.hearthStart}</button></div>}
        </section>
        <div className="group-conversation" aria-label={t.conversationHeading}>
          <h3>{t.conversationHeading}</h3>
          <div className="group-message-list" aria-live="polite">
            {messagesStatus === "loading"
              ? <LoadingText busy className="network-empty">{t.conversationLoading}</LoadingText>
              : messagesStatus === "error"
                ? <div className="network-empty" role="alert">{t.conversationError}</div>
                : messages.length === 0
                  ? <div className="network-empty">{t.conversationEmpty}</div>
                  : messages.map(message => <article
                      key={message.id}
                      className={message.senderUserId === currentUserId ? "is-mine" : ""}>
                      <header>
                        <strong>{message.senderNickname}</strong>
                        {message.createdAt && <time>{new Date(message.createdAt).toLocaleString(locale, { hour12: false })}</time>}
                      </header>
                      <p className="ugc-text">{message.messageBody}</p>
                    </article>)}
          </div>
          {onSendMessage && <div className="group-message-compose">
            <textarea value={messageBody} maxLength={2000}
              aria-label={t.messagePlaceholder} placeholder={t.messagePlaceholder}
              onChange={event => setMessageBody(event.target.value)} />
            <AsyncButton busy={isMessageBusy?.(selectedGroup.id) ?? false} busyText={t.messageBusy}
              disabled={!messageBody.trim()} onClick={() => {
                void onSendMessage(selectedGroup.id, messageBody).then(sent => {
                  if (sent) setMessageBody("");
                });
              }}>{t.sendMessage}</AsyncButton>
          </div>}
        </div>
        {!isOwner && <AsyncButton className="danger-quiet" busy={isLeaveBusy(selectedGroup.id)} busyText={t.leaveBusy} onClick={() => onLeaveGroup(selectedGroup.id)}>{t.leave}</AsyncButton>}
      </div>}
    </div>}
  </section>;
}
