import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ThoughtShredderSection } from "./ThoughtShredderSection";
import type { AiHealth, ShredderHistoryEntry, ShredderResult } from "../api";

afterEach(() => cleanup());

const aiHealth = (overrides: Partial<AiHealth> = {}): AiHealth => ({
  provider: "glm", model: "glm-4", apiKeyConfigured: true, fallbackAllowed: true, ...overrides
});

const historyEntry = (overrides: Partial<ShredderHistoryEntry> = {}): ShredderHistoryEntry => ({
  id: 1, title: "从混乱里留下的一句话", summary: "我需要休息一下。", memoryType: "SHREDDER", emotionalGravity: 0.42, ...overrides
});

const result = (overrides: Partial<ShredderResult> = {}): ShredderResult => ({
  originalHandlingMode: "KEEP_ONLY_RESULT", coreFeeling: "疲惫和压力", hiddenNeed: "被理解和被看见",
  noiseToDrop: ["把一次混乱直接解释成我整个人都不行"], sentenceToKeep: "我现在感到疲惫，也许是需要被理解。",
  memoryCard: { id: 7, title: "从混乱里留下的一句话", summary: "我现在感到疲惫", status: "ACTIVE", versionNo: 1, consentScope: null, memoryLayer: null, confidence: null },
  fragments: [], suggestedTodo: null, ...overrides
});

describe("ThoughtShredderSection", () => {
  it("shows the AI health status line", () => {
    render(<ThoughtShredderSection aiHealth={aiHealth()} history={[]} result={null} busy={false}
      onShred={() => undefined} onSettle={() => undefined} onDelete={() => undefined} />);
    expect(screen.getByText(/glm/)).toBeVisible();
    expect(screen.getByText(/真实模型已配置/)).toBeVisible();
  });

  it("submits the input text with the chosen save mode", () => {
    const onShred = vi.fn();
    render(<ThoughtShredderSection aiHealth={null} history={[]} result={null} busy={false}
      onShred={onShred} onSettle={() => undefined} onDelete={() => undefined} />);
    fireEvent.change(screen.getByPlaceholderText(/把混乱、愤怒、焦虑、碎碎念一次性倒进来/), { target: { value: "今天很累" } });
    fireEvent.change(screen.getByLabelText(/保存模式/), { target: { value: "KEEP_RAW" } });
    fireEvent.click(screen.getByRole("button", { name: /粉碎并沉淀/ }));
    expect(onShred).toHaveBeenCalledExactlyOnceWith("今天很累", "KEEP_RAW");
  });

  it("clears the source text only after a successful result arrives", () => {
    const props = { aiHealth: null, history: [], busy: false, onShred: vi.fn(),
      onSettle: () => undefined, onDelete: () => undefined };
    const { rerender } = render(<ThoughtShredderSection {...props} result={null} />);
    const input = screen.getByLabelText("把想法倒进来") as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: "今天很乱" } });
    expect(input.value).toBe("今天很乱");
    rerender(<ThoughtShredderSection {...props} result={result()} />);
    expect(input.value).toBe("");
  });

  // A DISPLAY_ONCE shred is never inserted, so its card has no id. Offering settle/delete would
  // post to /api/thought-shredder/null/... and fail; the user must be told it is not recoverable.
  it("offers no settle/delete actions for a view-once shred that was never persisted", () => {
    const onSettle = vi.fn();
    const onDelete = vi.fn();
    render(<ThoughtShredderSection aiHealth={null} history={[]} busy={false} onShred={() => undefined}
      onSettle={onSettle} onDelete={onDelete}
      result={result({ originalHandlingMode: "DISPLAY_ONCE", memoryCard: { ...result().memoryCard, id: null, status: "TRANSIENT" } })} />);
    expect(screen.queryByRole("button", { name: /沉淀到记忆/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^删除$/ })).not.toBeInTheDocument();
    expect(screen.getByText(/没有写入数据库/)).toBeVisible();
    expect(onSettle).not.toHaveBeenCalled();
    expect(onDelete).not.toHaveBeenCalled();
  });

  it("renders a shredded result with settle/delete actions", () => {
    const onSettle = vi.fn();
    const onDelete = vi.fn();
    render(<ThoughtShredderSection aiHealth={null} history={[]} result={result()} busy={false}
      onShred={() => undefined} onSettle={onSettle} onDelete={onDelete} />);
    expect(screen.getByText("疲惫和压力")).toBeVisible();
    expect(screen.getByText("被理解和被看见")).toBeVisible();
    expect(screen.getByText(/我现在感到疲惫，也许是需要被理解。/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: /沉淀到记忆/ }));
    expect(onSettle).toHaveBeenCalledExactlyOnceWith(7);
    fireEvent.click(screen.getByRole("button", { name: /删除/ }));
    expect(onDelete).toHaveBeenCalledExactlyOnceWith(7);
  });

  it("lists the shredding history", () => {
    render(<ThoughtShredderSection aiHealth={null} history={[historyEntry()]} result={null} busy={false}
      onShred={() => undefined} onSettle={() => undefined} onDelete={() => undefined} />);
    expect(screen.getByText("从混乱里留下的一句话")).toBeVisible();
    expect(screen.getByText(/0\.42/)).toBeVisible();
  });

  it("disables the shred button while busy", () => {
    render(<ThoughtShredderSection aiHealth={null} history={[]} result={null} busy={true}
      onShred={() => undefined} onSettle={() => undefined} onDelete={() => undefined} />);
    expect(screen.getByRole("button", { name: /粉碎并沉淀|正在粉碎/ })).toBeDisabled();
  });

  it("renders in English when locale is en-SG", () => {
    render(<ThoughtShredderSection locale="en-SG" aiHealth={null} history={[]} result={null} busy={false}
      onShred={() => undefined} onSettle={() => undefined} onDelete={() => undefined} />);
    expect(screen.getByRole("heading", { name: "Thought Shredder" })).toBeVisible();
  });
});
