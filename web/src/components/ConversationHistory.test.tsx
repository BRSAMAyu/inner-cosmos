import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { DialogSessionSummary } from "../api";
import { ConversationHistory } from "./ConversationHistory";

afterEach(cleanup);

const session: DialogSessionSummary = {
  id: 7,
  title: "今晚的对话",
  status: "ACTIVE",
  messageCount: 2,
  preview: "我想慢慢说",
  activeTurnId: null,
  startedAt: "2026-07-27T10:00:00Z",
  lastActivityAt: "2026-07-27T10:02:00Z",
  archivedAt: null,
  pinnedAt: null,
  updatedAt: "2026-07-27T10:02:00Z"
};

const renderHistory = (overrides: Partial<Parameters<typeof ConversationHistory>[0]> = {}) => {
  const props: Parameters<typeof ConversationHistory>[0] = {
    sessions: [session],
    currentSessionId: null,
    busy: false,
    locale: "zh-CN",
    onOpen: vi.fn(),
    onNew: vi.fn(),
    onRename: vi.fn().mockResolvedValue(undefined),
    onPin: vi.fn(),
    onArchive: vi.fn(),
    ...overrides
  };
  render(<ConversationHistory {...props} />);
  return props;
};

describe("ConversationHistory viewport behaviour", () => {
  it("collapses the manager after opening a conversation", () => {
    const props = renderHistory();
    fireEvent.click(screen.getByRole("button", { name: /会话记录/ }));
    fireEvent.click(screen.getByRole("button", { name: /今晚的对话/ }));

    expect(props.onOpen).toHaveBeenCalledWith(session);
    expect(screen.queryByRole("button", { name: /今晚的对话/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /会话记录/ })).toHaveAttribute("aria-expanded", "false");
  });

  it("collapses the manager after starting a new conversation", () => {
    const props = renderHistory();
    fireEvent.click(screen.getByRole("button", { name: /会话记录/ }));
    fireEvent.click(screen.getByRole("button", { name: "＋ 新对话" }));

    expect(props.onNew).toHaveBeenCalledOnce();
    expect(screen.queryByRole("button", { name: /今晚的对话/ })).not.toBeInTheDocument();
  });
});
