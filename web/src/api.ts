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
export type ProactiveEvent = { type: string; content: string; ts: string };
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
export type UserCorrection = {
  id: number; targetType: string; fieldName: string;
  oldValue: string | null; newValue: string | null; reason: string | null;
  status: string | null; createdAt: string;
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
export type PortraitDimension = { dim: string; valueJson: string; confidence: number | null; updatedAt: string | null };
export type PortraitHistoryEntry = { valueJson: string; recordedAt: string };
export type MemoryCard = {
  id: number; title: string; summary: string | null; status: string; versionNo: number;
  consentScope: string | null; memoryLayer: string | null; confidence: number | null;
};
export type EchoCapsule = {
  id: number; pseudonym: string; intro: string; authorizedMemoryIds: string;
  visibilityStatus: "PRIVATE" | "PUBLIC" | "NEEDS_REVIEW" | "HIDDEN" | "ARCHIVED";
  isPublic: boolean; activeGenomeVersionId: number | null; publicTags: string;
};
export type CapsuleGenomeVersion = {
  id: number; versionNo: number; parentVersionId: number | null; compilerVersion: string;
  status: "ACTIVE" | "NEEDS_REVIEW" | "SUPERSEDED" | "WITHDRAWN";
  evaluationJson: string; changeReason: string; createdAt: string;
};
export type CapsulePreview = {
  abstractSummary: string; removedSensitiveItems: string[]; publicTags: string[];
  suggestedPseudonym: string; personaPromptDraft: string; riskWarnings: string[];
};
export type CapsuleFidelitySummary = {
  genomeVersionId: number; versionNo: number | null; totalRatings: number; likeMeCount: number;
  notMeCount: number; factWrongCount: number; tooExposedCount: number; toneWrongCount: number;
  fidelityScore: number | null;
};
export type CapsuleSandbox = {
  capsuleId: number; genomeVersionId: number; genomeVersionNo: number; genomeStatus: string;
  question: string; reply: string; boundaryNotice: string; riskFlags: string[];
  providerAvailable: boolean; identityNotice: string;
};
export type CapsuleSandboxFeedback = {
  id: number; genomeVersionId: number; rating: string; ownerComment: string | null; status: string;
};
export type PublicCapsule = {
  id: number; pseudonym: string; intro: string; capsuleType: string; publicTags: string;
  echoEnergy: number; freshnessScore: number; conversationLimitPerDay: number; lastActivityAt: string | null;
};
export type ResonanceStrategy = "MIRROR" | "COMPLEMENT" | "GROWTH_EDGE" | "SERENDIPITY" | "CONTEXTUAL";
export type CapsuleMatch = {
  capsule: PublicCapsule; matchScore: number; matchReasons: string[]; matchSummary: string; resonant: boolean;
  strategy: ResonanceStrategy; strategyLabel: string; strategyDescription: string;
};
export type PersonaSession = { id: number; capsuleId: number; status: string; turnCount: number; dailyLimit: number };
export type PersonaMessage = { id: number; sessionId: number; senderType: "VISITOR" | "CAPSULE"; textContent: string };
export type CapsuleQuota = { usedTurns: number; remainingTurns: number; dailyLimit: number; exhausted: boolean };
export type SlowLetter = {
  id: number; senderUserId: number; receiverUserId: number; receiverCapsuleId: number; title: string; letterBody: string; status: string;
  parallaxDistance: number; estimatedArrivalAt: string;
};
export type SocialConnection = {
  id: number; status: string; userId: number; nickname: string; username: string; source: string;
};
export type FriendRelation = {
  id: number; requesterId: number; addresseeId: number; status: string; source: string;
};
export type ConnectionRequests = { incoming: SocialConnection[]; outgoing: SocialConnection[] };
export type PsychologySkillManifest = {
  id: string; version: string; owner: string; title: Record<string, string>; description: Record<string, string>;
  estimatedMinutes: number; riskTier: "L1" | "L2" | "L3"; agentInvocation: string; userInvocation: string;
  requiredScopes: string[]; allowedData: string[]; allowedTools: string[]; requiredInputs: string[];
  evidence: string[]; limitations: Record<string, string>; retentionChoices: PsychologyRetention[];
  evaluationSuite: string; fallback: string; escalation: string;
};
export type PsychologyRetention = "DISCARD_AFTER_SESSION" | "SAVE_RESULT" | "PROFILE_ELIGIBLE";
export type PsychologySkillRun = {
  id: number; skillId: string; skillVersion: string; locale: string;
  status: "COMPLETED" | "ESCALATED" | "REVOKED"; riskTier: string; retentionChoice: PsychologyRetention;
  consentScopes: string[]; result: Record<string, unknown>; evidence: string[];
  escalationCode: string | null; createdAt: string; revokedAt: string | null;
};
export type PsychologySkillSuggestion = {
  skillId: string; skillVersion: string; title: string; reason: string;
  invocation: "SUGGEST_ONLY"; createsRun: false;
};
let csrf: Csrf | null = null;
type AccessTokenProvider = () => Promise<string | null>;
let accessTokenProvider: AccessTokenProvider | null = null;
let accessTokenInvalidator: (() => Promise<void>) | null = null;
let bearerRequired = false;

export function configureBearerAuth(provider: AccessTokenProvider | null, required = false,
                                    invalidator: (() => Promise<void>) | null = null): void {
  accessTokenProvider = provider;
  accessTokenInvalidator = invalidator;
  bearerRequired = required;
  csrf = null;
}

function isNonPublicHost(hostname: string): boolean {
  const host = hostname.toLowerCase().replace(/^\[|\]$/g, "");
  if (host === "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.includes(":")) return true;
  const octets = host.split(".").map(Number);
  if (octets.length !== 4 || octets.some(value => !Number.isInteger(value) || value < 0 || value > 255)) return false;
  const [a, b] = octets;
  return a === 0 || a === 10 || a === 127 || a >= 224 || (a === 100 && b >= 64 && b <= 127)
    || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31)
    || (a === 192 && b === 168) || (a === 198 && (b === 18 || b === 19));
}

export function validateApiBase(raw: string, allowedOrigins: string, production: boolean): string {
  if (!raw.trim()) return "";
  let url: URL;
  try { url = new URL(raw); } catch { throw new Error("VITE_API_BASE_URL must be an absolute URL"); }
  if (url.username || url.password || url.search || url.hash) throw new Error("API URL cannot contain credentials, query, or fragment");
  if (url.pathname !== "/" || url.port) throw new Error("API URL must be an origin without a path or custom port");
  if (production && url.protocol !== "https:") throw new Error("Production API URL must use HTTPS");
  if (production && isNonPublicHost(url.hostname)) throw new Error("Production API URL cannot use a local or private host");
  const allowed = new Set(allowedOrigins.split(",").map(value => value.trim()).filter(Boolean).map(value => {
    const candidate = new URL(value);
    if (candidate.username || candidate.password || candidate.search || candidate.hash || candidate.pathname !== "/" || candidate.port) {
      throw new Error("VITE_API_ALLOWED_ORIGINS contains a non-origin value");
    }
    return candidate.origin;
  }));
  if (production && !allowed.has(url.origin)) throw new Error("Production API origin is not in the signed-build allowlist");
  return url.origin;
}

const rawApiBase = String(import.meta.env.VITE_API_BASE_URL ?? "");
let configuredApiBase = "";
export let apiConfigurationError: string | null = null;
try {
  configuredApiBase = validateApiBase(rawApiBase,
    String(import.meta.env.VITE_API_ALLOWED_ORIGINS ?? "https://api.innercosmos.sg"), import.meta.env.PROD);
} catch (error) {
  apiConfigurationError = error instanceof Error ? error.message : "Invalid production API origin";
}
export const hasConfiguredApiBase = configuredApiBase.length > 0;

export function apiUrl(path: string): string {
  if (!path.startsWith("/api/")) throw new Error("Only Inner Cosmos API paths are allowed");
  if (rawApiBase && apiConfigurationError) throw new Error(apiConfigurationError);
  return configuredApiBase ? `${configuredApiBase}${path}` : path;
}

export function subscribeProactive(
  onEvent: (event: ProactiveEvent) => void,
  onConnectionChange?: (connected: boolean) => void
): () => void {
  if (accessTokenProvider) {
    const controller = new AbortController();
    void subscribeProactiveBearer(controller, onEvent, onConnectionChange);
    return () => controller.abort();
  }
  if (typeof EventSource === "undefined") return () => undefined;
  const source = new EventSource(apiUrl("/api/proactive/stream"), { withCredentials: true });
  source.onopen = () => onConnectionChange?.(true);
  source.onerror = () => onConnectionChange?.(false);
  source.addEventListener("proactive", raw => {
    try {
      const value = JSON.parse((raw as MessageEvent<string>).data) as Partial<ProactiveEvent>;
      if (typeof value.type === "string" && typeof value.content === "string" && typeof value.ts === "string") {
        onEvent(value as ProactiveEvent);
      }
    } catch {
      // Malformed live hints are ignored. Durable notifications remain the source of truth.
    }
  });
  return () => source.close();
}

async function subscribeProactiveBearer(controller: AbortController, onEvent: (event: ProactiveEvent) => void,
                                        onConnectionChange?: (connected: boolean) => void): Promise<void> {
  while (!controller.signal.aborted) {
    try {
      const token = await accessTokenProvider?.();
      if (!token) throw new Error("Mobile authentication is required");
      const response = await fetch(apiUrl("/api/proactive/stream"), {
        signal: controller.signal, headers: { Accept: "text/event-stream", Authorization: `Bearer ${token}` }
      });
      if (response.status === 401) await accessTokenInvalidator?.();
      if (!response.ok || !response.body) throw new Error(`Proactive SSE HTTP ${response.status}`);
      onConnectionChange?.(true);
      const decoder = new SseDecoder();
      const reader = response.body.getReader();
      const textDecoder = new TextDecoder();
      while (!controller.signal.aborted) {
        const { done, value } = await reader.read();
        if (done) break;
        for (const frame of decoder.push(textDecoder.decode(value, { stream: true }))) {
          if (frame.event !== "proactive") continue;
          try {
            const event = JSON.parse(frame.data) as Partial<ProactiveEvent>;
            if (typeof event.type === "string" && typeof event.content === "string" && typeof event.ts === "string") {
              onEvent(event as ProactiveEvent);
            }
          } catch { /* durable notifications remain the source of truth */ }
        }
      }
    } catch (error) {
      if (controller.signal.aborted) return;
    }
    onConnectionChange?.(false);
    await new Promise(resolve => setTimeout(resolve, 2000));
  }
}

async function request<T>(url: string, init: RequestInit = {}, retriedCsrf = false, retriedBearer = false): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  const accessToken = await accessTokenProvider?.() ?? null;
  if (bearerRequired && !accessToken) throw new Error("Mobile OIDC authentication is required");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  if (!headers.has("Content-Type") && init.body) headers.set("Content-Type", "application/json");
  if (!accessToken && !["GET", "HEAD", "OPTIONS"].includes(method)) {
    csrf ??= await getCsrf();
    headers.set(csrf.headerName, csrf.token);
  }
  const response = await fetch(apiUrl(url), { ...init, headers, credentials: accessToken ? "omit" : "include" });
  const body = await response.json() as ApiEnvelope<T>;
  if (response.status === 401 && accessToken && accessTokenInvalidator && !retriedBearer) {
    await accessTokenInvalidator();
    return request<T>(url, init, retriedCsrf, true);
  }
  if (response.status === 403 && (body.code === "CSRF_INVALID" || body.error === "CSRF_INVALID") && !retriedCsrf) {
    csrf = null;
    return request<T>(url, init, true);
  }
  if (!response.ok || !body.success) throw new Error(body.message ?? `HTTP ${response.status}`);
  return body.data;
}

async function getCsrf(): Promise<Csrf> {
  if (bearerRequired) throw new Error("CSRF session authentication is disabled for native clients");
  const response = await fetch(apiUrl("/api/auth/csrf"), { credentials: "include" });
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
  logout: async () => {
    const result = await request<boolean>("/api/auth/logout", { method: "POST" });
    csrf = null;
    return result;
  },
  changePassword: (oldPassword: string, newPassword: string) => request<void>("/api/user/password", {
    method: "PUT", body: JSON.stringify({ oldPassword, newPassword })
  }),
  deleteAccount: async (password: string) => {
    const result = await request<void>("/api/user/account", { method: "DELETE", body: JSON.stringify({ password }) });
    csrf = null;
    return result;
  },
  exportData: () => request<Record<string, unknown>>("/api/user/export"),
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
  psychologySkills: () => request<PsychologySkillManifest[]>("/api/psychology/skills"),
  psychologySkillRuns: () => request<PsychologySkillRun[]>("/api/psychology/skills/runs"),
  psychologySkillSuggestion: (text: string, locale: "zh-CN" | "en-SG") => request<PsychologySkillSuggestion | null>("/api/psychology/skills/suggestions", {
    method: "POST", body: JSON.stringify({ text, locale })
  }),
  runPsychologySkill: (skillId: string, input: { explicitConsent: boolean; retentionChoice: PsychologyRetention;
    locale: "zh-CN" | "en-SG"; consentScopes: string[]; answers: Record<string, string> }) =>
    request<PsychologySkillRun>(`/api/psychology/skills/${skillId}/runs`, { method: "POST", body: JSON.stringify(input) }),
  revokePsychologySkillRun: (runId: number) =>
    request<PsychologySkillRun>(`/api/psychology/skills/runs/${runId}/revoke`, { method: "POST" }),
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
  recentCorrections: () => request<UserCorrection[]>("/api/aurora/corrections"),
  retireCorrection: (id: number) => request<void>(`/api/aurora/corrections/${id}`, { method: "DELETE" }),
  portrait: () => request<PortraitDimension[]>("/api/portrait"),
  portraitHistory: (dim: string) => request<PortraitHistoryEntry[]>(`/api/portrait/history?dim=${encodeURIComponent(dim)}`),
  starfield: (mode: StarfieldScene["mode"]) => request<StarfieldScene>(`/api/memory/starfield/v2?mode=${mode}`),
  starfieldDetail: (id: number) => request<StarfieldDetail>(`/api/memory/starfield/${id}/detail`),
  memoryOperations: () => request<MemoryOperation[]>("/api/memory/operations"),
  rollbackMemoryOperation: (id: number) => request<MemoryOperationResult>(`/api/memory/operations/${id}/rollback`, { method: "POST" }),
  memoryCards: () => request<MemoryCard[]>("/api/memory/cards"),
  myCapsules: () => request<EchoCapsule[]>("/api/capsule/my"),
  capsuleGenomeHistory: (id: number) => request<CapsuleGenomeVersion[]>(`/api/capsule/${id}/genome-history`),
  capsuleFidelity: (id: number) => request<CapsuleFidelitySummary[]>(`/api/capsule/${id}/sandbox/fidelity`),
  previewCapsule: (memoryIds: number[]) => request<CapsulePreview>("/api/capsule/preview-from-memory", {
    method: "POST", body: JSON.stringify({ memoryIds, privacyLevel: "STRICT", allowTopics: [], blockedTopics: [] })
  }),
  createCapsule: (input: { pseudonym: string; intro: string; memoryIds: number[]; publicTags: string[] }) =>
    request<EchoCapsule>("/api/capsule/create-from-memory", { method: "POST", body: JSON.stringify({
      ...input, visibilityStatus: "PRIVATE", isPublic: false, privacyLevel: "STRICT",
      allowTopics: ["自我观察", "日常支持"], blockedTopics: ["真实身份", "联系方式", "心理诊断"]
    }) }),
  recompileCapsule: (id: number, memoryIds: number[]) => request<CapsuleGenomeVersion>(`/api/capsule/${id}/genome/recompile`, {
    method: "POST", body: JSON.stringify({ memoryIds })
  }),
  setCapsuleVisibility: (id: number, visibilityStatus: "PRIVATE" | "PUBLIC", isPublic: boolean) =>
    request<EchoCapsule>(`/api/capsule/${id}/visibility`, { method: "POST", body: JSON.stringify({ visibilityStatus, isPublic }) }),
  archiveCapsule: (id: number) => request<unknown>(`/api/capsule/${id}/archive`, { method: "POST" }),
  sandboxCapsule: (id: number, question: string) => request<CapsuleSandbox>(`/api/capsule/${id}/sandbox/respond`, {
    method: "POST", body: JSON.stringify({ question })
  }),
  feedbackCapsuleSandbox: (id: number, input: { genomeVersionId: number; question: string; response: string; rating: string; comment?: string }) =>
    request<CapsuleSandboxFeedback>(`/api/capsule/${id}/sandbox/feedback`, { method: "POST", body: JSON.stringify(input) }),
  resonanceMatches: (strategy: ResonanceStrategy = "MIRROR") =>
    request<CapsuleMatch[]>(`/api/plaza/matches?strategy=${encodeURIComponent(strategy)}`),
  createPersonaSession: (capsuleId: number) => request<PersonaSession>("/api/persona-chat/session/create", {
    method: "POST", body: JSON.stringify({ capsuleId })
  }),
  personaMessages: (sessionId: number) => request<PersonaMessage[]>(`/api/persona-chat/session/${sessionId}/messages`),
  sendPersonaMessage: (sessionId: number, message: string) => request<PersonaMessage>("/api/persona-chat/message", {
    method: "POST", body: JSON.stringify({ sessionId, message })
  }),
  capsuleQuota: (capsuleId: number) => request<CapsuleQuota>(`/api/persona-chat/quota?capsuleId=${capsuleId}`),
  draftSlowLetter: (receiverCapsuleId: number, title: string, letterBody: string) => request<SlowLetter>("/api/letters/draft", {
    method: "POST", body: JSON.stringify({ receiverCapsuleId, title, letterBody })
  }),
  sendSlowLetter: (id: number) => request<SlowLetter>(`/api/letters/${id}/send`, { method: "POST" }),
  letterInbox: () => request<SlowLetter[]>("/api/letters/inbox"),
  transitionLetter: (id: number, action: "read" | "reply" | "decline" | "block" | "archive") =>
    request<SlowLetter>(`/api/letters/${id}/${action}`, { method: "POST" }),
  reportLetter: (id: number, reason: string) => request<void>(`/api/letters/${id}/report`, {
    method: "POST", body: JSON.stringify({ reason })
  }),
  replyWithSlowLetter: (id: number, title: string, letterBody: string) => request<SlowLetter>(`/api/letters/${id}/reply-with-letter`, {
    method: "POST", body: JSON.stringify({ title, letterBody })
  }),
  connectionRequests: () => request<ConnectionRequests>("/api/social/requests"),
  friends: () => request<SocialConnection[]>("/api/social/friends"),
  requestConnectionFromLetter: (letterId: number) => request<FriendRelation>(`/api/social/connections/from-letter/${letterId}`, { method: "POST" }),
  decideConnection: (id: number, decision: "accept" | "decline") => request<FriendRelation>(`/api/social/friends/${id}/${decision}`, { method: "POST" }),
  leaveConnection: (id: number) => request<FriendRelation>(`/api/social/friends/${id}/leave`, { method: "POST" })
};

export async function streamAurora(
  input: { sessionId: number; message: string; mode: string },
  signal: AbortSignal,
  onEvent: (event: AuroraStreamEvent) => void
): Promise<void> {
  const query = new URLSearchParams({
    sessionId: String(input.sessionId), message: input.message, mode: input.mode
  });
  let token = await accessTokenProvider?.() ?? null;
  if (bearerRequired && !token) throw new Error("Mobile OIDC authentication is required");
  let response = await fetch(apiUrl(`/api/aurora/stream?${query}`), {
    credentials: token ? "omit" : "include", headers: { Accept: "text/event-stream", ...(token ? { Authorization: `Bearer ${token}` } : {}) }, signal
  });
  if (response.status === 401 && token && accessTokenInvalidator) {
    await accessTokenInvalidator();
    token = await accessTokenProvider?.() ?? null;
    if (token) response = await fetch(apiUrl(`/api/aurora/stream?${query}`), {
      credentials: "omit", headers: { Accept: "text/event-stream", Authorization: `Bearer ${token}` }, signal
    });
  }
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
  let token = await accessTokenProvider?.() ?? null;
  if (bearerRequired && !token) throw new Error("Mobile OIDC authentication is required");
  let response = await fetch(apiUrl(`/api/aurora/turns/${turnId}/events`), {
    credentials: token ? "omit" : "include",
    headers: { Accept: "text/event-stream", ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(lastEventId ? { "Last-Event-ID": lastEventId } : {}) }
  });
  if (response.status === 401 && token && accessTokenInvalidator) {
    await accessTokenInvalidator();
    token = await accessTokenProvider?.() ?? null;
    if (token) response = await fetch(apiUrl(`/api/aurora/turns/${turnId}/events`), {
      credentials: "omit",
      headers: { Accept: "text/event-stream", Authorization: `Bearer ${token}`, ...(lastEventId ? { "Last-Event-ID": lastEventId } : {}) }
    });
  }
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
