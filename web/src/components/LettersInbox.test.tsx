import { cleanup, fireEvent, render, screen } from "@testing-library/react";
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
      onReplyDraftChange={() => undefined} onReply={onReply} onActOnLetter={onActOnLetter}
      onReportLetter={() => undefined} onRequestConnection={onRequestConnection}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "让回复慢信启程" }));
    expect(onReply).toHaveBeenCalledWith(letter);
    fireEvent.click(screen.getByRole("button", { name: "愿意认识对方" }));
    expect(onRequestConnection).toHaveBeenCalledWith(letter);
    fireEvent.click(screen.getByRole("button", { name: "屏蔽后续来信" }));
    expect(onActOnLetter).toHaveBeenCalledWith(letter, "block");
  });

  it("marks the reply button busy (disabled + aria-busy) for the letter being sent", () => {
    // AsyncButton disables + sets aria-busy immediately; the label only swaps after 1s (anti-flicker),
    // so assert the immediate, deterministic state rather than the delayed busy text.
    const { rerender } = render(<LettersInbox letterInbox={[letter]} replyDrafts={{ 7: "谢谢你告诉我" }} replyBusyId={7}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    const btn = screen.getByRole("button", { name: "让回复慢信启程" });
    expect(btn).toBeDisabled();
    expect(btn).toHaveAttribute("aria-busy", "true");

    // A different letter being busy does NOT disable this letter's reply button.
    rerender(<LettersInbox letterInbox={[letter]} replyDrafts={{ 7: "谢谢你告诉我" }} replyBusyId={999}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    const enabled = screen.getByRole("button", { name: "让回复慢信启程" });
    expect(enabled).toBeEnabled();
    expect(enabled).not.toHaveAttribute("aria-busy", "true");
  });

  it("shows letters the user has sent under the outbox tab", () => {
    const sent: SlowLetter = { id: 12, senderUserId: 1, receiverUserId: 3, receiverCapsuleId: 8,
      title: "谢谢你愿意在雨里等", letterBody: "我想让你知道那句话我记住了。", status: "IN_FLIGHT",
      parallaxDistance: 2, estimatedArrivalAt: "2026-07-18T00:00:00Z" };
    render(<LettersInbox letterInbox={[letter]} letterOutbox={[sent]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
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
    const flying: SlowLetter = { id: 13, senderUserId: 1, receiverUserId: 3, receiverCapsuleId: 8,
      title: "还在路上的信", letterBody: "还没到呢。", status: "FLYING",
      parallaxDistance: 2, estimatedArrivalAt: "2026-07-20T00:00:00Z" };
    render(<LettersInbox letterInbox={[]} letterOutbox={[flying]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("tab", { name: /寄出的/ }));
    expect(screen.getByText("飞行中")).toBeVisible();
    expect(screen.queryByText("FLYING")).not.toBeInTheDocument();
    expect(screen.getByText(/预计.*抵达/)).toBeVisible();
    expect(document.querySelector(".letter-flying-transit")).toBeTruthy();
  });

  it("does not render the flying transit visual for a letter that has already arrived", () => {
    render(<LettersInbox letterInbox={[]} letterOutbox={[{ ...letter, id: 40, status: "SENT" }]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("tab", { name: /寄出的/ }));
    expect(document.querySelector(".letter-flying-transit")).toBeFalsy();
  });

  it("lets the sender archive a concluded outbox letter, but not one still awaiting the recipient", () => {
    const onActOnLetter = vi.fn();
    const concluded: SlowLetter = { id: 21, senderUserId: 1, receiverUserId: 3, receiverCapsuleId: 8,
      title: "已经有结果的信", letterBody: "对方回应过了。", status: "DECLINED", parallaxDistance: 1, estimatedArrivalAt: "" };
    const stillFlying: SlowLetter = { id: 22, senderUserId: 1, receiverUserId: 3, receiverCapsuleId: 8,
      title: "还没到的信", letterBody: "还在路上。", status: "FLYING", parallaxDistance: 1, estimatedArrivalAt: "2026-07-20T00:00:00Z" };
    render(<LettersInbox letterInbox={[]} letterOutbox={[concluded, stillFlying]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={onActOnLetter}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("tab", { name: /寄出的/ }));
    expect(screen.getAllByRole("button", { name: "归档" })).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: "归档" }));
    expect(onActOnLetter).toHaveBeenCalledWith(concluded, "archive");
  });

  it("lists draft letters under the drafts tab and can send one", () => {
    const onSendDraft = vi.fn();
    const draft: SlowLetter = { id: 20, senderUserId: 1, receiverUserId: 3, receiverCapsuleId: 8,
      title: "还没寄出的信", letterBody: "我想慢慢改。", status: "DRAFT", parallaxDistance: 1, estimatedArrivalAt: "" };
    render(<LettersInbox letterInbox={[]} letterOutbox={[draft]} replyDrafts={{}}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]} onSendDraft={onSendDraft}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
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
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("tab", { name: /往来/ }));
    fireEvent.click(screen.getByRole("button", { name: /往来 #9/ }));
    expect(onOpenThread).toHaveBeenCalledExactlyOnceWith(9);
    rerender(<LettersInbox letterInbox={[]} replyDrafts={{}} threads={[{ id: 9, firstLetterId: 1, participantA: 1, participantB: 2, capsuleId: 4, status: "ACTIVE", lastLetterAt: "2026-07-17T00:00:00Z" }]}
      selectedThreadId={9} threadLetters={[{ ...letter, id: 30, title: "线程里的信", letterBody: "往来内容", status: "DELIVERED" }]}
      connectionRequests={{ incoming: [], outgoing: [] }} friends={[]} onOpenThread={onOpenThread}
      onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined}
      onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    expect(screen.getByText("线程里的信")).toBeVisible();
  });

  it("renders tabs, inbox actions and consent panel in English when locale is en-SG", () => {
    render(<LettersInbox locale="en-SG" letterInbox={[letter]} replyDrafts={{ 7: "thanks" }}
      connectionRequests={{ incoming: [{ id: 3, status: "PENDING", userId: 5, nickname: "Mira", username: "mira", source: "SLOW_LETTER" }], outgoing: [] }}
      friends={[]} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined} onDecideConnection={() => undefined} onLeaveConnection={() => undefined} />);
    expect(screen.getByRole("heading", { name: /Only after it arrives/ })).toBeVisible();
    expect(screen.getByRole("tab", { name: "Received" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Send the reply slow letter" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Report this letter" })).toBeVisible();
    expect(screen.getByText("Mira would like to know you after the letters")).toBeVisible();
    expect(screen.getByRole("button", { name: "I'd like to too" })).toBeVisible();
  });

  it("lets the user accept an incoming connection request", () => {
    const onDecideConnection = vi.fn();
    render(<LettersInbox letterInbox={[]} replyDrafts={{}}
      connectionRequests={{ incoming: [{ id: 3, status: "PENDING", userId: 5, nickname: "小满", username: "xm", source: "SLOW_LETTER" }], outgoing: [] }}
      friends={[]} onReplyDraftChange={() => undefined} onReply={() => undefined} onActOnLetter={() => undefined}
      onReportLetter={() => undefined} onRequestConnection={() => undefined} onDecideConnection={onDecideConnection} onLeaveConnection={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "我也愿意" }));
    expect(onDecideConnection).toHaveBeenCalledWith(3, "accept");
  });
});
