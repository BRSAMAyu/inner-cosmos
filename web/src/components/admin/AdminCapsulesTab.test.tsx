import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AdminCapsulesTab } from "./AdminCapsulesTab";
import type { AdminCapsuleRow } from "../../api";

afterEach(cleanup);

const capsule = (over: Partial<AdminCapsuleRow> = {}): AdminCapsuleRow => ({
  id: 1, ownerUserId: 1, capsuleType: "STANDARD", pseudonym: "星尘", intro: "一段自我介绍",
  echoEnergy: 0.42, freshnessScore: 0.5, visibilityStatus: "PUBLIC", isPublic: true, createdAt: null, ...over
});

describe("AdminCapsulesTab", () => {
  it("shows an empty state with no capsules", () => {
    render(<AdminCapsulesTab capsules={[]} busyId={null} onHide={() => undefined} onRestore={() => undefined} />);
    expect(screen.getByText("没有匹配的共鸣体")).toBeVisible();
  });

  it("filters by search text and status client-side", () => {
    render(<AdminCapsulesTab capsules={[capsule(), capsule({ id: 2, pseudonym: "夜行者", visibilityStatus: "HIDDEN" })]}
      busyId={null} onHide={() => undefined} onRestore={() => undefined} />);
    fireEvent.change(screen.getByPlaceholderText("搜索共鸣体..."), { target: { value: "夜行" } });
    expect(screen.queryByText("星尘")).not.toBeInTheDocument();
    expect(screen.getByText("夜行者")).toBeVisible();
  });

  it("hiding a capsule requires a reason then calls onHide", async () => {
    const onHide = vi.fn().mockResolvedValue(undefined);
    render(<AdminCapsulesTab capsules={[capsule()]} busyId={null} onHide={onHide} onRestore={() => undefined} />);
    fireEvent.click(screen.getByText("隐藏"));
    fireEvent.change(screen.getByPlaceholderText("填写原因..."), { target: { value: "违反社区规范" } });
    fireEvent.click(screen.getByText("确认"));
    expect(onHide).toHaveBeenCalledExactlyOnceWith(1, "违反社区规范");
  });

  it("restoring a capsule calls onRestore with a default reason", async () => {
    const onRestore = vi.fn().mockResolvedValue(undefined);
    render(<AdminCapsulesTab capsules={[capsule({ visibilityStatus: "HIDDEN" })]} busyId={null} onHide={() => undefined} onRestore={onRestore} />);
    fireEvent.click(screen.getByText("恢复"));
    fireEvent.click(screen.getByText("确认"));
    expect(onRestore).toHaveBeenCalledExactlyOnceWith(1, "管理员恢复");
  });

  it("renders in English when locale is en-SG", () => {
    render(<AdminCapsulesTab locale="en-SG" capsules={[capsule()]} busyId={null} onHide={() => undefined} onRestore={() => undefined} />);
    expect(screen.getByText("Hide")).toBeVisible();
    expect(screen.getByText("Restore")).toBeVisible();
  });
});
