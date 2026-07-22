import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AdminReportsTab } from "./AdminReportsTab";
import type { AdminReport } from "../../api";

afterEach(cleanup);

const report = (over: Partial<AdminReport> = {}): AdminReport =>
  ({ id: 1, reporterUserId: 2, targetType: "CAPSULE", targetId: 9, reason: "不当内容", status: "PENDING", createdAt: null, ...over });

describe("AdminReportsTab", () => {
  it("shows an empty state with no reports", () => {
    render(<AdminReportsTab reports={[]} statusFilter="" busyId={null} onChangeStatusFilter={() => undefined} onResolve={() => undefined} />);
    expect(screen.getByText("没有举报记录")).toBeVisible();
  });

  it("lists pending reports with resolve actions and hides them once resolved", () => {
    render(<AdminReportsTab reports={[report(), report({ id: 2, status: "RESOLVED" })]} statusFilter=""
      busyId={null} onChangeStatusFilter={() => undefined} onResolve={() => undefined} />);
    expect(screen.getByText("举报 #1")).toBeVisible();
    expect(screen.getByText("举报 #2")).toBeVisible();
    // Only the pending report (#1) gets action buttons; #2 is RESOLVED already.
    expect(screen.getAllByText("警告")).toHaveLength(1);
  });

  it("resolving a report requires a reason then calls onResolve with the action", async () => {
    const onResolve = vi.fn().mockResolvedValue(undefined);
    render(<AdminReportsTab reports={[report()]} statusFilter="" busyId={null}
      onChangeStatusFilter={() => undefined} onResolve={onResolve} />);
    fireEvent.click(screen.getByText("警告"));
    fireEvent.change(screen.getByPlaceholderText("填写处理理由..."), { target: { value: "已提醒用户" } });
    fireEvent.click(screen.getByText("确认处理"));
    expect(onResolve).toHaveBeenCalledExactlyOnceWith(1, "warn", "已提醒用户");
  });

  it("changing the status filter dropdown notifies the parent", () => {
    const onChangeStatusFilter = vi.fn();
    render(<AdminReportsTab reports={[]} statusFilter="" busyId={null}
      onChangeStatusFilter={onChangeStatusFilter} onResolve={() => undefined} />);
    fireEvent.change(screen.getByLabelText(/举报状态/), { target: { value: "PENDING" } });
    expect(onChangeStatusFilter).toHaveBeenCalledExactlyOnceWith("PENDING");
  });

  it("renders in English when locale is en-SG", () => {
    render(<AdminReportsTab locale="en-SG" reports={[report()]} statusFilter="" busyId={null}
      onChangeStatusFilter={() => undefined} onResolve={() => undefined} />);
    expect(screen.getByText("Report #1")).toBeVisible();
    expect(screen.getByText("Warn")).toBeVisible();
  });
});
