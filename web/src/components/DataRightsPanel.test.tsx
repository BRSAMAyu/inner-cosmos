import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DataRightsPanel } from "./DataRightsPanel";
import type { DataRetractionReceipt } from "../api";

afterEach(cleanup);

const receipt = (overrides: Partial<DataRetractionReceipt> = {}): DataRetractionReceipt => ({
  id: 1, subjectType: "CAPSULE", subjectId: 5, derivativeType: "CAPSULE_MATCH_INDEX",
  action: "ERASED", affectedCount: 1, reason: "owner archived capsule",
  createdAt: "2026-07-19T20:10:00", ...overrides
});

describe("DataRightsPanel", () => {
  it("invites the user to load receipts and calls onLoad", () => {
    const onLoad = vi.fn();
    render(<DataRightsPanel receipts={[]} loading={false} loaded={false} onLoad={onLoad} />);
    const button = screen.getByRole("button", { name: "查看数据权利回执" });
    fireEvent.click(button);
    expect(onLoad).toHaveBeenCalledOnce();
  });

  it("shows an empty state only after a load returns nothing", () => {
    const { rerender } = render(<DataRightsPanel receipts={[]} loading={false} loaded={false} onLoad={() => undefined} />);
    expect(screen.queryByText(/还没有任何回执/)).not.toBeInTheDocument();
    rerender(<DataRightsPanel receipts={[]} loading={false} loaded onLoad={() => undefined} />);
    expect(screen.getByText(/还没有任何回执/)).toBeVisible();
  });

  it("renders each receipt with a human-readable derivative, action, reason and count", () => {
    render(<DataRightsPanel loaded loading={false} onLoad={() => undefined} receipts={[
      receipt({ id: 2, derivativeType: "MEMORY_EMBEDDING", action: "CLEARED", affectedCount: 2, reason: "memory superseded by user correction" }),
      receipt({ id: 3, derivativeType: "CAPSULE_MATCH_INDEX", action: "ERASED", affectedCount: 1, reason: "owner archived capsule" })
    ]} />);
    expect(screen.getByText("记忆检索向量")).toBeVisible();
    expect(screen.getByText("已停用")).toBeVisible();
    expect(screen.getByText("memory superseded by user correction")).toBeVisible();
    expect(screen.getByText("共鸣体匹配向量")).toBeVisible();
    expect(screen.getByText("已彻底清除")).toBeVisible();
    expect(screen.getByText(/2 项/)).toBeVisible();
  });

  it("renders English (en-SG) copy and labels when that locale is selected", () => {
    render(<DataRightsPanel loaded loading={false} onLoad={() => undefined} locale="en-SG" receipts={[
      receipt({ id: 2, derivativeType: "MEMORY_EMBEDDING", action: "CLEARED", affectedCount: 2, reason: "memory superseded by user correction" })
    ]} />);
    expect(screen.getByRole("heading", { name: "What Aurora stopped using" })).toBeVisible();
    expect(screen.getByText("memory retrieval vector")).toBeVisible();
    expect(screen.getByText("stopped using")).toBeVisible();
    expect(screen.getByText(/2 items/)).toBeVisible();
  });
});
