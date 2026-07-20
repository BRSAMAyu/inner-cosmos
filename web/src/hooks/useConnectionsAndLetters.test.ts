import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../api";
import type { ConnectionRequests, DiscoverablePerson, LetterThread, RelationHealth, RelationMention, RelationTimelinePoint, SlowLetter, SocialConnection } from "../api";
import { useConnectionsAndLetters } from "./useConnectionsAndLetters";

vi.mock("../api", () => ({
  api: {
    letterInbox: vi.fn(),
    letterOutbox: vi.fn(),
    letterThreads: vi.fn(),
    letterThreadLetters: vi.fn(),
    connectionRequests: vi.fn(),
    friends: vi.fn(),
    discoverPeople: vi.fn(),
    relations: vi.fn(),
    relationTimeline: vi.fn(),
    relationHealth: vi.fn(),
    requestFriend: vi.fn(),
    requestConnectionFromLetter: vi.fn(),
    decideConnection: vi.fn(),
    leaveConnection: vi.fn(),
    sendSlowLetter: vi.fn(),
    transitionLetter: vi.fn(),
    reportLetter: vi.fn(),
    replyWithSlowLetter: vi.fn()
  }
}));

function setup() {
  const setStatus = vi.fn();
  const { result } = renderHook(() => useConnectionsAndLetters({ setStatus }));
  return { result, setStatus };
}

const person = (overrides: Partial<DiscoverablePerson> = {}): DiscoverablePerson => ({
  id: 1, username: "alice", nickname: "Alice", relationStatus: "NONE", ...overrides
});

const connection = (overrides: Partial<SocialConnection> = {}): SocialConnection => ({
  id: 1, nickname: "Bob", ...overrides
} as SocialConnection);

const requests = (overrides: Partial<ConnectionRequests> = {}): ConnectionRequests => ({
  incoming: [], outgoing: [], ...overrides
});

const relation = (overrides: Partial<RelationMention> = {}): RelationMention => ({
  id: 1, relationLabel: "妈妈", relationType: "FAMILY", emotionTags: null, triggerSummary: null, ...overrides
} as RelationMention);

const timelinePoint = (overrides: Partial<RelationTimelinePoint> = {}): RelationTimelinePoint => ({
  timestamp: "2026-07-18T00:00:00", emotions: null, summary: null, ...overrides
} as RelationTimelinePoint);

const health = (overrides: Partial<RelationHealth> = {}): RelationHealth => ({
  healthScore: 0.8, ...overrides
} as RelationHealth);

const letter = (overrides: Partial<SlowLetter> = {}): SlowLetter => ({
  id: 1, title: "写给你", letterBody: "最近好吗", status: "DELIVERED", ...overrides
} as SlowLetter);

const thread = (overrides: Partial<LetterThread> = {}): LetterThread => ({
  id: 1, status: "ACTIVE", lastLetterAt: null, ...overrides
} as LetterThread);

beforeEach(() => {
  vi.mocked(api.connectionRequests).mockResolvedValue(requests());
  vi.mocked(api.friends).mockResolvedValue([]);
  vi.mocked(api.discoverPeople).mockResolvedValue([]);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("useConnectionsAndLetters -- initial state", () => {
  it("starts with empty connections, relations and letters state", () => {
    const { result } = setup();
    expect(result.current.connectionRequests).toEqual({ incoming: [], outgoing: [] });
    expect(result.current.friends).toEqual([]);
    expect(result.current.people).toEqual([]);
    expect(result.current.peopleBusy).toBe(false);
    expect(result.current.relations).toEqual([]);
    expect(result.current.selectedRelation).toBeNull();
    expect(result.current.relationTimeline).toEqual([]);
    expect(result.current.relationHealth).toBeNull();
    expect(result.current.relationBusy).toBe(false);
    expect(result.current.letterInbox).toEqual([]);
    expect(result.current.letterOutbox).toEqual([]);
    expect(result.current.letterThreads).toEqual([]);
    expect(result.current.selectedThreadId).toBeNull();
    expect(result.current.threadLetters).toEqual([]);
    expect(result.current.draftBusy).toBe(false);
    expect(result.current.replyDrafts).toEqual({});
  });
});

describe("useConnectionsAndLetters -- bootstrap loaders", () => {
  it("loadLetterInbox/loadConnectionRequests/loadFriends populate their own state (no catch, matching the original mega Promise.all entries)", async () => {
    vi.mocked(api.letterInbox).mockResolvedValue([letter()]);
    vi.mocked(api.connectionRequests).mockResolvedValue(requests({ incoming: [{ id: 9, nickname: "Cara" } as never] }));
    vi.mocked(api.friends).mockResolvedValue([connection()]);
    const { result } = setup();
    await act(async () => {
      await Promise.all([result.current.loadLetterInbox(), result.current.loadConnectionRequests(), result.current.loadFriends()]);
    });
    expect(result.current.letterInbox).toHaveLength(1);
    expect(result.current.connectionRequests.incoming).toHaveLength(1);
    expect(result.current.friends).toHaveLength(1);
  });

  it("loadLetterOutbox/loadLetterThreads/loadRelations swallow failures (matching the original .catch(() => undefined) entries)", async () => {
    vi.mocked(api.letterOutbox).mockRejectedValue(new Error("down"));
    vi.mocked(api.letterThreads).mockRejectedValue(new Error("down"));
    vi.mocked(api.relations).mockRejectedValue(new Error("down"));
    const { result } = setup();
    await act(async () => {
      await Promise.all([result.current.loadLetterOutbox(), result.current.loadLetterThreads(), result.current.loadRelations()]);
    });
    expect(result.current.letterOutbox).toEqual([]);
    expect(result.current.letterThreads).toEqual([]);
    expect(result.current.relations).toEqual([]);
  });

  it("loadPeople trusts the backend provenance boundary and swallows failure", async () => {
    vi.mocked(api.discoverPeople).mockResolvedValue([person(), person({ id: 2, username: "mira", nickname: "Mira" })]);
    const { result } = setup();
    await act(async () => { await result.current.loadPeople(); });
    expect(result.current.people).toHaveLength(2);
  });
});

describe("useConnectionsAndLetters -- People Discovery / connections", () => {
  it("requestPersonConnection sends the invite, refreshes connections and reports success", async () => {
    vi.mocked(api.requestFriend).mockResolvedValue({} as never);
    vi.mocked(api.friends).mockResolvedValue([connection()]);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.requestPersonConnection(7); });
    expect(api.requestFriend).toHaveBeenCalledExactlyOnceWith(7);
    expect(result.current.friends).toHaveLength(1);
    expect(result.current.peopleBusy).toBe(false);
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("邀请已发出"));
  });

  it("requestPersonConnection reports the error and clears busy on failure", async () => {
    vi.mocked(api.requestFriend).mockRejectedValue(new Error("网络错误"));
    const { result, setStatus } = setup();
    await act(async () => { await result.current.requestPersonConnection(7); });
    expect(result.current.peopleBusy).toBe(false);
    expect(setStatus).toHaveBeenCalledWith("网络错误");
  });

  it("refreshConnections keeps the existing (already-filtered) people list when discoverPeople fails, instead of a stale closure", async () => {
    vi.mocked(api.discoverPeople).mockResolvedValueOnce([person()]);
    const { result } = setup();
    await act(async () => { await result.current.loadPeople(); });
    expect(result.current.people).toEqual([person()]);

    vi.mocked(api.discoverPeople).mockRejectedValueOnce(new Error("down"));
    await act(async () => { await result.current.refreshConnections(); });
    expect(result.current.people).toEqual([person()]);
  });

  it("decideConnection accepts/declines then refreshes connections", async () => {
    vi.mocked(api.decideConnection).mockResolvedValue({} as never);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.decideConnection(3, "accept"); });
    expect(api.decideConnection).toHaveBeenCalledExactlyOnceWith(3, "accept");
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("双方都已同意"));
  });

  it("leaveConnection leaves then refreshes connections", async () => {
    vi.mocked(api.leaveConnection).mockResolvedValue({} as never);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.leaveConnection(3); });
    expect(api.leaveConnection).toHaveBeenCalledExactlyOnceWith(3);
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("已退出这段连接"));
  });
});

describe("useConnectionsAndLetters -- relations", () => {
  it("openRelation loads the timeline and health for the selected relation", async () => {
    vi.mocked(api.relationTimeline).mockResolvedValue([timelinePoint()]);
    vi.mocked(api.relationHealth).mockResolvedValue(health());
    const { result } = setup();
    await act(async () => { await result.current.openRelation("妈妈"); });
    expect(result.current.selectedRelation).toBe("妈妈");
    expect(result.current.relationTimeline).toHaveLength(1);
    expect(result.current.relationHealth).toEqual(health());
    expect(result.current.relationBusy).toBe(false);
  });

  it("openRelation tolerates a failing relationHealth call (caught individually, timeline still loads)", async () => {
    vi.mocked(api.relationTimeline).mockResolvedValue([timelinePoint()]);
    vi.mocked(api.relationHealth).mockRejectedValue(new Error("down"));
    const { result } = setup();
    await act(async () => { await result.current.openRelation("妈妈"); });
    expect(result.current.relationTimeline).toHaveLength(1);
    expect(result.current.relationHealth).toBeNull();
  });

  it("openRelation reports an error and clears busy when the timeline call itself fails", async () => {
    vi.mocked(api.relationTimeline).mockRejectedValue(new Error("暂时读不到"));
    const { result, setStatus } = setup();
    await act(async () => { await result.current.openRelation("妈妈"); });
    expect(setStatus).toHaveBeenCalledWith("暂时读不到");
    expect(result.current.relationBusy).toBe(false);
  });
});

describe("useConnectionsAndLetters -- letters", () => {
  it("openThread loads the thread's letters", async () => {
    vi.mocked(api.letterThreadLetters).mockResolvedValue([letter()]);
    const { result } = setup();
    await act(async () => { await result.current.openThread(5); });
    expect(result.current.selectedThreadId).toBe(5);
    expect(result.current.threadLetters).toHaveLength(1);
  });

  it("sendDraft sends the draft, refreshes the outbox and resets busy", async () => {
    vi.mocked(api.sendSlowLetter).mockResolvedValue(letter({ status: "SENT" }));
    vi.mocked(api.letterOutbox).mockResolvedValue([letter({ status: "SENT" })]);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.sendDraft(1); });
    expect(api.sendSlowLetter).toHaveBeenCalledExactlyOnceWith(1);
    expect(result.current.letterOutbox).toHaveLength(1);
    expect(result.current.draftBusy).toBe(false);
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("已经启程"));
  });

  it("actOnLetter transitions the letter and patches it in place in the inbox", async () => {
    vi.mocked(api.letterInbox).mockResolvedValue([letter({ id: 1, status: "DELIVERED" })]);
    vi.mocked(api.transitionLetter).mockResolvedValue(letter({ id: 1, status: "READ" }));
    const { result } = setup();
    await act(async () => { await result.current.loadLetterInbox(); });
    await act(async () => { await result.current.actOnLetter(letter({ id: 1 }), "read"); });
    expect(result.current.letterInbox[0].status).toBe("READ");
  });

  it("reportLetter submits the report", async () => {
    vi.mocked(api.reportLetter).mockResolvedValue(undefined as never);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.reportLetter(letter()); });
    expect(api.reportLetter).toHaveBeenCalledExactlyOnceWith(1, expect.any(String));
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("已提交举报"));
  });

  it("updateReplyDraft stores per-letter reply drafts", () => {
    const { result } = setup();
    act(() => { result.current.updateReplyDraft(1, "回复内容"); });
    expect(result.current.replyDrafts).toEqual({ 1: "回复内容" });
  });

  it("replyWithLetter does nothing when the draft is blank", async () => {
    const { result } = setup();
    await act(async () => { await result.current.replyWithLetter(letter()); });
    expect(api.replyWithSlowLetter).not.toHaveBeenCalled();
  });

  it("replyWithLetter sends the reply, transitions a READ letter to REPLIED and clears the draft", async () => {
    vi.mocked(api.replyWithSlowLetter).mockResolvedValue(letter({ id: 2 }));
    vi.mocked(api.sendSlowLetter).mockResolvedValue(letter({ id: 2, status: "SENT" }));
    vi.mocked(api.transitionLetter).mockResolvedValue(letter({ id: 1, status: "REPLIED" }));
    vi.mocked(api.letterOutbox).mockResolvedValue([]);
    const { result, setStatus } = setup();
    act(() => { result.current.updateReplyDraft(1, "谢谢你的信"); });
    await act(async () => { await result.current.replyWithLetter(letter({ id: 1, status: "READ" })); });
    expect(api.replyWithSlowLetter).toHaveBeenCalledExactlyOnceWith(1, "回复：写给你", "谢谢你的信");
    expect(api.transitionLetter).toHaveBeenCalledExactlyOnceWith(1, "reply");
    expect(result.current.replyDrafts[1]).toBe("");
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("回复慢信已启程"));
  });

  it("requestConnection asks to connect from a letter and refreshes connections", async () => {
    vi.mocked(api.requestConnectionFromLetter).mockResolvedValue({} as never);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.requestConnection(letter()); });
    expect(api.requestConnectionFromLetter).toHaveBeenCalledExactlyOnceWith(1);
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("连接邀请已发出"));
  });
});
