import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryStarfield } from "./MemoryStarfield";
import type { MemoryOperation, StarfieldDetail, StarfieldScene } from "../api";

afterEach(cleanup);

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
    render(<MemoryStarfield starfield={starfield} starfieldBusy={false} onChangeMode={() => undefined}
      starfieldDetail={detail} detailBusy={null} onRevealStar={() => undefined} onCloseDetail={() => undefined}
      memoryOperations={[]} rollbackBusy={null} onRollback={() => undefined} onCorrectMemory={() => undefined}
      onUpdateImportance={() => undefined} onArchive={() => undefined} importanceBusy={1} archiveBusy={1} />);
    expect(screen.getByRole("button", { name: "保存中…" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "归档中…" })).toBeDisabled();
  });
});
