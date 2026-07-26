import { describe, expect, it } from "vitest";
import { APP_COPY } from "./appCopy";

describe("APP_COPY Chinese locale closure", () => {
  it("localizes Aurora's visible shell eyebrows", () => {
    const zh = APP_COPY["zh-CN"];
    expect([
      zh.heroEyebrow,
      zh.presenceEyebrow,
      zh.returnsEyebrow,
      zh.returnedEyebrow
    ]).toEqual([
      "内宇宙 · Aurora",
      "Aurora，与你同在",
      "Aurora 的回来约定",
      "Aurora 如约回来"
    ]);
    expect(zh.heroEyebrow).not.toBe("INNER COSMOS · AURORA");
    expect(zh.presenceEyebrow).not.toBe("AURORA, WITH YOU");
  });
});
