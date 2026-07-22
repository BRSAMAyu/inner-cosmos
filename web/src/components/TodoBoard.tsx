import { useState, type FormEvent } from "react";
import type { TodoDraft, TodoItem } from "../api";
import type { Locale } from "../i18n";
import type { TodoTab } from "../hooks/useTodoBoard";
import { AsyncButton } from "../loading";

// Port of src/main/resources/static/pages/todo.html into the AppShell (Phase 3, legacy batch B).

function isOverdue(item: TodoItem): boolean {
  return Boolean(item.deadline) && !["DONE", "CANCELLED"].includes(item.status)
    && new Date(item.deadline as string).getTime() < Date.now();
}

const COPY: Record<Locale, {
  aria: string; heading: string; intro: string; progress: (done: number, total: number) => string;
  taskNameLabel: string; taskNamePlaceholder: string; priorityLabel: string;
  priorityShort: Record<TodoItem["priority"], string>; deadlineLabel: string; descriptionLabel: string;
  descriptionPlaceholder: string; create: string; tabsAria: string; tabs: Record<TodoTab, string>;
  empty: Record<TodoTab, string>; statusLabel: Record<TodoItem["status"], string>;
  start: string; finish: string; split: string; edit: string; letGo: string;
  reopen: string; pickUp: string; delete: string; save: string; cancel: string; overdue: string;
}> = {
  "zh-CN": {
    aria: "待办清单", heading: "待办清单", intro: "待办不是自我审判，而是把下一步变小。",
    progress: (done, total) => `${done} / ${total} 完成`,
    taskNameLabel: "任务名称", taskNamePlaceholder: "你想轻轻推进什么事？", priorityLabel: "优先级",
    priorityShort: { HIGH: "优先", MEDIUM: "中等", LOW: "不急" },
    deadlineLabel: "截止时间", descriptionLabel: "任务备注", descriptionPlaceholder: "任务备注（可选，建议拆到十分钟内能开始）",
    create: "添加待办", tabsAria: "待办分类", tabs: { today: "今天", week: "本周", done: "已完成", letgo: "已放下" },
    empty: { today: "今天没有待办。可以先休息。", week: "本周没有待办。", done: "还没有完成的事项。", letgo: "没有放下的事项。" },
    statusLabel: { TODO: "待开始", DOING: "正在做", DONE: "已完成", CANCELLED: "已放下" },
    start: "开始", finish: "完成", split: "拆第一步", edit: "编辑", letGo: "放下",
    reopen: "重新打开", pickUp: "重新拾起", delete: "删除", save: "保存修改", cancel: "取消", overdue: "已过期"
  },
  "en-SG": {
    aria: "Todo list", heading: "Todo list", intro: "A todo isn't self-judgment — it just makes the next step smaller.",
    progress: (done, total) => `${done} / ${total} done`,
    taskNameLabel: "Task name", taskNamePlaceholder: "What do you want to gently move forward?", priorityLabel: "Priority",
    priorityShort: { HIGH: "High", MEDIUM: "Medium", LOW: "Low" },
    deadlineLabel: "Deadline", descriptionLabel: "Notes", descriptionPlaceholder: "Notes (optional — try to break it into a 10-minute start)",
    create: "Add todo", tabsAria: "Todo tabs", tabs: { today: "Today", week: "This week", done: "Done", letgo: "Let go" },
    empty: { today: "Nothing due today. You can rest.", week: "Nothing due this week.", done: "Nothing finished yet.", letgo: "Nothing let go yet." },
    statusLabel: { TODO: "Not started", DOING: "In progress", DONE: "Done", CANCELLED: "Let go" },
    start: "Start", finish: "Finish", split: "Split first step", edit: "Edit", letGo: "Let go",
    reopen: "Reopen", pickUp: "Pick back up", delete: "Delete", save: "Save changes", cancel: "Cancel", overdue: "Overdue"
  }
};

type Draft = { taskName: string; priority: TodoItem["priority"]; deadline: string; description: string };
const emptyDraft: Draft = { taskName: "", priority: "MEDIUM", deadline: "", description: "" };

function toTodoDraft(draft: Draft): TodoDraft {
  return {
    taskName: draft.taskName.trim(), priority: draft.priority,
    deadline: draft.deadline ? new Date(draft.deadline).toISOString() : null,
    description: draft.description.trim()
  };
}

export function TodoBoard({ todos, tab, busy, splitBusyId, onSelectTab, onCreate, onUpdateStatus, onSplit, onDelete, onUpdate, locale = "zh-CN" }: {
  todos: TodoItem[]; tab: TodoTab; busy: boolean; splitBusyId: number | null;
  onSelectTab: (tab: TodoTab) => void;
  onCreate: (input: TodoDraft) => void;
  onUpdateStatus: (id: number, status: TodoItem["status"]) => void;
  onSplit: (id: number) => void;
  onDelete: (id: number) => void;
  onUpdate: (id: number, input: TodoDraft) => void;
  locale?: Locale;
}) {
  const t = COPY[locale];
  const [createDraft, setCreateDraft] = useState<Draft>(emptyDraft);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState<Draft>(emptyDraft);

  const filtered = todos.filter(item => {
    if (tab === "done") return item.status === "DONE";
    if (tab === "letgo") return item.status === "CANCELLED";
    return item.status === "TODO" || item.status === "DOING";
  });
  const doneCount = todos.filter(item => item.status === "DONE").length;

  const submitCreate = (event: FormEvent) => {
    event.preventDefault();
    if (!createDraft.taskName.trim()) return;
    onCreate(toTodoDraft(createDraft));
    setCreateDraft(emptyDraft);
  };

  const beginEdit = (item: TodoItem) => {
    setEditingId(item.id);
    setEditDraft({
      taskName: item.taskName, priority: item.priority,
      deadline: item.deadline ? item.deadline.slice(0, 16) : "", description: item.description ?? ""
    });
  };

  const saveEdit = (event: FormEvent) => {
    event.preventDefault();
    if (editingId === null || !editDraft.taskName.trim()) return;
    onUpdate(editingId, toTodoDraft(editDraft));
    setEditingId(null);
  };

  return <section className="todo-board-space" aria-label={t.aria}>
    <span className="eyebrow">TODO · NEXT SMALL STEP</span>
    <h2>{t.heading}</h2>
    <p>{t.intro}</p>
    <p className="todo-progress-label">{t.progress(doneCount, todos.length)}</p>

    <form className="todo-create-form" onSubmit={submitCreate}>
      <input aria-label={t.taskNameLabel} placeholder={t.taskNamePlaceholder}
        value={createDraft.taskName} onChange={event => setCreateDraft(d => ({ ...d, taskName: event.target.value }))} />
      <select aria-label={t.priorityLabel} value={createDraft.priority}
        onChange={event => setCreateDraft(d => ({ ...d, priority: event.target.value as TodoItem["priority"] }))}>
        <option value="HIGH">{t.priorityShort.HIGH}</option>
        <option value="MEDIUM">{t.priorityShort.MEDIUM}</option>
        <option value="LOW">{t.priorityShort.LOW}</option>
      </select>
      <input type="datetime-local" aria-label={t.deadlineLabel}
        value={createDraft.deadline} onChange={event => setCreateDraft(d => ({ ...d, deadline: event.target.value }))} />
      <textarea aria-label={t.descriptionLabel} placeholder={t.descriptionPlaceholder}
        value={createDraft.description} onChange={event => setCreateDraft(d => ({ ...d, description: event.target.value }))} />
      <button type="submit" disabled={busy || !createDraft.taskName.trim()}>{t.create}</button>
    </form>

    <div className="todo-tab-bar" role="tablist" aria-label={t.tabsAria}>
      {(["today", "week", "done", "letgo"] as TodoTab[]).map(value =>
        <button key={value} type="button" role="tab" aria-selected={tab === value}
          className={tab === value ? "active" : ""} onClick={() => onSelectTab(value)}>{t.tabs[value]}</button>)}
    </div>

    {filtered.length === 0 ? <p className="todo-empty">{t.empty[tab]}</p> :
      <ul className="todo-list" role="list">
        {filtered.map(item => <li key={item.id} className={`todo-card status-${item.status.toLowerCase()} priority-${item.priority.toLowerCase()}`}>
          {editingId === item.id ? <form className="todo-edit-form" onSubmit={saveEdit}>
            <input aria-label={t.taskNameLabel} value={editDraft.taskName}
              onChange={event => setEditDraft(d => ({ ...d, taskName: event.target.value }))} />
            <select aria-label={t.priorityLabel} value={editDraft.priority}
              onChange={event => setEditDraft(d => ({ ...d, priority: event.target.value as TodoItem["priority"] }))}>
              <option value="HIGH">{t.priorityShort.HIGH}</option>
              <option value="MEDIUM">{t.priorityShort.MEDIUM}</option>
              <option value="LOW">{t.priorityShort.LOW}</option>
            </select>
            <input type="datetime-local" aria-label={t.deadlineLabel} value={editDraft.deadline}
              onChange={event => setEditDraft(d => ({ ...d, deadline: event.target.value }))} />
            <textarea aria-label={t.descriptionLabel} value={editDraft.description}
              onChange={event => setEditDraft(d => ({ ...d, description: event.target.value }))} />
            <div className="todo-edit-actions">
              <button type="submit">{t.save}</button>
              <button type="button" className="quiet" onClick={() => setEditingId(null)}>{t.cancel}</button>
            </div>
          </form> : <>
            <div className="todo-card-head"><strong>{item.taskName}</strong>
              <span className={`todo-priority priority-${item.priority.toLowerCase()}`}>{t.priorityShort[item.priority]}</span>
            </div>
            {item.description && <p className="todo-description">{item.description}</p>}
            <div className="todo-meta">
              <span className="todo-status-pill">{t.statusLabel[item.status]}</span>
              {item.deadline && <span className={`todo-deadline${isOverdue(item) ? " overdue" : ""}`}>
                {isOverdue(item) ? t.overdue + " · " : ""}{new Date(item.deadline).toLocaleString(locale)}
              </span>}
            </div>
            <div className="todo-actions">
              {(tab === "today" || tab === "week") && <>
                <button type="button" onClick={() => onUpdateStatus(item.id, "DOING")}>{t.start}</button>
                <button type="button" onClick={() => onUpdateStatus(item.id, "DONE")}>{t.finish}</button>
                <AsyncButton busy={splitBusyId === item.id} onClick={() => onSplit(item.id)}>{t.split}</AsyncButton>
                <button type="button" onClick={() => beginEdit(item)}>{t.edit}</button>
                <button type="button" className="quiet" onClick={() => onUpdateStatus(item.id, "CANCELLED")}>{t.letGo}</button>
              </>}
              {tab === "done" && <>
                <button type="button" onClick={() => onUpdateStatus(item.id, "TODO")}>{t.reopen}</button>
                <button type="button" onClick={() => beginEdit(item)}>{t.edit}</button>
              </>}
              {tab === "letgo" && <>
                <button type="button" onClick={() => onUpdateStatus(item.id, "TODO")}>{t.pickUp}</button>
                <button type="button" className="quiet" onClick={() => onDelete(item.id)}>{t.delete}</button>
              </>}
            </div>
          </>}
        </li>)}
      </ul>}
  </section>;
}
