import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { InnerCosmosOverview } from "./InnerCosmosOverview";
import type { StarfieldScene } from "../api";

afterEach(cleanup);

const starfield: StarfieldScene = {
  mode: "TIME", modeExplanation: "", generatedAt: "2026-07-25", legend: {},
  stars: [{ id: 9, title: "工作室里不敢交出的那张图", summary: "", theme: "正在成形的理解",
    color: "#aaa", gravity: 2.4, glow: .8, freshness: .7, x: 0, y: 0,
    memoryLayer: "EPISODIC", confidence: .82, versionNo: 1, peopleTags: null,
    status: "ACTIVE", occurredAt: "2026-07-25", ariaLabel: "", connectedMemoryIds: [] }],
  accessibleList: []
};

describe("InnerCosmosOverview", () => {
  it("surfaces weather, constellations, unresolved gravity and recent change as four direct paths", () => {
    const onOpenMemory = vi.fn();
    const onOpenDaily = vi.fn();
    const onOpenWeekly = vi.fn();
    const onOpenBeliefs = vi.fn();
    render(<InnerCosmosOverview starfield={starfield}
      dailyRecords={[{ id: 1, recordDate: "2026-07-25", theme: "不急着决定属于哪里",
        eventSummary: "", emotionWeather: "CLEAR", cognitiveSummary: "归属不必只选一边",
        todoSummary: "", auroraSummary: "", capsuleSuggested: false, userAccepted: true, status: "ACTIVE" }]}
      themes={[{ id: 1, themeName: "异乡与归属", themeSummary: "", themeType: "IDENTITY",
        keywords: "", memoryCount: 3, averageGravity: 7.3, lastTouchedAt: "", status: "ACTIVE" }]}
      onOpenMemory={onOpenMemory} onOpenDaily={onOpenDaily}
      onOpenWeekly={onOpenWeekly} onOpenBeliefs={onOpenBeliefs} />);

    expect(screen.getByText("清朗")).toBeVisible();
    expect(screen.getByText("异乡与归属")).toBeVisible();
    expect(screen.getByText("工作室里不敢交出的那张图")).toBeVisible();
    expect(screen.getByText("归属不必只选一边")).toBeVisible();
    expect(screen.getByText("你的记忆宇宙")).toBeVisible();
    expect(screen.getByText(/每颗星来自一次对话或记录/)).toBeVisible();
    expect(screen.queryByText("YOUR INNER COSMOS · NOW")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /工作室里不敢交出的那张图/ }));
    expect(onOpenMemory).toHaveBeenCalledExactlyOnceWith(9);
  });

  it("names the backend FOGGY weather in the overview", () => {
    render(<InnerCosmosOverview starfield={starfield}
      dailyRecords={[{ id: 1, recordDate: "2026-07-25", theme: "焦虑的一天", eventSummary: "",
        emotionWeather: "FOGGY", cognitiveSummary: "", todoSummary: "", auroraSummary: "",
        capsuleSuggested: false, userAccepted: true, status: "ACTIVE" }]}
      themes={[]} onOpenMemory={() => undefined} onOpenDaily={() => undefined}
      onOpenWeekly={() => undefined} onOpenBeliefs={() => undefined} />);
    expect(screen.getByText("有雾")).toBeVisible();
    expect(screen.queryByText("尚未命名")).not.toBeInTheDocument();
  });
});
