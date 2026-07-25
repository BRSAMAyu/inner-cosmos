import { describe, expect, it } from "vitest";
import {
  pwaRegisterTypeForMode,
  shouldActivatePwaUpdateImmediately,
} from "./pwaUpdatePolicy";

describe("PWA update policy", () => {
  it("activates classroom Demo updates immediately", () => {
    expect(pwaRegisterTypeForMode("classroom")).toBe("autoUpdate");
    expect(shouldActivatePwaUpdateImmediately("classroom")).toBe(true);
  });

  it.each(["production", "development", "mobile", "demo", "tauri"])(
    "keeps the user-controlled update flow in %s mode",
    (mode) => {
      expect(pwaRegisterTypeForMode(mode)).toBe("prompt");
      expect(shouldActivatePwaUpdateImmediately(mode)).toBe(false);
    },
  );
});
