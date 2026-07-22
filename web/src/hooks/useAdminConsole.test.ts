import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { api } from "../api";
import type { AdminAbTestConfig, AdminCapsuleRow, AdminReport, AdminUserRow } from "../api";
import { useAdminConsole } from "./useAdminConsole";

vi.mock("../api", () => ({
  api: {
    adminOverview: vi.fn(),
    adminUsers: vi.fn(),
    adminCapsules: vi.fn(),
    adminHideCapsule: vi.fn(),
    adminRestoreCapsule: vi.fn(),
    adminReports: vi.fn(),
    adminResolveReport: vi.fn(),
    adminAuditLogs: vi.fn(),
    adminSafetyEvents: vi.fn(),
    adminModelConfig: vi.fn(),
    aiHealth: vi.fn(),
    aiLogs: vi.fn(),
    abtestActive: vi.fn(),
    abtestStats: vi.fn(),
    abtestToggle: vi.fn()
  }
}));

const mockApi = vi.mocked(api);

const user = (over: Partial<AdminUserRow> = {}): AdminUserRow =>
  ({ id: 1, username: "alice", nickname: "Alice", role: "USER", bio: null, socialReachabilityStatus: null, ...over });

const capsule = (over: Partial<AdminCapsuleRow> = {}): AdminCapsuleRow => ({
  id: 1, ownerUserId: 1, capsuleType: "STANDARD", pseudonym: "星尘", intro: "intro",
  echoEnergy: 0.5, freshnessScore: 0.5, visibilityStatus: "PUBLIC", isPublic: true, createdAt: null, ...over
});

const report = (over: Partial<AdminReport> = {}): AdminReport =>
  ({ id: 1, reporterUserId: 2, targetType: "CAPSULE", targetId: 1, reason: "不当内容", status: "PENDING", createdAt: null, ...over });

const abTest = (over: Partial<AdminAbTestConfig> = {}): AdminAbTestConfig =>
  ({ id: 1, testName: "llm-provider", description: null, enabled: true, mockPercentage: 50, controlGroup: "MOCK", totalParticipants: 10, status: "ACTIVE", ...over });

function stubAll() {
  mockApi.adminOverview.mockResolvedValue({
    totalUsers: 1, activeUsersToday: 1, totalCapsules: 1, publicCapsules: 1, totalLetters: 0,
    pendingLetters: 0, totalAiLogs: 0, failedAiCalls: 0, safetyEvents: 0, pendingReports: 1
  });
  mockApi.adminUsers.mockResolvedValue([user()]);
  mockApi.adminCapsules.mockResolvedValue([capsule()]);
  mockApi.adminReports.mockResolvedValue([report()]);
  mockApi.adminAuditLogs.mockResolvedValue([]);
  mockApi.adminSafetyEvents.mockResolvedValue([]);
  mockApi.adminModelConfig.mockResolvedValue([]);
  mockApi.aiHealth.mockResolvedValue({
    mode: "dev", provider: "mock", model: "mock-1", apiKeyConfigured: false, fallbackAllowed: true,
    mockProvider: true, asrProvider: "mock", asrModel: "mock-asr", asrKeyConfigured: false,
    lastSuccess: null, lastError: null
  });
  mockApi.aiLogs.mockResolvedValue([]);
  mockApi.abtestActive.mockResolvedValue(abTest());
  mockApi.abtestStats.mockResolvedValue({});
}

describe("useAdminConsole", () => {
  it("loads overview, users, capsules and reports concurrently via loadAll", async () => {
    stubAll();
    const { result } = renderHook(() => useAdminConsole());
    await act(() => result.current.loadAll());
    expect(result.current.overview?.totalUsers).toBe(1);
    expect(result.current.users).toEqual([user()]);
    expect(result.current.capsules).toEqual([capsule()]);
    expect(result.current.reports).toEqual([report()]);
    expect(result.current.abtestConfig?.testName).toBe("llm-provider");
    expect(mockApi.abtestStats).toHaveBeenCalledWith("llm-provider");
    expect(result.current.loading).toBe(false);
  });

  it("hides a capsule then refreshes capsules and audit logs", async () => {
    stubAll();
    mockApi.adminHideCapsule.mockResolvedValue(undefined);
    const { result } = renderHook(() => useAdminConsole());
    await act(() => result.current.hideCapsule(1, "违规"));
    expect(mockApi.adminHideCapsule).toHaveBeenCalledWith(1, "违规");
    expect(mockApi.adminCapsules).toHaveBeenCalled();
    expect(mockApi.adminAuditLogs).toHaveBeenCalled();
    await waitFor(() => expect(result.current.status).toBe("已隐藏"));
  });

  it("resolves a report and refreshes reports/capsules/audit", async () => {
    stubAll();
    mockApi.adminResolveReport.mockResolvedValue(undefined);
    const { result } = renderHook(() => useAdminConsole());
    await act(() => result.current.resolveReport(1, "warn", "警告用户"));
    expect(mockApi.adminResolveReport).toHaveBeenCalledWith(1, "warn", "警告用户");
    await waitFor(() => expect(result.current.status).toBe("举报已处理"));
  });

  it("changing the report status filter re-fetches with the new status", async () => {
    stubAll();
    const { result } = renderHook(() => useAdminConsole());
    act(() => result.current.changeReportStatusFilter("PENDING"));
    await waitFor(() => expect(mockApi.adminReports).toHaveBeenCalledWith("PENDING"));
    expect(result.current.reportStatusFilter).toBe("PENDING");
  });

  it("toggles an A/B test and reloads the active config", async () => {
    stubAll();
    mockApi.abtestToggle.mockResolvedValue(undefined);
    const { result } = renderHook(() => useAdminConsole());
    await act(() => result.current.toggleAbTest(1, false));
    expect(mockApi.abtestToggle).toHaveBeenCalledWith(1, false);
    await waitFor(() => expect(result.current.status).toBe("测试已暂停"));
  });

  it("surfaces a friendly error message when hiding a capsule fails", async () => {
    stubAll();
    mockApi.adminHideCapsule.mockRejectedValue(new Error("网络错误"));
    const { result } = renderHook(() => useAdminConsole());
    await act(() => result.current.hideCapsule(1, "违规"));
    await waitFor(() => expect(result.current.status).toBe("网络错误"));
  });
});
