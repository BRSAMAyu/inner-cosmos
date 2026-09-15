import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LettersInbox } from "./LettersInbox";
import type { SlowLetter } from "../api";

afterEach(cleanup);

const letter: SlowLetter = {
  id: 7, senderUserId: 2, receiverUserId: 1, receiverCapsuleId: 4, title: "你写的黄昏让我停了一下",
  letterBody: "我读到你把夕阳当作恢复资源那段。", status: "READ", parallaxDistance: 1, estimatedArrivalAt: "2026-07-15T00:00:00Z"
};

describe("LettersInbox", () => {
  it("lets the recipient reply, request connection and block a letter", () => {
    const onReply = vi.fn();
    const onRequestConnection = vi.fn();
    const onActOnLetter = vi.fn();
    render(<LettersInbox letterInbox={[letter]} replyDrafts={{ 7: "谢谢你告诉我" }}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={onReply} onActOnLetter={onActOnLetter}
      onReportLetter={() => undefined} onRequestConnection={onRequestConnection}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "让回复慢信启程" }));
    expect(onReply).toHaveBeenCalledWith(letter);
    fireEvent.click(screen.getByRole("button", { name: "愿意认识对方" }));
    expect(onRequestConnection).toHaveBeenCalledWith(letter);
    fireEvent.click(screen.getByText("边界与安全"));
    fireEvent.click(screen.getByRole("button", { name: "屏蔽后续来信" }));
    expect(onActOnLetter).toHaveBeenCalledWith(letter, "block");
  });

  it("explains that composing without a connection routes to meeting people", () => {
    const onComposeNew = vi.fn();
    render(<LettersInbox letterInbox={[]} replyDrafts={{}} onComposeNew={onComposeNew}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    expect(screen.getByText("还没有可直接写信的连接；先去共鸣相遇，建立连接后再写。")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "先去遇见可以写信的人" }));
    expect(onComposeNew).toHaveBeenCalledOnce();
  });

  it("lets an accepted connection compose a slow letter here without jumping to a capsule", async () => {
    const onComposeNew = vi.fn();
    const onSendDirectLetter = vi.fn().mockResolvedValue(true);
    render(<LettersInbox letterInbox={[]} replyDrafts={{}} onComposeNew={onComposeNew}
      onSendDirectLetter={onSendDirectLetter}
      connectionRequests={{ incoming: [], outgoing: [] }}
      friends={[{ id: 3, status: "ACCEPTED", userId: 30, nickname: "阿哲", username: "azhe", source: "SOCIAL_PAGE" }]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false}
      isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);

    expect(screen.getByText("在当前页面打开写信表单，收信人只来自双方同意的连接。")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "写给已连接的好友" }));
    expect(onComposeNew).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText("选择一位好友"), { target: { value: "30" } });
    fireEvent.change(screen.getByLabelText("信的标题"), { target: { value: "近况" } });
    fireEvent.change(screen.getByLabelText("写下你真正想说的话…"), { target: { value: "最近还好吗？" } });
    fireEvent.click(screen.getByRole("button", { name: "让慢信启程" }));

    await waitFor(() => expect(onSendDirectLetter).toHaveBeenCalledExactlyOnceWith(
      30, "近况", "最近还好吗？",
      expect.objectContaining({ deliveryPreset: "DEMO_30S", timeZone: expect.any(String) })
    ));
  });

  it("does not show a compose entry when neither direct compose nor a discovery route is actionable", () => {
    render(<LettersInbox letterInbox={[]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      onSendDirectLetter={vi.fn()}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false}
      isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    expect(screen.queryByRole("button", { name: /写信|慢信|遇见/ })).not.toBeInTheDocument();
  });

  it("makes the two compose destinations explicit in English too", () => {
    const { rerender } = render(<LettersInbox locale="en-SG" letterInbox={[]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]} onComposeNew={() => undefined}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false}
      isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    expect(screen.getByRole("button", { name: "Meet someone you can write to" })).toBeVisible();
    expect(screen.getByText("No direct recipient yet. Meet through resonance and connect before writing.")).toBeVisible();

    rerender(<LettersInbox locale="en-SG" letterInbox={[]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }}
      friends={[{ id: 3, status: "ACCEPTED", userId: 30, nickname: "Mira", username: "mira", source: "SOCIAL_PAGE" }]}
      onComposeNew={() => undefined} onSendDirectLetter={vi.fn().mockResolvedValue(true)}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false}
      isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    expect(screen.getByRole("button", { name: "Write to a connection" })).toBeVisible();
    expect(screen.getByText("Opens the composer here. Recipients are limited to mutual connections.")).toBeVisible();
  });

  it("marks the reply button busy (disabled + aria-busy) for the letter being sent", () => {
    // AsyncButton disables + sets aria-busy immediately; the label only swaps after 1s (anti-flicker),
    // so assert the immediate, deterministic state rather than the delayed busy text.
    const { rerender } = render(<LettersInbox letterInbox={[letter]} replyDrafts={{ 7: "谢谢你告诉我" }} replyBusyId={7}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    const btn = screen.getByRole("button", { name: "让回复慢信启程" });
    expect(btn).toBeDisabled();
    expect(btn).toHaveAttribute("aria-busy", "true");

    // A different letter being busy does NOT disable this letter's reply button.
    rerender(<LettersInbox letterInbox={[letter]} replyDrafts={{ 7: "谢谢你告诉我" }} replyBusyId={999}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    const enabled = screen.getByRole("button", { name: "让回复慢信启程" });
    expect(enabled).toBeEnabled();
    expect(enabled).not.toHaveAttribute("aria-busy", "true");
  });

  it("shows letters the user has sent under the outbox tab", () => {
    // CP-33: the outbox tab receives the backend privacy projection, not full letters.
    const sent = { id: 12, title: "谢谢你愿意在雨里等", senderStatus: "SENT",
      statusExplanation: "SENT/FLYING=在途；DELIVERED=对方可读取；READ/REPLIED 由对方的回执选择决定。" };
    render(<LettersInbox letterInbox={[letter]} letterOutbox={[sent]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    // default tab is inbox: the received letter shows, the sent one does not
    expect(screen.getByText("你写的黄昏让我停了一下")).toBeVisible();
    expect(screen.queryByText("谢谢你愿意在雨里等")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: /寄出的/ }));
    expect(screen.getByText("谢谢你愿意在雨里等")).toBeVisible();
    expect(screen.queryByText("你写的黄昏让我停了一下")).not.toBeInTheDocument();
  });

  it("shows the real backend FLYING status translated, with its arrival ETA, not the never-sent IN_FLIGHT literal", () => {
    // The letter-state machine's real in-transit code is FLYING (see FlyingState.java), never IN_FLIGHT.
    const flying = { id: 13, title: "还在路上的信", senderStatus: "FLYING",
      scheduledArrivalAt: "2026-07-20T00:00:00Z" };
    render(<LettersInbox letterInbox={[]} letterOutbox={[flying]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("tab", { name: /寄出的/ }));
    expect(screen.getByText("飞行中")).toBeVisible();
    expect(screen.queryByText("FLYING")).not.toBeInTheDocument();
    expect(screen.getByText(/预计.*抵达/)).toBeVisible();
    expect(document.querySelector(".letter-flying-transit")).toBeTruthy();
  });

  it("does not render the flying transit visual for a letter that has already arrived", () => {
    render(<LettersInbox letterInbox={[]} letterOutbox={[{ id: 40, title: letter.title, senderStatus: "SENT" }]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("tab", { name: /寄出的/ }));
    expect(document.querySelector(".letter-flying-transit")).toBeFalsy();
  });

  it("lets the sender archive a concluded outbox letter, but not one still awaiting the recipient", () => {
    const onActOnLetter = vi.fn();
    // DECLINED arrives from the projection folded as CLOSED -- still archivable.
    const concluded = { id: 21, title: "已经有结果的信", senderStatus: "CLOSED", statusExplanation: "这封信已结束，未能继续往来。" };
    const stillFlying = { id: 22, title: "还没到的信", senderStatus: "FLYING", scheduledArrivalAt: "2026-07-20T00:00:00Z" };
    render(<LettersInbox letterInbox={[]} letterOutbox={[concluded, stillFlying]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={onActOnLetter}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("tab", { name: /寄出的/ }));
    expect(screen.getAllByRole("button", { name: "归档" })).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: "归档" }));
    expect(onActOnLetter).toHaveBeenCalledWith(concluded, "archive");
  });

  it("lists draft letters under the drafts tab and can send one", () => {
    const onSendDraft = vi.fn();
    const draft = { id: 20, title: "还没寄出的信", senderStatus: "DRAFT", letterBody: "我想慢慢改。" };
    render(<LettersInbox letterInbox={[]} letterOutbox={[draft]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]} onSendDraft={onSendDraft}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    // a DRAFT does not show under the read-only outbox (sent) tab
    fireEvent.click(screen.getByRole("tab", { name: /寄出的/ }));
    expect(screen.queryByText("还没寄出的信")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: /草稿/ }));
    expect(screen.getByText("还没寄出的信")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "让这封信启程" }));
    expect(onSendDraft).toHaveBeenCalledExactlyOnceWith(20);
  });

  it("opens a letter thread and shows its conversation", () => {
    const onOpenThread = vi.fn();
    const { rerender } = render(<LettersInbox letterInbox={[]} replyDrafts={{}} threads={[{ id: 9, firstLetterId: 1, participantA: 1, participantB: 2, capsuleId: 4, status: "ACTIVE", lastLetterAt: "2026-07-17T00:00:00Z" }]}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]} onOpenThread={onOpenThread}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("tab", { name: /往来/ }));
    fireEvent.click(screen.getByRole("button", { name: /往来 #9/ }));
    expect(onOpenThread).toHaveBeenCalledExactlyOnceWith(9);
    rerender(<LettersInbox letterInbox={[]} replyDrafts={{}} threads={[{ id: 9, firstLetterId: 1, participantA: 1, participantB: 2, capsuleId: 4, status: "ACTIVE", lastLetterAt: "2026-07-17T00:00:00Z" }]}
      selectedThreadId={9} threadLetters={[{ ...letter, id: 30, title: "线程里的信", letterBody: "往来内容", status: "DELIVERED" }]}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]} onOpenThread={onOpenThread}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    expect(screen.getByText("线程里的信")).toBeVisible();
  });

  // W2 UIUX audit follow-up: the thread-item button rendered <strong>label</strong><small>status</small>
  // with no aria-label, so its accessible name concatenated into a run-on string (e.g. "往来 #9ACTIVE").
  // Same shape as the ProductShellNavigation run-on bug this campaign already fixed. Fixed with a
  // properly separated aria-label and aria-hidden on the visual duplicate.
  it("gives the thread-item button a separated accessible name (label + status), not a run-on concatenation", () => {
    render(<LettersInbox letterInbox={[]} replyDrafts={{}} threads={[{ id: 9, firstLetterId: 1, participantA: 1, participantB: 2, capsuleId: 4, status: "ACTIVE", lastLetterAt: null }]}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("tab", { name: /往来/ }));
    expect(screen.getByRole("button", { name: "往来 #9 · ACTIVE" })).toBeInTheDocument();
  });

  it("renders tabs, inbox actions and consent panel in English when locale is en-SG", () => {
    render(<LettersInbox locale="en-SG" letterInbox={[letter]} replyDrafts={{ 7: "thanks" }}
      connectionRequests={{ incoming: [{ id: 3, status: "PENDING", userId: 5, nickname: "Mira", username: "mira", source: "SLOW_LETTER" }], outgoing: [] }}
      friends={[]} isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined} onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    expect(screen.getByRole("heading", { name: /Only after it arrives/ })).toBeVisible();
    expect(screen.getByRole("tab", { name: "Received" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Send the reply slow letter" })).toBeVisible();
    fireEvent.click(screen.getByText("Boundaries & safety"));
    expect(screen.getByRole("button", { name: "Report this letter" })).toBeVisible();
    expect(screen.getByText("Mira would like to know you after the letters")).toBeVisible();
    expect(screen.getByRole("button", { name: "I'd like to too" })).toBeVisible();
  });

  it("lets the user accept an incoming connection request", () => {
    const onDecideConnection = vi.fn();
    render(<LettersInbox letterInbox={[]} replyDrafts={{}}
      connectionRequests={{ incoming: [{ id: 3, status: "PENDING", userId: 5, nickname: "小满", username: "xm", source: "SLOW_LETTER" }], outgoing: [] }}
      friends={[]} isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined} onDecideConnection={onDecideConnection} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "我也愿意" }));
    expect(onDecideConnection).toHaveBeenCalledWith(3, "accept");
  });

  // W1 slow-letter voice reuse: tap-to-play a delivered letter's body read aloud, via the shared
  // InlineAudioPlayer (no second player). Mirrors the capsule-voice test contract in
  // ResonanceNetwork.test.tsx.
  it("shows a tap-to-play button on a delivered letter and fires onPlayLetterVoice", () => {
    const onPlayLetterVoice = vi.fn();
    render(<LettersInbox letterInbox={[letter]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      isLetterVoiceBusy={() => false} onPlayLetterVoice={onPlayLetterVoice}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined} onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "▶ 朗读这封信" }));
    expect(onPlayLetterVoice).toHaveBeenCalledWith(letter);
  });

  it("replaces the play button with the shared InlineAudioPlayer once audio is fetched", () => {
    render(<LettersInbox letterInbox={[letter]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      letterVoiceLetterId={letter.id} letterVoiceAudio="data:audio/mpeg;base64,AAAA" isLetterVoiceBusy={() => false} onPlayLetterVoice={() => undefined}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined} onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    // The InlineAudioPlayer takes over (its accessible name is the letter-voice aria-label).
    expect(screen.getByRole("button", { name: "听这封慢信被朗读出来" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "▶ 朗读这封信" })).not.toBeInTheDocument();
  });

  it("marks the letter-voice play button busy only for the letter being synthesized", () => {
    const { rerender } = render(<LettersInbox letterInbox={[letter]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      isLetterVoiceBusy={id => id === letter.id} onPlayLetterVoice={() => undefined}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined} onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    const btn = screen.getByRole("button", { name: "▶ 朗读这封信" });
    expect(btn).toBeDisabled();
    expect(btn).toHaveAttribute("aria-busy", "true");

    rerender(<LettersInbox letterInbox={[letter]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      isLetterVoiceBusy={() => false} onPlayLetterVoice={() => undefined}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined} onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    expect(screen.getByRole("button", { name: "▶ 朗读这封信" })).toBeEnabled();
  });

  it("shows the English read-aloud label under en-SG locale", () => {
    render(<LettersInbox locale="en-SG" letterInbox={[letter]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      isLetterVoiceBusy={() => false} onPlayLetterVoice={() => undefined}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined} onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    expect(screen.getByRole("button", { name: "▶ Read this letter aloud" })).toBeVisible();
  });

  // CP-33 §2-6: the recipient's per-letter read-receipt switch. Default is off (NEVER) and the
  // copy must say plainly that the sender learns nothing -- an honest "off", never a fake "unread".
  it("shows the receipt switch off by default with the no-receipt promise, and opting in fires ALWAYS", () => {
    const onSetReceiptPolicy = vi.fn();
    render(<LettersInbox letterInbox={[letter]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]} onSetReceiptPolicy={onSetReceiptPolicy}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    // absent receiptPolicy on the payload = persisted default NEVER: control renders OFF.
    expect(screen.getByText("已读回执")).toBeVisible();
    const toggle = screen.getByRole("button", { name: "不告知对方（默认）" });
    expect(toggle).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByText("对方不会收到已读回执；你读没读，寄件人都无从知道。")).toBeVisible();
    fireEvent.click(toggle);
    expect(onSetReceiptPolicy).toHaveBeenCalledExactlyOnceWith(letter, "ALWAYS");
  });

  it("shows the opted-in state with its honest consequence and switching back fires NEVER", () => {
    const onSetReceiptPolicy = vi.fn();
    const optedIn = { ...letter, receiptPolicy: "ALWAYS" } as SlowLetter;
    render(<LettersInbox letterInbox={[optedIn]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]} onSetReceiptPolicy={onSetReceiptPolicy}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    const toggle = screen.getByRole("button", { name: "愿意告知对方" });
    expect(toggle).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText("对方会看到这封信已读；这只影响这一封信。")).toBeVisible();
    fireEvent.click(toggle);
    expect(onSetReceiptPolicy).toHaveBeenCalledExactlyOnceWith(optedIn, "NEVER");
  });

  it("renders no receipt control at all when the host app has not wired the callback", () => {
    render(<LettersInbox letterInbox={[letter]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    expect(screen.queryByText("已读回执")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /不告知对方|愿意告知对方/ })).not.toBeInTheDocument();
  });

  it("marks the receipt switch busy only for the letter being saved", () => {
    const { rerender } = render(<LettersInbox letterInbox={[letter]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]} onSetReceiptPolicy={() => undefined}
      isReceiptPolicyBusy={id => id === letter.id}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    const busy = screen.getByRole("button", { name: "不告知对方（默认）" });
    expect(busy).toBeDisabled();
    expect(busy).toHaveAttribute("aria-busy", "true");

    rerender(<LettersInbox letterInbox={[letter]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]} onSetReceiptPolicy={() => undefined}
      isReceiptPolicyBusy={() => false}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    expect(screen.getByRole("button", { name: "不告知对方（默认）" })).toBeEnabled();
  });

  it("labels the outbox READ state as the recipient's choice in both locales", () => {
    const readLetter = { ...letter, id: 55, senderStatus: "READ" };
    const { rerender } = render(<LettersInbox letterInbox={[]} letterOutbox={[readLetter]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("tab", { name: /寄出的/ }));
    expect(screen.getByText("对方已读（对方选择告知）")).toBeVisible();

    rerender(<LettersInbox locale="en-SG" letterInbox={[]} letterOutbox={[readLetter]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("tab", { name: /Sent/ }));
    expect(screen.getByText("Read (they chose to share)")).toBeVisible();
  });

  it("shows the receipt switch in English under en-SG locale", () => {
    render(<LettersInbox locale="en-SG" letterInbox={[letter]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]} onSetReceiptPolicy={() => undefined}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    expect(screen.getByText("Read receipt")).toBeVisible();
    expect(screen.getByRole("button", { name: "Not telling the sender (default)" })).toBeVisible();
    expect(screen.getByText("The sender won't be told you read this letter.")).toBeVisible();
  });

  // CP-34 both-party-consent corrections on the shared letter thread.
  const thread9 = { id: 9, firstLetterId: 1, participantA: 1, participantB: 2, capsuleId: 4, status: "ACTIVE", lastLetterAt: null };
  const proposal = (overrides = {}) => ({
    id: 11, threadId: 9, proposerUserId: 2, counterpartUserId: 1,
    correctionField: "relationLabel", proposedValue: "老朋友", note: "我们更像老朋友",
    status: "PROPOSED", decisionReason: null, decidedAt: null,
    createdAt: "2026-09-15T00:00:00", updatedAt: "2026-09-15T00:00:00", ...overrides
  });
  const correctionsProps = {
    threadCorrections: { incoming: [proposal()], outgoing: [proposal({ id: 12, proposerUserId: 1, counterpartUserId: 2, proposedValue: "同路人" })] },
    threadCorrectionsStatus: "success" as const
  };

  it("renders incoming correction cards with accept/decline and the outgoing status list on the open thread", () => {
    const onAcceptCorrection = vi.fn();
    const onRejectCorrection = vi.fn();
    render(<LettersInbox letterInbox={[]} replyDrafts={{}} threads={[thread9]} selectedThreadId={9} threadLetters={[]}
      threadLettersStatus="success" {...correctionsProps}
      onAcceptCorrection={onAcceptCorrection} onRejectCorrection={onRejectCorrection}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("tab", { name: /往来/ }));
    expect(screen.getByText("等你确认的提案")).toBeVisible();
    expect(screen.getByText("关系称呼 → 老朋友")).toBeVisible();
    expect(screen.getByText("我们更像老朋友")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "接受" }));
    expect(onAcceptCorrection).toHaveBeenCalledWith(11);
    fireEvent.click(screen.getByRole("button", { name: "婉拒" }));
    expect(onRejectCorrection).toHaveBeenCalledWith(11);
    expect(screen.getByText("你提出的提案")).toBeVisible();
    expect(screen.getByText("关系称呼 → 同路人")).toBeVisible();
    expect(screen.getByText("待对方确认")).toBeVisible();
  });

  it("labels each outgoing proposal with its honest terminal status, and offers withdraw only while PROPOSED", () => {
    const onWithdrawCorrection = vi.fn();
    render(<LettersInbox letterInbox={[]} replyDrafts={{}} threads={[thread9]} selectedThreadId={9} threadLetters={[]} threadLettersStatus="success"
      threadCorrections={{ incoming: [], outgoing: [
        proposal({ id: 21, status: "APPLIED" }),
        proposal({ id: 22, status: "REJECTED", decisionReason: "我们还是叫原来的称呼吧", decidedAt: "2026-09-15T01:00:00" }),
        proposal({ id: 23, status: "WITHDRAWN" })
      ] }} threadCorrectionsStatus="success"
      onWithdrawCorrection={onWithdrawCorrection}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("tab", { name: /往来/ }));
    expect(screen.getByText("已应用")).toBeVisible();
    expect(screen.getByText("被婉拒")).toBeVisible();
    expect(screen.getByText("婉拒理由：我们还是叫原来的称呼吧")).toBeVisible();
    expect(screen.getByText("已撤回")).toBeVisible();
    expect(screen.queryByRole("button", { name: "撤回" })).not.toBeInTheDocument();
  });

  it("sends a correction proposal with the chosen field, value and optional note", () => {
    const onProposeCorrection = vi.fn();
    render(<LettersInbox letterInbox={[]} replyDrafts={{}} threads={[thread9]} selectedThreadId={9} threadLetters={[]} threadLettersStatus="success"
      threadCorrections={{ incoming: [], outgoing: [] }} threadCorrectionsStatus="success"
      onProposeCorrection={onProposeCorrection} onAcceptCorrection={() => undefined}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      isDraftBusy={() => false} isLetterActionBusy={() => false} isConnectionDecisionBusy={() => false} isConnectionLeaveBusy={() => false} isLetterConnectionBusy={() => false} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("tab", { name: /往来/ }));
    fireEvent.click(screen.getByText("对这段往来的理解提出一处修改"));
    fireEvent.change(screen.getByLabelText("你认为正确的内容"), { target: { value: "同行多年" } });
    fireEvent.change(screen.getByLabelText("补充说明（可选）"), { target: { value: "一直互相照应" } });
    fireEvent.click(screen.getByRole("button", { name: "送出提案" }));
    expect(onProposeCorrection).toHaveBeenCalledWith(9, "relationLabel", "同行多年", "一直互相照应");
  });
});
