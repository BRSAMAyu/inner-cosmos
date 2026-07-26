import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { LiveChatPanel } from "./LiveChatPanel";

const friend = { id: 4, status: "ACCEPTED", userId: 30, nickname: "阿哲", username: "azhe", source: "SOCIAL_PAGE" };
const baseProps = {
  friends: [friend], invites: { incoming: [], outgoing: [] }, sessions: [], selectedSessionId: null,
  messages: [], status: "success" as const, currentUserId: 1,
  isInviteBusy: () => false, isDecisionBusy: () => false, isMessageBusy: () => false, isEndBusy: () => false,
  onWriteLetter: vi.fn(), onInvite: vi.fn(), onRespond: vi.fn(), onSelectSession: vi.fn(),
  onSendMessage: vi.fn().mockResolvedValue(true), onEndSession: vi.fn()
};

describe("LiveChatPanel", () => {
  it("keeps slow letter and consent-based talk-now choices side by side", () => {
    const onWriteLetter = vi.fn();
    const onInvite = vi.fn();
    render(<LiveChatPanel {...baseProps} onWriteLetter={onWriteLetter} onInvite={onInvite} />);
    fireEvent.click(screen.getByRole("button", { name: "写慢信" }));
    fireEvent.click(screen.getByRole("button", { name: "邀请此刻聊聊" }));
    expect(onWriteLetter).toHaveBeenCalledWith(30);
    expect(onInvite).toHaveBeenCalledWith(30, 10);
    expect(screen.getByText(/双方都愿意后开启/)).toBeVisible();
  });

  it("shows an accepted time-boxed room and sends a real message", async () => {
    const onSendMessage = vi.fn().mockResolvedValue(true);
    render(<LiveChatPanel {...baseProps} sessions={[{
      id: 8, inviteId: 2, participantOneId: 1, participantOneNickname: "我",
      participantTwoId: 30, participantTwoNickname: "阿哲", durationMinutes: 10,
      status: "ACTIVE", startedAt: new Date().toISOString(),
      endsAt: new Date(Date.now() + 600_000).toISOString(), endedAt: null, endedByUserId: null
    }]} selectedSessionId={8} onSendMessage={onSendMessage} />);
    fireEvent.change(screen.getByLabelText("写下此刻想说的话…"), { target: { value: "我在，慢慢说。" } });
    fireEvent.click(screen.getByRole("button", { name: "发送" }));
    await waitFor(() => expect(onSendMessage).toHaveBeenCalledWith(8, "我在，慢慢说。"));
  });

  it("does not present historical decided invites as actionable", () => {
    render(<LiveChatPanel {...baseProps} friends={[]} invites={{ incoming: [{
      id: 3, inviterUserId: 30, inviterNickname: "阿哲", inviteeUserId: 1, inviteeNickname: "我",
      durationMinutes: 10, status: "ACCEPTED", expiresAt: "2026-07-26T12:00:00Z",
      respondedAt: "2026-07-26T11:55:00Z", createdAt: "2026-07-26T11:50:00Z"
    }], outgoing: [] }} />);
    expect(screen.queryByRole("button", { name: "现在见面" })).not.toBeInTheDocument();
  });
});
