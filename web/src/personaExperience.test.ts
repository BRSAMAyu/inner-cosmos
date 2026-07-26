import { describe, expect, it, vi } from "vitest";
import type { CapsuleMatch, PersonaSession, PublicCapsule } from "./api";
import { reloadPersonaCandidates, resumeOrCreatePersonaConversation } from "./personaExperience";

const session: PersonaSession = { id: 7, capsuleId: 4, status: "ACTIVE", turnCount: 1, dailyLimit: 5 };

describe("persona experience flows", () => {
  it("restores the authoritative active session and history without creating a duplicate", async () => {
    const createSession = vi.fn();
    const result = await resumeOrCreatePersonaConversation(4, {
      activeSession: vi.fn().mockResolvedValue(session),
      createSession,
      messages: vi.fn().mockResolvedValue([{ id: 9, sessionId: 7, senderType: "CAPSULE", textContent: "上次的回声" }]),
      quota: vi.fn().mockResolvedValue({ turnCount: 1, remaining: 4, dailyLimit: 5, seed: false, quotaDate: "2026-07-26" })
    });
    expect(result.resumed).toBe(true);
    expect(result.history[0].textContent).toBe("上次的回声");
    expect(createSession).not.toHaveBeenCalled();
  });

  it("does not create a replacement session when active-session lookup fails", async () => {
    const createSession = vi.fn();
    await expect(resumeOrCreatePersonaConversation(4, {
      activeSession: vi.fn().mockRejectedValue(new Error("network unavailable")),
      createSession,
      messages: vi.fn(),
      quota: vi.fn()
    })).rejects.toThrow("network unavailable");
    expect(createSession).not.toHaveBeenCalled();
  });

  it("reloads both owner-filtered candidate surfaces after a block", async () => {
    const matches = [{ capsule: { id: 8 } } as CapsuleMatch];
    const plaza = [{ id: 8 } as PublicCapsule];
    await expect(reloadPersonaCandidates("MIRROR", {
      matches: vi.fn().mockResolvedValue(matches),
      plaza: vi.fn().mockResolvedValue(plaza)
    })).resolves.toEqual({ matches, plaza });
  });
});
