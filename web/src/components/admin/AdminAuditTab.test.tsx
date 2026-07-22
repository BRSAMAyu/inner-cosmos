import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { AdminAuditTab } from "./AdminAuditTab";
import type { AdminAuditLog } from "../../api";

afterEach(cleanup);

const log = (over: Partial<AdminAuditLog> = {}): AdminAuditLog =>
  ({ id: 1, adminUserId: 1, actionType: "HIDE_CAPSULE", targetType: "CAPSULE", targetId: 9, detail: "违规隐藏", createdAt: null, ...over });

describe("AdminAuditTab", () => {
  it("shows an empty state with no logs", () => {
    render(<AdminAuditTab logs={[]} />);
    expect(screen.getByText("暂无审计日志")).toBeVisible();
  });

  it("lists audit log entries with action/target/detail", () => {
    render(<AdminAuditTab logs={[log()]} />);
    expect(screen.getByText("HIDE_CAPSULE")).toBeVisible();
    expect(screen.getByText(/CAPSULE #9/)).toBeVisible();
    expect(screen.getByText(/违规隐藏/)).toBeVisible();
  });

  it("renders in English when locale is en-SG", () => {
    render(<AdminAuditTab locale="en-SG" logs={[]} />);
    expect(screen.getByText("No audit logs yet")).toBeVisible();
  });
});
