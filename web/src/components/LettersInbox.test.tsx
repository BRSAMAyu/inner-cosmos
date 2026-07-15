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
