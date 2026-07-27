import { useState } from "react";
import type { DialogSessionSummary } from "../api";
import type { Locale } from "../i18n";

export function ConversationHistory({
  sessions, currentSessionId, busy, locale, compact = false,
  onOpen, onNew, onRename, onPin, onArchive, onReload
}: {
  sessions: DialogSessionSummary[];
  currentSessionId: number | null;
  busy: boolean;
  locale: Locale;
  compact?: boolean;
  onOpen: (session: DialogSessionSummary) => void;
  onNew: () => void;
  onRename: (session: DialogSessionSummary, title: string) => Promise<void>;
  onPin: (session: DialogSessionSummary) => void;
  onArchive: (session: DialogSessionSummary) => void;
  onReload?: (includeArchived: boolean) => Promise<void>;
}) {
  const english = locale === "en-SG";
  const [expanded, setExpanded] = useState(false);
  const [showArchived, setShowArchived] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingTitle, setEditingTitle] = useState("");

  const edit = (session: DialogSessionSummary) => {
    setEditingId(session.id);
    setEditingTitle(session.title);
  };
  const save = async (session: DialogSessionSummary) => {
    const title = editingTitle.trim();
    if (!title) return;
    await onRename(session, title);
    setEditingId(null);
  };
  const open = (session: DialogSessionSummary) => {
    setExpanded(false);
    setEditingId(null);
    onOpen(session);
  };
  const create = () => {
    setExpanded(false);
    setEditingId(null);
    onNew();
  };
  const ordered = [...sessions].sort((a, b) =>
    Number(Boolean(b.pinnedAt)) - Number(Boolean(a.pinnedAt))
    || String(b.lastActivityAt ?? "").localeCompare(String(a.lastActivityAt ?? "")));

  return <section className={compact ? "conversation-history compact" : "conversation-history"}
    aria-label={english ? "Conversation history" : "会话记录"}>
    <div className="conversation-history-head">
      <button type="button" className="quiet history-toggle" aria-expanded={expanded}
        onClick={() => setExpanded(value => !value)}>
        <span>{english ? "Conversations" : "会话记录"}</span>
        <small>{sessions.length} · {expanded ? (english ? "hide" : "收起") : (english ? "manage" : "管理")}</small>
      </button>
      <button type="button" className="history-new" disabled={busy} onClick={create}>
        {english ? "＋ New" : "＋ 新对话"}
      </button>
    </div>
    {expanded && <div className="conversation-history-list">
      {ordered.length === 0 && <p className="history-empty">
        {english ? "Your conversations will appear here and remain available after refresh." : "对话会在这里保存，刷新后仍可继续。"}
      </p>}
      {ordered.map(session => <article key={session.id}
        className={session.id === currentSessionId ? "current" : ""}>
        {editingId === session.id ? <form onSubmit={event => {
          event.preventDefault();
          void save(session);
        }}>
          <input autoFocus maxLength={160} value={editingTitle}
            aria-label={english ? "Conversation title" : "会话标题"}
            onChange={event => setEditingTitle(event.target.value)} />
          <button type="submit">{english ? "Save" : "保存"}</button>
          <button type="button" className="quiet" onClick={() => setEditingId(null)}>
            {english ? "Cancel" : "取消"}
          </button>
        </form> : <>
          <button type="button" className="history-open" onClick={() => open(session)}
            aria-current={session.id === currentSessionId ? "page" : undefined}>
            <strong>{session.pinnedAt ? "✦ " : ""}{session.title}</strong>
            <span>{session.preview || (english ? "No messages yet" : "还没有消息")}</span>
          </button>
          <div className="history-actions">
            <button type="button" title={english ? "Rename" : "重命名"} onClick={() => edit(session)}>✎</button>
            <button type="button" title={session.pinnedAt ? (english ? "Unpin" : "取消置顶") : (english ? "Pin" : "置顶")}
              onClick={() => onPin(session)}>⌃</button>
            <button type="button" title={session.archivedAt
              ? (english ? "Unarchive" : "移出归档")
              : (english ? "Archive" : "归档")} onClick={() => onArchive(session)}>
              {session.archivedAt ? "↟" : "□"}
            </button>
          </div>
        </>}
      </article>)}
      <button type="button" className="quiet history-archive-filter" onClick={() => {
        const next = !showArchived;
        setShowArchived(next);
        void onReload?.(next);
      }}>
        {showArchived ? (english ? "Hide archived" : "隐藏已归档") : (english ? "Show archived" : "查看已归档")}
      </button>
    </div>}
  </section>;
}
