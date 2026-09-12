import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PortraitClaimsView } from "../api";
import { PortraitClaimsPanel } from "./PortraitClaimsPanel";

afterEach(cleanup);

const view: PortraitClaimsView = {
  claims: [
    { claimId: 11, claimKey: "AURORA_UNDERSTANDING:0:self_understanding", claimType: "AURORA_UNDERSTANDING",
      state: "CONFIRMED", authorityLevel: "USER_CORRECTION", value: "\"安静恢复精力\"",
      version: "2", scope: "PRIVATE", sourceType: "USER_CORRECTION" },
    { claimId: 12, claimKey: "表达习惯", claimType: "EXPRESSION_STYLE",
      state: "INFERRED", authorityLevel: "MODEL_INFERENCE", value: "\"喜欢长段落自我分析\"",
      version: "1", scope: "CAPSULE_RUNTIME", sourceType: "AUTO_EXTRACTION" },
    { claimId: 13, claimKey: "关系节律", claimType: "RELATION_RHYTHM",
      state: "CONFLICTING", authorityLevel: "MODEL_INFERENCE", value: "\"每周联系一次\"",
      version: "3", scope: "SOCIAL", sourceType: "AUTO_EXTRACTION" }
  ],
  unknownDimensions: 2,
  explanation: "每项理解都标明来源与状态。",
  suppressed: [
    { claimId: 20, claimKey: "支持偏好", claimType: "PORTRAIT_DIM",
      state: "SUPPRESSED", authorityLevel: "MODEL_INFERENCE", value: "\"需要具体的行动建议\"",
      version: "2", scope: "PRIVATE", sourceType: "AUTO_EXTRACTION" }
  ]
};

function setup(overrides: Partial<Parameters<typeof PortraitClaimsPanel>[0]> = {}) {
  const onLoad = vi.fn();
  const onSuppress = vi.fn();
  const onRestore = vi.fn();
  const onDelete = vi.fn();
  const props = {
    view, loading: false, loaded: true, busyClaimId: null,
    onLoad, onSuppress, onRestore, onDelete, locale: "zh-CN" as const, ...overrides
  };
  render(<PortraitClaimsPanel {...props} />);
  return { onLoad, onSuppress, onRestore, onDelete };
}

describe("PortraitClaimsPanel (CP-23)", () => {
  it("auto-loads once when not yet loaded, and never re-triggers while loading", () => {
    const first = { onLoad: vi.fn() };
    render(<PortraitClaimsPanel view={null} loading={false} loaded={false}
      busyClaimId={null} onLoad={first.onLoad} onSuppress={vi.fn()}
      onRestore={vi.fn()} onDelete={vi.fn()} locale="zh-CN" />);
    expect(first.onLoad).toHaveBeenCalledOnce();

    const second = { onLoad: vi.fn() };
    render(<PortraitClaimsPanel view={null} loading={true} loaded={false}
      busyClaimId={null} onLoad={second.onLoad} onSuppress={vi.fn()}
      onRestore={vi.fn()} onDelete={vi.fn()} locale="zh-CN" />);
    expect(second.onLoad).not.toHaveBeenCalled();
  });

  it("shows every claim with its honest state, source and the unknown count", () => {
    setup();
    const list = screen.getAllByRole("list")[0];
    expect(within(list).getAllByRole("listitem")).toHaveLength(3);
    expect(screen.getByText("已确认")).toBeInTheDocument();
    expect(screen.getByText("推断")).toBeInTheDocument();
    expect(screen.getByText("冲突")).toBeInTheDocument();
    expect(screen.getByText(/来自你的确认/)).toBeInTheDocument();
    expect(screen.getAllByText(/来自 Aurora 的观察/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/还有 2 个维度/)).toBeInTheDocument();
    expect(screen.getByText(/安静恢复精力/)).toBeInTheDocument();
  });

  it("parks an inferred claim with an optional reason after one confirm step", () => {
    const { onSuppress } = setup();
    const rows = screen.getAllByRole("listitem");
    const inferredRow = rows.find(row => within(row).queryByText("推断"))!;
    fireEvent.click(within(inferredRow).getByRole("button", { name: "搁置" }));
    const reasonInput = within(inferredRow).getByPlaceholderText("可选：为什么这不太是你");
    fireEvent.change(reasonInput, { target: { value: "这不太是我" } });
    fireEvent.click(within(inferredRow).getAllByRole("button", { name: "搁置" }).at(-1)!);
    expect(onSuppress).toHaveBeenCalledExactlyOnceWith(12, "这不太是我");
  });

  it("delete is behind an explicit confirmation", () => {
    const { onDelete } = setup();
    const rows = screen.getAllByRole("listitem");
    const row = rows.find(item => within(item).queryByText("冲突"))!;
    fireEvent.click(within(row).getByRole("button", { name: "删除" }));
    expect(onDelete).not.toHaveBeenCalled();
    fireEvent.click(within(row).getByRole("button", { name: "确认删除" }));
    expect(onDelete).toHaveBeenCalledExactlyOnceWith(13, "");
  });

  it("parked claims live in their own section with restore, and no park/delete actions", () => {
    const { onRestore, onSuppress, onDelete } = setup();
    const section = screen.getByText("已搁置的理解").closest("details")!;
    fireEvent.click(screen.getByText("已搁置的理解"));
    const parked = within(section).getAllByRole("listitem")[0];
    expect(within(parked).getByText("已搁置")).toBeInTheDocument();
    expect(within(parked).queryByRole("button", { name: "搁置" })).not.toBeInTheDocument();
    expect(within(parked).queryByRole("button", { name: "删除" })).not.toBeInTheDocument();
    fireEvent.click(within(parked).getByRole("button", { name: "恢复" }));
    expect(onRestore).toHaveBeenCalledExactlyOnceWith(20);
    expect(onSuppress).not.toHaveBeenCalled();
    expect(onDelete).not.toHaveBeenCalled();
  });

  it("is honest when nothing is understood yet", () => {
    render(<PortraitClaimsPanel view={{ claims: [], unknownDimensions: 5, explanation: "",
      suppressed: [] }} loading={false} loaded={true} busyClaimId={null}
      onLoad={vi.fn()} onSuppress={vi.fn()} onRestore={vi.fn()} onDelete={vi.fn()} locale="en-SG" />);
    expect(screen.getByText("Aurora hasn't formed any understanding of you yet.")).toBeInTheDocument();
    expect(screen.getByText(/5 more dimensions are honestly unknown/)).toBeInTheDocument();
  });
});
