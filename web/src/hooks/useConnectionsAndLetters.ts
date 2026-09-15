import { useCallback, useRef, useState } from "react";
import {
  api, type ConnectionRequests, type DeliverySchedule, type DiscoverablePerson, type GroupInvite, type GroupMember, type GroupMessage,
  type LetterThread, type LiveChatInvites, type LiveChatMessage, type LiveChatSession,
  type RelationCorrectionProposal, type RelationMention, type RelationReview, type RelationTimelinePoint, type SlowLetter, type SlowLetterOutboxRow,
  type SocialConnection, type SocialGroup
} from "../api";
import { sendComposedLetter, type DraftedLetterState } from "../composeAndSend";
import type { Locale } from "../i18n";
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
  locale?: Locale;
};

// Gemini audit 4.9 (CONFIRMED/P1): a discriminated fetch status so "still loading" and "genuinely
// empty successful response" can never be conflated by a UI reading `someList.length === 0`.
export type FetchStatus = "idle" | "loading" | "success" | "error";

export function useConnectionsAndLetters({ setStatus, locale = "zh-CN" }: UseConnectionsAndLettersOptions) {
  const copy = useCallback((english: string, chinese: string) =>
    locale === "en-SG" ? english : chinese, [locale]);
  const [connectionRequests, setConnectionRequests] = useState<ConnectionRequests>({ incoming: [], outgoing: [] });
  const [friends, setFriends] = useState<SocialConnection[]>([]);
  const [people, setPeople] = useState<DiscoverablePerson[]>([]);
  const [relations, setRelations] = useState<RelationMention[]>([]);
  const [selectedRelation, setSelectedRelation] = useState<string | null>(null);
  const [relationTimeline, setRelationTimeline] = useState<RelationTimelinePoint[]>([]);
  const [relationReview, setRelationReview] = useState<RelationReview | null>(null);
  const [relationBusy, setRelationBusy] = useState(false);
  const [letterInbox, setLetterInbox] = useState<SlowLetter[]>([]);
  // CP-33: the outbox projection is the sender-facing privacy shape (no sent bodies,
  // CLOSED fold, receipt-choice-aware) -- typed after the backend VO, not SlowLetter.
  const [letterOutbox, setLetterOutbox] = useState<SlowLetterOutboxRow[]>([]);
  const [letterThreads, setLetterThreads] = useState<LetterThread[]>([]);
  const [lettersRefreshing, setLettersRefreshing] = useState(false);
  const [selectedThreadId, setSelectedThreadId] = useState<number | null>(null);
  const [threadLetters, setThreadLetters] = useState<SlowLetter[]>([]);
  const [threadLettersStatus, setThreadLettersStatus] = useState<FetchStatus>("idle");
  // CP-34 both-party-consent corrections on a shared letter thread. incoming = awaiting this
  // user's decision; outgoing = this user's own proposals with their honest status. Loaded with
  // the opened thread (non-fatal on failure — letters still render; the correction block simply
  // reports it could not load) and updated in place after each decision.
  const [threadCorrections, setThreadCorrections] = useState<{
    incoming: RelationCorrectionProposal[]; outgoing: RelationCorrectionProposal[];
  }>({ incoming: [], outgoing: [] });
  const [threadCorrectionsStatus, setThreadCorrectionsStatus] = useState<FetchStatus>("idle");
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
  const directLetterDraftRef = useRef<DraftedLetterState | null>(null);
  const [directLetterBusy, setDirectLetterBusy] = useState(false);
  const [groups, setGroups] = useState<SocialGroup[]>([]);
  const [groupInvites, setGroupInvites] = useState<GroupInvite[]>([]);
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null);
  const [groupMembers, setGroupMembers] = useState<GroupMember[]>([]);
  const [groupMembersStatus, setGroupMembersStatus] = useState<FetchStatus>("idle");
  const [groupMessages, setGroupMessages] = useState<GroupMessage[]>([]);
  const [groupMessagesStatus, setGroupMessagesStatus] = useState<FetchStatus>("idle");
  // CP-35: the last failed group-message send's server message, shown inline by the composer —
  // the muted-member 403 ("你已被群内禁言，剩余约 X 分钟") must reach the person it names, not
  // only the global status line. Cleared on a successful send, on opening another group, and on
  // every refresh so it never lingers as stale state.
  const [groupMessageError, setGroupMessageError] = useState<string | null>(null);
  const [liveChatInvites, setLiveChatInvites] = useState<LiveChatInvites>({ incoming: [], outgoing: [] });
  const [liveChatSessions, setLiveChatSessions] = useState<LiveChatSession[]>([]);
  const [selectedLiveChatSessionId, setSelectedLiveChatSessionId] = useState<number | null>(null);
  const [liveChatMessages, setLiveChatMessages] = useState<LiveChatMessage[]>([]);
  const [liveChatStatus, setLiveChatStatus] = useState<FetchStatus>("idle");
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
  const receiptPolicyBusyKeys = useBusyKeys<number>(); // keyed by letter id (CP-33 recipient receipt choice)
  const letterVoiceBusyKeys = useBusyKeys<number>(); // keyed by letter id (playLetterVoice)
  const draftBusyKeys = useBusyKeys<number>(); // keyed by draft id (sendDraft)
  const correctionBusyKeys = useBusyKeys<number>(); // keyed by proposal id (CP-34 accept/reject/withdraw)
  const correctionProposeBusyKeys = useBusyKeys<number>(); // keyed by thread id (CP-34 propose)
  const groupInviteBusyKeys = useBusyKeys<number>(); // keyed by groupId (inviteToGroup)
  const groupInviteDecisionBusyKeys = useBusyKeys<number>(); // keyed by memberId (respondToGroupInvite)
  const groupLeaveBusyKeys = useBusyKeys<number>(); // keyed by groupId (leaveGroup)
  const groupMessageBusyKeys = useBusyKeys<number>(); // keyed by groupId (sendGroupMessage)
  const groupGovernanceBusyKeys = useBusyKeys<number>(); // keyed by target userId (CP-35 mute/unmute/transfer)
  const groupDissolveBusyKeys = useBusyKeys<number>(); // keyed by groupId (CP-35 dissolve)
  const liveChatInviteBusyKeys = useBusyKeys<number>(); // keyed by target userId
  const liveChatDecisionBusyKeys = useBusyKeys<number>(); // keyed by invite id
  const liveChatMessageBusyKeys = useBusyKeys<number>(); // keyed by session id
  const liveChatEndBusyKeys = useBusyKeys<number>(); // keyed by session id

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
  const searchPeople = useCallback(async (query: string) => {
    try {
      const rows = await api.discoverPeople(query);
      setPeople(rows);
      return rows.length;
    } catch (error) {
      setStatus(error instanceof Error ? error.message
        : copy("Could not find that classroom partner yet.", "暂时没能找到这位课堂伙伴。"));
      return 0;
    }
  }, [copy, setStatus]);
  const loadRelations = useCallback(() => api.relations().then(setRelations).catch(() => undefined), []);
  const loadLetterThreads = useCallback(() => api.letterThreads().then(setLetterThreads).catch(() => undefined), []);
  const loadGroups = useCallback(() => api.myGroups().then(setGroups).catch(() => undefined), []);
  const loadGroupInvites = useCallback(() => api.groupInvites().then(setGroupInvites).catch(() => undefined), []);

  // Slow letters change state in the scheduler after they leave the sender's request. A user
  // should not need to reload the entire app to see a letter move from SENT -> FLYING -> DELIVERED
  // or appear in their inbox. Refresh the three letter projections as one coherent snapshot.
  const refreshLetters = useCallback(async () => {
    setLettersRefreshing(true);
    try {
      const [inbox, outbox, threads] = await Promise.all([
        api.letterInbox(), api.letterOutbox(), api.letterThreads()
      ]);
      setLetterInbox(inbox);
      setLetterOutbox(outbox);
      setLetterThreads(threads);
    } catch (error) {
      setStatus(error instanceof Error ? error.message
        : copy("Could not refresh slow letters. Please try again shortly.", "暂时无法刷新慢信，请稍后再试"));
    } finally {
      setLettersRefreshing(false);
    }
  }, [copy, setStatus]);

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

  const refreshGroups = useCallback(async () => {
    const [groupRows, inviteRows] = await Promise.all([api.myGroups(), api.groupInvites()]);
    setGroups(groupRows);
    setGroupInvites(inviteRows);
  }, []);

  const requestPersonConnection = useCallback((userId: number) => peopleBusyKeys.run(userId, async () => {
    try {
      await api.requestFriend(userId);
      await refreshConnections();
      setStatus(copy(
        "Invitation sent. No private content is shared before they agree, and this will not become instant chat.",
        "邀请已发出。对方同意前不会开放任何私密内容，也不会变成即时聊天。"));
    } catch (error) { setStatus(error instanceof Error ? error.message
      : copy("Could not send this invitation yet.", "暂时无法发出这个邀请")); }
  }), [copy, peopleBusyKeys, refreshConnections, setStatus]);

  const openRelation = useCallback(async (label: string) => {
    const generation = ++relationGenerationRef.current;
    const isCurrent = () => relationGenerationRef.current === generation;
    setSelectedRelation(label); setRelationBusy(true);
    setRelationTimeline([]); setRelationReview(null);
    try {
      const [timeline, review] = await Promise.all([
        api.relationTimeline(label),
        api.relationReview(label).catch(() => null)
      ]);
      if (!isCurrent()) return; // 4.4: a newer selection superseded this one -- discard silently.
      setRelationTimeline(timeline); setRelationReview(review);
    } catch (error) {
      if (!isCurrent()) return;
      setStatus(error instanceof Error ? error.message
        : copy("Could not read this relationship timeline yet.", "暂时读不到这段关系的时间线"));
    } finally { if (isCurrent()) setRelationBusy(false); }
  }, [copy, setStatus]);

  // CP-34: fetch both correction lists (user-scoped; LettersInbox filters to the selected
  // thread). Sets its own status so the UI can distinguish "none" from "could not load".
  const loadThreadCorrections = useCallback(async () => {
    setThreadCorrectionsStatus("loading");
    const [incoming, outgoing] = await Promise.all([
      api.incomingRelationCorrections(),
      api.outgoingRelationCorrections()
    ]);
    setThreadCorrections({ incoming, outgoing });
    setThreadCorrectionsStatus("success");
    return { incoming, outgoing };
  }, []);

  // After any decision the affected proposal moves lists (incoming → decided/outgoing view);
  // reload both rather than guessing the server's post-transition placement.
  const refreshCorrectionsQuietly = useCallback(() => {
    loadThreadCorrections().catch(() => setThreadCorrectionsStatus("error"));
  }, [loadThreadCorrections]);

  /** CP-34: propose a correction on a shared thread. Only a thread party may propose. */
  const proposeThreadCorrection = useCallback((threadId: number, correctionField: string,
      proposedValue: string, note?: string) =>
    correctionProposeBusyKeys.run(threadId, async () => {
      try {
        await api.proposeRelationCorrection({ threadId, correctionField, proposedValue, note });
        refreshCorrectionsQuietly();
        setStatus(copy("Correction proposed; it applies only if the other side accepts.",
          "纠错提案已送出；只有对方接受后才会生效。"));
      } catch (error) { setStatus(error instanceof Error ? error.message
        : copy("Could not send this correction yet.", "暂时无法送出这条纠错提案")); }
    }), [copy, correctionProposeBusyKeys, refreshCorrectionsQuietly, setStatus]);

  /** CP-34: counterpart accepts → APPLIED. Only the counterpart; the proposer cannot self-accept. */
  const acceptThreadCorrection = useCallback((proposalId: number) =>
    correctionBusyKeys.run(proposalId, async () => {
      try {
        await api.acceptRelationCorrection(proposalId);
        refreshCorrectionsQuietly();
        setStatus(copy("Correction accepted and applied to this exchange.", "已接受这条纠错，并应用到这段往来。"));
      } catch (error) { setStatus(error instanceof Error ? error.message
        : copy("Could not accept this correction yet.", "暂时无法接受这条纠错")); }
    }), [copy, correctionBusyKeys, refreshCorrectionsQuietly, setStatus]);

  /** CP-34: counterpart rejects with an optional reason → REJECTED (terminal). */
  const rejectThreadCorrection = useCallback((proposalId: number, reason?: string) =>
    correctionBusyKeys.run(proposalId, async () => {
      try {
        await api.rejectRelationCorrection(proposalId, reason);
        refreshCorrectionsQuietly();
        setStatus(copy("Correction declined; the original understanding stays.", "已婉拒这条纠错；原有理解保持不变。"));
      } catch (error) { setStatus(error instanceof Error ? error.message
        : copy("Could not decline this correction yet.", "暂时无法婉拒这条纠错")); }
    }), [copy, correctionBusyKeys, refreshCorrectionsQuietly, setStatus]);

  /** CP-34: proposer withdraws before any decision → WITHDRAWN (terminal). */
  const withdrawThreadCorrection = useCallback((proposalId: number) =>
    correctionBusyKeys.run(proposalId, async () => {
      try {
        await api.withdrawRelationCorrection(proposalId);
        refreshCorrectionsQuietly();
        setStatus(copy("Correction withdrawn.", "已撤回这条纠错提案。"));
      } catch (error) { setStatus(error instanceof Error ? error.message
        : copy("Could not withdraw this correction yet.", "暂时无法撤回这条纠错")); }
    }), [copy, correctionBusyKeys, refreshCorrectionsQuietly, setStatus]);

  const openThread = useCallback(async (threadId: number) => {
    const generation = ++threadGenerationRef.current;
    const isCurrent = () => threadGenerationRef.current === generation;
    setSelectedThreadId(threadId); setThreadLetters([]); setThreadLettersStatus("loading");
    setThreadCorrections({ incoming: [], outgoing: [] }); setThreadCorrectionsStatus("loading");
    try {
      // CP-34: corrections load alongside the letters but never fail the thread open — letters
      // still render if only this side request errors; the correction block reports its own state.
      const [letters, corrections] = await Promise.all([
        api.letterThreadLetters(threadId),
        loadThreadCorrections().catch(() => null)
      ]);
      if (!isCurrent()) return; // 4.4: a newer selection superseded this one -- discard silently.
      setThreadLetters(letters); setThreadLettersStatus("success");
      if (corrections === null) setThreadCorrectionsStatus("error");
    } catch (error) {
      if (!isCurrent()) return;
      setThreadLettersStatus("error");
      setStatus(error instanceof Error ? error.message
        : copy("Could not read this exchange yet.", "暂时读不到这段往来"));
    }
  }, [copy, loadThreadCorrections, setStatus]);

  const sendDraft = useCallback((id: number) => draftBusyKeys.run(id, async () => {
    try {
      await api.sendSlowLetter(id);
      await api.letterOutbox().then(setLetterOutbox).catch(() => undefined);
      setStatus(copy("This letter is on its way and will arrive at a slow-letter pace.", "这封信已经启程，会按慢信的节奏抵达。"));
    } catch (error) { setStatus(error instanceof Error ? error.message
      : copy("Could not send this draft yet.", "暂时无法寄出这封草稿")); }
  }), [copy, draftBusyKeys, setStatus]);

  const sendDirectLetter = useCallback(async (receiverUserId: number, title: string, body: string,
    delivery: DeliverySchedule = { deliveryPreset: "DEMO_3M", timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Shanghai" }) => {
    if (!receiverUserId || !title.trim() || !body.trim() || directLetterBusy) return false;
    setDirectLetterBusy(true);
    try {
      await sendComposedLetter({
        pending: directLetterDraftRef.current,
        onDraftCreated: next => { directLetterDraftRef.current = next; },
        createDraft: idempotencyKey => api.draftSlowLetterToUser(
          receiverUserId, title.trim(), body.trim(), idempotencyKey, delivery),
        sendDraft: (draftId, idempotencyKey) => api.sendSlowLetter(draftId, idempotencyKey)
      });
      directLetterDraftRef.current = null;
      await api.letterOutbox().then(setLetterOutbox).catch(() => undefined);
      setStatus(copy(
        "Your slow letter is on its way to this connection.",
        "慢信已直接寄给这位好友，正在按它自己的节奏前往。"));
      return true;
    } catch (error) {
      setStatus(error instanceof Error ? error.message
        : copy("The slow letter did not depart; your words are still here.", "慢信没有启程，你写的内容仍保留在这里。"));
      return false;
    } finally {
      setDirectLetterBusy(false);
    }
  }, [copy, directLetterBusy, setStatus]);

  const refreshLiveChats = useCallback(async () => {
    try {
      const [invites, sessions] = await Promise.all([
        api.liveChatInvites(), api.activeLiveChatSessions()
      ]);
      setLiveChatInvites(invites);
      setLiveChatSessions(sessions);
      const sessionId = sessions.some(session => session.id === selectedLiveChatSessionId)
        ? selectedLiveChatSessionId
        : sessions[0]?.id ?? null;
      setSelectedLiveChatSessionId(sessionId);
      if (sessionId === null) {
        setLiveChatMessages([]);
        setLiveChatStatus("success");
        return;
      }
      const messages = await api.liveChatMessages(sessionId);
      setLiveChatMessages(messages);
      setLiveChatStatus("success");
    } catch {
      setLiveChatStatus("error");
    }
  }, [selectedLiveChatSessionId]);

  const inviteLiveChat = useCallback((targetUserId: number, durationMinutes: 10 | 15) =>
    liveChatInviteBusyKeys.run(targetUserId, async () => {
      try {
        await api.inviteLiveChat(targetUserId, durationMinutes);
        await refreshLiveChats();
        setStatus(copy(
          "Invitation sent. The conversation opens only if they accept.",
          "「此刻聊聊」邀请已送达；只有对方接受后，实时会话才会打开。"));
      } catch (error) {
        setStatus(error instanceof Error ? error.message
          : copy("Could not send this invitation yet.", "暂时无法发出「此刻聊聊」邀请。"));
      }
    }), [copy, liveChatInviteBusyKeys, refreshLiveChats, setStatus]);

  const respondLiveChatInvite = useCallback((inviteId: number, decision: "accept" | "decline") =>
    liveChatDecisionBusyKeys.run(inviteId, async () => {
      try {
        const session = await api.respondLiveChatInvite(inviteId, decision);
        if (session) setSelectedLiveChatSessionId(session.id);
        await refreshLiveChats();
        setStatus(decision === "accept"
          ? copy("You're both here. This conversation has a gentle time boundary.", "你们都在此刻，会话已经开启，也有清楚的时间边界。")
          : copy("Invitation declined.", "已婉拒这次即时会面。"));
      } catch (error) {
        setStatus(error instanceof Error ? error.message
          : copy("Could not respond to this invitation.", "暂时无法回应这次邀请。"));
      }
    }), [copy, liveChatDecisionBusyKeys, refreshLiveChats, setStatus]);

  const selectLiveChatSession = useCallback(async (sessionId: number) => {
    setSelectedLiveChatSessionId(sessionId);
    setLiveChatStatus("loading");
    try {
      setLiveChatMessages(await api.liveChatMessages(sessionId));
      setLiveChatStatus("success");
    } catch {
      setLiveChatStatus("error");
    }
  }, []);

  const sendLiveChatMessage = useCallback((sessionId: number, messageBody: string) =>
    liveChatMessageBusyKeys.run(sessionId, async () => {
      const body = messageBody.trim();
      if (!body) return false;
      try {
        const sent = await api.sendLiveChatMessage(sessionId, body);
        setLiveChatMessages(current => current.some(message => message.id === sent.id)
          ? current : [...current, sent]);
        return true;
      } catch (error) {
        setStatus(error instanceof Error ? error.message
          : copy("Could not send this message.", "这句话暂时没有送达，请再试一次。"));
        return false;
      }
    }), [copy, liveChatMessageBusyKeys, setStatus]);

  const endLiveChatSession = useCallback((sessionId: number) =>
    liveChatEndBusyKeys.run(sessionId, async () => {
      try {
        await api.endLiveChatSession(sessionId);
        await refreshLiveChats();
        setStatus(copy(
          "This moment ended quietly. You can continue with a slow letter.",
          "这次相聚已经安静结束；想继续的话，可以写成一封慢信。"));
      } catch (error) {
        setStatus(error instanceof Error ? error.message
          : copy("Could not end this conversation.", "暂时无法结束这次会话。"));
      }
    }), [copy, liveChatEndBusyKeys, refreshLiveChats, setStatus]);

  // Accepts any letter-shaped row the views hand it (inbox SlowLetter or the outbox
  // projection) -- only the id reaches the API.
  const actOnLetter = useCallback((letter: { id: number }, action: "read" | "decline" | "block" | "archive") =>
    letterActionBusyKeys.run(letter.id, async () => {
      try {
        const updated = await api.transitionLetter(letter.id, action);
        setLetterInbox(rows => rows.map(row => row.id === updated.id ? updated : row));
        await refreshLetters();
        setStatus(action === "block"
          ? copy("Sender blocked; future slow letters will also be stopped.", "已屏蔽来信者；后续慢信也会被阻断。")
          : copy("Slow-letter boundary updated.", "慢信边界已更新。 "));
      } catch (error) { setStatus(error instanceof Error ? error.message
        : copy("Could not update this letter yet.", "暂时无法更新这封信")); }
    }), [copy, letterActionBusyKeys, setStatus]);

  const reportLetter = useCallback((letter: SlowLetter) => letterActionBusyKeys.run(letter.id, async () => {
    try {
      await api.reportLetter(letter.id, copy(
        "Recipient reported this slow letter from the Aurora interface.",
        "收件人从 Aurora 界面举报慢信"));
      setStatus(copy(
        "Report submitted. The letter is not automatically exposed; review remains restricted.",
        "已提交举报。举报不会自动公开信件内容，交由受限审核处理。 "));
    } catch (error) { setStatus(error instanceof Error ? error.message
      : copy("Could not submit this report yet.", "暂时无法提交举报")); }
  }), [copy, letterActionBusyKeys, setStatus]);

  // CP-33: the RECIPIENT's per-letter read-receipt choice. NEVER (default) keeps the read
  // state invisible to the sender. After a flip the three letter projections refresh as one
  // snapshot so the sender-facing mask (outbox/thread) and the inbox's policy stay coherent.
  const setReceiptPolicy = useCallback((letter: SlowLetter, policy: "ALWAYS" | "NEVER") =>
    receiptPolicyBusyKeys.run(letter.id, async () => {
      try {
        await api.setLetterReceiptPolicy(letter.id, policy);
        await refreshLetters();
        setStatus(policy === "NEVER"
          ? copy("The sender will not be told when this letter is read.", "对方不会收到这封信的已读回执。")
          : copy("The sender will be told when this letter is read.", "对方会收到这封信的已读回执。"));
      } catch (error) { setStatus(error instanceof Error ? error.message
        : copy("Could not update the read-receipt choice yet.", "暂时无法更新已读回执选择")); }
    }), [copy, receiptPolicyBusyKeys, refreshLetters, setStatus]);

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
      setLetterVoiceError(error instanceof Error ? error.message
        : copy("Could not read this letter aloud yet.", "暂时无法朗读这封信"));
    }
  }), [copy, letterVoiceBusyKeys]);

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
        createDraft: idempotencyKey => api.replyWithSlowLetter(
          letter.id, copy(`Reply: ${letter.title}`, `回复：${letter.title}`), body, idempotencyKey),
        sendDraft: (draftId, idempotencyKey) => api.sendSlowLetter(draftId, idempotencyKey)
      });
      const { [letter.id]: _discard, ...rest } = replyDraftsRef.current;
      replyDraftsRef.current = rest; // sent successfully -- clear so the next reply starts fresh.
      const updated = letter.status === "READ" ? await api.transitionLetter(letter.id, "reply") : letter;
      setLetterInbox(rows => rows.map(row => row.id === updated.id ? updated : row));
      void api.letterOutbox().then(setLetterOutbox).catch(() => undefined);
      setReplyDrafts(drafts => ({ ...drafts, [letter.id]: "" }));
      setStatus(copy(
        "Your slow-letter reply is on its way. It still travels through time instead of becoming instant chat.",
        "回复慢信已启程。它仍会经过时间，而不是变成即时聊天。 "));
    } catch (error) {
      // replyDraftsRef.current[letter.id] is intentionally left set (if a draft was created) so
      // retrying this same letter's reply reuses the same draft rather than creating another.
      setStatus(error instanceof Error ? error.message
        : copy("The slow-letter reply did not depart.", "回复慢信没有启程"));
    }
    finally { setReplyBusyId(null); }
  }, [copy, replyDrafts, setStatus]);

  const updateReplyDraft = useCallback((letterId: number, value: string) => {
    setReplyDrafts(drafts => ({ ...drafts, [letterId]: value }));
  }, []);

  const requestConnection = useCallback((letter: SlowLetter) => letterConnectionBusyKeys.run(letter.id, async () => {
    try {
      await api.requestConnectionFromLetter(letter.id);
      await refreshConnections();
      setStatus(copy(
        "Connection invitation sent. You become a real connection only after the other person explicitly accepts.",
        "连接邀请已发出。只有对方明确接受后，双方才会成为真实连接。 "));
    } catch (error) { setStatus(error instanceof Error ? error.message
      : copy("Could not send the connection invitation yet.", "暂时无法发出连接邀请")); }
  }), [copy, letterConnectionBusyKeys, refreshConnections, setStatus]);

  const decideConnection = useCallback((id: number, decision: "accept" | "decline") => connectionDecisionBusyKeys.run(id, async () => {
    try {
      await api.decideConnection(id, decision);
      await refreshConnections();
      setStatus(decision === "accept"
        ? copy("Both people have agreed to this connection.", "双方都已同意这段连接。")
        : copy("Declined; no relationship was created automatically.", "已婉拒；不会自动建立任何关系。 "));
    } catch (error) { setStatus(error instanceof Error ? error.message
      : copy("Could not process this connection invitation yet.", "暂时无法处理连接邀请")); }
  }), [connectionDecisionBusyKeys, copy, refreshConnections, setStatus]);

  const leaveConnection = useCallback((id: number) => connectionLeaveBusyKeys.run(id, async () => {
    try {
      await api.leaveConnection(id);
      await refreshConnections();
      setStatus(copy("You left this connection.", "已退出这段连接。 "));
    } catch (error) { setStatus(error instanceof Error ? error.message
      : copy("Could not leave this connection yet.", "暂时无法退出连接")); }
  }), [connectionLeaveBusyKeys, copy, refreshConnections, setStatus]);

  const createGroup = useCallback(async (groupName: string) => {
    const name = groupName.trim();
    if (!name) return;
    setGroupCreateBusy(true);
    try {
      const created = await api.createGroup(name);
      setGroups(current => [created, ...current]);
      setStatus(copy("Group created.", "群组已创建。"));
    } catch (error) { setStatus(error instanceof Error ? error.message
      : copy("Could not create this group yet.", "暂时无法创建群组")); }
    finally { setGroupCreateBusy(false); }
  }, [copy, setStatus]);

  const openGroup = useCallback(async (groupId: number) => {
    const generation = ++groupGenerationRef.current;
    const isCurrent = () => groupGenerationRef.current === generation;
    setSelectedGroupId(groupId);
    setGroupMembers([]); setGroupMembersStatus("loading");
    setGroupMessages([]); setGroupMessagesStatus("loading");
    setGroupMessageError(null);
    try {
      const [members, messages] = await Promise.all([
        api.groupMembers(groupId),
        api.groupMessages(groupId)
      ]);
      if (!isCurrent()) return; // 4.4: a newer selection superseded this one -- discard silently.
      setGroupMembers(members); setGroupMembersStatus("success");
      setGroupMessages(messages); setGroupMessagesStatus("success");
    } catch (error) {
      if (!isCurrent()) return;
      setGroupMembersStatus("error");
      setGroupMessagesStatus("error");
      setStatus(error instanceof Error ? error.message
        : copy("Could not open this group yet.", "暂时无法打开这个群组"));
    }
  }, [copy, setStatus]);

  const joinClassroomGroup = useCallback(async () => {
    try {
      const group = await api.joinClassroomGroup();
      await loadGroups();
      await openGroup(group.id);
      setStatus(copy(
        "You joined the live classroom group. Everyone here can now read and reply.",
        "你已加入现场共同小组，现在全场都可以看见并回复消息。"));
      return true;
    } catch (error) {
      setStatus(error instanceof Error ? error.message
        : copy("Could not join the classroom group yet.", "暂时没能加入现场共同小组。"));
      return false;
    }
  }, [copy, loadGroups, openGroup, setStatus]);

  const refreshSelectedGroupContext = useCallback(async () => {
    if (selectedGroupId === null) return;
    try {
      const [members, messages] = await Promise.all([
        api.groupMembers(selectedGroupId),
        api.groupMessages(selectedGroupId)
      ]);
      setGroupMembers(members);
      setGroupMembersStatus("success");
      setGroupMessages(messages);
      setGroupMessagesStatus("success");
      setGroupMessageError(null);
    } catch {
      // Background sync is intentionally quiet; an explicit open/send still surfaces errors.
    }
  }, [selectedGroupId]);

  const sendGroupMessage = useCallback((groupId: number, messageBody: string) =>
    groupMessageBusyKeys.run(groupId, async () => {
      const body = messageBody.trim();
      if (!body) return false;
      try {
        const sent = await api.sendGroupMessage(groupId, body);
        setGroupMessages(current => current.some(message => message.id === sent.id)
          ? current
          : [...current, sent]);
        setGroupMessagesStatus("success");
        setGroupMessageError(null);
        return true;
      } catch (error) {
        // CP-35: the server's own message (e.g. the mute 403 naming the remaining minutes) is
        // shown verbatim beside the composer — never reworded or softened client-side.
        const message = error instanceof Error ? error.message
          : copy("Could not send this group message yet.", "暂时无法发送这条群消息");
        setGroupMessageError(message);
        setStatus(message);
        return false;
      }
    }), [copy, groupMessageBusyKeys, setStatus]);

  const inviteToGroup = useCallback((groupId: number, userId: number) => groupInviteBusyKeys.run(groupId, async () => {
    try {
      await api.inviteToGroup(groupId, userId);
      setStatus(copy("Invitation sent; waiting for them to accept.", "邀请已发出，等待对方接受。"));
    } catch (error) { setStatus(error instanceof Error ? error.message
      : copy("Could not invite this friend yet.", "暂时无法邀请这位朋友")); }
  }), [copy, groupInviteBusyKeys, setStatus]);

  const respondToGroupInvite = useCallback((memberId: number, decision: "accept" | "decline") =>
    groupInviteDecisionBusyKeys.run(memberId, async () => {
      try {
        await api.respondToGroupInvite(memberId, decision);
        setGroupInvites(current => current.filter(invite => invite.memberId !== memberId));
        if (decision === "accept") await loadGroups();
        setStatus(decision === "accept"
          ? copy("Joined the group.", "已加入群组。")
          : copy("Group invitation declined.", "已婉拒这个群组邀请。 "));
      } catch (error) { setStatus(error instanceof Error ? error.message
        : copy("Could not process this invitation yet.", "暂时无法处理这个邀请")); }
    }), [copy, groupInviteDecisionBusyKeys, loadGroups, setStatus]);

  const leaveGroup = useCallback((groupId: number) => groupLeaveBusyKeys.run(groupId, async () => {
    try {
      await api.leaveGroup(groupId);
      setGroups(current => current.filter(group => group.id !== groupId));
      if (selectedGroupId === groupId) {
        setSelectedGroupId(null); setGroupMembers([]); setGroupMessages([]);
      }
      setStatus(copy("You left the group.", "已退出这个群组。 "));
    } catch (error) { setStatus(error instanceof Error ? error.message
      : copy("Could not leave this group yet.", "暂时无法退出这个群组")); }
  }), [copy, groupLeaveBusyKeys, selectedGroupId, setStatus]);

  // ---- CP-35 group governance (host-only server-side; the backend refuses non-hosts). ----

  /** Mute a member. durationMinutes null = muted until the host manually lifts it. */
  const muteGroupMember = useCallback((groupId: number, userId: number, durationMinutes: number | null) =>
    groupGovernanceBusyKeys.run(userId, async () => {
      try {
        await api.muteGroupMember(groupId, userId, durationMinutes);
        await refreshSelectedGroupContext();
        setStatus(durationMinutes == null
          ? copy("Muted until you lift it manually.", "已禁言，需你手动解除。")
          : copy(`Muted for ${durationMinutes} minutes.`, `已禁言 ${durationMinutes} 分钟。`));
      } catch (error) { setStatus(error instanceof Error ? error.message
        : copy("Could not mute this member yet.", "暂时无法禁言这位成员")); }
    }), [copy, groupGovernanceBusyKeys, refreshSelectedGroupContext, setStatus]);

  const unmuteGroupMember = useCallback((groupId: number, userId: number) =>
    groupGovernanceBusyKeys.run(userId, async () => {
      try {
        await api.unmuteGroupMember(groupId, userId);
        await refreshSelectedGroupContext();
        setStatus(copy("This member can speak again.", "这位成员已恢复发言。"));
      } catch (error) { setStatus(error instanceof Error ? error.message
        : copy("Could not lift this mute yet.", "暂时无法解除禁言")); }
    }), [copy, groupGovernanceBusyKeys, refreshSelectedGroupContext, setStatus]);

  /** Transfer ownership — the current host becomes an ordinary member. */
  const transferGroupOwnership = useCallback((groupId: number, userId: number) =>
    groupGovernanceBusyKeys.run(userId, async () => {
      try {
        await api.transferGroupOwnership(groupId, userId);
        await loadGroups();
        await refreshSelectedGroupContext();
        setStatus(copy("Ownership transferred.", "群主已移交。"));
      } catch (error) { setStatus(error instanceof Error ? error.message
        : copy("Could not transfer ownership yet.", "暂时无法移交群主")); }
    }), [copy, groupGovernanceBusyKeys, loadGroups, refreshSelectedGroupContext, setStatus]);

  /** Dissolve the group for everyone (terminal) — the component confirms first. */
  const dissolveGroup = useCallback((groupId: number) =>
    groupDissolveBusyKeys.run(groupId, async () => {
      try {
        await api.dissolveGroup(groupId);
        setGroups(current => current.filter(group => group.id !== groupId));
        if (selectedGroupId === groupId) {
          setSelectedGroupId(null); setGroupMembers([]); setGroupMessages([]); setGroupMessageError(null);
        }
        setStatus(copy("The group has been dissolved.", "这个群组已解散。"));
      } catch (error) { setStatus(error instanceof Error ? error.message
        : copy("Could not dissolve this group yet.", "暂时无法解散这个群组")); }
    }), [copy, groupDissolveBusyKeys, selectedGroupId, setStatus]);

  return {
    connectionRequests, friends, people, isPersonBusy: peopleBusyKeys.isBusy,
    relations, selectedRelation, relationTimeline, relationReview, relationBusy,
    letterInbox, letterOutbox, letterThreads, selectedThreadId, threadLetters, threadLettersStatus,
    lettersRefreshing,
    threadCorrections, threadCorrectionsStatus,
    isCorrectionBusy: correctionBusyKeys.isBusy, isCorrectionProposeBusy: correctionProposeBusyKeys.isBusy,
    isDraftBusy: draftBusyKeys.isBusy, replyBusyId, replyDrafts,
    isLetterActionBusy: letterActionBusyKeys.isBusy,
    setReceiptPolicy, isReceiptPolicyBusy: receiptPolicyBusyKeys.isBusy,
    isConnectionDecisionBusy: connectionDecisionBusyKeys.isBusy,
    isConnectionLeaveBusy: connectionLeaveBusyKeys.isBusy,
    isLetterConnectionBusy: letterConnectionBusyKeys.isBusy,
    letterVoiceLetterId, letterVoiceAudio, letterVoiceError, isLetterVoiceBusy: letterVoiceBusyKeys.isBusy,
    groups, groupInvites, selectedGroupId, groupMembers, groupMembersStatus,
    groupMessages, groupMessagesStatus, directLetterBusy,
    liveChatInvites, liveChatSessions, selectedLiveChatSessionId, liveChatMessages, liveChatStatus,
    groupCreateBusy, isGroupInviteBusy: groupInviteBusyKeys.isBusy,
    isGroupInviteDecisionBusy: groupInviteDecisionBusyKeys.isBusy, isGroupLeaveBusy: groupLeaveBusyKeys.isBusy,
    isGroupMessageBusy: groupMessageBusyKeys.isBusy,
    groupMessageError, isGroupGovernanceBusy: groupGovernanceBusyKeys.isBusy,
    isGroupDissolveBusy: groupDissolveBusyKeys.isBusy,
    isLiveChatInviteBusy: liveChatInviteBusyKeys.isBusy,
    isLiveChatDecisionBusy: liveChatDecisionBusyKeys.isBusy,
    isLiveChatMessageBusy: liveChatMessageBusyKeys.isBusy,
    isLiveChatEndBusy: liveChatEndBusyKeys.isBusy,
    loadLetterInbox, loadConnectionRequests, loadFriends, loadLetterOutbox, loadPeople, searchPeople, loadRelations, loadLetterThreads,
    loadGroups, loadGroupInvites,
    refreshConnections, refreshGroups, refreshSelectedGroupContext, refreshLetters, refreshLiveChats,
    requestPersonConnection, openRelation, openThread, sendDraft, sendDirectLetter,
    actOnLetter, reportLetter, replyWithLetter, updateReplyDraft, playLetterVoice,
    requestConnection, decideConnection, leaveConnection,
    createGroup, joinClassroomGroup, openGroup, inviteToGroup, respondToGroupInvite, leaveGroup, sendGroupMessage,
    proposeThreadCorrection, acceptThreadCorrection, rejectThreadCorrection, withdrawThreadCorrection,
    muteGroupMember, unmuteGroupMember, transferGroupOwnership, dissolveGroup,
    inviteLiveChat, respondLiveChatInvite, selectLiveChatSession, sendLiveChatMessage, endLiveChatSession
  };
}
