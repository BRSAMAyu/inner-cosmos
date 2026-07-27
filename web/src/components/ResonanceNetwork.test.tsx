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

  it("shows the top three candidates first and expands the rest on demand", () => {
    const matches = Array.from({ length: 5 }, (_, index): CapsuleMatch => ({
      ...match,
      capsule: { ...match.capsule, id: index + 1, pseudonym: `同行者 ${index + 1}` },
      matchSummary: `匹配理由 ${index + 1}`
    }));
    render(<ResonanceNetwork resonanceMatches={matches} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={null}
      personaSession={null} personaMessages={[]} personaDraft="" personaQuota={null} letterTitle="" letterBody="" sentLetter={null}
      onChooseStrategy={() => undefined} onChooseMatch={() => undefined} onStartPersonaConversation={() => undefined}
      onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined} onLetterTitleChange={() => undefined}
      onLetterBodyChange={() => undefined} onSendLetter={() => undefined} />);
    expect(screen.getAllByRole("listitem")).toHaveLength(3);
    expect(screen.queryByRole("listitem", { name: "同行者 4 · 匹配理由 4" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "再看 2 个" }));
    expect(screen.getAllByRole("listitem")).toHaveLength(5);
    expect(screen.getByRole("listitem", { name: "同行者 5 · 匹配理由 5" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "收起，只看最相关的 3 个" }));
    expect(screen.getAllByRole("listitem")).toHaveLength(3);
  });

  it("lets a visitor start a persona conversation and send a turn", () => {
    const onStart = vi.fn();
    const onSend = vi.fn();
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 0, dailyLimit: 5 };
    const messages: PersonaMessage[] = [{ id: 1, sessionId: 1, senderType: "CAPSULE", textContent: "谢谢你愿意说" }];
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={match}
      personaSession={session} personaMessages={messages} personaDraft="想继续聊聊" personaQuota={{ turnCount: 1, remaining: 4, dailyLimit: 5, seed: false, quotaDate: "2026-07-25" }}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={onStart} onPersonaDraftChange={() => undefined} onSendPersonaTurn={onSend}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined} />);
    expect(screen.getByText("谢谢你愿意说")).toBeVisible();
    expect(screen.getByText("今天还可以聊 4 轮")).toBeVisible();
    expect(screen.queryByText(/– 轮/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "发送这一轮" }));
    expect(onSend).toHaveBeenCalledOnce();
    expect(onStart).not.toHaveBeenCalled();
  });

  it("keeps anti-abuse quota in the background until the visitor is close to it", () => {
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 1, dailyLimit: 30 };
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={match}
      personaSession={session} personaMessages={[]} personaDraft="继续" personaQuota={{ turnCount: 1, remaining: 29, dailyLimit: 30, seed: false, quotaDate: "2026-07-25" }}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined} />);

    expect(screen.getByText("可以自然聊，不用赶进度")).toBeVisible();
    expect(screen.getByRole("button", { name: "发送这一轮" })).toBeEnabled();
  });

  it("only disables the composer when the authoritative remaining quota reaches zero", () => {
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 30, dailyLimit: 30 };
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={match}
      personaSession={session} personaMessages={[]} personaDraft="继续" personaQuota={{ turnCount: 30, remaining: 0, dailyLimit: 30, seed: false, quotaDate: "2026-07-25" }}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined} />);

    expect(screen.getByText(/今天的对话额度已用完/)).toBeVisible();
    expect(screen.getByRole("button", { name: "发送这一轮" })).toBeDisabled();
  });

  it("keeps the composer available and labels the classroom runtime when quota is unlimited", () => {
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 99, dailyLimit: 0 };
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={match}
      personaSession={session} personaMessages={[]} personaDraft="继续聊"
      personaQuota={{ turnCount: 0, remaining: -1, dailyLimit: 0, seed: false, quotaDate: "2026-07-27", unlimited: true }}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined} />);

    expect(screen.getByText("现场演示不限对话轮次")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "发送这一轮" })).toBeEnabled();
  });

  it("uses the server match tier as a friendly visible badge", () => {
    render(<ResonanceNetwork resonanceMatches={[{ ...match, matchTier: "NONE", resonant: false }]} resonanceStrategy="MIRROR"
      visitorBusy={false} visitorMatch={null} personaSession={null} personaMessages={[]} personaDraft="" personaQuota={null}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined} />);
    expect(screen.getByText("探索相遇")).toBeVisible();
  });

  it("lets a capsule reply be marked as landed once and then disables repeat taps", () => {
    const onMarkLanded = vi.fn();
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 1, dailyLimit: 5 };
    const messages: PersonaMessage[] = [{ id: 1, sessionId: 1, senderType: "CAPSULE", textContent: "谢谢你愿意说" }];
    const props = {
      resonanceMatches: [match], resonanceStrategy: "MIRROR" as const, visitorBusy: false, visitorMatch: match,
      personaSession: session, personaMessages: messages, personaDraft: "", personaQuota: { turnCount: 1, remaining: 4, dailyLimit: 5, seed: false, quotaDate: "2026-07-25" },
      letterTitle: "", letterBody: "", sentLetter: null, onChooseStrategy: () => undefined, onChooseMatch: () => undefined,
      onStartPersonaConversation: () => undefined, onPersonaDraftChange: () => undefined, onSendPersonaTurn: () => undefined,
      onLetterTitleChange: () => undefined, onLetterBodyChange: () => undefined, onSendLetter: () => undefined,
      onMarkLanded
    };
    const rendered = render(<ResonanceNetwork {...props} landed={false} />);
    fireEvent.click(screen.getByRole("button", { name: "这条回复有共鸣" }));
    expect(onMarkLanded).toHaveBeenCalledOnce();
    rendered.rerender(<ResonanceNetwork {...props} landed />);
    expect(screen.getByRole("button", { name: "已记录" })).toBeDisabled();
  });

  it("does not render a slow-letter entry when the owner's boundary explicitly forbids it", () => {
    const noLetterMatch: CapsuleMatch = { ...match, capsule: { ...match.capsule, allowLetterRequest: false } };
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 1, dailyLimit: 5 };
    render(<ResonanceNetwork resonanceMatches={[noLetterMatch]} resonanceStrategy="MIRROR" visitorBusy={false}
      visitorMatch={noLetterMatch} personaSession={session}
      personaMessages={[{ id: 1, sessionId: 1, senderType: "CAPSULE", textContent: "谢谢你愿意说" }]}
      personaDraft="" personaQuota={{ turnCount: 1, remaining: 4, dailyLimit: 5, seed: false, quotaDate: "2026-07-25" }}
      letterTitle="题目" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined} />);
    expect(screen.queryByLabelText("慢信正文")).not.toBeInTheDocument();
  });

  it("lets a visitor report or block mid-chat, without waiting for a delivered letter", () => {
    const onReportSession = vi.fn();
    const onBlockSession = vi.fn();
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 0, dailyLimit: 5 };
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={match}
      personaSession={session} personaMessages={[]} personaDraft="" personaQuota={{ turnCount: 0, remaining: 5, dailyLimit: 5, seed: false, quotaDate: "2026-07-25" }}
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
      personaSession={session} personaMessages={[]} personaDraft="想继续聊聊" personaQuota={{ turnCount: 0, remaining: 5, dailyLimit: 5, seed: false, quotaDate: "2026-07-25" }}
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
      personaSession={session} personaMessages={[]} personaDraft="" personaQuota={{ turnCount: 0, remaining: 5, dailyLimit: 5, seed: false, quotaDate: "2026-07-25" }}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined} />);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  // W2 UIUX audit follow-up: the match-card button rendered <span>badge</span><strong>pseudonym</strong>
  // <p>intro</p><small>summary</small> with no aria-label, so its accessible name concatenated all
  // four pieces (including the full user-authored intro paragraph) into one run-on string -- the same
  // shape as the ProductShellNavigation run-on bug this campaign already fixed, but worse. Fixed with
  // a short, properly separated aria-label and aria-hidden on the visual duplicate.
  it("gives the match-card button a short, separated accessible name instead of concatenating the whole card", () => {
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={null}
      personaSession={null} personaMessages={[]} personaDraft="" personaQuota={null} letterTitle="" letterBody="" sentLetter={null}
      onChooseStrategy={() => undefined} onChooseMatch={() => undefined} onStartPersonaConversation={() => undefined}
      onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined} onLetterTitleChange={() => undefined}
      onLetterBodyChange={() => undefined} onSendLetter={() => undefined} />);
    const card = screen.getByRole("listitem", { name: "同行者 · 最近都在面对转变" });
    expect(card.tagName).toBe("BUTTON");
    // the visual content (badge/intro paragraph) must not leak into the accessible name
    expect(card).not.toHaveAccessibleName(/阶段相近的人/);
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
    expect(screen.getByRole("button", { name: "Talk with this facet first" })).toBeVisible();
  });

  // W1 capsule-voice reuse: a visitor can tap to hear the latest capsule reply spoken in a voice
  // distinct from Aurora's. The play affordance is opt-in (tap-to-play), shown only on the most
  // recent CAPSULE reply, and reuses the shared InlineAudioPlayer once audio is fetched.
  it("shows a tap-to-play 'hear this echo' button only on the latest capsule reply", () => {
    const onPlayPersonaVoice = vi.fn();
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 2, dailyLimit: 5 };
    const messages: PersonaMessage[] = [
      { id: 1, sessionId: 1, senderType: "CAPSULE", textContent: "早些时候的回声" },
      { id: 2, sessionId: 1, senderType: "VISITOR", textContent: "继续" },
      { id: 3, sessionId: 1, senderType: "CAPSULE", textContent: "最新的回声" }
    ];
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={match}
      personaSession={session} personaMessages={messages} personaDraft="" personaQuota={{ turnCount: 1, remaining: 4, dailyLimit: 5, seed: false, quotaDate: "2026-07-25" }}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined}
      onPlayPersonaVoice={onPlayPersonaVoice} />);
    // Exactly one play affordance, on the latest capsule reply.
    const playButton = screen.getByRole("button", { name: "▶ 听这条回声" });
    expect(playButton).toBeVisible();
    fireEvent.click(playButton);
    expect(onPlayPersonaVoice).toHaveBeenCalledOnce();
  });

  it("does not offer capsule voice before a capsule reply exists", () => {
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 0, dailyLimit: 5 };
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={match}
      personaSession={session} personaMessages={[]} personaDraft="" personaQuota={{ turnCount: 0, remaining: 5, dailyLimit: 5, seed: false, quotaDate: "2026-07-25" }}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined}
      onPlayPersonaVoice={() => undefined} />);
    expect(screen.queryByRole("button", { name: "▶ 听这条回声" })).not.toBeInTheDocument();
  });

  it("replaces the play button with the shared InlineAudioPlayer once audio is fetched", () => {
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 1, dailyLimit: 5 };
    const messages: PersonaMessage[] = [{ id: 5, sessionId: 1, senderType: "CAPSULE", textContent: "听这条回声" }];
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={match}
      personaSession={session} personaMessages={messages} personaDraft="" personaQuota={{ turnCount: 1, remaining: 4, dailyLimit: 5, seed: false, quotaDate: "2026-07-25" }}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined}
      onPlayPersonaVoice={() => undefined} personaVoiceAudio="data:audio/mpeg;base64,AAA" />);
    // The fetch button is gone, replaced by the InlineAudioPlayer (aria-label = capsuleVoiceAria).
    expect(screen.queryByRole("button", { name: "▶ 听这条回声" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "听到这个共鸣体的回复（与 Aurora 不同的声音）" })).toBeVisible();
  });

  it("surfaces a capsule-voice synthesis error as an alert on the reply bubble", () => {
    const session: PersonaSession = { id: 1, capsuleId: 4, status: "ACTIVE", turnCount: 1, dailyLimit: 5 };
    const messages: PersonaMessage[] = [{ id: 5, sessionId: 1, senderType: "CAPSULE", textContent: "听这条回声" }];
    render(<ResonanceNetwork resonanceMatches={[match]} resonanceStrategy="MIRROR" visitorBusy={false} visitorMatch={match}
      personaSession={session} personaMessages={messages} personaDraft="" personaQuota={{ turnCount: 1, remaining: 4, dailyLimit: 5, seed: false, quotaDate: "2026-07-25" }}
      letterTitle="" letterBody="" sentLetter={null} onChooseStrategy={() => undefined} onChooseMatch={() => undefined}
      onStartPersonaConversation={() => undefined} onPersonaDraftChange={() => undefined} onSendPersonaTurn={() => undefined}
      onLetterTitleChange={() => undefined} onLetterBodyChange={() => undefined} onSendLetter={() => undefined}
      onPlayPersonaVoice={() => undefined} personaVoiceError="共鸣体语音暂时不可用" />);
    expect(screen.getByRole("alert")).toHaveTextContent("共鸣体语音暂时不可用");
  });
});
