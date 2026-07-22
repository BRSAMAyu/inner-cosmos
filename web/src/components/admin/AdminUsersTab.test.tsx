import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { AdminUsersTab } from "./AdminUsersTab";
import type { AdminUserRow } from "../../api";

afterEach(cleanup);

const user = (over: Partial<AdminUserRow> = {}): AdminUserRow =>
  ({ id: 1, username: "alice", nickname: "Alice", role: "USER", bio: null, socialReachabilityStatus: null, ...over });

describe("AdminUsersTab", () => {
  it("shows an empty state with no users", () => {
    render(<AdminUsersTab users={[]} />);
    expect(screen.getByText("没有用户数据")).toBeVisible();
  });

  it("lists users with role and bio", () => {
    render(<AdminUsersTab users={[user({ role: "ADMIN", bio: "热爱星空" })]} />);
    expect(screen.getByText("Alice")).toBeVisible();
    expect(screen.getByText("ADMIN")).toBeVisible();
    expect(screen.getByText(/热爱星空/)).toBeVisible();
  });

  it("renders in English when locale is en-SG", () => {
    render(<AdminUsersTab locale="en-SG" users={[]} />);
    expect(screen.getByText("No user data")).toBeVisible();
  });
});
