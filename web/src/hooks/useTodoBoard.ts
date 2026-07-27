import { useCallback, useState } from "react";
import { api, type TodoDraft, type TodoItem } from "../api";
import type { Locale } from "../i18n";

// Port of src/main/resources/static/pages/todo.html into the AppShell (Phase 3, legacy batch B):
// full CRUD over TodoController plus Aurora's "split into first step" action. Mirrors the shape of
// useConnectionsAndLetters.ts -- one small hook owning this domain's own fetch/mutate cycle, always
// reloading the full list after a write (matching the legacy page's own load()-after-every-action style).

export type TodoTab = "today" | "week" | "done" | "letgo";

export type UseTodoBoardOptions = { setStatus: (status: string) => void; locale?: Locale };

export function useTodoBoard({ setStatus, locale = "zh-CN" }: UseTodoBoardOptions) {
  const en = locale === "en-SG";
  const [todos, setTodos] = useState<TodoItem[]>([]);
  const [tab, setTab] = useState<TodoTab>("today");
  const [busy, setBusy] = useState(false);
  const [splitBusyId, setSplitBusyId] = useState<number | null>(null);

  const loadTodos = useCallback(() => api.todoList().then(setTodos), []);

  const createTodo = useCallback(async (input: TodoDraft) => {
    setBusy(true);
    try {
      await api.createTodo(input);
      await loadTodos();
      setStatus(en ? "To-do added." : "待办已添加");
    } catch (error) { setStatus(error instanceof Error ? error.message : en ? "Could not add this to-do yet." : "暂时无法添加待办"); }
    finally { setBusy(false); }
  }, [en, loadTodos, setStatus]);

  const updateTodo = useCallback(async (id: number, input: TodoDraft) => {
    setBusy(true);
    try {
      await api.updateTodo(id, input);
      await loadTodos();
      setStatus(en ? "Changes saved." : "修改已保存");
    } catch (error) { setStatus(error instanceof Error ? error.message : en ? "Could not save these changes yet." : "暂时无法保存修改"); }
    finally { setBusy(false); }
  }, [en, loadTodos, setStatus]);

  const updateStatus = useCallback(async (id: number, status: TodoItem["status"]) => {
    try {
      await api.updateTodoStatus(id, status);
      await loadTodos();
      const labels: Record<TodoItem["status"], string> = en
        ? { DOING: "Started.", DONE: "Completed.", CANCELLED: "Let go.", TODO: "Reopened." }
        : { DOING: "已开始", DONE: "已完成", CANCELLED: "已放下", TODO: "已重新打开" };
      setStatus(labels[status] ?? (en ? "Status updated." : "状态已更新"));
    } catch (error) { setStatus(error instanceof Error ? error.message : en ? "Could not update this to-do yet." : "暂时无法更新这个待办"); }
  }, [en, loadTodos, setStatus]);

  const splitTodo = useCallback(async (id: number) => {
    setSplitBusyId(id);
    try {
      await api.splitTodo(id);
      await loadTodos();
      setStatus(en ? "A smaller first step is ready." : "已经拆出更小的第一步");
    } catch (error) { setStatus(error instanceof Error ? error.message : en ? "Could not split this to-do yet." : "暂时无法拆分这个待办"); }
    finally { setSplitBusyId(null); }
  }, [en, loadTodos, setStatus]);

  const deleteTodo = useCallback(async (id: number) => {
    try {
      await api.deleteTodo(id);
      await loadTodos();
      setStatus(en ? "Deleted." : "已删除");
    } catch (error) { setStatus(error instanceof Error ? error.message : en ? "Could not delete this to-do yet." : "暂时无法删除这个待办"); }
  }, [en, loadTodos, setStatus]);

  return {
    todos, tab, busy, splitBusyId,
    setTab, loadTodos, createTodo, updateTodo, updateStatus, splitTodo, deleteTodo
  };
}
