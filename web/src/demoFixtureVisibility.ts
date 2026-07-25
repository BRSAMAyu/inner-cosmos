import type { CapsuleMatch, PublicCapsule } from "./api";

const VERIFIER_INTRO = "A consent-bound facet created by the public demo verifier.";

export function isAutomatedDemoCapsule(capsule: PublicCapsule): boolean {
  return /^Demo Echo \d+$/.test(capsule.pseudonym) && capsule.intro === VERIFIER_INTRO;
}

export function userVisiblePublicCapsules(capsules: PublicCapsule[]): PublicCapsule[] {
  return capsules.filter(capsule => !isAutomatedDemoCapsule(capsule));
}

export function userVisibleResonanceMatches(matches: CapsuleMatch[]): CapsuleMatch[] {
  return matches.filter(match => !isAutomatedDemoCapsule(match.capsule));
}
