export type TurnStatus =
  | "GENERATING" | "PLANNED" | "STREAMING" | "PARTIAL"
  | "COMPLETED" | "INTERRUPTED" | "CANCELLED";

export interface DialogMessage {
  id: number;
  speaker: "USER" | "AURORA";
  textContent: string;
}

export interface TimelineBubble {
  id: number;
  bubbleOrder: number;
  content: string;
  status: "PLANNED" | "COMMITTED" | "CANCELLED";
  deliveredChars: number;
}

export interface TurnTimeline {
  turn: { id: number; status: TurnStatus };
  bubbles: TimelineBubble[];
  events: Array<{ eventSequence: number; eventType: string }>;
}

type EventBase<T extends string, P> = {
  id: string;
  type: T;
  payload: P;
};

export type AuroraStreamEvent =
  | EventBase<"turn.started", { turnId: number }>
  | EventBase<"turn.plan", { turnId: number; planId: number }>
  | EventBase<"bubble.started", { order: number }>
  | EventBase<"token", { content: string }>
  | EventBase<"segment", { break: true }>
  | EventBase<"bubble.completed", { order: number }>
  | EventBase<"meta", Record<string, unknown>>
  | EventBase<"turn.interrupted", { reason: string }>
  | EventBase<"turn.completed", { message: string }>
  | EventBase<"safety", { riskLevel: string; featureTarget: string; safeMessage?: string }>
  | EventBase<"error", { message: string }>
  | EventBase<"done", { message: string }>
  | EventBase<"timeline.event", {
      turnId: number;
      sequence: number;
      eventType: string;
      payload: Record<string, unknown>;
    }>;

// Gemini audit 4.2 (CONFIRMED/P0): event types that constitute a real end-of-turn. If the
// underlying connection closes (clean EOF) without ANY of these having been seen, that is NOT a
// successful completion -- it is indistinguishable from the connection just dropping mid-
// generation, and streamAurora's terminal-reason contract (see api.ts) uses this exact set to
// tell the two apart.
export const TERMINAL_EVENT_TYPES: ReadonlySet<AuroraStreamEvent["type"]> = new Set([
  "turn.completed", "turn.interrupted", "safety", "error", "done"
]);

/**
 * Gemini audit 4.2: the explicit terminal-state contract a stream function returns instead of
 * silently succeeding on any EOF.
 *   - "TERMINAL_EVENT": the stream ended after a real terminal event (done/turn.completed/
 *     turn.interrupted/safety/error) was observed -- a genuine, known end-of-turn.
 *   - "EOF_WITHOUT_TERMINAL": the connection closed with no terminal event ever seen -- the
 *     caller must treat this as still-in-flight, not as success, and attempt bounded recovery.
 */
export type StreamTerminalReason = "TERMINAL_EVENT" | "EOF_WITHOUT_TERMINAL";

export interface SseFrame {
  id: string;
  event: string;
  data: string;
}

/** Incremental SSE decoder: handles CRLF, split chunks and multiple data lines. */
export class SseDecoder {
  private buffer = "";

  push(chunk: string): SseFrame[] {
    this.buffer += chunk.replace(/\r\n/g, "\n");
    const frames: SseFrame[] = [];
    let boundary = this.buffer.indexOf("\n\n");
    while (boundary >= 0) {
      const raw = this.buffer.slice(0, boundary);
      this.buffer = this.buffer.slice(boundary + 2);
      const frame = decodeFrame(raw);
      if (frame) frames.push(frame);
      boundary = this.buffer.indexOf("\n\n");
    }
    return frames;
  }
}

function decodeFrame(raw: string): SseFrame | null {
  let id = "";
  let event = "message";
  const data: string[] = [];
  for (const line of raw.split("\n")) {
    if (!line || line.startsWith(":")) continue;
    const colon = line.indexOf(":");
    const field = colon < 0 ? line : line.slice(0, colon);
    let value = colon < 0 ? "" : line.slice(colon + 1);
    if (value.startsWith(" ")) value = value.slice(1);
    if (field === "id") id = value;
    else if (field === "event") event = value;
    else if (field === "data") data.push(value);
  }
  return data.length ? { id, event, data: data.join("\n") } : null;
}

export function toTypedEvent(frame: SseFrame): AuroraStreamEvent | null {
  let payload: unknown;
  try { payload = JSON.parse(frame.data); } catch { return null; }
  if (!payload || typeof payload !== "object") return null;
  return { id: frame.id, type: frame.event, payload } as AuroraStreamEvent;
}

export function sequenceFromEventId(id: string): number {
  const value = Number(id.split(":").at(-1));
  return Number.isFinite(value) ? value : 0;
}
