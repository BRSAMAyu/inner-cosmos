import { useCallback, useRef, useState } from "react";
import {
  api, type ConnectionRequests, type DiscoverablePerson, type GroupInvite, type GroupMember, type LetterThread,
  type RelationHealth, type RelationMention, type RelationTimelinePoint, type SlowLetter, type SocialConnection, type SocialGroup
} from "../api";
import { sendComposedLetter, type DraftedLetterState } from "../composeAndSend";
import { useBusyKeys } from "./useBusyKeys";

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
  const [replyBusyId, setReplyBusyId] = useState<number | null>(null);
  const [replyDrafts, setReplyDrafts] = useState<Record<number, string>>({});
  // W1 slow-letter voice reuse: ephemeral playback state for "hear this delivered letter read
  // aloud". One active clip at a time (letterVoiceLetterId names which letter the audio/error
  // belong to), mirroring the capsule-voice personaVoiceAudio pattern in AuroraApp.tsx. The audio
  // is a base64 data URI fetched on demand from POST /api/letters/{id}/voice.
  const [letterVoiceLetterId, setLetterVoiceLetterId] = useState<number | null>(null);
  const [letterVoiceAudio, setLetterVoiceAudio] = useState<string | null>(null);
  const [letterVoiceError, setLetterVoiceError] = useState<string | null>(null);
  // Gemini audit 4.5 (CONFIRMED/P1): keyed by the PARENT letter's id, persists each in-flight
  // reply's created-draft id + idempotency key across a failed send-retry (see
  // web/src/composeAndSend.ts) -- a retry must reuse the same reply draft, not create another one.
  const replyDraftsRef = useRef<Record<number, DraftedLetterState>>({});
  const [groups, setGroups] = useState<SocialGroup[]>([]);
  const [groupInvites, setGroupInvites] = useState<GroupInvite[]>([]);
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null);
  const [groupMembers, setGroupMembers] = useState<GroupMember[]>([]);
  const [groupMembersStatus, setGroupMembersStatus] = useState<FetchStatus>("idle");
  const [groupCreateBusy, setGroupCreateBusy] = useState(false); // no resource id -- one create form

  // Gemini audit 4.8 (CONFIRMED/P1): "多个 social action 使用普通 button，无 per-resource busy
  // guard." Each of these tracks ITS OWN resource-keyed in-flight set -- an action on one resource
  // (e.g. inviting user 7 to group 3) must never disable an unrelated action's button (e.g.
  // leaving group 9, or inviting a different user to a different group). Previously
  // peopleBusy/groupBusy/draftBusy were single shared booleans (the exact anti-pattern the audit
  // calls out), and decideConnection/leaveConnection/requestConnection/actOnLetter/reportLetter had
  // NO busy guard at all, allowing a double-click double-submit.
  const peopleBusyKeys = useBusyKeys<number>(); // keyed by target userId (requestPersonConnection)
  const connectionDecisionBusyKeys = useBusyKeys<number>(); // keyed by connection-request id (decideConnection)
  const connectionLeaveBusyKeys = useBusyKeys<number>(); // keyed by connection id (leaveConnection)
  const letterConnectionBusyKeys = useBusyKeys<number>(); // keyed by letter id (requestConnection "willKnow")
  const letterActionBusyKeys = useBusyKeys<number>(); // keyed by letter id (actOnLetter + reportLetter)
  const letterVoiceBusyKeys = useBusyKeys<number>(); // keyed by letter id (playLetterVoice)
  const draftBusyKeys = useBusyKeys<number>(); // keyed by draft id (sendDraft)
  const groupInviteBusyKeys = useBusyKeys<number>(); // keyed by groupId (inviteToGroup)
  const groupInviteDecisionBusyKeys = useBusyKeys<number>(); // keyed by memberId (respondToGroupInvite)
  const groupLeaveBusyKeys = useBusyKeys<number>(); // keyed by groupId (leaveGroup)

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

  const requestPersonConnection = useCallback((userId: number) => peopleBusyKeys.run(userId, async () => {
    try {
      await api.requestFriend(userId);
      await refreshConnections();
      setStatus("邀请已发出。对方同意前不会开放任何私密内容，也不会变成即时聊天。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法发出这个邀请"); }
  }), [peopleBusyKeys, refreshConnections, setStatus]);

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

  const sendDraft = useCallback((id: number) => draftBusyKeys.run(id, async () => {
    try {
      await api.sendSlowLetter(id);
      await api.letterOutbox().then(setLetterOutbox).catch(() => undefined);
      setStatus("这封信已经启程，会按慢信的节奏抵达。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法寄出这封草稿"); }
  }), [draftBusyKeys, setStatus]);

  const actOnLetter = useCallback((letter: SlowLetter, action: "read" | "decline" | "block" | "archive") =>
    letterActionBusyKeys.run(letter.id, async () => {
      try {
        const updated = await api.transitionLetter(letter.id, action);
        setLetterInbox(rows => rows.map(row => row.id === updated.id ? updated : row));
        setLetterOutbox(rows => rows.map(row => row.id === updated.id ? updated : row));
        setStatus(action === "block" ? "已屏蔽来信者；后续慢信也会被阻断。" : "慢信边界已更新。 ");
      } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法更新这封信"); }
    }), [letterActionBusyKeys, setStatus]);

  const reportLetter = useCallback((letter: SlowLetter) => letterActionBusyKeys.run(letter.id, async () => {
    try {
      await api.reportLetter(letter.id, "收件人从 Aurora 界面举报慢信");
      setStatus("已提交举报。举报不会自动公开信件内容，交由受限审核处理。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法提交举报"); }
  }), [letterActionBusyKeys, setStatus]);

  // W1 slow-letter voice reuse: tap-to-play a delivered letter's body read aloud. The recipient's
  // tap is the user gesture that authorizes autoplay on arrival. Per-letter busy guard so playing
  // one letter never disables another's button. A synthesis failure (e.g. TTS not configured) surfaces
  // as a visible inline error on that letter -- the letter itself is never affected.
  const playLetterVoice = useCallback((letter: SlowLetter) => letterVoiceBusyKeys.run(letter.id, async () => {
    setLetterVoiceLetterId(letter.id);
    setLetterVoiceAudio(null);
    setLetterVoiceError(null);
    try {
      const { audio } = await api.letterVoice(letter.id);
      setLetterVoiceAudio(audio);
    } catch (error) {
      setLetterVoiceError(error instanceof Error ? error.message : "暂时无法朗读这封信");
    }
  }), [letterVoiceBusyKeys]);

  const replyWithLetter = useCallback(async (letter: SlowLetter) => {
    const body = replyDrafts[letter.id]?.trim();
    if (!body) return;
    setReplyBusyId(letter.id);
    try {
      // Gemini audit 4.5: reuses replyDraftsRef.current[letter.id] (set by a prior failed attempt
      // replying to this SAME parent letter) instead of unconditionally drafting again -- a retry
      // after a failed send must never produce a second, duplicate reply draft.
      await sendComposedLetter({
        pending: replyDraftsRef.current[letter.id] ?? null,
        onDraftCreated: next => { replyDraftsRef.current = { ...replyDraftsRef.current, [letter.id]: next }; },
        createDraft: idempotencyKey => api.replyWithSlowLetter(letter.id, `回复：${letter.title}`, body, idempotencyKey),
        sendDraft: (draftId, idempotencyKey) => api.sendSlowLetter(draftId, idempotencyKey)
      });
      const { [letter.id]: _discard, ...rest } = replyDraftsRef.current;
      replyDraftsRef.current = rest; // sent successfully -- clear so the next reply starts fresh.
      const updated = letter.status === "READ" ? await api.transitionLetter(letter.id, "reply") : letter;
      setLetterInbox(rows => rows.map(row => row.id === updated.id ? updated : row));
      void api.letterOutbox().then(setLetterOutbox).catch(() => undefined);
      setReplyDrafts(drafts => ({ ...drafts, [letter.id]: "" }));
      setStatus("回复慢信已启程。它仍会经过时间，而不是变成即时聊天。 ");
    } catch (error) {
      // replyDraftsRef.current[letter.id] is intentionally left set (if a draft was created) so
      // retrying this same letter's reply reuses the same draft rather than creating another.
      setStatus(error instanceof Error ? error.message : "回复慢信没有启程");
    }
    finally { setReplyBusyId(null); }
  }, [replyDrafts, setStatus]);

  const updateReplyDraft = useCallback((letterId: number, value: string) => {
    setReplyDrafts(drafts => ({ ...drafts, [letterId]: value }));
  }, []);

  const requestConnection = useCallback((letter: SlowLetter) => letterConnectionBusyKeys.run(letter.id, async () => {
    try {
      await api.requestConnectionFromLetter(letter.id);
      await refreshConnections();
      setStatus("连接邀请已发出。只有对方明确接受后，双方才会成为真实连接。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法发出连接邀请"); }
  }), [letterConnectionBusyKeys, refreshConnections, setStatus]);

  const decideConnection = useCallback((id: number, decision: "accept" | "decline") => connectionDecisionBusyKeys.run(id, async () => {
    try {
      await api.decideConnection(id, decision);
      await refreshConnections();
      setStatus(decision === "accept" ? "双方都已同意这段连接。" : "已婉拒；不会自动建立任何关系。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法处理连接邀请"); }
  }), [connectionDecisionBusyKeys, refreshConnections, setStatus]);

  const leaveConnection = useCallback((id: number) => connectionLeaveBusyKeys.run(id, async () => {
    try {
      await api.leaveConnection(id);
      await refreshConnections();
      setStatus("已退出这段连接。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法退出连接"); }
  }), [connectionLeaveBusyKeys, refreshConnections, setStatus]);

  const createGroup = useCallback(async (groupName: string) => {
    const name = groupName.trim();
    if (!name) return;
    setGroupCreateBusy(true);
    try {
      const created = await api.createGroup(name);
      setGroups(current => [created, ...current]);
      setStatus("群组已创建。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法创建群组"); }
    finally { setGroupCreateBusy(false); }
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

  const inviteToGroup = useCallback((groupId: number, userId: number) => groupInviteBusyKeys.run(groupId, async () => {
    try {
      await api.inviteToGroup(groupId, userId);
      setStatus("邀请已发出，等待对方接受。");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法邀请这位朋友"); }
  }), [groupInviteBusyKeys, setStatus]);

  const respondToGroupInvite = useCallback((memberId: number, decision: "accept" | "decline") =>
    groupInviteDecisionBusyKeys.run(memberId, async () => {
      try {
        await api.respondToGroupInvite(memberId, decision);
        setGroupInvites(current => current.filter(invite => invite.memberId !== memberId));
        if (decision === "accept") await loadGroups();
        setStatus(decision === "accept" ? "已加入群组。" : "已婉拒这个群组邀请。 ");
      } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法处理这个邀请"); }
    }), [groupInviteDecisionBusyKeys, loadGroups, setStatus]);

  const leaveGroup = useCallback((groupId: number) => groupLeaveBusyKeys.run(groupId, async () => {
    try {
      await api.leaveGroup(groupId);
      setGroups(current => current.filter(group => group.id !== groupId));
      if (selectedGroupId === groupId) { setSelectedGroupId(null); setGroupMembers([]); }
      setStatus("已退出这个群组。 ");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法退出这个群组"); }
  }), [groupLeaveBusyKeys, selectedGroupId, setStatus]);

  return {
    connectionRequests, friends, people, isPersonBusy: peopleBusyKeys.isBusy,
    relations, selectedRelation, relationTimeline, relationHealth, relationBusy,
    letterInbox, letterOutbox, letterThreads, selectedThreadId, threadLetters, threadLettersStatus,
    isDraftBusy: draftBusyKeys.isBusy, replyBusyId, replyDrafts,
    isLetterActionBusy: letterActionBusyKeys.isBusy,
    isConnectionDecisionBusy: connectionDecisionBusyKeys.isBusy,
    isConnectionLeaveBusy: connectionLeaveBusyKeys.isBusy,
    isLetterConnectionBusy: letterConnectionBusyKeys.isBusy,
    letterVoiceLetterId, letterVoiceAudio, letterVoiceError, isLetterVoiceBusy: letterVoiceBusyKeys.isBusy,
    groups, groupInvites, selectedGroupId, groupMembers, groupMembersStatus,
    groupCreateBusy, isGroupInviteBusy: groupInviteBusyKeys.isBusy,
    isGroupInviteDecisionBusy: groupInviteDecisionBusyKeys.isBusy, isGroupLeaveBusy: groupLeaveBusyKeys.isBusy,
    loadLetterInbox, loadConnectionRequests, loadFriends, loadLetterOutbox, loadPeople, loadRelations, loadLetterThreads,
    loadGroups, loadGroupInvites,
    refreshConnections, requestPersonConnection, openRelation, openThread, sendDraft,
    actOnLetter, reportLetter, replyWithLetter, updateReplyDraft, playLetterVoice,
    requestConnection, decideConnection, leaveConnection,
    createGroup, openGroup, inviteToGroup, respondToGroupInvite, leaveGroup
  };
}
