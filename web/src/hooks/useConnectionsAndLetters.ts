import { useCallback, useRef, useState } from "react";
import {
  api, type ConnectionRequests, type DiscoverablePerson, type GroupInvite, type GroupMember, type LetterThread,
  type RelationHealth, type RelationMention, type RelationTimelinePoint, type SlowLetter, type SocialConnection, type SocialGroup
} from "../api";

// Extracted from AuroraApp.tsx (B1 domain-hook decomposition, second slice): the "connections/letters"
// product space -- People Discovery, relation mentions/timeline, connection requests/friends, and the
// slow-letter inbox/outbox/threads. See docs/goal/tracks/track-b-status.yml and
// evidence/track-b/README.md for exactly what moved.
//
// Deliberately NOT included, despite the name similarity: the Resonance space's OWN
// compose-a-new-slow-letter-to-a-persona-match flow (letterTitle/letterBody/sentLetter/visitorBusy,
// used by sendLetterToMatch in AuroraApp.tsx) stays in AuroraApp.tsx -- it shares sessionId-adjacent
// visitor/persona state with the capsule/resonance domain and is a different feature (composing a
// brand-new letter to a capsule persona) from this hook's reply/decide/archive flow over already-
// arrived letters in the inbox.

export type UseConnectionsAndLettersOptions = {
  /** The app-wide status banner is a cross-cutting concern (see web/src/loading.tsx's B1 loading-audit
   * checkpoint); this hook only ever writes to it, never reads it. */
  setStatus: (status: string) => void;
};

// Gemini audit 4.9 (CONFIRMED/P1): a discriminated fetch status so "still loading" and "genuinely
// empty successful response" can never be conflated by a UI reading `someList.length === 0`.
export type FetchStatus = "idle" | "loading" | "success" | "error";

export function useConnectionsAndLetters({ setStatus }: UseConnectionsAndLettersOptions) {
  const [connectionRequests, setConnectionRequests] = useState<ConnectionRequests>({ incoming: [], outgoing: [] });
  const [friends, setFriends] = useState<SocialConnection[]>([]);
  const [people, setPeople] = useState<DiscoverablePerson[]>([]);
  const [peopleBusy, setPeopleBusy] = useState(false);
  const [relations, setRelations] = useState<RelationMention[]>([]);
  const [selectedRelation, setSelectedRelation] = useState<string | null>(null);
  const [relationTimeline, setRelationTimeline] = useState<RelationTimelinePoint[]>([]);
  const [relationHealth, setRelationHealth] = useState<RelationHealth | null>(null);
  const [relationBusy, setRelationBusy] = useState(false);
  const [letterInbox, setLetterInbox] = useState<SlowLetter[]>([]);
  const [letterOutbox, setLetterOutbox] = useState<SlowLetter[]>([]);
  const [letterThreads, setLetterThreads] = useState<LetterThread[]>([]);
  const [selectedThreadId, setSelectedThreadId] = useState<number | null>(null);
  const [threadLetters, setThreadLetters] = useState<SlowLetter[]>([]);
  const [threadLettersStatus, setThreadLettersStatus] = useState<FetchStatus>("idle");
  const [draftBusy, setDraftBusy] = useState(false);
  const [replyBusyId, setReplyBusyId] = useState<number | null>(null);
  const [replyDrafts, setReplyDrafts] = useState<Record<number, string>>({});
  const [groups, setGroups] = useState<SocialGroup[]>([]);
  const [groupInvites, setGroupInvites] = useState<GroupInvite[]>([]);
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null);
  const [groupMembers, setGroupMembers] = useState<GroupMember[]>([]);
  const [groupMembersStatus, setGroupMembersStatus] = useState<FetchStatus>("idle");
  const [groupBusy, setGroupBusy] = useState(false);

  // Gemini audit 4.4 (CONFIRMED/P1): relation/thread/group loaders had no request epoch, so a slow
  // response for a stale selection could overwrite the currently-selected resource's state (e.g.
  // select relation A, quickly reselect B, then A's late response clobbers B's already-rendered
  // timeline). Each loader below bumps its own monotonic generation counter BEFORE starting the
  // fetch and captures that generation; every subsequent state-commit checks the ref is still the
  // CURRENT generation before writing. A stale generation's response is discarded silently rather
  // than applied -- the equivalent of an AbortController/sequence-number guard for plain (non-
  // cancellable) GET fetches that don't take an AbortSignal today.
  const relationGenerationRef = useRef(0);
  const threadGenerationRef = useRef(0);
  const groupGenerationRef = useRef(0);

  // ---- Bootstrap loaders. AuroraApp.tsx's own bootstrap() still fires ONE 23-way Promise.all of
  // every domain's initial fetch (unchanged in shape by this extraction, matching the precedent set
  // by useAuroraSession's loadWakeIntents/loadNotifications) -- these seven small functions are the
  // hook-owned replacements for what used to be inline api.* calls in that array. Per-entry catch
  // behavior is preserved exactly: letterInbox/connectionRequests/friends have none (a failure there
  // should fail the whole bootstrap), the other four swallow failures silently, exactly as before. ----
  const loadLetterInbox = useCallback(() => api.letterInbox().then(setLetterInbox), []);
  const loadConnectionRequests = useCallback(() => api.connectionRequests().then(setConnectionRequests), []);
  const loadFriends = useCallback(() => api.friends().then(setFriends), []);
  const loadLetterOutbox = useCallback(() => api.letterOutbox().then(setLetterOutbox).catch(() => undefined), []);
  // The backend applies persisted account provenance (HUMAN/SHOWCASE only). Do not
  // re-infer identity from usernames in the browser.
  const loadPeople = useCallback(() => api.discoverPeople().then(setPeople).catch(() => undefined), []);
  const loadRelations = useCallback(() => api.relations().then(setRelations).catch(() => undefined), []);
  const loadLetterThreads = useCallback(() => api.letterThreads().then(setLetterThreads).catch(() => undefined), []);
  const loadGroups = useCallback(() => api.myGroups().then(setGroups).catch(() => undefined), []);
  const loadGroupInvites = useCallback(() => api.groupInvites().then(setGroupInvites).catch(() => undefined), []);

  // Re-fetches the three connection-shaped lists together, still as one concurrent Promise.all
  // (matching the original shape). One deliberate, documented improvement over the original: the
  // original AuroraApp.tsx version closed over the component's `people` variable at call time
  // (`api.discoverPeople().catch(() => people)`), which happened to be safe only because that
  // function was redefined fresh every render; here, as a stable useCallback, a functional setPeople
  // update is used instead so a discoverPeople failure keeps whatever the freshest `people` state is,
  // not a value captured when this callback was created. Observably identical (both keep the existing,
  // list unchanged on failure) -- see docs/goal/tracks/track-b-status.yml discoveries.
  const refreshConnections = useCallback(async () => {
    const [requestsRows, acceptedRows, discoverable] = await Promise.all([
      api.connectionRequests(), api.friends(),
      api.discoverPeople().then(rows => ({ ok: true as const, rows })).catch(() => ({ ok: false as const }))
    ]);
    setConnectionRequests(requestsRows);
    setFriends(acceptedRows);
    setPeople(current => discoverable.ok ? discoverable.rows : current);
  }, []);

  const requestPersonConnection = useCallback(async (userId: number) => {
    setPeopleBusy(true);
    try {
      await api.requestFriend(userId);
      await refreshConnections();
      setStatus("邀请已发出。对方同意前不会开放任何私密内容，也不会变成即时聊天。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法发出这个邀请"); }
    finally { setPeopleBusy(false); }
  }, [refreshConnections, setStatus]);

  const openRelation = useCallback(async (label: string) => {
    const generation = ++relationGenerationRef.current;
    const isCurrent = () => relationGenerationRef.current === generation;
    setSelectedRelation(label); setRelationBusy(true);
    setRelationTimeline([]); setRelationHealth(null);
    try {
      const [timeline, health] = await Promise.all([
        api.relationTimeline(label),
        api.relationHealth(label).catch(() => null)
      ]);
      if (!isCurrent()) return; // 4.4: a newer selection superseded this one -- discard silently.
      setRelationTimeline(timeline); setRelationHealth(health);
    } catch (error) {
      if (!isCurrent()) return;
      setStatus(error instanceof Error ? error.message : "暂时读不到这段关系的时间线");
    } finally { if (isCurrent()) setRelationBusy(false); }
  }, [setStatus]);

  const openThread = useCallback(async (threadId: number) => {
    const generation = ++threadGenerationRef.current;
    const isCurrent = () => threadGenerationRef.current === generation;
    setSelectedThreadId(threadId); setThreadLetters([]); setThreadLettersStatus("loading");
    try {
      const letters = await api.letterThreadLetters(threadId);
      if (!isCurrent()) return; // 4.4: a newer selection superseded this one -- discard silently.
      setThreadLetters(letters); setThreadLettersStatus("success");
    } catch (error) {
      if (!isCurrent()) return;
      setThreadLettersStatus("error");
      setStatus(error instanceof Error ? error.message : "暂时读不到这段往来");
    }
  }, [setStatus]);

  const sendDraft = useCallback(async (id: number) => {
    setDraftBusy(true);
    try {
      await api.sendSlowLetter(id);
      await api.letterOutbox().then(setLetterOutbox).catch(() => undefined);
      setStatus("这封信已经启程，会按慢信的节奏抵达。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法寄出这封草稿"); }
    finally { setDraftBusy(false); }
  }, [setStatus]);

  const actOnLetter = useCallback(async (letter: SlowLetter, action: "read" | "decline" | "block" | "archive") => {
    try {
      const updated = await api.transitionLetter(letter.id, action);
      setLetterInbox(rows => rows.map(row => row.id === updated.id ? updated : row));
      setLetterOutbox(rows => rows.map(row => row.id === updated.id ? updated : row));
      setStatus(action === "block" ? "已屏蔽来信者；后续慢信也会被阻断。" : "慢信边界已更新。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法更新这封信"); }
  }, [setStatus]);

  const reportLetter = useCallback(async (letter: SlowLetter) => {
    try {
      await api.reportLetter(letter.id, "收件人从 Aurora 界面举报慢信");
      setStatus("已提交举报。举报不会自动公开信件内容，交由受限审核处理。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法提交举报"); }
  }, [setStatus]);

  const replyWithLetter = useCallback(async (letter: SlowLetter) => {
    const body = replyDrafts[letter.id]?.trim();
    if (!body) return;
    setReplyBusyId(letter.id);
    try {
      const draft = await api.replyWithSlowLetter(letter.id, `回复：${letter.title}`, body);
      await api.sendSlowLetter(draft.id);
      const updated = letter.status === "READ" ? await api.transitionLetter(letter.id, "reply") : letter;
      setLetterInbox(rows => rows.map(row => row.id === updated.id ? updated : row));
      void api.letterOutbox().then(setLetterOutbox).catch(() => undefined);
      setReplyDrafts(drafts => ({ ...drafts, [letter.id]: "" }));
      setStatus("回复慢信已启程。它仍会经过时间，而不是变成即时聊天。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "回复慢信没有启程"); }
    finally { setReplyBusyId(null); }
  }, [replyDrafts, setStatus]);

  const updateReplyDraft = useCallback((letterId: number, value: string) => {
    setReplyDrafts(drafts => ({ ...drafts, [letterId]: value }));
  }, []);

  const requestConnection = useCallback(async (letter: SlowLetter) => {
    try {
      await api.requestConnectionFromLetter(letter.id);
      await refreshConnections();
      setStatus("连接邀请已发出。只有对方明确接受后，双方才会成为真实连接。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法发出连接邀请"); }
  }, [refreshConnections, setStatus]);

  const decideConnection = useCallback(async (id: number, decision: "accept" | "decline") => {
    try {
      await api.decideConnection(id, decision);
      await refreshConnections();
      setStatus(decision === "accept" ? "双方都已同意这段连接。" : "已婉拒；不会自动建立任何关系。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法处理连接邀请"); }
  }, [refreshConnections, setStatus]);

  const leaveConnection = useCallback(async (id: number) => {
    try {
      await api.leaveConnection(id);
      await refreshConnections();
      setStatus("已退出这段连接。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法退出连接"); }
  }, [refreshConnections, setStatus]);

  const createGroup = useCallback(async (groupName: string) => {
    const name = groupName.trim();
    if (!name) return;
    setGroupBusy(true);
    try {
      const created = await api.createGroup(name);
      setGroups(current => [created, ...current]);
      setStatus("群组已创建。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法创建群组"); }
    finally { setGroupBusy(false); }
  }, [setStatus]);

  const openGroup = useCallback(async (groupId: number) => {
    const generation = ++groupGenerationRef.current;
    const isCurrent = () => groupGenerationRef.current === generation;
    setSelectedGroupId(groupId); setGroupMembers([]); setGroupMembersStatus("loading");
    try {
      const members = await api.groupMembers(groupId);
      if (!isCurrent()) return; // 4.4: a newer selection superseded this one -- discard silently.
      setGroupMembers(members); setGroupMembersStatus("success");
    } catch (error) {
      if (!isCurrent()) return;
      setGroupMembersStatus("error");
      setStatus(error instanceof Error ? error.message : "暂时读不到这个群组的成员");
    }
  }, [setStatus]);

  const inviteToGroup = useCallback(async (groupId: number, userId: number) => {
    setGroupBusy(true);
    try {
      await api.inviteToGroup(groupId, userId);
      setStatus("邀请已发出，等待对方接受。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法邀请这位朋友"); }
    finally { setGroupBusy(false); }
  }, [setStatus]);

  const respondToGroupInvite = useCallback(async (memberId: number, decision: "accept" | "decline") => {
    setGroupBusy(true);
    try {
      await api.respondToGroupInvite(memberId, decision);
      setGroupInvites(current => current.filter(invite => invite.memberId !== memberId));
      if (decision === "accept") await loadGroups();
      setStatus(decision === "accept" ? "已加入群组。" : "已婉拒这个群组邀请。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法处理这个邀请"); }
    finally { setGroupBusy(false); }
  }, [loadGroups, setStatus]);

  const leaveGroup = useCallback(async (groupId: number) => {
    setGroupBusy(true);
    try {
      await api.leaveGroup(groupId);
      setGroups(current => current.filter(group => group.id !== groupId));
      if (selectedGroupId === groupId) { setSelectedGroupId(null); setGroupMembers([]); }
      setStatus("已退出这个群组。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法退出这个群组"); }
    finally { setGroupBusy(false); }
  }, [selectedGroupId, setStatus]);

  return {
    connectionRequests, friends, people, peopleBusy,
    relations, selectedRelation, relationTimeline, relationHealth, relationBusy,
    letterInbox, letterOutbox, letterThreads, selectedThreadId, threadLetters, threadLettersStatus,
    draftBusy, replyBusyId, replyDrafts,
    groups, groupInvites, selectedGroupId, groupMembers, groupMembersStatus, groupBusy,
    loadLetterInbox, loadConnectionRequests, loadFriends, loadLetterOutbox, loadPeople, loadRelations, loadLetterThreads,
    loadGroups, loadGroupInvites,
    refreshConnections, requestPersonConnection, openRelation, openThread, sendDraft,
    actOnLetter, reportLetter, replyWithLetter, updateReplyDraft,
    requestConnection, decideConnection, leaveConnection,
    createGroup, openGroup, inviteToGroup, respondToGroupInvite, leaveGroup
  };
}
