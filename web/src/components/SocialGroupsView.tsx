import { useState } from "react";
import type { GroupInvite, GroupMember, SocialConnection, SocialGroup } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

const COPY: Record<Locale, {
  aria: string; heading: string; count: (n: number) => string; intro: string; empty: string;
  namePlaceholder: string; createBusy: string; create: string;
  invitesHeading: string; acceptBusy: string; accept: string; declineBusy: string; decline: string;
  membersHeading: string; roleOwner: string; roleMember: string;
  inviteAria: string; invitePlaceholder: string; inviteBusy: string; invite: string;
  leaveBusy: string; leave: string; noMembers: string; noFriends: string;
}> = {
  "zh-CN": {
    aria: "慢群组", heading: "慢慢认识的一小群人，不是群聊噪音", count: n => `${n} 个群组`,
    intro: "群组是你和一小群人之间的边界，不会自动变成实时群聊——邀请和加入都需要明确同意。",
    empty: "还没有加入任何群组。",
    namePlaceholder: "群组名字", createBusy: "正在创建", create: "创建群组",
    invitesHeading: "等待你回应的群组邀请", acceptBusy: "正在加入", accept: "接受", declineBusy: "正在婉拒", decline: "婉拒",
    membersHeading: "成员", roleOwner: "群主", roleMember: "成员",
    inviteAria: "邀请朋友加入", invitePlaceholder: "选择一位朋友", inviteBusy: "正在邀请", invite: "邀请",
    leaveBusy: "正在退出", leave: "退出群组", noMembers: "还没有成员。", noFriends: "还没有可邀请的朋友。"
  },
  "en-SG": {
    aria: "Slow groups", heading: "Slow groups, not group chat noise", count: n => `${n} group${n === 1 ? "" : "s"}`,
    intro: "A group is a boundary between you and a small circle — it never silently becomes a live group chat; invites and joining both need explicit consent.",
    empty: "You haven't joined any groups yet.",
    namePlaceholder: "Group name", createBusy: "Creating", create: "Create group",
    invitesHeading: "Group invites awaiting your response", acceptBusy: "Joining", accept: "Accept", declineBusy: "Declining", decline: "Decline",
    membersHeading: "Members", roleOwner: "Owner", roleMember: "Member",
    inviteAria: "Invite a friend to join", invitePlaceholder: "Choose a friend", inviteBusy: "Inviting", invite: "Invite",
    leaveBusy: "Leaving", leave: "Leave group", noMembers: "No members yet.", noFriends: "No friends to invite yet."
  }
};

export function SocialGroupsView({ groups, invites, friends, selectedGroupId, members, busy,
  onSelectGroup, onCreateGroup, onInvite, onRespondInvite, onLeaveGroup, locale = "zh-CN" }: {
  groups: SocialGroup[]; invites: GroupInvite[]; friends: SocialConnection[];
  selectedGroupId: number | null; members: GroupMember[]; busy: boolean;
  onSelectGroup: (id: number) => void; onCreateGroup: (name: string) => void;
  onInvite: (groupId: number, userId: number) => void;
  onRespondInvite: (memberId: number, decision: "accept" | "decline") => void;
  onLeaveGroup: (groupId: number) => void;
  locale?: Locale;
}) {
  const t = COPY[locale];
  const [name, setName] = useState("");
  const [inviteUserId, setInviteUserId] = useState("");
  const selectedGroup = groups.find(g => g.id === selectedGroupId) ?? null;

  return <section className="social-groups" aria-label={t.aria}>
    <div className="resonance-heading"><div><span className="eyebrow">SLOW GROUPS</span><h2>{t.heading}</h2></div>
      <span>{t.count(groups.length)}</span></div>
    <p className="resonance-intro">{t.intro}</p>

    {invites.length > 0 && <div className="group-invites">
      <strong>{t.invitesHeading}</strong>
      {invites.map(invite => <article key={invite.memberId}>
        <span>{invite.groupName}</span>
        <div>
          <AsyncButton busy={busy} busyText={t.acceptBusy} onClick={() => onRespondInvite(invite.memberId, "accept")}>{t.accept}</AsyncButton>
          <button type="button" onClick={() => onRespondInvite(invite.memberId, "decline")}>{t.decline}</button>
        </div>
      </article>)}
    </div>}

    <div className="group-create">
      <input value={name} onChange={event => setName(event.target.value)} placeholder={t.namePlaceholder} />
      <AsyncButton busy={busy} busyText={t.createBusy} disabled={!name.trim()} onClick={() => { onCreateGroup(name); setName(""); }}>{t.create}</AsyncButton>
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
        {members.length === 0 ? <div className="network-empty">{t.noMembers}</div> : <ul role="list">
          {members.map(member => <li key={member.userId}>
            <span>{member.nickname}</span><small>{member.memberRole === "OWNER" ? t.roleOwner : t.roleMember}</small>
          </li>)}
        </ul>}
        {friends.length > 0 && <div className="group-invite-form">
          <label>{t.inviteAria}<select value={inviteUserId} onChange={event => setInviteUserId(event.target.value)}>
            <option value="">{t.invitePlaceholder}</option>
            {friends.map(friend => <option key={friend.userId} value={friend.userId}>{friend.nickname}</option>)}
          </select></label>
          <AsyncButton busy={busy} busyText={t.inviteBusy} disabled={!inviteUserId}
            onClick={() => { onInvite(selectedGroup.id, Number(inviteUserId)); setInviteUserId(""); }}>{t.invite}</AsyncButton>
        </div>}
        {friends.length === 0 && <p className="muted">{t.noFriends}</p>}
        <AsyncButton className="danger-quiet" busy={busy} busyText={t.leaveBusy} onClick={() => onLeaveGroup(selectedGroup.id)}>{t.leave}</AsyncButton>
      </div>}
    </div>}
  </section>;
}
