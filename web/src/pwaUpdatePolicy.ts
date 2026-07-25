export type PwaRegisterType = "prompt" | "autoUpdate";

/**
 * Classroom builds favour a consistent, current demo on every device. The normal
 * web build keeps the gentler prompt flow so an update cannot interrupt an active
 * Aurora conversation.
 */
export function pwaRegisterTypeForMode(mode: string): PwaRegisterType {
  return mode === "classroom" ? "autoUpdate" : "prompt";
}

export function shouldActivatePwaUpdateImmediately(mode: string): boolean {
  return mode === "classroom";
}
