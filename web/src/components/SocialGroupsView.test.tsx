import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SocialGroupsView } from "./SocialGroupsView";
import type { GroupInvite, GroupMember, SocialConnection, SocialGroup } from "../api";

afterEach(cleanup);

const group = (over: Partial<SocialGroup> = {}): SocialGroup => ({
  id: 1, ownerUserId: 1, groupName: "老朋友们", intro: "", visibility: "PRIVATE", ...over
});
const friend = (over: Partial<SocialConnection> = {}): SocialConnection => ({
  id: 1, status: "ACCEPTED", userId: 30, nickname: "阿哲", username: "azhe", source: "SOCIAL_PAGE", ...over
});

describe("SocialGroupsView", () => {
  it("shows an empty state and lets the owner create a new group", () => {
    const onCreateGroup = vi.fn();
    render(<SocialGroupsView groups={[]} invites={[]} friends={[]} selectedGroupId={null} members={[]} busy={false} currentUserId={1}
      onSelectGroup={() => undefined} onCreateGroup={onCreateGroup} onInvite={() => undefined}
      onRespondInvite={() => undefined} onLeaveGroup={() => undefined} />);
    expect(screen.getByText(/还没有加入任何群组/)).toBeVisible();
    fireEvent.change(screen.getByPlaceholderText("群组名字"), { target: { value: "新的小组" } });
    fireEvent.click(screen.getByRole("button", { name: "创建群组" }));
    expect(onCreateGroup).toHaveBeenCalledExactlyOnceWith("新的小组");
  });

  it("lists groups, selects one, invites a friend and shows members", () => {
    const onSelectGroup = vi.fn();
    const onInvite = vi.fn();
    render(<SocialGroupsView groups={[group()]} invites={[]} friends={[friend()]} selectedGroupId={1}
      members={[{ userId: 1, memberRole: "OWNER", nickname: "我" }]} busy={false} currentUserId={1}
      onSelectGroup={onSelectGroup} onCreateGroup={() => undefined} onInvite={onInvite}
      onRespondInvite={() => undefined} onLeaveGroup={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: /老朋友们/ }));
    expect(onSelectGroup).toHaveBeenCalledExactlyOnceWith(1);
    expect(screen.getByText("我")).toBeVisible();
    fireEvent.change(screen.getByLabelText("邀请朋友加入"), { target: { value: "30" } });
    fireEvent.click(screen.getByRole("button", { name: "邀请" }));
    expect(onInvite).toHaveBeenCalledExactlyOnceWith(1, 30);
  });

  it("shows pending invites and lets the invitee accept or decline", () => {
    const onRespondInvite = vi.fn();
    const invite: GroupInvite = { memberId: 9, groupId: 2, groupName: "读书会" };
    render(<SocialGroupsView groups={[]} invites={[invite]} friends={[]} selectedGroupId={null} members={[]} busy={false} currentUserId={1}
      onSelectGroup={() => undefined} onCreateGroup={() => undefined} onInvite={() => undefined}
      onRespondInvite={onRespondInvite} onLeaveGroup={() => undefined} />);
    expect(screen.getByText(/读书会/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "接受" }));
    expect(onRespondInvite).toHaveBeenCalledExactlyOnceWith(9, "accept");
    fireEvent.click(screen.getByRole("button", { name: "婉拒" }));
    expect(onRespondInvite).toHaveBeenLastCalledWith(9, "decline");
  });

  it("lets a member leave the selected group", () => {
    const onLeaveGroup = vi.fn();
    // group() defaults ownerUserId: 1 -- currentUserId 2 represents an ordinary member, not the owner.
    render(<SocialGroupsView groups={[group()]} invites={[]} friends={[]} selectedGroupId={1}
      members={[{ userId: 2, memberRole: "MEMBER", nickname: "我" } satisfies GroupMember]} busy={false} currentUserId={2}
      onSelectGroup={() => undefined} onCreateGroup={() => undefined} onInvite={() => undefined}
      onRespondInvite={() => undefined} onLeaveGroup={onLeaveGroup} />);
    fireEvent.click(screen.getByRole("button", { name: "退出群组" }));
    expect(onLeaveGroup).toHaveBeenCalledExactlyOnceWith(1);
  });

  it("hides the leave button for the group owner, since the server always rejects it (regression: 2.2.4)", () => {
    render(<SocialGroupsView groups={[group({ ownerUserId: 1 })]} invites={[]} friends={[]} selectedGroupId={1}
      members={[{ userId: 1, memberRole: "OWNER", nickname: "我" } satisfies GroupMember]} busy={false} currentUserId={1}
      onSelectGroup={() => undefined} onCreateGroup={() => undefined} onInvite={() => undefined}
      onRespondInvite={() => undefined} onLeaveGroup={() => undefined} />);
    expect(screen.queryByRole("button", { name: "退出群组" })).not.toBeInTheDocument();
  });

  it("renders in English when locale is en-SG", () => {
    render(<SocialGroupsView locale="en-SG" groups={[group()]} invites={[]} friends={[]} selectedGroupId={null} members={[]} busy={false} currentUserId={1}
      onSelectGroup={() => undefined} onCreateGroup={() => undefined} onInvite={() => undefined}
      onRespondInvite={() => undefined} onLeaveGroup={() => undefined} />);
    expect(screen.getByRole("heading", { name: "Slow groups, not group chat noise" })).toBeVisible();
    expect(screen.getByPlaceholderText("Group name")).toBeVisible();
  });
});
