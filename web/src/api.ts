import { SseDecoder, toTypedEvent, type AuroraStreamEvent, type DialogMessage, type TurnTimeline } from "./protocol";
import type { components as CoreApiComponents } from "./generated/inner-cosmos-v1";

type CoreApiSchemas = CoreApiComponents["schemas"];
type CoreLoginRequest = CoreApiSchemas["LoginRequest"];
type CoreChatRequest = CoreApiSchemas["ChatRequest"];
type CoreCapsuleCreateRequest = CoreApiSchemas["CapsuleCreateRequest"];
type CoreCapsuleBoundaryPatch = CoreApiSchemas["CapsuleBoundaryPatch"];
type CoreSlowLetterDraftRequest = CoreApiSchemas["SlowLetterDraftRequest"];
type CorePersonaMessageRequest = CoreApiSchemas["PersonaMessageRequest"];
type CoreStreamStage = CoreApiSchemas["StreamStageEnvelope"]["data"];

type ApiEnvelope<T> = { success: boolean; data: T; message?: string; code?: string; error?: string };
type Csrf = { token: string; headerName: string };
export type WakeIntent = {
  id: number; purpose: string; reasonForUser: string; content: string;
  earliestAt: string; preferredAt: string; latestAt: string; timezone: string;
  status: "PLANNED" | "CLAIMED" | "FIRED" | "CANCELLED" | "EXPIRED" | "SUPERSEDED";
  contextSessionId: number | null; supersedesIntentId: number | null; userFeedback: string | null;
};
export type Notification = { id: number; type: string; title: string; body: string; refId: number; refType: string; read: boolean };
export type GoodbyeResult = {
  success: boolean; line: string; stepsCompleted: string[]; confirmed: boolean; reverted: boolean;
  confidence: number; goodbyeStrength: string;
};
export type DeviceRegistration = {
  id: number; installationId: string; platform: string; transport: "FCM" | "APNS" | "LOCAL_EVIDENCE";
  appVersion: string; locale: string; timezone: string; enabled: boolean; revoked: boolean; lastSeenAt: string;
};
// Consumes Track A contract delta TA-DELTA-001 (GET /api/me/data-rights/receipts). Sensitive-free.
export type DataRetractionReceipt = {
  id: number;
  subjectType: "MEMORY" | "CAPSULE" | "DATA_USE_GRANT";
  subjectId: number;
  derivativeType: "CAPSULE_MATCH_INDEX" | "MEMORY_EMBEDDING" | "CAPSULE_PERSONA" | "GENOME";
  action: "ERASED" | "CLEARED" | "REVIEW_REQUIRED";
  affectedCount: number;
  reason: string;
  createdAt: string;
};
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
    versionNo: number; memoryLayer: string; confidence: number; provenanceRefs: string | null;
    userImportance: number | null };
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
export type ClaimCandidate = {
  id: number; claimType: string; value: string; authorityLevel: string; confidence: number;
  provenanceMessageIds: number[]; evidenceText: string; uncertain: boolean;
  alreadyActive: boolean; createdAt: string | null;
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
  ownerContextNote?: string | null; standInEnabled?: boolean; realContactPolicy?: string;
};
export type CapsuleGenomeVersion = {
  id: number; versionNo: number; parentVersionId: number | null; compilerVersion: string;
  status: "ACTIVE" | "NEEDS_REVIEW" | "SUPERSEDED" | "WITHDRAWN";
  evaluationJson: string; changeReason: string; createdAt: string;
};
export type CapsuleBoundary = CoreApiSchemas["CapsuleBoundary"];
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
export type DiscoverablePerson = { id: number; username: string; nickname: string; relationStatus: string };
export type RelationMention = { id: number; relationLabel: string; relationType: string | null; emotionTags: string | null; triggerSummary: string | null; boundaryHint: string | null };
export type RelationTimelinePoint = { timestamp: string; emotions: string | null; summary: string | null };
export type RelationHealth = { relationLabel: string; healthScore: number };
export type LetterThread = { id: number; firstLetterId: number; participantA: number; participantB: number; capsuleId: number | null; status: string; lastLetterAt: string | null };
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
let csrfPromise: Promise<Csrf> | null = null;
let csrfEpoch = 0;
type AccessTokenProvider = () => Promise<string | null>;
let accessTokenProvider: AccessTokenProvider | null = null;
let accessTokenInvalidator: (() => Promise<void>) | null = null;
let bearerRequired = false;
const capsuleBoundaryEtags = new Map<number, string>();

function newIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
  const random = typeof crypto !== "undefined" && typeof crypto.getRandomValues === "function"
    ? crypto.getRandomValues(new Uint32Array(4)).join("-") : `${Date.now()}-${Math.random()}`;
  return `web-${random}`.replace(/[^A-Za-z0-9._:-]/g, "-").slice(0, 128);
}

function needsIdempotency(method: string, url: string): boolean {
  if (method !== "POST") return false;
  const core = ["/api/v1/aurora/", "/api/v1/capsule/", "/api/v1/letters/", "/api/v1/persona-chat/"]
    .some(prefix => url.startsWith(prefix));
  return core && !url.endsWith("/stream-stage") && !url.endsWith("/rhythm-check");
}

export function configureBearerAuth(provider: AccessTokenProvider | null, required = false,
                                    invalidator: (() => Promise<void>) | null = null): void {
  accessTokenProvider = provider;
  accessTokenInvalidator = invalidator;
  bearerRequired = required;
  invalidateCsrf();
}

function invalidateCsrf(): void {
  csrf = null;
  csrfPromise = null;
  csrfEpoch += 1;
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

export function validateApiBase(raw: string, allowedOrigins: string, production: boolean,
                                mobileLocal = false, desktopLocal = false,
                                localOrigin = import.meta.env.VITE_LOCAL_API_ORIGIN ?? ""): string {
  if (!raw.trim()) return "";
  let url: URL;
  try { url = new URL(raw); } catch { throw new Error("VITE_API_BASE_URL must be an absolute URL"); }
  if (url.username || url.password || url.search || url.hash) throw new Error("API URL cannot contain credentials, query, or fragment");
  const exactMobileLocal = mobileLocal && Boolean(localOrigin) && url.origin === localOrigin;
  const exactDesktopLocal = desktopLocal && Boolean(localOrigin) && url.origin === localOrigin;
  const exactInstalledLocal = exactMobileLocal || exactDesktopLocal;
  if (url.pathname !== "/" || (url.port && !exactInstalledLocal)) throw new Error("API URL must be an origin without a path or custom port");
  if (mobileLocal && !exactMobileLocal) throw new Error("Local mobile API must match the build-time development origin");
  if (desktopLocal && !exactDesktopLocal) throw new Error("Local desktop API must use the fixed loopback port 8080");
  if (production && !mobileLocal && !desktopLocal && url.protocol !== "https:") throw new Error("Production API URL must use HTTPS");
  if (production && !mobileLocal && !desktopLocal && isNonPublicHost(url.hostname)) throw new Error("Production API URL cannot use a local or private host");
  const allowed = new Set(allowedOrigins.split(",").map(value => value.trim()).filter(Boolean).map(value => {
    const candidate = new URL(value);
    const localAllowed = (mobileLocal || desktopLocal) && Boolean(localOrigin) && candidate.origin === localOrigin;
    if (candidate.username || candidate.password || candidate.search || candidate.hash || candidate.pathname !== "/" || (candidate.port && !localAllowed)) {
      throw new Error("VITE_API_ALLOWED_ORIGINS contains a non-origin value");
    }
    return candidate.origin;
  }));
  if (production && !allowed.has(url.origin)) throw new Error("Production API origin is not in the signed-build allowlist");
  return url.origin;
}

const rawApiBase = String(import.meta.env.VITE_API_BASE_URL ?? "");
export const mobileLocalBuild = String(import.meta.env.VITE_MOBILE_LOCAL ?? "") === "true";
export const desktopLocalBuild = String(import.meta.env.VITE_DESKTOP_LOCAL ?? "") === "true";
let configuredApiBase = "";
export let apiConfigurationError: string | null = null;
try {
  configuredApiBase = validateApiBase(rawApiBase,
    String(import.meta.env.VITE_API_ALLOWED_ORIGINS ?? "https://api.innercosmos.sg"), import.meta.env.PROD,
    mobileLocalBuild, desktopLocalBuild);
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
  if (needsIdempotency(method, url) && !headers.has("Idempotency-Key")) {
    headers.set("Idempotency-Key", newIdempotencyKey());
  }
  const boundaryMatch = url.match(/^\/api\/v1\/capsule\/(\d+)\/boundary$/);
  if (method === "POST" && boundaryMatch && !headers.has("If-Match")) {
    const etag = capsuleBoundaryEtags.get(Number(boundaryMatch[1]));
    if (!etag) throw new Error("Reload the capsule boundary before saving changes");
    headers.set("If-Match", etag);
  }
  let requestCsrf: Csrf | null = null;
  if (!accessToken && !["GET", "HEAD", "OPTIONS"].includes(method)) {
    requestCsrf = await getOrLoadCsrf();
    headers.set(requestCsrf.headerName, requestCsrf.token);
  }
  const response = await fetch(apiUrl(url), { ...init, headers, credentials: accessToken ? "omit" : "include" });
  if (boundaryMatch && response.ok) {
    const etag = response.headers.get("ETag");
    if (etag) capsuleBoundaryEtags.set(Number(boundaryMatch[1]), etag);
  }
  if (response.status === 401 && accessToken && accessTokenInvalidator && !retriedBearer) {
    await accessTokenInvalidator();
    return request<T>(url, { ...init, headers }, retriedCsrf, true);
  }
  const responseText = await response.text();
  let body: ApiEnvelope<T>;
  try {
    body = responseText ? JSON.parse(responseText) as ApiEnvelope<T> : { success: false, data: undefined as T };
  } catch {
    throw new Error(`API returned invalid JSON (HTTP ${response.status})`);
  }
  if (response.status === 403 && (body.code === "CSRF_INVALID" || body.error === "CSRF_INVALID") && !retriedCsrf) {
    await refreshCsrf(requestCsrf?.token);
    return request<T>(url, { ...init, headers }, true);
  }
  if (!response.ok || !body.success) throw new Error(body.message ?? `HTTP ${response.status}`);
  return body.data;
}

async function getCsrf(): Promise<Csrf> {
  if (bearerRequired) throw new Error("CSRF session authentication is disabled for native clients");
  const response = await fetch(apiUrl("/api/v1/auth/csrf"), { credentials: "include" });
  const body = await response.json() as ApiEnvelope<Csrf>;
  if (!body.success) throw new Error(body.message ?? "无法建立安全会话");
  return body.data;
}

async function getOrLoadCsrf(): Promise<Csrf> {
  if (csrf) return csrf;
  if (!csrfPromise) {
    const epoch = csrfEpoch;
    const pending = getCsrf().then(token => {
      if (csrfEpoch === epoch) csrf = token;
      return token;
    });
    csrfPromise = pending;
    const clear = () => { if (csrfPromise === pending) csrfPromise = null; };
    void pending.then(clear, clear);
  }
  return csrfPromise;
}

async function refreshCsrf(failedToken?: string): Promise<Csrf> {
  if (csrf && csrf.token !== failedToken) return csrf;
  // Another request may already be refreshing the same rejected token.
  if (csrfPromise) return csrfPromise;
  csrf = null;
  csrfEpoch += 1;
  return getOrLoadCsrf();
}

export const api = {
  login: async (username: string, password: string) => {
    const body: CoreLoginRequest = { username, password,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Singapore" };
    const result = await request<unknown>("/api/v1/auth/login", {
      method: "POST", body: JSON.stringify(body)
    });
    // AuthController rotates the session ID after login. The pre-authentication
    // synchronizer token must never be reused with the authenticated session.
    invalidateCsrf();
    return result;
  },
  // Same contract as the legacy /pages/register.html form: username required, nickname
  // optional (falls back to username server-side too, but we mirror it here so the request
  // body always matches what a human filled in), password 8-128 chars (server-validated).
  // AuthController.register() also establishes the session (rotates the id, stores the
  // user id), so a successful register can call the same onSuccess/bootstrap path as login.
  register: async (username: string, nickname: string, password: string) => {
    const body: { username: string; nickname: string; password: string } = {
      username, nickname: nickname || username, password
    };
    const result = await request<unknown>("/api/v1/auth/register", {
      method: "POST", body: JSON.stringify(body)
    });
    invalidateCsrf();
    return result;
  },
  logout: async () => {
    const result = await request<boolean>("/api/v1/auth/logout", { method: "POST" });
    invalidateCsrf();
    return result;
  },
  changePassword: (oldPassword: string, newPassword: string) => request<void>("/api/user/password", {
    method: "PUT", body: JSON.stringify({ oldPassword, newPassword })
  }),
  deleteAccount: async (password: string) => {
    const result = await request<void>("/api/user/account", { method: "DELETE", body: JSON.stringify({ password }) });
    invalidateCsrf();
    return result;
  },
  exportData: () => request<Record<string, unknown>>("/api/user/export"),
  safetyResources: () => request<string[]>("/api/safety/resources"),
  dataRightsReceipts: (limit?: number) => request<DataRetractionReceipt[]>(
    "/api/me/data-rights/receipts" + (limit ? `?limit=${limit}` : "")),
  createSession: () => request<{ id: number }>("/api/dialog/session/create", {
    method: "POST", body: JSON.stringify({ title: "Aurora 对话", sessionType: "AURORA_CHAT" })
  }),
  messages: (sessionId: number) => request<DialogMessage[]>(`/api/dialog/session/${sessionId}/messages`),
  timeline: (turnId: number) => request<TurnTimeline>(`/api/v1/aurora/turns/${turnId}/timeline`),
  stop: (turnId: number) => request<TurnTimeline>(`/api/v1/aurora/turns/${turnId}/stop`, { method: "POST" }),
  triggerGoodbye: (sessionId: number | null, trigger: string = "BUTTON") => request<GoodbyeResult>("/api/aurora/goodbye", {
    method: "POST", body: JSON.stringify(sessionId ? { sessionId: String(sessionId), trigger } : { trigger })
  }),
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
  devices: () => request<DeviceRegistration[]>("/api/v1/devices"),
  registerDevice: (installationId: string, input: {
    platform: "ANDROID" | "IOS" | "WINDOWS" | "MACOS"; transport: "FCM" | "APNS" | "LOCAL_EVIDENCE";
    token?: string; appVersion: string; locale: string; timezone: string;
  }) => request<DeviceRegistration>(`/api/v1/devices/${encodeURIComponent(installationId)}`,
    { method: "PUT", body: JSON.stringify(input) }),
  revokeDevice: (installationId: string) => request<void>(`/api/v1/devices/${encodeURIComponent(installationId)}`,
    { method: "DELETE" }),
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
  claimCandidates: () => request<ClaimCandidate[]>("/api/aurora/claims/candidates"),
  confirmClaimCandidate: (id: number) => request<CorrectionConfirmation>(`/api/aurora/claims/candidates/${id}/confirm`, { method: "POST" }),
  dismissClaimCandidate: (id: number) => request<{ dismissed: number }>(`/api/aurora/claims/candidates/${id}`, { method: "DELETE" }),
  portrait: () => request<PortraitDimension[]>("/api/portrait"),
  portraitHistory: (dim: string) => request<PortraitHistoryEntry[]>(`/api/portrait/history?dim=${encodeURIComponent(dim)}`),
  starfield: (mode: StarfieldScene["mode"]) => request<StarfieldScene>(`/api/memory/starfield/v2?mode=${mode}`),
  starfieldDetail: (id: number) => request<StarfieldDetail>(`/api/memory/starfield/${id}/detail`),
  memoryOperations: () => request<MemoryOperation[]>("/api/memory/operations"),
  rollbackMemoryOperation: (id: number) => request<MemoryOperationResult>(`/api/memory/operations/${id}/rollback`, { method: "POST" }),
  memoryCards: () => request<MemoryCard[]>("/api/memory/cards"),
  updateMemoryImportance: (id: number, importance: number) =>
    request<void>(`/api/memory/cards/${id}/importance`, { method: "POST", body: JSON.stringify({ importance }) }),
  archiveMemory: (id: number) => request<void>(`/api/memory/cards/${id}/archive`, { method: "POST" }),
  myCapsules: () => request<EchoCapsule[]>("/api/capsule/my"),
  capsuleGenomeHistory: (id: number) => request<CapsuleGenomeVersion[]>(`/api/capsule/${id}/genome-history`),
  capsuleFidelity: (id: number) => request<CapsuleFidelitySummary[]>(`/api/capsule/${id}/sandbox/fidelity`),
  previewCapsule: (memoryIds: number[]) => request<CapsulePreview>("/api/capsule/preview-from-memory", {
    method: "POST", body: JSON.stringify({ memoryIds, privacyLevel: "STRICT", allowTopics: [], blockedTopics: [] })
  }),
  createCapsule: (input: Required<Pick<CoreCapsuleCreateRequest, "pseudonym" | "intro" | "memoryIds" | "publicTags">>
    & Pick<CoreCapsuleCreateRequest, "ownerContextNote" | "standInEnabled" | "realContactPolicy" | "visibilityStatus">) => {
    const body: CoreCapsuleCreateRequest = {
      ...input, visibilityStatus: input.visibilityStatus ?? "PRIVATE", isPublic: false, privacyLevel: "STRICT",
      allowTopics: ["自我观察", "日常支持"], blockedTopics: ["真实身份", "联系方式", "心理诊断"]
    };
    return request<EchoCapsule>("/api/v1/capsule/create-from-memory", { method: "POST", body: JSON.stringify(body) });
  },
  updateCapsuleContext: (id: number, patch: { ownerContextNote?: string; standInEnabled?: boolean; realContactPolicy?: string }) =>
    request<EchoCapsule>(`/api/capsule/${id}/context`, { method: "POST", body: JSON.stringify(patch) }),
  recompileCapsule: (id: number, memoryIds: number[]) => request<CapsuleGenomeVersion>(`/api/capsule/${id}/genome/recompile`, {
    method: "POST", body: JSON.stringify({ memoryIds })
  }),
  setCapsuleVisibility: (id: number, visibilityStatus: "PRIVATE" | "PUBLIC", isPublic: boolean) =>
    request<EchoCapsule>(`/api/capsule/${id}/visibility`, { method: "POST", body: JSON.stringify({ visibilityStatus, isPublic }) }),
  archiveCapsule: (id: number) => request<unknown>(`/api/capsule/${id}/archive`, { method: "POST" }),
  capsuleBoundary: (id: number) => request<CapsuleBoundary | null>(`/api/v1/capsule/${id}/boundary`),
  updateCapsuleBoundary: (id: number, boundary: CoreCapsuleBoundaryPatch) =>
    request<CapsuleBoundary>(`/api/v1/capsule/${id}/boundary`, { method: "POST", body: JSON.stringify(boundary) }),
  sandboxCapsule: (id: number, question: string) => request<CapsuleSandbox>(`/api/capsule/${id}/sandbox/respond`, {
    method: "POST", body: JSON.stringify({ question })
  }),
  feedbackCapsuleSandbox: (id: number, input: { genomeVersionId: number; question: string; response: string; rating: string; comment?: string }) =>
    request<CapsuleSandboxFeedback>(`/api/capsule/${id}/sandbox/feedback`, { method: "POST", body: JSON.stringify(input) }),
  resonanceMatches: (strategy: ResonanceStrategy = "MIRROR") =>
    request<CapsuleMatch[]>(`/api/plaza/matches?strategy=${encodeURIComponent(strategy)}`),
  plazaCapsules: () => request<PublicCapsule[]>("/api/plaza/capsules"),
  createPersonaSession: (capsuleId: number) => request<PersonaSession>("/api/v1/persona-chat/session/create", {
    method: "POST", body: JSON.stringify({ capsuleId })
  }),
  personaMessages: (sessionId: number) => request<PersonaMessage[]>(`/api/persona-chat/session/${sessionId}/messages`),
  sendPersonaMessage: (sessionId: number, message: string) => {
    const body: CorePersonaMessageRequest = { sessionId, message };
    return request<PersonaMessage>("/api/v1/persona-chat/message", { method: "POST", body: JSON.stringify(body) });
  },
  capsuleQuota: (capsuleId: number) => request<CapsuleQuota>(`/api/persona-chat/quota?capsuleId=${capsuleId}`),
  reportPersonaSession: (sessionId: number, reason: string) => request<void>(`/api/persona-chat/session/${sessionId}/report`, {
    method: "POST", body: JSON.stringify({ reason })
  }),
  blockPersonaSession: (sessionId: number) => request<void>(`/api/persona-chat/session/${sessionId}/block`, { method: "POST" }),
  draftSlowLetter: (receiverCapsuleId: number, title: string, letterBody: string) => {
    const body: CoreSlowLetterDraftRequest = { receiverCapsuleId, title, letterBody };
    return request<SlowLetter>("/api/v1/letters/draft", { method: "POST", body: JSON.stringify(body) });
  },
  sendSlowLetter: (id: number) => request<SlowLetter>(`/api/letters/${id}/send`, { method: "POST" }),
  letterThreads: () => request<LetterThread[]>("/api/letters/threads"),
  letterThreadLetters: (threadId: number) => request<SlowLetter[]>(`/api/letters/threads/${threadId}/letters`),
  letterInbox: () => request<SlowLetter[]>("/api/letters/inbox"),
  letterOutbox: () => request<SlowLetter[]>("/api/letters/outbox"),
  transitionLetter: (id: number, action: "read" | "reply" | "decline" | "block" | "archive") =>
    request<SlowLetter>(`/api/letters/${id}/${action}`, { method: "POST" }),
  reportLetter: (id: number, reason: string) => request<void>(`/api/letters/${id}/report`, {
    method: "POST", body: JSON.stringify({ reason })
  }),
  replyWithSlowLetter: (id: number, title: string, letterBody: string) => request<SlowLetter>(`/api/letters/${id}/reply-with-letter`, {
    method: "POST", body: JSON.stringify({ title, letterBody })
  }),
  discoverPeople: () => request<DiscoverablePerson[]>("/api/social/people"),
  requestFriend: (userId: number) => request<FriendRelation>("/api/social/friends/request", {
    method: "POST", body: JSON.stringify({ userId, source: "SOCIAL_PAGE" })
  }),
  connectionRequests: () => request<ConnectionRequests>("/api/social/requests"),
  friends: () => request<SocialConnection[]>("/api/social/friends"),
  requestConnectionFromLetter: (letterId: number) => request<FriendRelation>(`/api/social/connections/from-letter/${letterId}`, { method: "POST" }),
  decideConnection: (id: number, decision: "accept" | "decline") => request<FriendRelation>(`/api/social/friends/${id}/${decision}`, { method: "POST" }),
  leaveConnection: (id: number) => request<FriendRelation>(`/api/social/friends/${id}/leave`, { method: "POST" }),
  relations: () => request<RelationMention[]>("/api/relation/list"),
  relationStats: () => request<Record<string, number>>("/api/relation/stats"),
  relationHighEmotion: () => request<RelationMention[]>("/api/relation/high-emotion"),
  relationTimeline: (label: string) => request<RelationTimelinePoint[]>(`/api/relation/timeline?label=${encodeURIComponent(label)}`),
  relationHealth: (label: string) => request<RelationHealth>(`/api/relation/health?label=${encodeURIComponent(label)}`)
};

export type AsrResult = { text: string; audioDurationSec: number; speechRate: number; pauseCount: number; longPauseCount: number; inputConfidence: number };

// Voice input: multipart upload to the ASR endpoint. Bypasses request()'s JSON Content-Type so the
// browser sets the multipart boundary; still carries CSRF (session) or bearer (mobile) auth.
export async function transcribeAudio(blob: Blob): Promise<AsrResult> {
  const form = new FormData();
  form.append("file", blob, "voice.webm");
  const headers = new Headers();
  const token = await accessTokenProvider?.() ?? null;
  if (token) headers.set("Authorization", `Bearer ${token}`);
  else { const c = await getOrLoadCsrf(); headers.set(c.headerName, c.token); }
  const response = await fetch(apiUrl("/api/asr/transcribe"), {
    method: "POST", body: form, headers, credentials: token ? "omit" : "include"
  });
  if (!response.ok) throw new Error(`ASR HTTP ${response.status}`);
  const env = await response.json() as ApiEnvelope<AsrResult>;
  if (!env.success) throw new Error(env.message ?? "语音转写失败");
  return env.data;
}

export async function streamAurora(
  input: Pick<CoreChatRequest, "sessionId" | "message"> & { mode: string },
  signal: AbortSignal,
  onEvent: (event: AuroraStreamEvent) => void
): Promise<void> {
  const body: CoreChatRequest = input;
  const staged = await request<CoreStreamStage>("/api/v1/aurora/stream-stage", {
    method: "POST", body: JSON.stringify(body)
  });
  const query = new URLSearchParams({ token: staged.token });
  let token = await accessTokenProvider?.() ?? null;
  if (bearerRequired && !token) throw new Error("Mobile OIDC authentication is required");
  let response = await fetch(apiUrl(`/api/v1/aurora/stream?${query}`), {
    credentials: token ? "omit" : "include", headers: { Accept: "text/event-stream", ...(token ? { Authorization: `Bearer ${token}` } : {}) }, signal
  });
  if (response.status === 401 && token && accessTokenInvalidator) {
    await accessTokenInvalidator();
    token = await accessTokenProvider?.() ?? null;
    if (token) response = await fetch(apiUrl(`/api/v1/aurora/stream?${query}`), {
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
  let response = await fetch(apiUrl(`/api/v1/aurora/turns/${turnId}/events`), {
    credentials: token ? "omit" : "include",
    headers: { Accept: "text/event-stream", ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(lastEventId ? { "Last-Event-ID": lastEventId } : {}) }
  });
  if (response.status === 401 && token && accessTokenInvalidator) {
    await accessTokenInvalidator();
    token = await accessTokenProvider?.() ?? null;
    if (token) response = await fetch(apiUrl(`/api/v1/aurora/turns/${turnId}/events`), {
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
