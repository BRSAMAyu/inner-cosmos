import { SseDecoder, toTypedEvent, type AuroraStreamEvent, type DialogMessage, type TurnTimeline } from "./protocol";

type ApiEnvelope<T> = { success: boolean; data: T; message?: string; code?: string; error?: string };
type Csrf = { token: string; headerName: string };
export type WakeIntent = {
  id: number; purpose: string; reasonForUser: string; content: string;
  earliestAt: string; preferredAt: string; latestAt: string; timezone: string;
  status: "PLANNED" | "CLAIMED" | "FIRED" | "CANCELLED" | "EXPIRED" | "SUPERSEDED";
  contextSessionId: number | null; supersedesIntentId: number | null; userFeedback: string | null;
};
export type Notification = { id: number; type: string; title: string; body: string; refId: number; refType: string; read: boolean };
export type SelfEvolution = {
  candidates: Array<{ id: number; dimension: string; proposedBelief: string; confidence: number; evidenceRefs: string; createdAt: string }>;
  proposals: Array<{
    id: number; sourceReflectionId: number; dimension: string; currentBelief: string | null;
    proposedBelief: string; evidenceRefs: string; counterEvidence: string; expectedImpact: string;
    changesConstitution: boolean; rollbackTargetVersionId: number; policyVersion: string;
    status: "DRAFT" | "EVALUATED" | "ACTIVATED" | "REJECTED";
    evaluation: null | { decision: "PASS" | "FAIL"; reasons: string; sandboxBefore: string; sandboxAfter: string;
      fidelityScore: number; qualityScore: number; continuityScore: number };
    createdAt: string;
  }>;
  versions: Array<{
    id: number; versionNo: number; parentVersionId: number | null; rollbackTargetVersionId: number | null;
    sourceProposalId: number | null; constitutionHash: string; publicNarrative: string;
    status: "ACTIVE" | "RETIRED"; activatedAt: string;
  }>;
};
export type CorrectionCommand = {
  targetType: "AURORA_UNDERSTANDING" | "PORTRAIT_DIM" | "MEMORY_CARD";
  targetId: number;
  fieldName: string;
  oldValue: string | null;
  newValue: string;
  reason: string | null;
};
export type CorrectionImpact = {
  claimKey: string; newValue: string; affectedMemoryCount: number;
  authorizedCapsuleContextCount: number; confirmationRequired: boolean;
  impacts: Array<{ kind: string; targetId: number | null; label: string; action: string }>;
};
export type UnderstandingClaim = {
  id: number; claimKey: string; valueJson: string; authorityLevel: string;
  status: "ACTIVE" | "SUPERSEDED" | "RETIRED"; version: number; createdAt: string;
};
export type StarfieldStar = {
  id: number; title: string; summary: string | null; theme: string; color: string;
  gravity: number; glow: number; freshness: number; x: number; y: number;
  memoryLayer: string; confidence: number; versionNo: number; peopleTags: string | null;
  status: string; occurredAt: string | null; ariaLabel: string; connectedMemoryIds: number[];
};
export type StarfieldScene = {
  mode: "TIME" | "THEME" | "PEOPLE"; modeExplanation: string;
  stars: StarfieldStar[]; accessibleList: StarfieldStar[]; legend: Record<string, string>; generatedAt: string;
};
export type MemoryOperation = {
  id: number; operationType: string; primaryMemoryId: number | null; oldVersion: number;
  newVersion: number; reasonCode: string; actorType: string; rollbackOfOperationId: number | null;
  status: "APPLIED" | "ROLLED_BACK"; createdAt: string;
};
export type MemoryOperationResult = {
  operation: MemoryOperation;
  memories: StarfieldStar[];
  projectionReceipts: Array<{ id: number; projectionType: string; status: string; generation: number; detail: string }>;
};
export type StarfieldDetail = {
  card: { id: number; title: string; summary: string | null; sourceSessionId: number | null;
    versionNo: number; memoryLayer: string; confidence: number; provenanceRefs: string | null };
  gravityExplanation: string; auroraObservation: string; provenanceExplanation: string;
  versionHistory: MemoryOperation[];
  links: Array<{ id: number; sourceMemoryId: number; targetMemoryId: number; linkType: string; status: string }>;
  projectionReceipts: Array<{ id: number; projectionType: string; status: string; detail: string }>;
};
export type CorrectionConfirmation = {
  correction: { id: number; newValue: string; status: string; impactSummary: string };
  activeClaim: UnderstandingClaim;
  propagation: Array<{ id: number; targetKind: string; status: string; detail: string }>;
};
let csrf: Csrf | null = null;

async function request<T>(url: string, init: RequestInit = {}, retriedCsrf = false): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && init.body) headers.set("Content-Type", "application/json");
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    csrf ??= await getCsrf();
    headers.set(csrf.headerName, csrf.token);
  }
  const response = await fetch(url, { ...init, headers, credentials: "include" });
  const body = await response.json() as ApiEnvelope<T>;
  if (response.status === 403 && (body.code === "CSRF_INVALID" || body.error === "CSRF_INVALID") && !retriedCsrf) {
    csrf = null;
    return request<T>(url, init, true);
  }
  if (!response.ok || !body.success) throw new Error(body.message ?? `HTTP ${response.status}`);
  return body.data;
}

async function getCsrf(): Promise<Csrf> {
  const response = await fetch("/api/auth/csrf", { credentials: "include" });
  const body = await response.json() as ApiEnvelope<Csrf>;
  if (!body.success) throw new Error(body.message ?? "无法建立安全会话");
  return body.data;
}

export const api = {
  login: async (username: string, password: string) => {
    const result = await request<unknown>("/api/auth/login", {
      method: "POST", body: JSON.stringify({ username, password,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Singapore" })
    });
    // AuthController rotates the session ID after login. The pre-authentication
    // synchronizer token must never be reused with the authenticated session.
    csrf = null;
    return result;
  },
  createSession: () => request<{ id: number }>("/api/dialog/session/create", {
    method: "POST", body: JSON.stringify({ title: "Aurora 对话", sessionType: "AURORA_CHAT" })
  }),
  messages: (sessionId: number) => request<DialogMessage[]>(`/api/dialog/session/${sessionId}/messages`),
  timeline: (turnId: number) => request<TurnTimeline>(`/api/aurora/turns/${turnId}/timeline`),
  stop: (turnId: number) => request<TurnTimeline>(`/api/aurora/turns/${turnId}/stop`, { method: "POST" }),
  wakeIntents: () => request<WakeIntent[]>("/api/aurora/wake-intents"),
  wakeIntent: (id: number) => request<WakeIntent>(`/api/aurora/wake-intents/${id}`),
  negotiateWakeIntent: (input: { when: string; purpose: string; reasonForUser: string; content: string;
    timezone: string; contextSessionId: number | null }) => request<WakeIntent>("/api/aurora/wake-intents/negotiate", {
      method: "POST", body: JSON.stringify(input)
    }),
  scheduleWakeIntent: (input: Omit<WakeIntent, "id" | "status">) => request<WakeIntent>("/api/aurora/wake-intents", {
    method: "POST", body: JSON.stringify(input)
  }),
  rescheduleWakeIntent: (id: number, input: Pick<WakeIntent, "earliestAt" | "preferredAt" | "latestAt">) =>
    request<WakeIntent>(`/api/aurora/wake-intents/${id}/schedule`, { method: "PUT", body: JSON.stringify(input) }),
  cancelWakeIntent: (id: number) => request<WakeIntent>(`/api/aurora/wake-intents/${id}/cancel`, { method: "POST" }),
  wakeFeedback: (id: number, choice: "MATCHED" | "LATER" | "STOP_SIMILAR") =>
    request<WakeIntent>(`/api/aurora/wake-intents/${id}/feedback`, { method: "POST", body: JSON.stringify({ choice }) }),
  notifications: () => request<Notification[]>("/api/notifications"),
  readNotification: (id: number) => request<unknown>(`/api/notifications/${id}/read`, { method: "POST" }),
  updateTimezone: (timezone: string) => request<unknown>("/api/user/profile", {
    method: "PUT", body: JSON.stringify({ timezone })
  }),
  selfEvolution: () => request<SelfEvolution>("/api/aurora/self/evolution"),
  proposeSelfEvolution: (candidateId: number, expectedImpact: string) => request<SelfEvolution>("/api/aurora/self/evolution/proposals", {
    method: "POST", body: JSON.stringify({ candidateId, expectedImpact, counterEvidence: [], changesConstitution: false })
  }),
  evaluateSelfEvolution: (id: number) => request<SelfEvolution>(`/api/aurora/self/evolution/proposals/${id}/evaluate`, { method: "POST" }),
  activateSelfEvolution: (id: number) => request<SelfEvolution>(`/api/aurora/self/evolution/proposals/${id}/activate`, { method: "POST" }),
  rollbackSelfEvolution: (targetVersionId: number) => request<SelfEvolution>("/api/aurora/self/evolution/rollback", {
    method: "POST", body: JSON.stringify({ targetVersionId, restoreRelationship: false })
  }),
  previewCorrection: (input: CorrectionCommand) => request<CorrectionImpact>("/api/aurora/corrections/preview", {
    method: "POST", body: JSON.stringify(input)
  }),
  confirmCorrection: (input: CorrectionCommand) => request<CorrectionConfirmation>("/api/aurora/corrections/confirm", {
    method: "POST", body: JSON.stringify(input)
  }),
  understandingClaims: () => request<UnderstandingClaim[]>("/api/aurora/corrections/claims"),
  starfield: (mode: StarfieldScene["mode"]) => request<StarfieldScene>(`/api/memory/starfield/v2?mode=${mode}`),
  starfieldDetail: (id: number) => request<StarfieldDetail>(`/api/memory/starfield/${id}/detail`),
  memoryOperations: () => request<MemoryOperation[]>("/api/memory/operations"),
  rollbackMemoryOperation: (id: number) => request<MemoryOperationResult>(`/api/memory/operations/${id}/rollback`, { method: "POST" })
};

export async function streamAurora(
  input: { sessionId: number; message: string; mode: string },
  signal: AbortSignal,
  onEvent: (event: AuroraStreamEvent) => void
): Promise<void> {
  const query = new URLSearchParams({
    sessionId: String(input.sessionId), message: input.message, mode: input.mode
  });
  const response = await fetch(`/api/aurora/stream?${query}`, {
    credentials: "include", headers: { Accept: "text/event-stream" }, signal
  });
  if (!response.ok || !response.body) throw new Error(`SSE HTTP ${response.status}`);
  const decoder = new SseDecoder();
  const reader = response.body.getReader();
  const textDecoder = new TextDecoder();
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    for (const frame of decoder.push(textDecoder.decode(value, { stream: true }))) {
      const event = toTypedEvent(frame);
      if (event) onEvent(event);
    }
  }
}

export async function replayTurnEvents(
  turnId: number,
  lastEventId: string,
  onEvent: (event: AuroraStreamEvent) => void
): Promise<string> {
  const response = await fetch(`/api/aurora/turns/${turnId}/events`, {
    credentials: "include",
    headers: { Accept: "text/event-stream", ...(lastEventId ? { "Last-Event-ID": lastEventId } : {}) }
  });
  if (!response.ok || !response.body) throw new Error(`Replay HTTP ${response.status}`);
  const decoder = new SseDecoder();
  const reader = response.body.getReader();
  const textDecoder = new TextDecoder();
  let latest = lastEventId;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    for (const frame of decoder.push(textDecoder.decode(value, { stream: true }))) {
      if (frame.id) latest = frame.id;
      const event = toTypedEvent(frame);
      if (event) onEvent(event);
    }
  }
  return latest;
}
