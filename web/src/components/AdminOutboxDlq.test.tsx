import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AdminOutboxDlq } from "./AdminOutboxDlq";
import { ApiCodeError, type OutboxDeadLetterPage } from "../api";

afterEach(cleanup);

function entry(overrides: Partial<OutboxDeadLetterPage["entries"][number]> = {}): OutboxDeadLetterPage["entries"][number] {
  return {
    id: 101,
    eventId: "0f0e0d0c-1111-2222-3333-444455556666",
    eventType: "data.retracted.v1",
    aggregateType: "USER",
    aggregateId: "7",
    payloadSummary: '{"userId":7,"asset":"MEMORY"}…',
    attempts: 3,
    lastError: "No registered handler for data.retracted.v1",
    createdAt: "2026-09-14T08:00:00",
    lastAttemptAt: "2026-09-15T04:05:06",
    ...overrides
  };
}

function page(overrides: Partial<OutboxDeadLetterPage> = {}): OutboxDeadLetterPage {
  return { enabled: true, total: 1, limit: 20, offset: 0, entries: [entry()], ...overrides };
}

describe("AdminOutboxDlq", () => {
  it("reports enabled:false as 'outbox not deployed' instead of dressing it up as an empty queue", async () => {
    const loader = vi.fn().mockResolvedValue(page({ enabled: false, total: 0, entries: [] }));
    render(<AdminOutboxDlq loader={loader} />);
    expect(await screen.findByText("事件外发未启用")).toBeVisible();
    expect(screen.getByText(/inner-cosmos\.events\.outbox\.enabled=false/)).toBeVisible();
    // The empty-queue copy must NOT appear: there is no queue behind a disabled outbox.
    expect(screen.queryByText("当前没有死信")).not.toBeInTheDocument();
    expect(loader).toHaveBeenCalledExactlyOnceWith(20, 0);
  });

  it("shows the honest empty state when the outbox is enabled but has no DEAD rows", async () => {
    const loader = vi.fn().mockResolvedValue(page({ total: 0, entries: [] }));
    render(<AdminOutboxDlq loader={loader} />);
    expect(await screen.findByText("当前没有死信")).toBeVisible();
    expect(screen.getByText("死信队列为空：没有 DEAD 状态的事件。")).toBeVisible();
    expect(screen.queryByText("事件外发未启用")).not.toBeInTheDocument();
  });

  it("renders the rows the backend actually returned, with paging", async () => {
    const loader = vi.fn()
      .mockResolvedValueOnce(page({
        total: 25,
        entries: [
          entry(),
          entry({ id: 102, eventId: "aabbccdd-9999-8888-7777-666655554444", eventType: "dialog.finished.v1",
            payloadSummary: '{"sessionId":42}…', attempts: 5, lastError: null, lastAttemptAt: null })
        ]
      }))
      .mockResolvedValueOnce(page({ total: 25, offset: 20, entries: [entry({ id: 103 })] }));
    render(<AdminOutboxDlq loader={loader} />);
    expect(await screen.findByText("data.retracted.v1")).toBeVisible();
    // Full eventId is in the title attribute; the cell shows the 8-char recognisable prefix.
    expect(screen.getByTitle("0f0e0d0c-1111-2222-3333-444455556666")).toBeInTheDocument();
    expect(screen.getByText("0f0e0d0c")).toBeInTheDocument();
    expect(screen.getByText('{"userId":7,"asset":"MEMORY"}…')).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("No registered handler for data.retracted.v1")).toBeInTheDocument();
    // LocalDateTime arrives zone-less; the cell shows it with the T normalized, null as a dash.
    expect(screen.getByText("2026-09-15 04:05:06")).toBeInTheDocument();
    expect(screen.getByText("dialog.finished.v1")).toBeInTheDocument();
    // Row 2 has BOTH lastError and lastAttemptAt null — each renders the honest dash.
    expect(screen.getAllByText("—")).toHaveLength(2);
    expect(screen.getByText("第 1–2 条，共 25 条")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "下一页" }));
    await waitFor(() => expect(loader).toHaveBeenNthCalledWith(2, 20, 20));
    // The second page's row (id 103) has rendered from the backend's real offset-20 data.
    await screen.findByText("第 21–21 条，共 25 条");
    expect(screen.getByRole("button", { name: "上一页" })).toBeEnabled();
  });

  it("refreshes the list from the backend's real state after a confirmed replay", async () => {
    // First load: one DEAD row. After replay the row leaves the page (no longer DEAD).
    const loader = vi.fn()
      .mockResolvedValueOnce(page())
      .mockResolvedValueOnce(page({ total: 0, entries: [] }));
    const replayer = vi.fn().mockResolvedValue({ eventId: "0f0e0d0c-1111-2222-3333-444455556666", status: "PENDING" });
    render(<AdminOutboxDlq loader={loader} replayer={replayer} />);
    fireEvent.click(await screen.findByRole("button", { name: "重放" }));
    expect(replayer).toHaveBeenCalledExactlyOnceWith("0f0e0d0c-1111-2222-3333-444455556666");
    expect(await screen.findByText(/已重新入队，当前状态 PENDING/)).toBeVisible();
    // The list was reloaded from the backend: the replayed row is gone and the empty state shows.
    await waitFor(() => expect(loader).toHaveBeenCalledTimes(2));
    expect(await screen.findByText("当前没有死信")).toBeVisible();
    expect(screen.queryByTitle("0f0e0d0c-1111-2222-3333-444455556666")).not.toBeInTheDocument();
  });

  it("shows a replay 409 (no longer DEAD) verbatim instead of pretending it succeeded", async () => {
    const loader = vi.fn().mockResolvedValue(page());
    const replayer = vi.fn().mockRejectedValue(new ApiCodeError(
      "outbox 事件当前状态为 PENDING，仅 DEAD 状态可重放", "CONFLICT"));
    render(<AdminOutboxDlq loader={loader} replayer={replayer} />);
    fireEvent.click(await screen.findByRole("button", { name: "重放" }));
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("重放失败：outbox 事件当前状态为 PENDING，仅 DEAD 状态可重放");
    // The list was NOT refreshed after a failed replay; the row stays.
    expect(loader).toHaveBeenCalledTimes(1);
    expect(screen.getByTitle("0f0e0d0c-1111-2222-3333-444455556666")).toBeInTheDocument();
  });

  it("shows a replay 404 (unknown eventId) verbatim", async () => {
    const loader = vi.fn().mockResolvedValue(page());
    const replayer = vi.fn().mockRejectedValue(new ApiCodeError("outbox 事件不存在: 00000000-0000-0000-0000-000000000000", "NOT_FOUND"));
    render(<AdminOutboxDlq loader={loader} replayer={replayer} />);
    fireEvent.click(await screen.findByRole("button", { name: "重放" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("重放失败：outbox 事件不存在");
  });

  it("reports a non-admin 401 honestly instead of an empty queue", async () => {
    const loader = vi.fn().mockRejectedValue(new ApiCodeError("需要管理员权限", "UNAUTHORIZED"));
    render(<AdminOutboxDlq loader={loader} />);
    expect(await screen.findByText("需要管理员权限才能查看死信队列。")).toBeVisible();
    expect(screen.queryByText("当前没有死信")).not.toBeInTheDocument();
    expect(screen.queryByText("事件外发未启用")).not.toBeInTheDocument();
  });

  it("renders the honest states in English when locale is en-SG", async () => {
    const loader = vi.fn().mockResolvedValue(page({ enabled: false, total: 0, entries: [] }));
    render(<AdminOutboxDlq locale="en-SG" loader={loader} />);
    expect(await screen.findByText("Event outbox is not enabled")).toBeVisible();
  });
});
