import { describe, expect, it, vi } from "vitest";
import { sendComposedLetter, type DraftedLetterState } from "./composeAndSend";

// Gemini audit 4.5 (CONFIRMED/P1): "after creating a draft, if send fails, retry creates ANOTHER
// draft". sendComposedLetter is the shared retry-safe compose-and-send primitive: it must create a
// draft AT MOST ONCE per logical compose attempt, persisting the draft id (and the idempotency key
// it was created with) via onDraftCreated BEFORE attempting to send, so that if the caller retries
// after a failed send, it can pass the SAME pending state back in and this function reuses the
// existing draft instead of calling createDraft again.

describe("sendComposedLetter", () => {
  it("creates a draft once, persists it via onDraftCreated, then sends it", async () => {
    const createDraft = vi.fn().mockResolvedValue({ id: 42 });
    const sendDraft = vi.fn().mockResolvedValue({ id: 42, status: "SENT" });
    const onDraftCreated = vi.fn();

    const result = await sendComposedLetter({
      pending: null, onDraftCreated, createDraft, sendDraft,
      newIdempotencyKey: () => "key-1"
    });

    expect(createDraft).toHaveBeenCalledExactlyOnceWith("key-1");
    expect(onDraftCreated).toHaveBeenCalledExactlyOnceWith({ draftId: 42, idempotencyKey: "key-1" });
    expect(sendDraft).toHaveBeenCalledExactlyOnceWith(42, "key-1");
    expect(result).toEqual({ id: 42, status: "SENT" });
  });

  it("a retry after a failed send reuses the SAME draft id and idempotency key -- does NOT call createDraft again", async () => {
    const createDraft = vi.fn();
    const sendDraft = vi.fn().mockResolvedValue({ id: 42, status: "SENT" });
    const pending: DraftedLetterState = { draftId: 42, idempotencyKey: "key-1" };

    const result = await sendComposedLetter({
      pending, onDraftCreated: vi.fn(), createDraft, sendDraft
    });

    expect(createDraft).not.toHaveBeenCalled();
    expect(sendDraft).toHaveBeenCalledExactlyOnceWith(42, "key-1");
    expect(result).toEqual({ id: 42, status: "SENT" });
  });

  it("full retry sequence: first attempt's draft creation succeeds but send fails -- the SECOND attempt (using the caller's persisted pending state) must not create a second draft", async () => {
    const createDraft = vi.fn().mockResolvedValue({ id: 7 });
    const sendDraft = vi.fn()
      .mockRejectedValueOnce(new Error("network down"))
      .mockResolvedValueOnce({ id: 7, status: "SENT" });
    let persisted: DraftedLetterState = null;
    const onDraftCreated = (next: NonNullable<DraftedLetterState>) => { persisted = next; };

    await expect(sendComposedLetter({
      pending: persisted, onDraftCreated, createDraft, sendDraft, newIdempotencyKey: () => "key-7"
    })).rejects.toThrow("network down");

    // Even though the send failed, the draft was created and the caller's onDraftCreated callback
    // must have captured it BEFORE the send was attempted -- this is what a caller uses to persist
    // across the failure so a retry can reuse it.
    expect(persisted).toEqual({ draftId: 7, idempotencyKey: "key-7" });
    expect(createDraft).toHaveBeenCalledTimes(1);

    // The caller retries, passing the persisted pending state back in.
    const result = await sendComposedLetter({
      pending: persisted, onDraftCreated, createDraft, sendDraft
    });

    expect(createDraft).toHaveBeenCalledTimes(1); // still only once across both attempts
    expect(sendDraft).toHaveBeenNthCalledWith(2, 7, "key-7");
    expect(result).toEqual({ id: 7, status: "SENT" });
  });
});
