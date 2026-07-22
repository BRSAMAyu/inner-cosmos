import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ResonanceNetwork } from "./ResonanceNetwork";
import type { CapsuleMatch, PersonaMessage, PersonaSession } from "../api";

afterEach(cleanup);

const match: CapsuleMatch = {
  capsule: { id: 4, pseudonym: "同行者", intro: "阶段相近的人", capsuleType: "USER_CAPSULE", publicTags: "[]", echoEnergy: 1, freshnessScore: 1, conversationLimitPerDay: 5, lastActivityAt: null },
  matchScore: .8, matchReasons: ["共同主题"], matchSummary: "最近都在面对转变", resonant: true,
  strategy: "MIRROR", strategyLabel: "相似共鸣", strategyDescription: "此刻处境相近"
};

describe("ResonanceNetwork", () => {
  it("delegates a strategy switch and a match selection", () => {
    const onChooseStrategy = vi.fn();
    const onChooseMatch = vi.fn();
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={null}
      personaSession={null} personaMessages={[]} personaDraft="" personaQuota={null} letterTitle="" letterBody="" sentLetter={null}
      onChooseStrategy={onChooseStrategy} onChooseMatch={onChooseMatch} onStartPersonaConversation={() => undefined}
      onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined} onLetterTitleChange={() => undefined}
      onLetterBodyChange={() => undefined} onSendLetter={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "有意义的互补" }));
    expect(onChooseStrategy).toHaveBeenCalledWith("COMPLEMENT");
    fireEvent.click(screen.getByText("同行者"));
    expect(onChooseMatch).toHaveBeenCalledWith(4);
  });

  it("lets a visitor start a persona conversation and send a turn", () => {
    const onStart = vi.fn();
    const onSend = vi.fn();
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 0, dailyLimit: 5 };
    const messages: PersonaMessage[] = [{ id: 1, sessionId: 1, senderType: "CAPSULE", textContent: "谢谢你愿意说" }];
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={match}
      personaSession={session} personaMessages={messages} personaDraft="想继续聊聊" personaQuota={{ usedTurns: 1, remainingTurns: 4, dailyLimit: 5, exhausted: false }}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={onStart} onPersonaDraftChange={() => undefined} onSendPersonaTurn={onSend}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined} />);
    expect(screen.getByText("谢谢你愿意说")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "发送这一轮" }));
    expect(onSend).toHaveBeenCalledOnce();
    expect(onStart).not.toHaveBeenCalled();
  });

  it("lets a visitor report or block mid-chat, without waiting for a delivered letter", () => {
    const onReportSession = vi.fn();
    const onBlockSession = vi.fn();
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 0, dailyLimit: 5 };
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={match}
      personaSession={session} personaMessages={[]} personaDraft="" personaQuota={{ usedTurns: 0, remainingTurns: 5, dailyLimit: 5, exhausted: false }}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined}
      onReportSession={onReportSession} onBlockSession={onBlockSession} />);
    fireEvent.click(screen.getByRole("button", { name: "举报这段对话" }));
    expect(onReportSession).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole("button", { name: "屏蔽这个共鸣体" }));
    expect(onBlockSession).toHaveBeenCalledOnce();
  });

  it("does not show report/block affordances before a persona conversation has started", () => {
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={match}
      personaSession={null} personaMessages={[]} personaDraft="" personaQuota={null}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined}
      onReportSession={() => undefined} onBlockSession={() => undefined} />);
    expect(screen.queryByRole("button", { name: "举报这段对话" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "屏蔽这个共鸣体" })).not.toBeInTheDocument();
  });

  it("shows a turn-scoped error next to the composer, without needing the global status banner", () => {
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 0, dailyLimit: 5 };
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={match}
      personaSession={session} personaMessages={[]} personaDraft="想继续聊聊" personaQuota={{ usedTurns: 0, remainingTurns: 5, dailyLimit: 5, exhausted: false }}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined}
      personaTurnError="这轮对话没有送达，草稿内容仍在这里" />);
    expect(screen.getByText("这轮对话没有送达，草稿内容仍在这里")).toBeVisible();
    expect(screen.getByLabelText("写给共鸣体")).toHaveValue("想继续聊聊");
  });

  it("does not show a turn error when there is none", () => {
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 0, dailyLimit: 5 };
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={match}
      personaSession={session} personaMessages={[]} personaDraft="" personaQuota={{ usedTurns: 0, remainingTurns: 5, dailyLimit: 5, exhausted: false }}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined} />);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("renders the network, strategies and entry in English when locale is en-SG", () => {
    render(<ResonanceNetwork locale="en-SG" resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false}
      visitorMatch={match} personaSession={null} personaMessages={[]} personaDraft="" personaQuota={null}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined} />);
    expect(screen.getByRole("heading", { name: "Not swiping cards — understanding why you'd meet" })).toBeVisible();
    expect(screen.getByText("1 candidate right now")).toBeVisible();
    expect(screen.getByRole("button", { name: "Meaningful complement" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Enter a limited but natural conversation" })).toBeVisible();
  });
});
