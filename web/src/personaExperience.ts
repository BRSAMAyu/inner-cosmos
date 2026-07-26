import type { CapsuleMatch, CapsuleQuota, PersonaMessage, PersonaSession, PublicCapsule, ResonanceStrategy } from "./api";

export async function resumeOrCreatePersonaConversation(capsuleId: number, deps: {
  activeSession: (capsuleId: number) => Promise<PersonaSession | null>;
  createSession: (capsuleId: number) => Promise<PersonaSession>;
  messages: (sessionId: number) => Promise<PersonaMessage[]>;
  quota: (capsuleId: number) => Promise<CapsuleQuota>;
}): Promise<{ session: PersonaSession; history: PersonaMessage[]; quota: CapsuleQuota; resumed: boolean }> {
  const active = await deps.activeSession(capsuleId);
  const session = active ?? await deps.createSession(capsuleId);
  const [history, quota] = await Promise.all([deps.messages(session.id), deps.quota(capsuleId)]);
  return { session, history, quota, resumed: active !== null };
}

export async function reloadPersonaCandidates(strategy: ResonanceStrategy, deps: {
  matches: (strategy: ResonanceStrategy) => Promise<CapsuleMatch[]>;
  plaza: () => Promise<PublicCapsule[]>;
}): Promise<{ matches: CapsuleMatch[]; plaza: PublicCapsule[] }> {
  const [matches, plaza] = await Promise.all([deps.matches(strategy), deps.plaza()]);
  return { matches, plaza };
}
