# Milestone M4: R4 Frontend & E2E Fault Tolerance Audit Report

## 1. Observation

During the read-only audit of the React 19 / Vite frontend application (`web/src/`), the following exact code patterns, control flows, and structural issues were identified across SSE long connections, React state management, custom hooks, component trees, and UI/UX edge cases.

### Finding 1: Un-cancellable Connection Recovery Loop Corrupts Concurrent Streaming State
- **Location**: `web/src/hooks/useAuroraSession.ts`, lines 160-199 (`recover`), lines 346-368 (`send`), lines 240-251 (`stop`).
- **Verbatim Code**:
  ```ts
  // web/src/hooks/useAuroraSession.ts:160-199
  const recover = useCallback(async (turnId: number, sid: number) => {
    if (reconnectingRef.current) return;
    reconnectingRef.current = true;
    setStatus(t.reconnecting);
    try {
      lastEventIdRef.current = await replayTurnEvents(turnId, lastEventIdRef.current, event => { ... });
      for (let attempt = 0; attempt < 40; attempt++) {
        const timeline = await api.timeline(turnId);
        const recovered = timeline.bubbles.filter(...).map(...);
        setMessages(current => [
          ...current.filter(m => !m.key.startsWith(`live-${turnId}-`) && !m.key.startsWith(`replay-${turnId}-`)),
          ...recovered
        ]);
        if (terminal.has(timeline.turn.status)) {
          await replaceFromHistory(sid);
          setStatus(...);
          finishTurn();
          return;
        }
        await new Promise(resolve => setTimeout(resolve, 500));
      }
    } finally {
      reconnectingRef.current = false;
    }
  }, [finishTurn, replaceFromHistory, setStatus, t]);
  ```
- **Observation**: `recover()` enters a 40-iteration loop (taking up to 20 seconds polling `api.timeline(turnId)`). It holds no reference to an `AbortController` signal and does not inspect `activeTurnRef.current`. When `recover()` finishes or encounters terminal status for `turnId`, it unconditionally executes `finishTurn()`, which resets `activeTurnRef.current = null`, `setActiveTurnId(null)`, `bubbleKeyRef.current = null`, and sets `runtimeSignal.stage` to `"idle"`.

### Finding 2: Unhandled Stream EOF Without Terminal Marker Causes Infinite UI Streaming Hang
- **Location**: `web/src/api.ts`, lines 710-717 (`streamAurora`); `web/src/hooks/useAuroraSession.ts`, lines 361-368 (`send`).
- **Verbatim Code**:
  ```ts
  // web/src/api.ts:710-717
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    for (const frame of decoder.push(textDecoder.decode(value, { stream: true }))) {
      const event = toTypedEvent(frame);
      if (event) onEvent(event);
    }
  }
  ```
- **Observation**: `streamAurora` loops until `reader.read()` returns `{ done: true }`. If the SSE connection terminates cleanly (HTTP 200 EOF or reverse-proxy timeout) without receiving a terminal SSE event (`turn.completed`, `done`, `turn.interrupted`, `safety`, `error`), `streamAurora` returns cleanly without throwing an exception. In `useAuroraSession.ts`, `send()` finishes awaiting `streamAurora` without executing `finishTurn()`. `activeTurnRef.current` remains non-null and `runtimeSignal.stage` stays `"speaking"`.

### Finding 3: EventSource Permanent Reconnection Loop on Auth Failure
- **Location**: `web/src/api.ts`, lines 317-332 (`subscribeProactive`), lines 335-368 (`subscribeProactiveBearer`).
- **Verbatim Code**:
  ```ts
  // web/src/api.ts:317-318
  const source = new EventSource(apiUrl("/api/proactive/stream"), { withCredentials: true });
  source.onopen = () => onConnectionChange?.(true);
  source.onerror = () => onConnectionChange?.(false);
  ```
- **Observation**: Standard browser `EventSource` automatically retries when an HTTP error or disconnect occurs. If the session expires (HTTP 401/403), `source.onerror` calls `onConnectionChange(false)`, but does not invoke `source.close()`. In mobile Bearer mode (`subscribeProactiveBearer`), an auth error in `accessTokenProvider()` triggers an infinite `while(!controller.signal.aborted)` loop with a 2-second sleep, repeatedly failing and logging errors to the console.

### Finding 4: Async State Race Condition in Relation, Thread & Group Loaders
- **Location**: `web/src/hooks/useConnectionsAndLetters.ts`, lines 95-106 (`openRelation`), lines 108-112 (`openThread`), lines 196-200 (`openGroup`).
- **Verbatim Code**:
  ```ts
  // web/src/hooks/useConnectionsAndLetters.ts:95-105
  const openRelation = useCallback(async (label: string) => {
    setSelectedRelation(label); setRelationBusy(true);
    setRelationTimeline([]); setRelationHealth(null);
    try {
      const [timeline, health] = await Promise.all([
        api.relationTimeline(label),
        api.relationHealth(label).catch(() => null)
      ]);
      setRelationTimeline(timeline); setRelationHealth(health);
    } catch (error) { setStatus(...); }
    finally { setRelationBusy(false); }
  }, [setStatus]);
  ```
- **Observation**: When a user quickly clicks Relation A followed by Relation B, two concurrent requests are issued. If Relation A's response resolves after Relation B's response, `setRelationTimeline` and `setRelationHealth` overwrite state with Relation A's data while `selectedRelation` is set to Relation B. No request versioning, cancellation signal, or guard checks if `selectedRelation` matches `label` upon resolution.

### Finding 5: Double Draft Creation on Failed Slow Letter Transmission
- **Location**: `web/src/AuroraApp.tsx`, lines 820-831 (`sendLetterToMatch`); `web/src/hooks/useConnectionsAndLetters.ts`, lines 140-154 (`replyWithLetter`).
- **Verbatim Code**:
  ```ts
  // web/src/AuroraApp.tsx:820-831
  const sendLetterToMatch = async () => {
    if (!visitorMatch || !letterTitle.trim() || !letterBody.trim()) return;
    setVisitorBusy(true);
    try {
      const draft = await api.draftSlowLetter(visitorMatch.capsule.id, letterTitle.trim(), letterBody.trim());
      const sent = await api.sendSlowLetter(draft.id);
      setSentLetter(sent);
      ...
    } catch (error) { setStatus(...); }
    finally { setVisitorBusy(false); }
  };
  ```
- **Observation**: `sendLetterToMatch` calls `api.draftSlowLetter` followed by `api.sendSlowLetter`. If `draftSlowLetter` succeeds but `sendSlowLetter` fails (e.g., transient network failure), the catch block runs and leaves the draft persisted in the server database. Retrying letter submission creates a second draft letter via `api.draftSlowLetter`, accumulating orphaned duplicate drafts in the outbox.

### Finding 6: Unmounted Component Async State Updates & Promise Leak in Voice Recorder
- **Location**: `web/src/components/AuroraConversation.tsx`, lines 69-84 (`finishRecording`).
- **Verbatim Code**:
  ```ts
  // web/src/components/AuroraConversation.tsx:69-84
  const finishRecording = async (cancel = false) => {
    const recorder = recorderRef.current;
    ...
    const blob = await recorder.stop(cancel);
    if (!blob || cancel || !onTranscribe) return;
    setTranscribing(true);
    try {
      const text = await onTranscribe(blob);
      if (text) onDraftChange((draftRef.current ? `${draftRef.current} ` : "") + text);
    } catch { ... }
    finally { setTranscribing(false); }
  };
  useEffect(() => () => { void finishRecording(true); }, []);
  ```
- **Observation**: Unmounting `AuroraConversation` while recording or transcribing triggers `finishRecording(true)`. However, if `onTranscribe` is already in flight when unmount occurs, `setTranscribing(false)` and `onDraftChange(...)` execute post-unmount, causing React unmounted state update warnings and dangling promise references.

### Finding 7: Zero React Error Boundaries in Entire Application Tree
- **Location**: `web/src/main.tsx`, lines 34-46; `web/src/AuroraApp.tsx`.
- **Verbatim Code**:
  ```tsx
  // web/src/main.tsx:34-46
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <BrowserRouter basename={nativeShell ? "/" : "/app/aurora"}>
        <AuroraApp />
      </BrowserRouter>
      ...
    </StrictMode>
  );
  ```
- **Observation**: A search for `ErrorBoundary`, `componentDidCatch`, or `getDerivedStateFromError` across `web/src/` returns 0 results. No Error Boundary component exists in the application. Any unhandled runtime exception during rendering (e.g. malformed data in state, date parsing error, or null dereference) crashes the entire React component tree to a blank white screen.

### Finding 8: Missing Disable-on-Submit & Double-Click Risk in Social and Letter Action Buttons
- **Location**: `web/src/components/LettersInbox.tsx`, lines 96-101 (`markRead`, `decline`, `willKnow`, `block`, `report`), lines 140-145 (`accept`, `declineConn`, `leave`); `web/src/components/ResonanceNetwork.tsx`, lines 95-96 (`reportSession`, `blockSession`).
- **Verbatim Code**:
  ```tsx
  // web/src/components/LettersInbox.tsx:96-100
  {letter.status === "DELIVERED" && <button onClick={() => onActOnLetter(letter, "read")}>{t.markRead}</button>}
  {declinable.has(letter.status) && <button onClick={() => onActOnLetter(letter, "decline")}>{t.decline}</button>}
  {repliable.has(letter.status) && <button onClick={() => onRequestConnection(letter)}>{t.willKnow}</button>}
  {letter.status !== "BLOCKED" && <button onClick={() => onActOnLetter(letter, "block")}>{t.block}</button>}
  <button onClick={() => onReportLetter(letter)}>{t.report}</button>
  ```
- **Observation**: These buttons use standard HTML `<button>` elements without `disabled` or `busy` props linked to async operation state. Double-clicking any of these buttons dispatches multiple concurrent POST calls to backend endpoints.

### Finding 9: Infinite Loading Display Bug in Thread Letters Inbox
- **Location**: `web/src/components/LettersInbox.tsx`, line 133.
- **Verbatim Code**:
  ```tsx
  // web/src/components/LettersInbox.tsx:133
  : threadLetters.length === 0 ? <div className="network-empty">{t.threadLoading}</div>
  ```
- **Observation**: In the thread view tab of `LettersInbox`, if a selected thread contains 0 letters (or after letters are archived), `threadLetters.length === 0` evaluates to true, rendering the message `{t.threadLoading}` ("正在读取这段往来…") permanently instead of an empty thread message.

### Finding 10: Premature Form Modal Closure on Password Change / Account Deletion Failures
- **Location**: `web/src/components/AccountSettings.tsx`, lines 141-154 (`submitPassword`, `submitDelete`).
- **Verbatim Code**:
  ```ts
  // web/src/components/AccountSettings.tsx:141-147
  const submitPassword = () => {
    if (!oldPassword || !newPassword) { setPasswordError(t.errFill); return; }
    if (newPassword.length < 8) { setPasswordError(t.errLen); return; }
    if (newPassword !== newPassword2) { setPasswordError(t.errMismatch); return; }
    onChangePassword(oldPassword, newPassword);
    closePasswordForm();
  };
  ```
- **Observation**: `submitPassword` and `submitDelete` invoke `onChangePassword` / `onDeleteAccount` and immediately call `closePasswordForm()` / `closeDeleteForm()`, clearing form inputs and hiding the form before the async network request completes. If the backend request fails (e.g. invalid current password), the form is already closed and inputs cleared, frustrating user recovery.

### Finding 11: Text Overflow Risk in Memory Cards, Letters, and Capsule Intros
- **Location**: `web/src/components/LettersInbox.tsx` (lines 91, 108, 119), `web/src/components/CapsuleWorkbench.tsx` (lines 228, 236, 255), `web/src/components/MemoryStarfield.tsx` (lines 103, 112).
- **Observation**: Text paragraphs (`<p>`) displaying memory card summaries, slow letter bodies, and capsule introductions do not consistently declare `overflow-wrap: break-word` or `word-break: break-word` in CSS classes. Extremely long words, URLs, or unformatted text strings can overflow parent container boundaries.

---

## 2. Logic Chain

1. **SSE & Connection Recovery (Findings 1, 2, 3)**:
   - **Step 1.1**: Observation 1 shows `recover()` running an un-cancellable 40-iteration loop polling `api.timeline(turnId)`. When it completes, it calls `finishTurn()`.
   - **Step 1.2**: If Turn A experiences a network drop and enters `recover(turnA)`, the user can start Turn B or click "Stop". `send()` or `stop()` sets `activeTurnRef.current = turnB`.
   - **Step 1.3**: Because `recover(turnA)` does not check if `activeTurnRef.current` has changed, its background execution eventually hits terminal status for `turnA` and executes `finishTurn()`.
   - **Step 1.4**: `finishTurn()` clears `activeTurnRef.current` (wiping `turnB`), clears `bubbleKeyRef`, and sets `runtimeSignal.stage` to `"idle"`.
   - **Step 1.5**: Therefore, concurrent recovery of an interrupted SSE turn causes silent destruction of any newly started streaming turn.
   - **Step 1.6**: Observation 2 shows `streamAurora` returning without error on stream EOF. If no terminal event was received before EOF, `finishTurn()` is never invoked, leaving `activeTurnRef.current` populated and the UI permanently stuck in `"speaking"` stage.
   - **Step 1.7**: Observation 3 shows `EventSource` retrying indefinitely on auth failure (401/403) without closing the connection, causing redundant background network requests.

2. **React State & Memory Leaks (Findings 4, 5, 6)**:
   - **Step 2.1**: Observation 4 shows `openRelation`, `openThread`, and `openGroup` updating state upon promise resolution without verifying that the resolved entity matches the currently selected selection.
   - **Step 2.2**: Fast user clicks generate concurrent requests where out-of-order resolution causes stale data from a previous selection to overwrite the active selection's state.
   - **Step 2.3**: Observation 5 demonstrates that `sendLetterToMatch` creates a draft before sending. Failure during sending leaves a persisted draft on the server. Subsequent submission attempts call `draftSlowLetter` again, creating redundant orphaned drafts.
   - **Step 2.4**: Observation 6 shows `finishRecording` scheduling async callbacks post-unmount, leading to React unmounted state update warnings and memory leaks.

3. **Exception Boundaries & Edge-Case UX (Findings 7, 8, 9, 10, 11)**:
   - **Step 3.1**: Observation 7 confirms zero Error Boundaries exist in the application. Any unhandled JS exception during render unmounts the entire app to a blank screen.
   - **Step 3.2**: Observation 8 confirms multiple mutation buttons lack `disabled`/`busy` guards, allowing rapid double-clicks to trigger duplicate API requests.
   - **Step 3.3**: Observation 9 reveals `LettersInbox.tsx` checking `threadLetters.length === 0` to display a loading indicator, making empty threads display "Loading..." indefinitely.
   - **Step 3.4**: Observation 10 proves `AccountSettings.tsx` closes forms synchronously before async password/delete operations complete, destroying user input on error.
   - **Step 3.5**: Observation 11 shows missing word-break/overflow-wrap styling on user-generated text paragraphs, leading to UI overflow on long strings.

---

## 3. Caveats

- **No Source Code Modifications Made**: In compliance with Explorer subagent guidelines, all findings are derived from read-only inspection. No changes were made to source files under `web/src/` or `src/`.
- **Backend Behavior Assumption**: The audit assumes standard Spring Boot backend behavior for REST & SSE endpoints as specified in OpenAPI schema (`inner-cosmos-v1.d.ts`) and `api.ts`.
- **Network Environment**: Mobile OIDC and Capacitor native bridge paths were audited statically via code inspection; device hardware testing (iOS APNs / Android FCM native push) requires physical devices.

---

## 4. Conclusion

The React 19 / Vite frontend demonstrates strong architecture and clean domain decomposition (`useAuroraSession`, `useConnectionsAndLetters`). However, critical fault tolerance, race condition, and error handling gaps exist:

1. **High Impact**:
   - `useAuroraSession` un-cancellable recovery loop (`recover()`) corrupts concurrent streaming turns upon network glitches.
   - SSE streams terminating without explicit terminal events cause permanent UI hanging in `"speaking"` state.
   - Complete absence of React Error Boundaries leaves the app vulnerable to blank white screen crashes on unexpected exceptions.

2. **Medium Impact**:
   - Async state race conditions in `useConnectionsAndLetters` (`openRelation`, `openThread`, `openGroup`) cause out-of-order data corruption.
   - Draft creation logic in `sendLetterToMatch` creates duplicate orphaned drafts on transient network send failures.
   - Missing `disabled={busy}` state on social action buttons (`LettersInbox`, `ResonanceNetwork`) permits double-click duplicate API requests.
   - Synchronous form modal closure in `AccountSettings` wipes user inputs prior to async API resolution.

3. **Low Impact**:
   - Permanent `EventSource` retry on 401/403 authentication failures.
   - Infinite loading indicator display for empty thread letters in `LettersInbox`.
   - Text overflow risk on unformatted long text strings in card/letter components.

---

## 5. Verification Method & Exact Code Refactorings

### Recommended Code Refactorings

#### Refactoring 1: Fix Recovery Race Condition in `useAuroraSession.ts`
Modify `recover` in `web/src/hooks/useAuroraSession.ts` to check `activeTurnRef.current`:
```ts
// web/src/hooks/useAuroraSession.ts
const recover = useCallback(async (turnId: number, sid: number) => {
  if (reconnectingRef.current) return;
  reconnectingRef.current = true;
  setStatus(t.reconnecting);
  try {
    lastEventIdRef.current = await replayTurnEvents(turnId, lastEventIdRef.current, event => {
      if (activeTurnRef.current !== turnId) return; // Guard against turn switch
      if (event.type === "timeline.event") {
        setStatus(t.restoringEvent(event.payload.eventType));
      } else {
        handleEventRef.current(event);
      }
    });
    for (let attempt = 0; attempt < 40; attempt++) {
      if (activeTurnRef.current !== turnId) return; // Abort recovery if superseded
      const timeline = await api.timeline(turnId);
      if (activeTurnRef.current !== turnId) return; // Re-check after async fetch
      const recovered = timeline.bubbles
        .filter(b => b.status === "COMMITTED" || b.deliveredChars > 0)
        .map(b => ({
          key: `replay-${turnId}-${b.id}`,
          speaker: "AURORA" as const,
          text: b.status === "COMMITTED" ? b.content : b.content.slice(0, b.deliveredChars),
          partial: b.status !== "COMMITTED"
        }));
      setMessages(current => [
        ...current.filter(m => !m.key.startsWith(`live-${turnId}-`) && !m.key.startsWith(`replay-${turnId}-`)),
        ...recovered
      ]);
      if (terminal.has(timeline.turn.status)) {
        await replaceFromHistory(sid);
        setStatus(timeline.turn.status === "COMPLETED" ? t.recoveredCompleted : t.recoveredInterrupted);
        if (activeTurnRef.current === turnId) finishTurn();
        return;
      }
      await new Promise(resolve => setTimeout(resolve, 500));
    }
    if (activeTurnRef.current === turnId) setStatus(t.stillGenerating);
  } finally {
    reconnectingRef.current = false;
  }
}, [finishTurn, replaceFromHistory, setStatus, t]);
```

#### Refactoring 2: Fix Hung SSE Stream EOF in `useAuroraSession.ts`
In `send()` in `web/src/hooks/useAuroraSession.ts`:
```ts
try {
  await streamAurora({ sessionId, message: text, mode }, controller.signal, handleEvent);
  // Guard against clean stream EOF without terminal SSE event
  if (activeTurnRef.current !== null) {
    const turnId = activeTurnRef.current;
    await recover(turnId, sessionId);
  }
} catch (error) {
  if ((error as Error).name === "AbortError") return;
  const turnId = activeTurnRef.current;
  if (turnId) await recover(turnId, sessionId);
  else setStatus(t.noTimelineRetry);
}
```

#### Refactoring 3: Fix Async State Race Condition in `useConnectionsAndLetters.ts`
Add active selection checks in `openRelation`, `openThread`, `openGroup`:
```ts
const selectedRelationRef = useRef<string | null>(null);
const openRelation = useCallback(async (label: string) => {
  setSelectedRelation(label);
  selectedRelationRef.current = label;
  setRelationBusy(true);
  setRelationTimeline([]); setRelationHealth(null);
  try {
    const [timeline, health] = await Promise.all([
      api.relationTimeline(label),
      api.relationHealth(label).catch(() => null)
    ]);
    if (selectedRelationRef.current !== label) return; // Drop stale out-of-order response
    setRelationTimeline(timeline); setRelationHealth(health);
  } catch (error) {
    if (selectedRelationRef.current === label) setStatus(error instanceof Error ? error.message : "暂时读不到这段关系的时间线");
  } finally {
    if (selectedRelationRef.current === label) setRelationBusy(false);
  }
}, [setStatus]);
```

#### Refactoring 4: Add Top-Level React Error Boundary Component
Create `web/src/components/ErrorBoundary.tsx`:
```tsx
import { Component, type ErrorInfo, type ReactNode } from "react";

interface Props { children: ReactNode; }
interface State { hasError: boolean; error: Error | null; }

export class ErrorBoundary extends Component<Props, State> {
  public state: State = { hasError: false, error: null };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("Uncaught React error:", error, errorInfo);
  }

  public render() {
    if (this.state.hasError) {
      return (
        <div className="login-shell" role="alert">
          <div className="login">
            <span className="eyebrow">INNER COSMOS</span>
            <h1>应用遇到未预期的错误</h1>
            <p>{this.state.error?.message || "页面在渲染过程中遇到了问题。"}</p>
            <button type="button" className="send" onClick={() => window.location.reload()}>
              刷新并重新加载
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
```
Wrap `<AuroraApp />` in `web/src/main.tsx`:
```tsx
<StrictMode>
  <ErrorBoundary>
    <BrowserRouter basename={nativeShell ? "/" : "/app/aurora"}>
      <AuroraApp />
    </BrowserRouter>
  </ErrorBoundary>
</StrictMode>
```

#### Refactoring 5: Fix Infinite Thread Loading Display in `LettersInbox.tsx`
Change line 133 of `web/src/components/LettersInbox.tsx`:
```tsx
// Before:
: threadLetters.length === 0 ? <div className="network-empty">{t.threadLoading}</div>
// After:
: draftBusy ? <div className="network-empty">{t.threadLoading}</div>
: threadLetters.length === 0 ? <div className="network-empty">{t.threadsEmpty}</div>
```

---

### Verification Steps
1. **Frontend Test Suite Execution**:
   Run tests in `web/`:
   ```powershell
   cd web
   npm test
   ```
   All unit and integration tests under `web/src/*.test.ts` and `web/src/components/*.test.tsx` should execute and pass.

2. **Network Reconnection Simulation**:
   - Trigger a streaming turn with Aurora.
   - Simulate a network drop mid-stream (e.g. DevTools Offline toggle).
   - Interrupt/send a new message immediately after restoring connection.
   - Verify that `activeTurnRef` is not wiped by stale recovery polls and the new message stream proceeds without hanging.

3. **Error Boundary Invalidation Check**:
   - Temporarily throw `new Error("Test crash")` inside a component's render body.
   - Verify that the application renders the `ErrorBoundary` fallback screen with the "Refresh" button rather than unmounting to a blank page.
