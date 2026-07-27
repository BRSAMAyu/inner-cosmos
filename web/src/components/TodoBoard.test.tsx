import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TodoBoard } from "./TodoBoard";
import type { TodoItem } from "../api";

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-07-27T09:00:00+08:00"));
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

const todo = (overrides: Partial<TodoItem> = {}): TodoItem => ({
  id: 1, taskName: "整理考试范围第一章", description: "只列标题，不要求立刻背。", priority: "HIGH",
  status: "TODO", deadline: "2026-07-27T10:00:00+08:00", sourceMemoryCardId: null, ...overrides
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
    expect(screen.getByText("待办 · 下一小步")).toBeVisible();
    expect(screen.queryByText("TODO · NEXT SMALL STEP")).not.toBeInTheDocument();
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

  it("separates today, this week and undated planning items by deadline", () => {
    const rows = [
      todo({ id: 1, taskName: "今天截止", deadline: "2026-07-27T18:00:00+08:00" }),
      todo({ id: 2, taskName: "本周截止", deadline: "2026-07-30T18:00:00+08:00" }),
      todo({ id: 3, taskName: "尚未排期", deadline: null })
    ];
    const { rerender } = render(<TodoBoard {...baseProps()} todos={rows} />);
    expect(screen.getByText("今天截止")).toBeVisible();
    expect(screen.queryByText("本周截止")).not.toBeInTheDocument();
    expect(screen.queryByText("尚未排期")).not.toBeInTheDocument();

    rerender(<TodoBoard {...baseProps()} tab="week" todos={rows} />);
    expect(screen.queryByText("今天截止")).not.toBeInTheDocument();
    expect(screen.getByText("本周截止")).toBeVisible();
    expect(screen.getByText("尚未排期")).toBeVisible();
    expect(screen.getByText("未设截止时间 · 本周规划池")).toBeVisible();
  });

  it("keeps the week tab meaningful on a Sunday via a rolling 7-day window", () => {
    // 2026-07-26 is a Sunday: the old `(7 - getDay()) % 7` arithmetic collapsed endOfWeek to
    // endOfToday on Sundays, so the week tab showed only undated items even with dated ones due
    // later this week. "本周" is defined as a rolling 7 days from today, not the calendar week.
    vi.setSystemTime(new Date("2026-07-26T09:00:00+08:00"));
    const rows = [
      todo({ id: 1, taskName: "周三截止", deadline: "2026-07-29T18:00:00+08:00" }),
      todo({ id: 2, taskName: "下下周截止", deadline: "2026-08-05T18:00:00+08:00" })
    ];
    render(<TodoBoard {...baseProps()} tab="week" todos={rows} />);
    expect(screen.getByText("周三截止")).toBeVisible();
    expect(screen.queryByText("下下周截止")).not.toBeInTheDocument();
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
      taskName: "整理考试范围第一、二章", priority: "HIGH", deadline: "2026-07-27T02:00:00.000Z", description: "只列标题，不要求立刻背。"
    });
  });

  it("shows reopen/delete actions on the let-go tab", () => {
    const onDelete = vi.fn();
    render(<TodoBoard {...baseProps()} tab="letgo" todos={[todo({ status: "CANCELLED", deadline: null })]} onDelete={onDelete} />);
    expect(screen.getByRole("button", { name: "重新拾起" })).toBeVisible();
    expect(screen.getByRole("button", { name: "删除" })).toBeVisible();
  });

  it("requires a second confirming click before an irreversible delete fires", () => {
    const onDelete = vi.fn();
    render(<TodoBoard {...baseProps()} tab="letgo" todos={[todo({ status: "CANCELLED", deadline: null })]} onDelete={onDelete} />);
    fireEvent.click(screen.getByRole("button", { name: "删除" }));
    expect(onDelete).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "确认删除？" }));
    expect(onDelete).toHaveBeenCalledExactlyOnceWith(1);
  });

  it("lets the owner back out of a pending delete confirmation", () => {
    const onDelete = vi.fn();
    render(<TodoBoard {...baseProps()} tab="letgo" todos={[todo({ status: "CANCELLED", deadline: null })]} onDelete={onDelete} />);
    fireEvent.click(screen.getByRole("button", { name: "删除" }));
    fireEvent.click(screen.getByRole("button", { name: "取消" }));
    expect(onDelete).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "删除" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "确认删除？" })).not.toBeInTheDocument();
  });

  it("renders in English when locale is en-SG", () => {
    render(<TodoBoard {...baseProps()} locale="en-SG" />);
    expect(screen.getByText("Nothing due today. You can rest.")).toBeVisible();
    expect(screen.getByLabelText("Deadline")).toHaveAttribute("lang", "en-SG");
  });

  it("keeps the edit deadline picker localized too", () => {
    render(<TodoBoard {...baseProps()} locale="en-SG" todos={[todo({ taskName: "Revise chapter one" })]} />);
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    const editForm = screen.getByRole("button", { name: "Save changes" }).closest("form")!;
    expect(within(editForm).getByLabelText("Deadline")).toHaveAttribute("lang", "en-SG");
  });
});
