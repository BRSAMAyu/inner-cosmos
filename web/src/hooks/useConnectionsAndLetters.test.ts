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
    replyWithSlowLetter: vi.fn(),
    myGroups: vi.fn(),
    createGroup: vi.fn(),
    groupInvites: vi.fn(),
    inviteToGroup: vi.fn(),
    respondToGroupInvite: vi.fn(),
    leaveGroup: vi.fn(),
    groupMembers: vi.fn()
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

// A controllable promise so a test can decide exactly when a "slow" fetch resolves, after a
// "fast" fetch for a different resource has already resolved and committed its own state.
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

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
    expect(result.current.isPersonBusy(1)).toBe(false);
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
    expect(result.current.isDraftBusy(1)).toBe(false);
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
    expect(result.current.isPersonBusy(7)).toBe(false);
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("邀请已发出"));
  });

  it("requestPersonConnection reports the error and clears busy on failure", async () => {
    vi.mocked(api.requestFriend).mockRejectedValue(new Error("网络错误"));
    const { result, setStatus } = setup();
    await act(async () => { await result.current.requestPersonConnection(7); });
    expect(result.current.isPersonBusy(7)).toBe(false);
    expect(setStatus).toHaveBeenCalledWith("网络错误");
  });

  // Gemini audit 4.8 (CONFIRMED/P1): requestPersonConnection previously used a single shared
  // `peopleBusy` boolean -- inviting person 7 would disable person 9's invite button too, even
  // though they're unrelated. isPersonBusy must be keyed by userId.
  it("requestPersonConnection only marks the TARGET userId busy -- an unrelated userId stays free while the request is in flight", async () => {
    const slow = deferred<void>();
    vi.mocked(api.requestFriend).mockReturnValueOnce(slow.promise as never);
    const { result } = setup();

    let requestStarted: Promise<void>;
    act(() => { requestStarted = result.current.requestPersonConnection(7); });
    expect(result.current.isPersonBusy(7)).toBe(true);
    expect(result.current.isPersonBusy(9)).toBe(false); // unrelated person, must stay free

    await act(async () => { slow.resolve(); await requestStarted; });
    expect(result.current.isPersonBusy(7)).toBe(false);
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

  // Gemini audit 4.8 (CONFIRMED/P1): decideConnection and leaveConnection previously had NO busy
  // guard at all -- nothing stopped a double-click double-submit on the same connection request.
  // Both must also be keyed per-resource so acting on request 3 never blocks request 5.
  it("decideConnection marks only the TARGET request id busy while in flight; an unrelated request id stays free", async () => {
    const slow = deferred<void>();
    vi.mocked(api.decideConnection).mockReturnValueOnce(slow.promise as never);
    const { result } = setup();

    let decideStarted: Promise<void>;
    act(() => { decideStarted = result.current.decideConnection(3, "accept"); });
    expect(result.current.isConnectionDecisionBusy(3)).toBe(true);
    expect(result.current.isConnectionDecisionBusy(5)).toBe(false);

    await act(async () => { slow.resolve(); await decideStarted; });
    expect(result.current.isConnectionDecisionBusy(3)).toBe(false);
  });

  it("leaveConnection marks only the TARGET connection id busy while in flight; an unrelated connection id stays free", async () => {
    const slow = deferred<void>();
    vi.mocked(api.leaveConnection).mockReturnValueOnce(slow.promise as never);
    const { result } = setup();

    let leaveStarted: Promise<void>;
    act(() => { leaveStarted = result.current.leaveConnection(3); });
    expect(result.current.isConnectionLeaveBusy(3)).toBe(true);
    expect(result.current.isConnectionLeaveBusy(5)).toBe(false);

    await act(async () => { slow.resolve(); await leaveStarted; });
    expect(result.current.isConnectionLeaveBusy(3)).toBe(false);
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

  // Gemini audit 4.4 (CONFIRMED/P1): relation loader has no request epoch. A slow response for a
  // stale selection ("妈妈") that resolves AFTER the user has already moved on to a different
  // selection ("爸爸") must never overwrite the currently-selected relation's timeline/health.
  it("a slow openRelation('妈妈') response arriving after openRelation('爸爸') already committed must NOT overwrite '爸爸''s timeline/health", async () => {
    const slowTimeline = deferred<RelationTimelinePoint[]>();
    const slowHealth = deferred<RelationHealth>();
    vi.mocked(api.relationTimeline).mockReturnValueOnce(slowTimeline.promise);
    vi.mocked(api.relationHealth).mockReturnValueOnce(slowHealth.promise);
    const { result } = setup();

    let openMomStarted: Promise<void>;
    act(() => { openMomStarted = result.current.openRelation("妈妈"); });

    vi.mocked(api.relationTimeline).mockResolvedValueOnce([timelinePoint({ summary: "爸爸的时间线" })]);
    vi.mocked(api.relationHealth).mockResolvedValueOnce(health({ healthScore: 0.5 }));
    await act(async () => { await result.current.openRelation("爸爸"); });
    expect(result.current.selectedRelation).toBe("爸爸");
    expect(result.current.relationTimeline[0].summary).toBe("爸爸的时间线");

    // Now let the STALE "妈妈" response finally arrive.
    await act(async () => {
      slowTimeline.resolve([timelinePoint({ summary: "妈妈的时间线（过期）" })]);
      slowHealth.resolve(health({ healthScore: 0.9 }));
      await openMomStarted;
    });

    // The stale response must have been discarded -- the UI must still show "爸爸"'s data.
    expect(result.current.selectedRelation).toBe("爸爸");
    expect(result.current.relationTimeline[0].summary).toBe("爸爸的时间线");
    expect(result.current.relationHealth?.healthScore).toBe(0.5);
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

  // Gemini audit 4.9 (CONFIRMED/P1): `threadLetters.length === 0` conflated "still loading" and
  // "genuinely empty successful response". The hook must expose an explicit status so the two are
  // distinguishable, and it must be visibly "loading" the instant a thread is opened, BEFORE the
  // fetch resolves -- not just after.
  it("openThread exposes an explicit loading state distinct from a genuinely empty successful response", async () => {
    const slow = deferred<SlowLetter[]>();
    vi.mocked(api.letterThreadLetters).mockReturnValueOnce(slow.promise);
    const { result } = setup();

    let openStarted: Promise<void>;
    act(() => { openStarted = result.current.openThread(5); });
    // Still in flight: must be visibly "loading", NOT indistinguishable from a real empty list.
    expect(result.current.threadLettersStatus).toBe("loading");
    expect(result.current.threadLetters).toEqual([]);

    await act(async () => { slow.resolve([]); await openStarted; });
    // A genuinely empty thread: status flips to "success" even though the array is still empty --
    // this is what makes it distinguishable from the loading state above.
    expect(result.current.threadLettersStatus).toBe("success");
    expect(result.current.threadLetters).toEqual([]);
  });

  it("openThread surfaces an explicit error status (not just a silently-empty list) when the fetch fails", async () => {
    vi.mocked(api.letterThreadLetters).mockRejectedValue(new Error("暂时读不到这段往来"));
    const { result } = setup();
    await act(async () => { await result.current.openThread(5); });
    expect(result.current.threadLettersStatus).toBe("error");
  });

  // Gemini audit 4.4: a slow openThread(A) response arriving after openThread(B) already
  // committed must not clobber thread B's already-displayed letters.
  it("a slow openThread(5) response arriving after openThread(6) already committed must NOT overwrite thread 6's letters", async () => {
    const slow = deferred<SlowLetter[]>();
    vi.mocked(api.letterThreadLetters).mockReturnValueOnce(slow.promise);
    const { result } = setup();

    let openFiveStarted: Promise<void>;
    act(() => { openFiveStarted = result.current.openThread(5); });

    vi.mocked(api.letterThreadLetters).mockResolvedValueOnce([letter({ id: 6, title: "线程六" })]);
    await act(async () => { await result.current.openThread(6); });
    expect(result.current.selectedThreadId).toBe(6);
    expect(result.current.threadLetters[0].title).toBe("线程六");

    await act(async () => {
      slow.resolve([letter({ id: 5, title: "线程五（过期）" })]);
      await openFiveStarted;
    });

    expect(result.current.selectedThreadId).toBe(6);
    expect(result.current.threadLetters[0].title).toBe("线程六");
  });

  it("sendDraft sends the draft, refreshes the outbox and resets busy", async () => {
    vi.mocked(api.sendSlowLetter).mockResolvedValue(letter({ status: "SENT" }));
    vi.mocked(api.letterOutbox).mockResolvedValue([letter({ status: "SENT" })]);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.sendDraft(1); });
    expect(api.sendSlowLetter).toHaveBeenCalledExactlyOnceWith(1);
    expect(result.current.letterOutbox).toHaveLength(1);
    expect(result.current.isDraftBusy(1)).toBe(false);
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("已经启程"));
  });

  // Gemini audit 4.8 (CONFIRMED/P1): sendDraft previously used a single shared `draftBusy` boolean
  // -- sending draft 1 would disable draft 2's send button too, even though they're unrelated
  // drafts a user could otherwise send independently.
  it("sendDraft marks only the TARGET draft id busy while in flight; an unrelated draft id stays free", async () => {
    const slow = deferred<SlowLetter>();
    vi.mocked(api.sendSlowLetter).mockReturnValueOnce(slow.promise as never);
    const { result } = setup();

    let sendStarted: Promise<void>;
    act(() => { sendStarted = result.current.sendDraft(1); });
    expect(result.current.isDraftBusy(1)).toBe(true);
    expect(result.current.isDraftBusy(2)).toBe(false);

    await act(async () => { slow.resolve(letter({ status: "SENT" })); await sendStarted; });
    expect(result.current.isDraftBusy(1)).toBe(false);
  });

  it("actOnLetter transitions the letter and patches it in place in the inbox", async () => {
    vi.mocked(api.letterInbox).mockResolvedValue([letter({ id: 1, status: "DELIVERED" })]);
    vi.mocked(api.transitionLetter).mockResolvedValue(letter({ id: 1, status: "READ" }));
    const { result } = setup();
    await act(async () => { await result.current.loadLetterInbox(); });
    await act(async () => { await result.current.actOnLetter(letter({ id: 1 }), "read"); });
    expect(result.current.letterInbox[0].status).toBe("READ");
  });

  it("actOnLetter also patches the letter in the outbox, so archiving a sent letter updates its own list", async () => {
    vi.mocked(api.letterOutbox).mockResolvedValue([letter({ id: 5, status: "DECLINED" })]);
    vi.mocked(api.transitionLetter).mockResolvedValue(letter({ id: 5, status: "ARCHIVED" }));
    const { result } = setup();
    await act(async () => { await result.current.loadLetterOutbox(); });
    await act(async () => { await result.current.actOnLetter(letter({ id: 5 }), "archive"); });
    expect(result.current.letterOutbox[0].status).toBe("ARCHIVED");
  });

  it("reportLetter submits the report", async () => {
    vi.mocked(api.reportLetter).mockResolvedValue(undefined as never);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.reportLetter(letter()); });
    expect(api.reportLetter).toHaveBeenCalledExactlyOnceWith(1, expect.any(String));
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("已提交举报"));
  });

  // Gemini audit 4.8 (CONFIRMED/P1): actOnLetter/reportLetter previously had NO busy guard at all
  // (plain onClick buttons in LettersInbox), allowing a double-click double-submit. Both are keyed
  // by letter id and share ONE busy tracker (isLetterActionBusy) -- acting on letter 1 must not
  // affect letter 2's buttons.
  it("actOnLetter marks only the TARGET letter id busy while in flight; an unrelated letter id stays free", async () => {
    const slow = deferred<SlowLetter>();
    vi.mocked(api.transitionLetter).mockReturnValueOnce(slow.promise as never);
    const { result } = setup();

    let actStarted: Promise<void>;
    act(() => { actStarted = result.current.actOnLetter(letter({ id: 1 }), "read"); });
    expect(result.current.isLetterActionBusy(1)).toBe(true);
    expect(result.current.isLetterActionBusy(2)).toBe(false);

    await act(async () => { slow.resolve(letter({ id: 1, status: "READ" })); await actStarted; });
    expect(result.current.isLetterActionBusy(1)).toBe(false);
  });

  it("reportLetter shares the SAME per-letter busy key as actOnLetter (both are 'an action on this letter')", async () => {
    const slow = deferred<void>();
    vi.mocked(api.reportLetter).mockReturnValueOnce(slow.promise as never);
    const { result } = setup();

    let reportStarted: Promise<void>;
    act(() => { reportStarted = result.current.reportLetter(letter({ id: 1 })); });
    expect(result.current.isLetterActionBusy(1)).toBe(true);

    await act(async () => { slow.resolve(); await reportStarted; });
    expect(result.current.isLetterActionBusy(1)).toBe(false);
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
    expect(api.replyWithSlowLetter).toHaveBeenCalledExactlyOnceWith(1, "回复：写给你", "谢谢你的信", expect.any(String));
    expect(api.transitionLetter).toHaveBeenCalledExactlyOnceWith(1, "reply");
    expect(result.current.replyDrafts[1]).toBe("");
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("回复慢信已启程"));
  });

  // Gemini audit 4.5 (CONFIRMED/P1): "create-draft 后 send 失败，重试会再次 create." A retry of
  // replyWithLetter for the SAME parent letter, after sendSlowLetter failed the first time, must
  // reuse the already-created reply draft rather than calling replyWithSlowLetter (create) again.
  it("replyWithLetter retried after a failed send reuses the SAME reply draft instead of creating another", async () => {
    vi.mocked(api.replyWithSlowLetter).mockResolvedValue(letter({ id: 9 }));
    vi.mocked(api.sendSlowLetter)
      .mockRejectedValueOnce(new Error("network down"))
      .mockResolvedValueOnce(letter({ id: 9, status: "SENT" }));
    vi.mocked(api.transitionLetter).mockResolvedValue(letter({ id: 1, status: "REPLIED" }));
    vi.mocked(api.letterOutbox).mockResolvedValue([]);
    const { result, setStatus } = setup();
    act(() => { result.current.updateReplyDraft(1, "谢谢你的信"); });

    await act(async () => { await result.current.replyWithLetter(letter({ id: 1, status: "READ" })); });
    expect(api.replyWithSlowLetter).toHaveBeenCalledTimes(1);
    expect(setStatus).toHaveBeenCalledWith("network down");
    // The draft was NOT cleared on failure -- the reply text is still there for the user to retry.
    expect(result.current.replyDrafts[1]).toBe("谢谢你的信");

    await act(async () => { await result.current.replyWithLetter(letter({ id: 1, status: "READ" })); });
    // Still only ONE create call across both attempts -- the retry reused the same draft id.
    expect(api.replyWithSlowLetter).toHaveBeenCalledTimes(1);
    expect(api.sendSlowLetter).toHaveBeenCalledTimes(2);
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("回复慢信已启程"));
  });

  it("requestConnection asks to connect from a letter and refreshes connections", async () => {
    vi.mocked(api.requestConnectionFromLetter).mockResolvedValue({} as never);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.requestConnection(letter()); });
    expect(api.requestConnectionFromLetter).toHaveBeenCalledExactlyOnceWith(1);
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("连接邀请已发出"));
  });

  // Gemini audit 4.8 (CONFIRMED/P1): requestConnection ("willKnow" button) previously had NO busy
  // guard at all. Keyed by letter id -- an unrelated letter must stay free.
  it("requestConnection marks only the TARGET letter id busy while in flight; an unrelated letter id stays free", async () => {
    const slow = deferred<void>();
    vi.mocked(api.requestConnectionFromLetter).mockReturnValueOnce(slow.promise as never);
    const { result } = setup();

    let requestStarted: Promise<void>;
    act(() => { requestStarted = result.current.requestConnection(letter({ id: 1 })); });
    expect(result.current.isLetterConnectionBusy(1)).toBe(true);
    expect(result.current.isLetterConnectionBusy(2)).toBe(false);

    await act(async () => { slow.resolve(); await requestStarted; });
    expect(result.current.isLetterConnectionBusy(1)).toBe(false);
  });
});

describe("useConnectionsAndLetters -- groups", () => {
  it("loadGroups and loadGroupInvites populate their own state", async () => {
    vi.mocked(api.myGroups).mockResolvedValue([{ id: 1, ownerUserId: 1, groupName: "老朋友们", intro: "", visibility: "PRIVATE" }]);
    vi.mocked(api.groupInvites).mockResolvedValue([{ memberId: 9, groupId: 2, groupName: "读书会" }]);
    const { result } = setup();
    await act(async () => { await Promise.all([result.current.loadGroups(), result.current.loadGroupInvites()]); });
    expect(result.current.groups).toHaveLength(1);
    expect(result.current.groupInvites).toHaveLength(1);
  });

  it("createGroup adds the new group to the front of the list", async () => {
    vi.mocked(api.createGroup).mockResolvedValue({ id: 5, ownerUserId: 1, groupName: "新群组", intro: "", visibility: "PRIVATE" });
    const { result, setStatus } = setup();
    await act(async () => { await result.current.createGroup("新群组"); });
    expect(api.createGroup).toHaveBeenCalledExactlyOnceWith("新群组");
    expect(result.current.groups[0].groupName).toBe("新群组");
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("群组已创建"));
  });

  it("createGroup does nothing for a blank name", async () => {
    const { result } = setup();
    await act(async () => { await result.current.createGroup("   "); });
    expect(api.createGroup).not.toHaveBeenCalled();
  });

  it("openGroup selects the group and loads its members", async () => {
    vi.mocked(api.groupMembers).mockResolvedValue([{ userId: 1, memberRole: "OWNER", nickname: "我" }]);
    const { result } = setup();
    await act(async () => { await result.current.openGroup(5); });
    expect(result.current.selectedGroupId).toBe(5);
    expect(result.current.groupMembers).toHaveLength(1);
  });

  // Gemini audit 4.9 sibling (same conflation as threadLetters): `groupMembers.length === 0` was
  // the only signal SocialGroupsView had, so a group that is still loading its member list looked
  // identical to a group that genuinely has no members yet.
  it("openGroup exposes an explicit loading state distinct from a genuinely empty member list", async () => {
    const slow = deferred<Array<{ userId: number; memberRole: string; nickname: string }>>();
    vi.mocked(api.groupMembers).mockReturnValueOnce(slow.promise as never);
    const { result } = setup();

    let openStarted: Promise<void>;
    act(() => { openStarted = result.current.openGroup(5); });
    expect(result.current.groupMembersStatus).toBe("loading");
    expect(result.current.groupMembers).toEqual([]);

    await act(async () => { slow.resolve([]); await openStarted; });
    expect(result.current.groupMembersStatus).toBe("success");
    expect(result.current.groupMembers).toEqual([]);
  });

  // Gemini audit 4.4: a slow openGroup(A) response arriving after openGroup(B) already committed
  // must not clobber group B's already-displayed member list.
  it("a slow openGroup(5) response arriving after openGroup(6) already committed must NOT overwrite group 6's members", async () => {
    const slow = deferred<Array<{ userId: number; memberRole: string; nickname: string }>>();
    vi.mocked(api.groupMembers).mockReturnValueOnce(slow.promise as never);
    const { result } = setup();

    let openFiveStarted: Promise<void>;
    act(() => { openFiveStarted = result.current.openGroup(5); });

    vi.mocked(api.groupMembers).mockResolvedValueOnce([{ userId: 6, memberRole: "OWNER", nickname: "组六成员" }]);
    await act(async () => { await result.current.openGroup(6); });
    expect(result.current.selectedGroupId).toBe(6);
    expect(result.current.groupMembers[0].nickname).toBe("组六成员");

    await act(async () => {
      slow.resolve([{ userId: 5, memberRole: "MEMBER", nickname: "组五成员（过期）" }]);
      await openFiveStarted;
    });

    expect(result.current.selectedGroupId).toBe(6);
    expect(result.current.groupMembers[0].nickname).toBe("组六成员");
  });

  it("inviteToGroup calls the API with the target friend's userId", async () => {
    vi.mocked(api.inviteToGroup).mockResolvedValue(undefined as never);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.inviteToGroup(5, 30); });
    expect(api.inviteToGroup).toHaveBeenCalledExactlyOnceWith(5, 30);
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("邀请已发出"));
  });

  // Gemini audit 4.8 (CONFIRMED/P1): the anti-pattern named explicitly in the audit -- a single
  // shared `groupBusy` boolean previously meant inviting into group 5 disabled EVERY group action
  // for EVERY other group, including leaving a completely unrelated group 9. Each is now its own
  // keyed tracker.
  it("inviteToGroup marks only the TARGET groupId busy; leaveGroup for an unrelated groupId stays free the whole time", async () => {
    const slow = deferred<void>();
    vi.mocked(api.inviteToGroup).mockReturnValueOnce(slow.promise as never);
    const { result } = setup();

    let inviteStarted: Promise<void>;
    act(() => { inviteStarted = result.current.inviteToGroup(5, 30); });
    expect(result.current.isGroupInviteBusy(5)).toBe(true);
    expect(result.current.isGroupInviteBusy(9)).toBe(false);
    expect(result.current.isGroupLeaveBusy(9)).toBe(false); // a DIFFERENT action, unrelated group -- must never be true

    await act(async () => { slow.resolve(); await inviteStarted; });
    expect(result.current.isGroupInviteBusy(5)).toBe(false);
  });

  it("respondToGroupInvite removes the invite from the list and reloads groups on accept", async () => {
    vi.mocked(api.respondToGroupInvite).mockResolvedValue(undefined as never);
    vi.mocked(api.myGroups).mockResolvedValue([{ id: 2, ownerUserId: 9, groupName: "读书会", intro: "", visibility: "PRIVATE" }]);
    const { result } = setup();
    await act(async () => { await result.current.loadGroupInvites(); });
    vi.mocked(api.groupInvites).mockResolvedValue([{ memberId: 9, groupId: 2, groupName: "读书会" }]);
    await act(async () => { await result.current.loadGroupInvites(); });
    await act(async () => { await result.current.respondToGroupInvite(9, "accept"); });
    expect(result.current.groupInvites).toHaveLength(0);
    expect(api.myGroups).toHaveBeenCalled();
  });

  it("respondToGroupInvite marks only the TARGET memberId busy; an unrelated memberId's invite stays free", async () => {
    const slow = deferred<void>();
    vi.mocked(api.respondToGroupInvite).mockReturnValueOnce(slow.promise as never);
    const { result } = setup();

    let respondStarted: Promise<void>;
    act(() => { respondStarted = result.current.respondToGroupInvite(9, "accept"); });
    expect(result.current.isGroupInviteDecisionBusy(9)).toBe(true);
    expect(result.current.isGroupInviteDecisionBusy(11)).toBe(false);

    await act(async () => { slow.resolve(); await respondStarted; });
    expect(result.current.isGroupInviteDecisionBusy(9)).toBe(false);
  });

  it("leaveGroup removes the group from the list and clears selection if it was selected", async () => {
    vi.mocked(api.myGroups).mockResolvedValue([{ id: 5, ownerUserId: 9, groupName: "读书会", intro: "", visibility: "PRIVATE" }]);
    vi.mocked(api.groupMembers).mockResolvedValue([]);
    vi.mocked(api.leaveGroup).mockResolvedValue(undefined as never);
    const { result } = setup();
    await act(async () => { await result.current.loadGroups(); });
    await act(async () => { await result.current.openGroup(5); });
    await act(async () => { await result.current.leaveGroup(5); });
    expect(result.current.groups).toHaveLength(0);
    expect(result.current.selectedGroupId).toBeNull();
  });

  it("leaveGroup marks only the TARGET groupId busy; an unrelated groupId stays free", async () => {
    const slow = deferred<void>();
    vi.mocked(api.leaveGroup).mockReturnValueOnce(slow.promise as never);
    const { result } = setup();

    let leaveStarted: Promise<void>;
    act(() => { leaveStarted = result.current.leaveGroup(5); });
    expect(result.current.isGroupLeaveBusy(5)).toBe(true);
    expect(result.current.isGroupLeaveBusy(9)).toBe(false);

    await act(async () => { slow.resolve(); await leaveStarted; });
    expect(result.current.isGroupLeaveBusy(5)).toBe(false);
  });
});
