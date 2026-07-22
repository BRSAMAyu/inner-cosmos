import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { TodoBoard } from "./TodoBoard";
import type { TodoItem } from "../api";

afterEach(cleanup);

const todo = (overrides: Partial<TodoItem> = {}): TodoItem => ({
  id: 1, taskName: "整理考试范围第一章", description: "只列标题，不要求立刻背。", priority: "HIGH",
  status: "TODO", deadline: null, sourceMemoryCardId: null, ...overrides
});

function baseProps() {
  return {
    todos: [] as TodoItem[], tab: "today" as const, busy: false, splitBusyId: null,
    onSelectTab: vi.fn(), onCreate: vi.fn(), onUpdateStatus: vi.fn(), onSplit: vi.fn(),
    onDelete: vi.fn(), onUpdate: vi.fn()
  };
}

describe("TodoBoard", () => {
  it("shows an empty state for the active tab", () => {
    render(<TodoBoard {...baseProps()} />);
    expect(screen.getByText("今天没有待办。可以先休息。")).toBeVisible();
  });

  it("creates a todo from the form and resets it", () => {
    const onCreate = vi.fn();
    render(<TodoBoard {...baseProps()} onCreate={onCreate} />);
    fireEvent.change(screen.getByLabelText("任务名称"), { target: { value: "给朋友回消息" } });
    fireEvent.change(screen.getByLabelText("优先级"), { target: { value: "LOW" } });
    fireEvent.click(screen.getByRole("button", { name: "添加待办" }));
    expect(onCreate).toHaveBeenCalledExactlyOnceWith({ taskName: "给朋友回消息", priority: "LOW", deadline: null, description: "" });
    expect((screen.getByLabelText("任务名称") as HTMLInputElement).value).toBe("");
  });

  it("does not submit an empty task name", () => {
    const onCreate = vi.fn();
    render(<TodoBoard {...baseProps()} onCreate={onCreate} />);
    fireEvent.click(screen.getByRole("button", { name: "添加待办" }));
    expect(onCreate).not.toHaveBeenCalled();
  });

  it("lists active todos on the today tab with start/finish/split/edit/let-go actions", () => {
    const onUpdateStatus = vi.fn();
    render(<TodoBoard {...baseProps()} todos={[todo()]} onUpdateStatus={onUpdateStatus} />);
    expect(screen.getByText("整理考试范围第一章")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "完成" }));
    expect(onUpdateStatus).toHaveBeenCalledExactlyOnceWith(1, "DONE");
  });

  it("calls onSplit for the 'split first step' action", () => {
    const onSplit = vi.fn();
    render(<TodoBoard {...baseProps()} todos={[todo()]} onSplit={onSplit} />);
    fireEvent.click(screen.getByRole("button", { name: "拆第一步" }));
    expect(onSplit).toHaveBeenCalledExactlyOnceWith(1);
  });

  it("shows a busy state for the todo currently being split", () => {
    render(<TodoBoard {...baseProps()} todos={[todo()]} splitBusyId={1} />);
    expect(screen.getByRole("button", { name: "拆第一步" })).toBeDisabled();
  });

  it("wires the done tab to onSelectTab", () => {
    const onSelectTab = vi.fn();
    render(<TodoBoard {...baseProps()} onSelectTab={onSelectTab} />);
    fireEvent.click(screen.getByRole("tab", { name: "已完成" }));
    expect(onSelectTab).toHaveBeenCalledExactlyOnceWith("done");
  });

  it("edits a todo inline and saves the update", () => {
    const onUpdate = vi.fn();
    render(<TodoBoard {...baseProps()} todos={[todo()]} onUpdate={onUpdate} />);
    fireEvent.click(screen.getByRole("button", { name: "编辑" }));
    const form = screen.getByRole("button", { name: "保存修改" }).closest("form")!;
    fireEvent.change(within(form).getByLabelText("任务名称"), { target: { value: "整理考试范围第一、二章" } });
    fireEvent.click(within(form).getByRole("button", { name: "保存修改" }));
    expect(onUpdate).toHaveBeenCalledExactlyOnceWith(1, {
      taskName: "整理考试范围第一、二章", priority: "HIGH", deadline: null, description: "只列标题，不要求立刻背。"
    });
  });

  it("shows reopen/delete actions on the let-go tab", () => {
    const onDelete = vi.fn();
    render(<TodoBoard {...baseProps()} tab="letgo" todos={[todo({ status: "CANCELLED" })]} onDelete={onDelete} />);
    fireEvent.click(screen.getByRole("button", { name: "删除" }));
    expect(onDelete).toHaveBeenCalledExactlyOnceWith(1);
  });

  it("renders in English when locale is en-SG", () => {
    render(<TodoBoard {...baseProps()} locale="en-SG" />);
    expect(screen.getByText("Nothing due today. You can rest.")).toBeVisible();
  });
});
