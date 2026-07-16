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
