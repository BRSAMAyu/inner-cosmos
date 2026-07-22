import { useCallback, useState } from "react";
import {
  api, type ConnectionRequests, type DiscoverablePerson, type LetterThread,
  type RelationHealth, type RelationMention, type RelationTimelinePoint, type SlowLetter, type SocialConnection
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
  const [draftBusy, setDraftBusy] = useState(false);
  const [replyBusyId, setReplyBusyId] = useState<number | null>(null);
  const [replyDrafts, setReplyDrafts] = useState<Record<number, string>>({});

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
    setSelectedRelation(label); setRelationBusy(true);
    setRelationTimeline([]); setRelationHealth(null);
    try {
      const [timeline, health] = await Promise.all([
        api.relationTimeline(label),
        api.relationHealth(label).catch(() => null)
      ]);
      setRelationTimeline(timeline); setRelationHealth(health);
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时读不到这段关系的时间线"); }
    finally { setRelationBusy(false); }
  }, [setStatus]);

  const openThread = useCallback(async (threadId: number) => {
    setSelectedThreadId(threadId); setThreadLetters([]);
    try { setThreadLetters(await api.letterThreadLetters(threadId)); }
    catch (error) { setStatus(error instanceof Error ? error.message : "暂时读不到这段往来"); }
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

  return {
    connectionRequests, friends, people, peopleBusy,
    relations, selectedRelation, relationTimeline, relationHealth, relationBusy,
    letterInbox, letterOutbox, letterThreads, selectedThreadId, threadLetters, draftBusy, replyBusyId, replyDrafts,
    loadLetterInbox, loadConnectionRequests, loadFriends, loadLetterOutbox, loadPeople, loadRelations, loadLetterThreads,
    refreshConnections, requestPersonConnection, openRelation, openThread, sendDraft,
    actOnLetter, reportLetter, replyWithLetter, updateReplyDraft,
    requestConnection, decideConnection, leaveConnection
  };
}
