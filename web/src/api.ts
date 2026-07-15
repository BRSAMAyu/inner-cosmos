import { SseDecoder, toTypedEvent, type AuroraStreamEvent, type DialogMessage, type TurnTimeline } from "./protocol";

type ApiEnvelope<T> = { success: boolean; data: T; message?: string; code?: string };
type Csrf = { token: string; headerName: string };
let csrf: Csrf | null = null;

async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && init.body) headers.set("Content-Type", "application/json");
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    csrf ??= await getCsrf();
    headers.set(csrf.headerName, csrf.token);
  }
  const response = await fetch(url, { ...init, headers, credentials: "include" });
  if (response.status === 403 && csrf) {
    csrf = null;
  }
  const body = await response.json() as ApiEnvelope<T>;
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
  login: (username: string, password: string) => request<unknown>("/api/auth/login", {
    method: "POST", body: JSON.stringify({ username, password })
  }),
  createSession: () => request<{ id: number }>("/api/dialog/session/create", {
    method: "POST", body: JSON.stringify({ title: "Aurora 对话", sessionType: "AURORA_CHAT" })
  }),
  messages: (sessionId: number) => request<DialogMessage[]>(`/api/dialog/session/${sessionId}/messages`),
  timeline: (turnId: number) => request<TurnTimeline>(`/api/aurora/turns/${turnId}/timeline`),
  stop: (turnId: number) => request<TurnTimeline>(`/api/aurora/turns/${turnId}/stop`, { method: "POST" })
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
