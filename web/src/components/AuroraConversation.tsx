import type { FormEvent } from "react";

export type AuroraUiMessage = { key: string; speaker: "USER" | "AURORA"; text: string; partial?: boolean };

export function AuroraConversation({ messages, activeTurnId, draft, sessionReady, onDraftChange, onSubmit, onStop }: {
  messages: AuroraUiMessage[];
  activeTurnId: number | null;
  draft: string;
  sessionReady: boolean;
  onDraftChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onStop: () => void;
}) {
  return <>
    <section className="conversation" aria-live="polite" aria-label="与 Aurora 的对话">
      {messages.length === 0 && <div className="empty"><span>✦</span><p>把现在最真实的一句话放在这里。</p></div>}
      {messages.map(message => <article className={`message ${message.speaker.toLowerCase()} ${message.partial ? "partial" : ""}`} key={message.key}>
        <span className="speaker">{message.speaker === "AURORA" ? "Aurora" : "你"}</span>
        <p>{message.text || "…"}</p>
        {message.partial && message.text && <small>停在这里</small>}
      </article>)}
    </section>
    <form className="composer" onSubmit={onSubmit}>
      <textarea value={draft} onChange={event => onDraftChange(event.target.value)}
        placeholder={activeTurnId ? "直接说出新的想法，Aurora 会停下并重新理解…" : "此刻，你想从哪里说起？"}
        aria-label="写给 Aurora" onKeyDown={event => {
          if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); event.currentTarget.form?.requestSubmit(); }
        }} />
      <div className="actions">
        {activeTurnId && <button type="button" className="stop" onClick={onStop}>停止回应</button>}
        <button type="submit" className="send" disabled={!draft.trim() || !sessionReady}>{activeTurnId ? "打断并发送" : "发送"}</button>
      </div>
    </form>
  </>;
}
