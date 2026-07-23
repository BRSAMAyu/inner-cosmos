// Gemini audit 4.5 (CONFIRMED/P1): "create-draft后 send 失败，重试会再次 create." Every "write a
// draft, then send it" flow in this app (Resonance's compose-a-new-letter-to-a-match, and the
// slow-letter inbox's reply-with-letter) used to call create-draft again on every retry, because
// neither flow remembered the draft id a PRIOR attempt had already created. If send failed (e.g. a
// network blip) after the draft had already been created, the user's retry silently produced a
// second, duplicate draft/letter server-side.
//
// sendComposedLetter is the shared, retry-safe primitive both flows now go through: it creates the
// draft AT MOST ONCE per logical compose attempt. The caller persists the returned draft id (and
// the idempotency key it was created with) via onDraftCreated -- this happens BEFORE the send is
// attempted, so even if send throws, the caller still has what it needs to retry without creating
// another draft: pass the same `pending` value back in on the next call.
//
// The idempotency key is also handed to createDraft itself (not just sendDraft): if the draft
// creation call succeeds server-side but the response never reaches the client (a true
// at-least-once network failure), a caller that retries with a FRESH pending=null would normally
// re-invoke createDraft -- passing the same key each time within one logical attempt gives a
// backend that honours Idempotency-Key the chance to no-op instead of creating a duplicate. This
// worktree only owns the frontend contract; the backend idempotent-compose contract (audit item
// 1.8) is out of scope here and may not be live yet in this checkout -- see the item 4.5 report for
// that boundary.

export type DraftedLetterState = { draftId: number; idempotencyKey: string } | null;

export type SendComposedLetterParams<T> = {
  /** The caller's persisted state from a prior attempt of the SAME logical compose, or null for a
   *  brand-new compose. Must be reset to null by the caller once the send finally succeeds, and
   *  whenever the compose target changes (e.g. the user picks a different capsule/letter to reply
   *  to) so a stale draft from an unrelated compose is never resumed. */
  pending: DraftedLetterState;
  /** Called synchronously the moment a NEW draft is created, before send is attempted, so the
   *  caller can persist it (state/ref) even if the subsequent send throws. */
  onDraftCreated: (next: NonNullable<DraftedLetterState>) => void;
  createDraft: (idempotencyKey: string) => Promise<{ id: number }>;
  sendDraft: (draftId: number, idempotencyKey: string) => Promise<T>;
  newIdempotencyKey?: () => string;
};

function defaultIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
  return `compose-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export async function sendComposedLetter<T>({
  pending, onDraftCreated, createDraft, sendDraft, newIdempotencyKey = defaultIdempotencyKey
}: SendComposedLetterParams<T>): Promise<T> {
  let current = pending;
  if (!current) {
    const idempotencyKey = newIdempotencyKey();
    const draft = await createDraft(idempotencyKey);
    current = { draftId: draft.id, idempotencyKey };
    onDraftCreated(current);
  }
  return sendDraft(current.draftId, current.idempotencyKey);
}
