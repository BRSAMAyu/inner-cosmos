import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../api";
import type { TodoItem } from "../api";
import { useTodoBoard } from "./useTodoBoard";

vi.mock("../api", () => ({
  api: {
    todoList: vi.fn(),
    createTodo: vi.fn(),
    updateTodo: vi.fn(),
    updateTodoStatus: vi.fn(),
    splitTodo: vi.fn(),
    deleteTodo: vi.fn()
  }
}));

const todo = (overrides: Partial<TodoItem> = {}): TodoItem => ({
  id: 1, taskName: "整理考试范围", description: "", priority: "MEDIUM", status: "TODO",
  deadline: null, sourceMemoryCardId: null, ...overrides
});

function setup() {
  const setStatus = vi.fn();
  const { result } = renderHook(() => useTodoBoard({ setStatus }));
  return { result, setStatus };
}

beforeEach(() => {
  vi.mocked(api.todoList).mockResolvedValue([]);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("useTodoBoard -- initial state", () => {
  it("starts empty, on the today tab, not busy", () => {
    const { result } = setup();
    expect(result.current.todos).toEqual([]);
    expect(result.current.tab).toBe("today");
    expect(result.current.busy).toBe(false);
    expect(result.current.splitBusyId).toBeNull();
  });
});

describe("useTodoBoard -- loadTodos", () => {
  it("populates todos from the backend", async () => {
    vi.mocked(api.todoList).mockResolvedValue([todo({ id: 5 })]);
    const { result } = setup();
    await act(async () => { await result.current.loadTodos(); });
    expect(result.current.todos).toHaveLength(1);
    expect(result.current.todos[0].id).toBe(5);
  });
});

describe("useTodoBoard -- createTodo", () => {
  it("creates a todo, reloads the list, and reports success", async () => {
    vi.mocked(api.createTodo).mockResolvedValue(todo());
    vi.mocked(api.todoList).mockResolvedValue([todo()]);
    const { result, setStatus } = setup();
    await act(async () => {
      await result.current.createTodo({ taskName: "整理考试范围", priority: "MEDIUM", deadline: null, description: "" });
    });
    expect(api.createTodo).toHaveBeenCalledExactlyOnceWith({ taskName: "整理考试范围", priority: "MEDIUM", deadline: null, description: "" });
    expect(result.current.todos).toHaveLength(1);
    expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("已添加"));
  });

  it("reports the error message and leaves the list unchanged when creation fails", async () => {
    vi.mocked(api.createTodo).mockRejectedValue(new Error("网络错误"));
    const { result, setStatus } = setup();
    await act(async () => {
      await result.current.createTodo({ taskName: "x", priority: "MEDIUM", deadline: null, description: "" });
    });
    expect(setStatus).toHaveBeenCalledWith("网络错误");
    expect(result.current.todos).toEqual([]);
  });
});

describe("useTodoBoard -- status transitions and split", () => {
  it("updates status, reloads, and reports success", async () => {
    vi.mocked(api.updateTodoStatus).mockResolvedValue(todo({ status: "DOING" }));
    vi.mocked(api.todoList).mockResolvedValue([todo({ status: "DOING" })]);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.updateStatus(1, "DOING"); });
    expect(api.updateTodoStatus).toHaveBeenCalledExactlyOnceWith(1, "DOING");
    expect(result.current.todos[0].status).toBe("DOING");
    expect(setStatus).toHaveBeenCalled();
  });

  it("tracks which todo is being split via splitBusyId and clears it afterward", async () => {
    let resolveSplit: (value: TodoItem) => void = () => undefined;
    vi.mocked(api.splitTodo).mockReturnValue(new Promise(resolve => { resolveSplit = resolve; }));
    vi.mocked(api.todoList).mockResolvedValue([todo()]);
    const { result } = setup();
    let pending!: Promise<void>;
    act(() => { pending = result.current.splitTodo(7); });
    expect(result.current.splitBusyId).toBe(7);
    await act(async () => { resolveSplit(todo()); await pending; });
    expect(result.current.splitBusyId).toBeNull();
  });

  it("deletes a todo and reloads the list", async () => {
    vi.mocked(api.deleteTodo).mockResolvedValue(true);
    vi.mocked(api.todoList).mockResolvedValue([]);
    const { result, setStatus } = setup();
    await act(async () => { await result.current.deleteTodo(3); });
    expect(api.deleteTodo).toHaveBeenCalledExactlyOnceWith(3);
    expect(setStatus).toHaveBeenCalled();
  });

  it("updates an existing todo via updateTodo", async () => {
    vi.mocked(api.updateTodo).mockResolvedValue(todo({ taskName: "新的名字" }));
    vi.mocked(api.todoList).mockResolvedValue([todo({ taskName: "新的名字" })]);
    const { result } = setup();
    await act(async () => {
      await result.current.updateTodo(1, { taskName: "新的名字", priority: "HIGH", deadline: null, description: "" });
    });
    expect(api.updateTodo).toHaveBeenCalledExactlyOnceWith(1, { taskName: "新的名字", priority: "HIGH", deadline: null, description: "" });
    expect(result.current.todos[0].taskName).toBe("新的名字");
  });
});

describe("useTodoBoard -- tab selection", () => {
  it("switches the active tab", () => {
    const { result } = setup();
    act(() => { result.current.setTab("done"); });
    expect(result.current.tab).toBe("done");
  });
});
