import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  layoutMemoryStars, memoryStarDiameter, MemoryStarfield, NEUTRAL_STAR_COLOR, NEUTRAL_STAR_DIAMETER,
  NEUTRAL_STAR_OPACITY, STARFIELD_VIEW_STORAGE_KEY
} from "./MemoryStarfield";
import type { MemoryOperation, StarfieldDetail, StarfieldScene } from "../api";

// The CP-11 view toggle persists into localStorage; a leaked stored preference would flip the
// initial presentation of every later test, so storage is reset alongside the DOM.
afterEach(() => { cleanup(); localStorage.clear(); vi.unstubAllGlobals(); });

const starfield: StarfieldScene = {
  mode: "TIME", modeExplanation: "按时间排列",
  stars: [{ id: 1, title: "星1", summary: "摘要", theme: "工作", color: "#fff", gravity: 1, glow: .8,
    freshness: 1, x: 0, y: 0, memoryLayer: "EPISODIC", confidence: .9, versionNo: 2, peopleTags: null,
    status: "ACTIVE", occurredAt: null, ariaLabel: "星1", connectedMemoryIds: [] }],
  accessibleList: [{ id: 1, title: "星1", summary: "摘要", theme: "工作", color: "#fff", gravity: 1, glow: .8,
    freshness: 1, x: 0, y: 0, memoryLayer: "EPISODIC", confidence: .9, versionNo: 2, peopleTags: null,
    status: "ACTIVE", occurredAt: null, ariaLabel: "星1", connectedMemoryIds: [] }],
  legend: { EPISODIC: "情景记忆" }, generatedAt: "2026-07-15T00:00:00Z"
};

const operation: MemoryOperation = {
  id: 5, operationType: "UPDATE", primaryMemoryId: 1, oldVersion: 1, newVersion: 2,
  reasonCode: "USER_CORRECTION", actorType: "USER", rollbackOfOperationId: null,
  status: "APPLIED", createdAt: "2026-07-15T00:00:00Z"
};

const detail: StarfieldDetail = {
  card: { id: 1, title: "星1", summary: "摘要", sourceSessionId: 1, versionNo: 2, memoryLayer: "EPISODIC", confidence: .9, provenanceRefs: null, userImportance: 1.5 },
  gravityExplanation: "因为你反复提到它", auroraObservation: "观察", provenanceExplanation: "来自一次对话",
  versionHistory: [], links: [], projectionReceipts: []
};

describe("MemoryStarfield", () => {
  it("fans missing-time memories into a stable, label-safe constellation", () => {
    const missingTimeStars = [1, 2, 3].map(id => ({
      ...starfield.stars[0], id, x: 90, y: 15, occurredAt: null
    }));
    const positions = layoutMemoryStars(missingTimeStars, "TIME");
    const points = missingTimeStars.map(item => positions.get(item.id)!);

    expect(new Set(points.map(point => `${point.left},${point.top}`)).size).toBe(3);
    expect(points.every(point => point.left >= 12 && point.left <= 82)).toBe(true);
    expect(points.every(point => point.top >= 14 && point.top <= 86)).toBe(true);
    expect(layoutMemoryStars([...missingTimeStars].reverse(), "TIME")).toEqual(positions);
  });

  it("keeps known time on the horizontal axis while separating collisions", () => {
    const sameMoment = [1, 2, 3].map(id => ({
      ...starfield.stars[0], id, x: 90, y: 0, occurredAt: "2026-07-15T08:00:00Z"
    }));
    const points = sameMoment.map(item => layoutMemoryStars(sameMoment, "TIME").get(item.id)!);

    expect(points[0].left).toBeLessThanOrEqual(82);
    expect(new Set(points.map(point => `${point.left},${point.top}`)).size).toBe(3);
    expect(Math.max(...points.map(point => point.left)) - Math.min(...points.map(point => point.left))).toBeGreaterThanOrEqual(30);
  });

  it("keeps a large same-moment burst readable beyond twelve memories", () => {
    const burst = Array.from({ length: 18 }, (_, index) => ({
      ...starfield.stars[0], id: index + 1, x: 90, y: 0, occurredAt: "2026-07-15T08:00:00Z"
    }));
    const points = burst.map(item => layoutMemoryStars(burst, "TIME").get(item.id)!);
    expect(new Set(points.map(point => `${point.left},${point.top}`)).size).toBe(18);
  });

  it("makes emotional gravity visibly change star size without unbounded growth", () => {
    expect(memoryStarDiameter(0)).toBe(10);
    expect(memoryStarDiameter(1)).toBe(16);
    expect(memoryStarDiameter(2)).toBe(22);
    expect(memoryStarDiameter(99)).toBe(30);
  });

  it("draws each visible memory relationship once", () => {
    const linked = {
      ...starfield,
      stars: [
        { ...starfield.stars[0], connectedMemoryIds: [2] },
        { ...starfield.stars[0], id: 2, title: "星2", connectedMemoryIds: [1] }
      ]
    };
    const { container } = render(<MemoryStarfield starfield={linked} starfieldBusy={false}
      onChangeMode={() => undefined} starfieldDetail={null} detailBusy={null}
      onRevealStar={() => undefined} onCloseDetail={() => undefined} memoryOperations={[]}
      rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={() => undefined} />);
    expect(container.querySelectorAll(".cosmos-links line")).toHaveLength(1);
  });

  it("delegates a mode switch without mutating its own state", () => {
    const onChangeMode = vi.fn();
    render(<MemoryStarfield starfield={starfield} starfieldBusy={false} onChangeMode={onChangeMode}
      starfieldDetail={null} detailBusy={null} onRevealStar={() => undefined} onCloseDetail={() => undefined}
      memoryOperations={[]} rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "主题" }));
    expect(onChangeMode).toHaveBeenCalledWith("THEME");
  });

  it("reveals and closes a star's provenance panel", () => {
    const onRevealStar = vi.fn();
    const onCloseDetail = vi.fn();
    render(<MemoryStarfield starfield={starfield} starfieldBusy={false} onChangeMode={() => undefined}
      starfieldDetail={detail} detailBusy={null} onRevealStar={onRevealStar} onCloseDetail={onCloseDetail}
      memoryOperations={[]} rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "查看来源与变化" }));
    expect(onRevealStar).toHaveBeenCalledWith(1);
    expect(screen.getByText("来自一次对话")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "关闭记忆来源" }));
    expect(onCloseDetail).toHaveBeenCalledOnce();
  });

  it("moves focus into a loaded star detail and lets Escape close it", () => {
    const onCloseDetail = vi.fn();
    render(<MemoryStarfield starfield={starfield} starfieldBusy={false} onChangeMode={() => undefined}
      starfieldDetail={detail} detailBusy={null} onRevealStar={() => undefined} onCloseDetail={onCloseDetail}
      memoryOperations={[]} rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={() => undefined} />);
    const close = screen.getByRole("button", { name: "关闭记忆来源" });
    expect(close).toHaveFocus();
    const summaries = screen.getByRole("dialog").querySelectorAll("summary");
    (summaries[summaries.length - 1] as HTMLElement).focus();
    fireEvent.keyDown(document, { key: "Tab" });
    expect(close).toHaveFocus();
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onCloseDetail).toHaveBeenCalledOnce();
  });

  it("opens a memory when the user clicks its visible star, not only the list action", () => {
    const onRevealStar = vi.fn();
    render(<MemoryStarfield starfield={starfield} starfieldBusy={false} onChangeMode={() => undefined}
      starfieldDetail={null} detailBusy={null} onRevealStar={onRevealStar} onCloseDetail={() => undefined}
      memoryOperations={[]} rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "打开记忆：星1" }));
    expect(onRevealStar).toHaveBeenCalledExactlyOnceWith(1);
  });

  it("shows an immediate visible loading drawer while a clicked star is being fetched", () => {
    render(<MemoryStarfield starfield={starfield} starfieldBusy={false} onChangeMode={() => undefined}
      starfieldDetail={null} detailBusy={1} selectedStarId={1}
      onRevealStar={() => undefined} onCloseDetail={() => undefined}
      memoryOperations={[]} rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={() => undefined} />);
    expect(screen.getByRole("dialog", { name: "记忆来源与变化" })).toHaveAttribute("aria-busy", "true");
    expect(screen.getByRole("dialog", { name: "记忆来源与变化" })).toHaveFocus();
    expect(screen.getByRole("status")).toHaveTextContent("正在补充");
  });

  it("keeps a useful local preview and retry action when provenance loading fails", () => {
    const onRevealStar = vi.fn();
    render(<MemoryStarfield locale="en-SG" starfield={starfield} starfieldBusy={false}
      onChangeMode={() => undefined} starfieldDetail={null} detailBusy={null} selectedStarId={1}
      detailError="HTTP 503" onRevealStar={onRevealStar} onCloseDetail={() => undefined}
      memoryOperations={[]} rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={() => undefined} />);

    expect(screen.getByRole("dialog")).toHaveTextContent("摘要");
    expect(screen.getByRole("alert")).toHaveTextContent("The source detail did not load");
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(onRevealStar).toHaveBeenCalledExactlyOnceWith(1);
  });

  it("explains an empty view and offers a direct route back to Aurora", () => {
    const onStartMemory = vi.fn();
    render(<MemoryStarfield starfield={{ ...starfield, mode: "THEME", stars: [], accessibleList: [] }}
      starfieldBusy={false} onChangeMode={() => undefined} starfieldDetail={null} detailBusy={null}
      onRevealStar={() => undefined} onCloseDetail={() => undefined} memoryOperations={[]} rollbackBusy={null}
      onRollback={() => undefined} onCorrectMemory={() => undefined} onStartMemory={onStartMemory} />);
    expect(screen.getByText("还没有足够的记忆形成主题")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "回到 Aurora 留下第一颗星" }));
    expect(onStartMemory).toHaveBeenCalledOnce();
  });

  it("offers rollback only for reversible operations still applied", () => {
    const onRollback = vi.fn();
    render(<MemoryStarfield starfield={starfield} starfieldBusy={false} onChangeMode={() => undefined}
      starfieldDetail={null} detailBusy={null} onRevealStar={() => undefined} onCloseDetail={() => undefined}
      memoryOperations={[operation]} rollbackBusy={null} onRollback={onRollback} onCorrectMemory={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "撤回这次变更" }));
    expect(onRollback).toHaveBeenCalledWith(operation);
  });

  it("lets the user start a correction from a specific memory star", () => {
    const onCorrectMemory = vi.fn();
    render(<MemoryStarfield starfield={starfield} starfieldBusy={false} onChangeMode={() => undefined}
      starfieldDetail={null} detailBusy={null} onRevealStar={() => undefined} onCloseDetail={() => undefined}
      memoryOperations={[]} rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={onCorrectMemory} />);
    fireEvent.click(screen.getByRole("button", { name: "这条不准确了" }));
    expect(onCorrectMemory).toHaveBeenCalledWith(starfield.accessibleList[0]);
  });

  it("tunes importance from the current value and saves it for that card", () => {
    const onUpdateImportance = vi.fn();
    render(<MemoryStarfield starfield={starfield} starfieldBusy={false} onChangeMode={() => undefined}
      starfieldDetail={detail} detailBusy={null} onRevealStar={() => undefined} onCloseDetail={() => undefined}
      memoryOperations={[]} rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={() => undefined}
      onUpdateImportance={onUpdateImportance} onArchive={() => undefined} importanceBusy={null} archiveBusy={null} />);
    const slider = screen.getByRole("slider") as HTMLInputElement;
    expect(slider.value).toBe("1.5");
    fireEvent.change(slider, { target: { value: "0.8" } });
    fireEvent.click(screen.getByRole("button", { name: "保存重要度" }));
    expect(onUpdateImportance).toHaveBeenCalledExactlyOnceWith(1, 0.8);
  });

  it("archives the revealed memory card", () => {
    const onArchive = vi.fn();
    render(<MemoryStarfield starfield={starfield} starfieldBusy={false} onChangeMode={() => undefined}
      starfieldDetail={detail} detailBusy={null} onRevealStar={() => undefined} onCloseDetail={() => undefined}
      memoryOperations={[]} rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={() => undefined}
      onUpdateImportance={() => undefined} onArchive={onArchive} importanceBusy={null} archiveBusy={null} />);
    fireEvent.click(screen.getByRole("button", { name: "归档这颗记忆" }));
    expect(onArchive).toHaveBeenCalledExactlyOnceWith(1);
  });

  it("disables importance and archive actions while that card is busy", () => {
    // AsyncButton (web/src/loading.tsx) holds the original label for the first second of a busy
    // state (the spec's "don't flash before 1s" rule), so a synchronous render/assert -- as every
    // other AsyncButton-adopting component's tests in this repo already do -- checks disabled on
    // the original label, not an instantly-swapped busy label.
    render(<MemoryStarfield starfield={starfield} starfieldBusy={false} onChangeMode={() => undefined}
      starfieldDetail={detail} detailBusy={null} onRevealStar={() => undefined} onCloseDetail={() => undefined}
      memoryOperations={[]} rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={() => undefined}
      onUpdateImportance={() => undefined} onArchive={() => undefined} importanceBusy={1} archiveBusy={1} />);
    expect(screen.getByRole("button", { name: "保存重要度" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "归档这颗记忆" })).toBeDisabled();
  });

  it("renders the starfield, view modes and history in English when locale is en-SG", () => {
    render(<MemoryStarfield locale="en-SG" starfield={starfield} starfieldBusy={false} onChangeMode={() => undefined}
      starfieldDetail={null} detailBusy={null} onRevealStar={() => undefined} onCloseDetail={() => undefined}
      memoryOperations={[operation]} rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={() => undefined}
      onUpdateImportance={() => undefined} onArchive={() => undefined} />);
    expect(screen.getByRole("heading", { name: "Your memory isn't a filing cabinet" })).toBeVisible();
    expect(screen.getByText(/Each star is an understanding Aurora formed/)).toBeVisible();
    expect(screen.getByText(/Choose a star to see how this memory shapes/)).toBeVisible();
    expect(screen.getByText("1 current memory")).toBeVisible(); // singular
    expect(screen.getByRole("button", { name: "Time" })).toBeVisible();
    expect(screen.getByRole("button", { name: "View source & changes" })).toBeVisible();
    expect(screen.getByRole("button", { name: "This isn't accurate" })).toBeVisible();
    expect(screen.getByRole("heading", { name: "Recent memory changes" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Undo this change" })).toBeVisible();
    expect(screen.getByText("Time flows left to right. Memories from the same moment fan apart, while those without an exact time rest in a gentle central orbit.")).toBeVisible();
    expect(screen.getByText("Emotional gravity and long-term importance")).toBeVisible();
    expect(screen.queryByText("按时间排列")).not.toBeInTheDocument();
    expect(screen.queryByText("情景记忆")).not.toBeInTheDocument();
  });
});

/** jsdom ships no window.matchMedia; stub it the way loading.test.tsx does. */
function stubReducedMotion(reduce: boolean) {
  vi.stubGlobal("matchMedia", (query: string) => ({
    matches: reduce && query.includes("reduce"),
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
    onchange: null
  }));
}

/** A scene whose stars genuinely differ in emotional gravity, colour and glow. */
function variedScene(encodingOff: boolean, locale?: "zh-CN"): StarfieldScene {
  const gravities = [0.1, 1.4, 3];
  const stars = gravities.map((gravity, index) => ({
    ...starfield.stars[0], id: index + 1, title: `星${index + 1}`, gravity,
    color: ["#ff4b4b", "#4bff88", "#4b88ff"][index], glow: [0.3, 0.6, 0.95][index]
  }));
  return {
    ...starfield, stars, accessibleList: stars,
    legend: { "尺寸": "情感重力与长期重要性", "亮度": "近期活跃程度", "边缘": "理解置信度",
      "连线": "合并、延续或人物关联", "距离": "从右侧列表打开可访问详情" },
    ...(encodingOff ? { emotionEncoding: false } : {})
  } as StarfieldScene;
}

describe("CP-11 accessible list view — explicit toggle, persistence, reduced motion", () => {
  const renderStarfield = (scene: StarfieldScene = starfield) => render(<MemoryStarfield
    starfield={scene} starfieldBusy={false} onChangeMode={() => undefined} starfieldDetail={null}
    detailBusy={null} onRevealStar={() => undefined} onCloseDetail={() => undefined}
    memoryOperations={[]} rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={() => undefined} />);

  it("lets the user leave the 3D-style map for the plain list and remembers the choice", () => {
    const { unmount } = renderStarfield();
    expect(screen.getByRole("button", { name: "打开记忆：星1" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "列表视图", pressed: false }));
    // The map (and its absolutely-positioned star buttons) is gone from the tree and the tab
    // order; the list keeps its own keyboard-reachable actions.
    expect(screen.queryByRole("button", { name: "打开记忆：星1" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "查看来源与变化" })).toBeVisible();
    expect(screen.getByRole("button", { name: "这条不准确了" })).toBeVisible();
    expect(localStorage.getItem(STARFIELD_VIEW_STORAGE_KEY)).toBe("list");
    unmount();
    renderStarfield();
    expect(screen.queryByRole("button", { name: "打开记忆：星1" })).not.toBeInTheDocument();
  });

  it("returns to the map on request and persists the map preference instead", () => {
    renderStarfield();
    fireEvent.click(screen.getByRole("button", { name: "列表视图" }));
    fireEvent.click(screen.getByRole("button", { name: "星图视图" }));
    expect(screen.getByRole("button", { name: "打开记忆：星1" })).toBeVisible();
    expect(localStorage.getItem(STARFIELD_VIEW_STORAGE_KEY)).toBe("map");
  });

  it("starts on the list when the user prefers reduced motion and nothing is stored", () => {
    stubReducedMotion(true);
    renderStarfield();
    expect(screen.queryByRole("button", { name: "打开记忆：星1" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "查看来源与变化" })).toBeVisible();
  });

  it("keeps an explicitly stored map choice even under prefers-reduced-motion", () => {
    stubReducedMotion(true);
    localStorage.setItem(STARFIELD_VIEW_STORAGE_KEY, "map");
    renderStarfield();
    expect(screen.getByRole("button", { name: "打开记忆：星1" })).toBeVisible();
  });

  it("shows every memory in the list view instead of the map's folded preview", () => {
    const five = [1, 2, 3, 4, 5].map(id => ({ ...starfield.stars[0], id, title: `星${id}` }));
    const { container } = renderStarfield({ ...starfield, stars: five, accessibleList: five });
    expect(container.querySelectorAll(".cosmos-more-memories")).toHaveLength(1); // map view folds rows 4–5
    fireEvent.click(screen.getByRole("button", { name: "列表视图" }));
    expect(container.querySelectorAll(".cosmos-list")).toHaveLength(1); // one plain list, no fold
    expect(container.querySelectorAll(".cosmos-list > li")).toHaveLength(5);
    expect(screen.queryByText(/展开其余/)).not.toBeInTheDocument();
  });
});

describe("CP-11 emotional-encoding closure — the list leaks nothing the map hides", () => {
  const renderVaried = (encodingOff: boolean, locale?: "zh-CN" | "en-SG") => render(<MemoryStarfield
    locale={locale} starfield={variedScene(encodingOff)} starfieldBusy={false}
    onChangeMode={() => undefined} starfieldDetail={null} detailBusy={null}
    onRevealStar={() => undefined} onCloseDetail={() => undefined} memoryOperations={[]} rollbackBusy={null}
    onRollback={() => undefined} onCorrectMemory={() => undefined} />);

  it("keeps gravity, colour and glow visible per star while encoding is on", () => {
    const { container } = renderVaried(false);
    const widths = [...container.querySelectorAll<HTMLSpanElement>(".cosmos-star-core")]
      .map(core => core.style.width);
    expect(new Set(widths).size).toBe(3); // size still carries emotional gravity
    const colors = [...container.querySelectorAll<HTMLButtonElement>(".cosmos-star")]
      .map(star => star.style.color);
    expect(new Set(colors).size).toBe(3);
    expect(container.querySelector(".cosmos-space")!.getAttribute("data-emotion-encoding")).toBe("on");
    expect(screen.getByText("情感重力与长期重要性")).toBeVisible(); // legend still explains encoding
  });

  it("flattens size, colour and glow to one neutral value on the map when encoding is off", () => {
    const { container } = renderVaried(true);
    const cores = [...container.querySelectorAll<HTMLSpanElement>(".cosmos-star-core")];
    expect(cores).toHaveLength(3);
    for (const core of cores) {
      expect(core.style.width).toBe(`${NEUTRAL_STAR_DIAMETER}px`);
      expect(core.style.height).toBe(`${NEUTRAL_STAR_DIAMETER}px`);
      expect(core.style.background).toBe(NEUTRAL_STAR_COLOR);
    }
    for (const star of [...container.querySelectorAll<HTMLButtonElement>(".cosmos-star")]) {
      expect(star.style.color).toBe(NEUTRAL_STAR_COLOR);
      expect(star.style.opacity).toBe(String(NEUTRAL_STAR_OPACITY));
    }
    expect(container.querySelector(".cosmos-space")!.getAttribute("data-emotion-encoding")).toBe("off");
  });

  it("shows the neutral legend note and never the emotion-describing entries when off", () => {
    renderVaried(true);
    expect(screen.getByText("已关闭情绪编码：星体大小、颜色与亮度统一为中性，不体现情绪权重")).toBeVisible();
    expect(screen.queryByText("情感重力与长期重要性")).not.toBeInTheDocument(); // size entry rewritten
    expect(screen.queryByText("近期活跃程度")).not.toBeInTheDocument(); // glow entry dropped
  });

  it("keeps the accessible list free of emotion-dimension wording when encoding is off", () => {
    const { container } = renderVaried(true);
    for (const list of [...container.querySelectorAll<HTMLOListElement>(".cosmos-list")]) {
      expect(list.textContent).not.toMatch(/重力|强度|情绪权重/);
    }
    // The list rows still carry their non-emotion facts: layer, confidence and version.
    expect(container.querySelector(".cosmos-list")!.textContent).toContain("置信度 90% · v2");
  });

  it("applies the same neutral legend in English instead of the emotion-describing one", () => {
    renderVaried(true, "en-SG");
    expect(screen.getByText(/Emotional encoding is off — size, colour and glow stay one neutral value/))
      .toBeVisible();
    expect(screen.queryByText("Emotional gravity and long-term importance")).not.toBeInTheDocument();
    expect(screen.queryByText("Recent activity")).not.toBeInTheDocument();
  });

  it("stays closed in the list view too — no map-only channel survives the switch", () => {
    const { container } = renderVaried(true);
    fireEvent.click(screen.getByRole("button", { name: "列表视图" }));
    expect(container.querySelector(".cosmos-map")).not.toBeInTheDocument();
    for (const list of [...container.querySelectorAll<HTMLOListElement>(".cosmos-list")]) {
      expect(list.textContent).not.toMatch(/重力|强度|情绪权重/);
    }
    expect(screen.getByText(/已关闭情绪编码/)).toBeVisible(); // closure statement remains visible
  });
});
