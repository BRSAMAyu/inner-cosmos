import { describe, expect, it } from "vitest";
import { demoContentText, localizeDemoPersona } from "./demoContentLocale";

describe("curated demo content localisation", () => {
  it("localises the two social-match personas and keeps en-SG canonical", () => {
    expect(demoContentText("The One Who Walks by the River", "zh-CN")).toBe("沿河行走的人");
    expect(demoContentText("The One Learning to Include Herself in Care", "zh-CN"))
      .toBe("学着也照顾自己的人");
    expect(demoContentText(
      "On care, responsibility and boundaries: still gentle, no longer proving care through exhaustion.",
      "zh-CN"
    )).toBe("关于照顾、责任与边界：依然温柔，但不再用耗尽自己证明在乎。");
    expect(demoContentText("The One Who Walks by the River", "en-SG"))
      .toBe("The One Who Walks by the River");
  });

  it("localises representative memory, theme and timeline fixtures for both stories", () => {
    expect(demoContentText("The regular Wednesday riverside route", "zh-CN"))
      .toBe("每周三固定的河边路线");
    expect(demoContentText("Elsewhere and belonging", "zh-CN")).toBe("异乡与归属");
    expect(demoContentText("Aurora remembers this river route is not escape; it is how you recover sensation.", "zh-CN"))
      .toBe("Aurora 记得，这条河边路线不是逃避，而是你找回感受的方式。");
    expect(demoContentText("The guilt that appears during rest", "zh-CN")).toBe("休息时冒出来的愧疚");
    expect(demoContentText("Recovering without guilt", "zh-CN")).toBe("不带愧疚地恢复");
    expect(demoContentText("Nothing terrible happened last night. That is new evidence you can trust.", "zh-CN"))
      .toBe("昨晚没有发生可怕的事。这是你可以相信的新证据。");
  });

  it("localises the active Lin Che memory, theme and timeline fixtures", () => {
    expect(demoContentText("The self-blame loop when a project stalls", "zh-CN"))
      .toBe("项目停滞时的自责循环");
    expect(demoContentText("Real AI without scripted replies", "zh-CN"))
      .toBe("不靠固定话术的真实 AI");
    expect(demoContentText(
      "Aurora noticed that you need one verifiable end-to-end loop today, not another concept.",
      "zh-CN"
    )).toBe("Aurora 注意到，你今天需要的是一个可验证的端到端闭环，而不是又一个概念。");
  });

  it("localises the demo chooser without mutating the API object", () => {
    const canonical = {
      key: "shen-yan",
      name: "Shen Yan",
      headline: "Finding herself again, far from home",
      story: "Five months of exchange life, portfolio work and a slowly changing sense of loneliness",
      themes: ["Elsewhere", "Solitude", "Creative work", "Belonging"],
      active: false
    };
    const zh = localizeDemoPersona(canonical, "zh-CN");

    expect(zh).toMatchObject({
      name: "沈砚",
      headline: "在远方重新找到自己",
      themes: ["异乡", "独处", "创作", "归属"]
    });
    expect(canonical.name).toBe("Shen Yan");
    expect(localizeDemoPersona(canonical, "en-SG")).toBe(canonical);
  });
});
