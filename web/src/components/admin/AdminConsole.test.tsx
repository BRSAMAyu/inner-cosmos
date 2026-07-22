import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AdminConsole } from "./AdminConsole";
import { useAdminConsole } from "../../hooks/useAdminConsole";

vi.mock("../../hooks/useAdminConsole");

afterEach(cleanup);

function baseHookState(overrides: Partial<ReturnType<typeof useAdminConsole>> = {}): ReturnType<typeof useAdminConsole> {
  return {
    overview: { totalUsers: 3, activeUsersToday: 1, totalCapsules: 2, publicCapsules: 2, totalLetters: 4,
      pendingLetters: 0, totalAiLogs: 5, failedAiCalls: 0, safetyEvents: 1, pendingReports: 2 },
    users: [], capsules: [], reports: [], reportStatusFilter: "", auditLogs: [], safetyEvents: [],
    modelConfig: [], aiHealth: null, aiLogs: [], abtestConfig: null, abtestStats: null,
    loading: false, busyId: null, status: null,
    loadAll: vi.fn().mockResolvedValue(undefined), loadCapsules: vi.fn(), loadReports: vi.fn(), loadAbTestStats: vi.fn(),
    hideCapsule: vi.fn(), restoreCapsule: vi.fn(), resolveReport: vi.fn(),
    changeReportStatusFilter: vi.fn(), toggleAbTest: vi.fn(), clearStatus: vi.fn(),
    ...overrides
  };
}

describe("AdminConsole", () => {
  it("loads all admin data on mount and shows the overview metrics", () => {
    const hookState = baseHookState();
    vi.mocked(useAdminConsole).mockReturnValue(hookState);
    render(<AdminConsole onBack={() => undefined} />);
    expect(hookState.loadAll).toHaveBeenCalled();
    expect(screen.getByText("待处理举报")).toBeVisible();
    expect(screen.getByText("待处理举报").closest("article")).toHaveTextContent("2");
  });

  it("defaults to the users tab and switches tabs on click", () => {
    vi.mocked(useAdminConsole).mockReturnValue(baseHookState({
      users: [{ id: 1, username: "alice", nickname: "Alice", role: "USER", bio: null, socialReachabilityStatus: null }],
      reports: [{ id: 1, reporterUserId: 2, targetType: "CAPSULE", targetId: 1, reason: "不当内容", status: "PENDING", createdAt: null }]
    }));
    render(<AdminConsole onBack={() => undefined} />);
    expect(screen.getByText("Alice")).toBeVisible();
    fireEvent.click(screen.getByRole("tab", { name: "举报" }));
    expect(screen.getByText("举报 #1")).toBeVisible();
  });

  it("calls onBack when the back button is clicked", () => {
    vi.mocked(useAdminConsole).mockReturnValue(baseHookState());
    const onBack = vi.fn();
    render(<AdminConsole onBack={onBack} />);
    fireEvent.click(screen.getByText("返回核心"));
    expect(onBack).toHaveBeenCalled();
  });

  it("shows a status banner when the hook reports one", () => {
    vi.mocked(useAdminConsole).mockReturnValue(baseHookState({ status: "已隐藏" }));
    render(<AdminConsole onBack={() => undefined} />);
    expect(screen.getByRole("status")).toHaveTextContent("已隐藏");
  });

  it("renders in English when locale is en-SG", () => {
    vi.mocked(useAdminConsole).mockReturnValue(baseHookState());
    render(<AdminConsole locale="en-SG" onBack={() => undefined} />);
    expect(screen.getByRole("heading", { name: "Admin console" })).toBeVisible();
    expect(screen.getByRole("tab", { name: "Reports" })).toBeVisible();
  });
});
