import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AdminAbTestTab } from "./AdminAbTestTab";
import type { AdminAbTestConfig, AdminAbTestStats } from "../../api";

afterEach(cleanup);

const config = (over: Partial<AdminAbTestConfig> = {}): AdminAbTestConfig => ({
  id: 1, testName: "llm-provider", description: "MOCK vs REMOTE", enabled: true,
  mockPercentage: 50, controlGroup: "MOCK", totalParticipants: 20, status: "ACTIVE", ...over
});

describe("AdminAbTestTab", () => {
  it("shows an empty state when there is no active config", () => {
    render(<AdminAbTestTab config={null} stats={null} busyId={null} onToggle={() => undefined} />);
    expect(screen.getByText("暂无活跃的 A/B 测试")).toBeVisible();
  });

  it("shows the single active test with traffic split and running status", () => {
    render(<AdminAbTestTab config={config()} stats={null} busyId={null} onToggle={() => undefined} />);
    expect(screen.getByText("llm-provider")).toBeVisible();
    expect(screen.getByText("运行中")).toBeVisible();
    expect(screen.getByText(/MOCK 50% \/ REMOTE 50%/)).toBeVisible();
  });

  it("toggling calls onToggle with the inverted enabled flag", () => {
    const onToggle = vi.fn();
    render(<AdminAbTestTab config={config()} stats={null} busyId={null} onToggle={onToggle} />);
    fireEvent.click(screen.getByText("暂停"));
    expect(onToggle).toHaveBeenCalledExactlyOnceWith(1, false);
  });

  it("shows a comparison table when stats are present", () => {
    const stats: AdminAbTestStats = {
      MOCK: { groupName: "MOCK", totalRequests: 10, successCount: 9, fallbackCount: 1, avgLatency: 300, successRate: 0.9 },
      REMOTE: { groupName: "REMOTE", totalRequests: 8, successCount: 8, fallbackCount: 0, avgLatency: 500, successRate: 1 }
    };
    render(<AdminAbTestTab config={config()} stats={stats} busyId={null} onToggle={() => undefined} />);
    expect(screen.getByText("累计指标对比")).toBeVisible();
    expect(screen.getByText("90.0%")).toBeVisible();
    expect(screen.getByText("300ms")).toBeVisible();
  });

  it("renders in English when locale is en-SG", () => {
    render(<AdminAbTestTab locale="en-SG" config={config()} stats={null} busyId={null} onToggle={() => undefined} />);
    expect(screen.getByText("Running")).toBeVisible();
    expect(screen.getByText("Pause")).toBeVisible();
  });
});
