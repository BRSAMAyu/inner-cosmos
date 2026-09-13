import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PortraitClaimsView, UnderstandingClaim } from "../api";
import { PortraitClaimsPanel } from "./PortraitClaimsPanel";

// CP-21: the panel detects version conflicts through the api module's code channel
// (ApiCodeError with code "CONFLICT" — the backend's HTTP 409). The mock keeps the
// exact same discrimination so a BAD_REQUEST can never masquerade as a conflict.
vi.mock("../api", () => ({
  isVersionConflictError: (error: unknown) =>
    error instanceof Error && (error as { code?: string }).code === "CONFLICT"
}));

const conflictError = () => Object.assign(new Error("version is stale"), { code: "CONFLICT" });
const plainError = () => Object.assign(new Error("只有当前有效的理解才能搁置"), { code: "BAD_REQUEST" });

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
    onLoad, onSuppress, onRestore, onDelete,
    onLoadHistory: vi.fn(() => Promise.resolve([])),
    onOpenSourceSession: vi.fn(),
    locale: "zh-CN" as const, ...overrides
  };
  render(<PortraitClaimsPanel {...props} />);
  return { onLoad, onSuppress, onRestore, onDelete };
}

describe("PortraitClaimsPanel (CP-23)", () => {
  it("auto-loads once when not yet loaded, and never re-triggers while loading", () => {
    const first = { onLoad: vi.fn() };
    render(<PortraitClaimsPanel view={null} loading={false} loaded={false}
      busyClaimId={null} onLoad={first.onLoad} onSuppress={vi.fn()}
      onRestore={vi.fn()} onDelete={vi.fn()} onLoadHistory={vi.fn(() => Promise.resolve([]))}
      onOpenSourceSession={vi.fn()} locale="zh-CN" />);
    expect(first.onLoad).toHaveBeenCalledOnce();

    const second = { onLoad: vi.fn() };
    render(<PortraitClaimsPanel view={null} loading={true} loaded={false}
      busyClaimId={null} onLoad={second.onLoad} onSuppress={vi.fn()}
      onRestore={vi.fn()} onDelete={vi.fn()} onLoadHistory={vi.fn(() => Promise.resolve([]))}
      onOpenSourceSession={vi.fn()} locale="zh-CN" />);
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
    expect(onSuppress).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ claimId: 12 }), "这不太是我");
  });

  it("delete is behind an explicit confirmation", () => {
    const { onDelete } = setup();
    const rows = screen.getAllByRole("listitem");
    const row = rows.find(item => within(item).queryByText("冲突"))!;
    fireEvent.click(within(row).getByRole("button", { name: "删除" }));
    expect(onDelete).not.toHaveBeenCalled();
    fireEvent.click(within(row).getByRole("button", { name: "确认删除" }));
    expect(onDelete).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ claimId: 13 }), "");
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
    expect(onRestore).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ claimId: 20 }));
    expect(onSuppress).not.toHaveBeenCalled();
    expect(onDelete).not.toHaveBeenCalled();
  });

  it("shows the belief-change timeline per claim: versions oldest→newest with sources and statuses", async () => {
    // The API returns the chain newest-first; the panel renders oldest-first.
    const history: UnderstandingClaim[] = [
      { id: 31, claimKey: "表达习惯", valueJson: "\"安静但直接的短句\"", authorityLevel: "USER_CORRECTION",
        status: "SUPPRESSED", version: 2, createdAt: "2026-09-08T10:00:00",
        sourceId: null, sourceType: "USER_CORRECTION" },
      { id: 30, claimKey: "表达习惯", valueJson: "\"喜欢长段落自我分析\"", authorityLevel: "MODEL_INFERENCE",
        status: "SUPERSEDED", version: 1, createdAt: "2026-09-01T10:00:00",
        sourceId: 77, sourceType: "AUTO_EXTRACTION" }
    ];
    const onOpenSourceSession = vi.fn();
    const onLoadHistory = vi.fn(() => Promise.resolve(history));
    const { userEvent } = { userEvent: null };
    render(<PortraitClaimsPanel view={view} loading={false} loaded={true} busyClaimId={null}
      onLoad={vi.fn()} onSuppress={vi.fn()} onRestore={vi.fn()} onDelete={vi.fn()}
      onLoadHistory={onLoadHistory} onOpenSourceSession={onOpenSourceSession} locale="zh-CN" />);

    const targetRow = screen.getAllByRole("listitem")
      .find(row => within(row).queryByText("推断"))!;
    fireEvent.click(within(targetRow).getByRole("button", { name: "看它怎么变的" }));
    expect(onLoadHistory).toHaveBeenCalledExactlyOnceWith("表达习惯");
    const timeline = await within(targetRow).findByRole("list", { name: "看它怎么变的" });
    const entries = within(timeline).getAllByRole("listitem");
    expect(entries).toHaveLength(2);
    // Oldest first: the superseded inference precedes the corrected version.
    expect(within(entries[0]).getByText(/来自 Aurora 的观察/)).toBeInTheDocument();
    expect(within(entries[0]).getByText(/已被取代/)).toBeInTheDocument();
    expect(within(entries[1]).getByText(/你纠正后的理解/)).toBeInTheDocument();
    expect(within(entries[1]).getByText(/被搁置/)).toBeInTheDocument();

    // CP-23↔CP-21 cross-link: only the extraction-backed version links to its source
    // conversation (the provenance chain's root); the corrected version has none.
    expect(within(entries[0]).getByRole("button", { name: "查看来源对话" })).toBeInTheDocument();
    expect(within(entries[1]).queryByRole("button", { name: "查看来源对话" })).not.toBeInTheDocument();
    fireEvent.click(within(entries[0]).getByRole("button", { name: "查看来源对话" }));
    expect(onOpenSourceSession).toHaveBeenCalledExactlyOnceWith(77);

    // Toggling collapses the timeline; a second expand reuses the cached chain.
    fireEvent.click(within(targetRow).getByRole("button", { name: "看它怎么变的" }));
    expect(within(targetRow).queryByRole("list", { name: "看它怎么变的" })).not.toBeInTheDocument();
    fireEvent.click(within(targetRow).getByRole("button", { name: "看它怎么变的" }));
    expect(await within(targetRow).findByRole("list", { name: "看它怎么变的" })).toBeInTheDocument();
    expect(onLoadHistory).toHaveBeenCalledOnce();
    void userEvent;
  });

  it("is honest when nothing is understood yet", () => {
    render(<PortraitClaimsPanel view={{ claims: [], unknownDimensions: 5, explanation: "",
      suppressed: [] }} loading={false} loaded={true} busyClaimId={null}
      onLoad={vi.fn()} onSuppress={vi.fn()} onRestore={vi.fn()} onDelete={vi.fn()}
      onLoadHistory={vi.fn(() => Promise.resolve([]))} onOpenSourceSession={vi.fn()}
      locale="en-SG" />);
    expect(screen.getByText("Aurora hasn't formed any understanding of you yet.")).toBeInTheDocument();
    expect(screen.getByText(/5 more dimensions are honestly unknown/)).toBeInTheDocument();
  });
});

describe("PortraitClaimsPanel (CP-21 conflict refresh)", () => {
  function claimRowByState(state: string) {
    return screen.getAllByRole("listitem").find(row => within(row).queryByText(state))!;
  }

  it("a 409/version conflict on suppress raises the honest banner and KEEPS the local draft", async () => {
    const onSuppress = vi.fn(() => Promise.reject(conflictError()));
    render(<PortraitClaimsPanel view={view} loading={false} loaded={true} busyClaimId={null}
      onLoad={vi.fn()} onSuppress={onSuppress} onRestore={vi.fn()} onDelete={vi.fn()}
      onLoadHistory={vi.fn(() => Promise.resolve([]))} onOpenSourceSession={vi.fn()} locale="zh-CN" />);

    const inferredRow = claimRowByState("推断");
    fireEvent.click(within(inferredRow).getByRole("button", { name: "搁置" }));
    fireEvent.change(within(inferredRow).getByPlaceholderText("可选：为什么这不太是你"),
      { target: { value: "这不太是我" } });
    fireEvent.click(within(inferredRow).getAllByRole("button", { name: "搁置" }).at(-1)!);

    const banner = await within(inferredRow).findByRole("alert");
    expect(within(banner).getByText("他人在你之前更新了这条内容")).toBeInTheDocument();
    // 选型：本地草稿保留——理由输入框和文字都还在，刷新后可对着最新状态重做。
    expect(within(inferredRow).getByPlaceholderText("可选：为什么这不太是你")).toHaveValue("这不太是我");
  });

  it("「查看最新」 on the banner re-pulls the view and clears the banner", async () => {
    const onSuppress = vi.fn(() => Promise.reject(conflictError()));
    const onLoad = vi.fn();
    render(<PortraitClaimsPanel view={view} loading={false} loaded={true} busyClaimId={null}
      onLoad={onLoad} onSuppress={onSuppress} onRestore={vi.fn()} onDelete={vi.fn()}
      onLoadHistory={vi.fn(() => Promise.resolve([]))} onOpenSourceSession={vi.fn()} locale="zh-CN" />);

    const inferredRow = claimRowByState("推断");
    fireEvent.click(within(inferredRow).getByRole("button", { name: "搁置" }));
    fireEvent.click(within(inferredRow).getAllByRole("button", { name: "搁置" }).at(-1)!);
    await within(inferredRow).findByRole("alert");

    fireEvent.click(within(inferredRow).getByRole("button", { name: "查看最新" }));
    expect(onLoad).toHaveBeenCalledOnce();
    await vi.waitFor(() => {
      expect(within(inferredRow).queryByRole("alert")).not.toBeInTheDocument();
    });
  });

  it("a conflicted restore shows the banner inside the parked row too", async () => {
    const onRestore = vi.fn(() => Promise.reject(conflictError()));
    render(<PortraitClaimsPanel view={view} loading={false} loaded={true} busyClaimId={null}
      onLoad={vi.fn()} onSuppress={vi.fn()} onRestore={onRestore} onDelete={vi.fn()}
      onLoadHistory={vi.fn(() => Promise.resolve([]))} onOpenSourceSession={vi.fn()} locale="zh-CN" />);

    const section = screen.getByText("已搁置的理解").closest("details")!;
    fireEvent.click(screen.getByText("已搁置的理解"));
    const parked = within(section).getAllByRole("listitem")[0];
    fireEvent.click(within(parked).getByRole("button", { name: "恢复" }));
    expect(within(await within(parked).findByRole("alert"))
      .getByText("他人在你之前更新了这条内容")).toBeInTheDocument();
  });

  it("a non-conflict failure never raises the conflict banner (不误报)", async () => {
    const onSuppress = vi.fn(() => Promise.reject(plainError()));
    render(<PortraitClaimsPanel view={view} loading={false} loaded={true} busyClaimId={null}
      onLoad={vi.fn()} onSuppress={onSuppress} onRestore={vi.fn()} onDelete={vi.fn()}
      onLoadHistory={vi.fn(() => Promise.resolve([]))} onOpenSourceSession={vi.fn()} locale="zh-CN" />);

    const inferredRow = claimRowByState("推断");
    fireEvent.click(within(inferredRow).getByRole("button", { name: "搁置" }));
    fireEvent.click(within(inferredRow).getAllByRole("button", { name: "搁置" }).at(-1)!);
    await vi.waitFor(() => {
      expect(within(inferredRow).queryByRole("alert")).not.toBeInTheDocument();
    });
  });

  it("a delete that dies on conflict keeps its confirmation open instead of pretending success", async () => {
    const onDelete = vi.fn(() => Promise.reject(conflictError()));
    render(<PortraitClaimsPanel view={view} loading={false} loaded={true} busyClaimId={null}
      onLoad={vi.fn()} onSuppress={vi.fn()} onRestore={vi.fn()} onDelete={onDelete}
      onLoadHistory={vi.fn(() => Promise.resolve([]))} onOpenSourceSession={vi.fn()} locale="zh-CN" />);

    const conflictingRow = claimRowByState("冲突");
    fireEvent.click(within(conflictingRow).getByRole("button", { name: "删除" }));
    fireEvent.click(within(conflictingRow).getByRole("button", { name: "确认删除" }));
    await within(conflictingRow).findByRole("alert");
    expect(within(conflictingRow).getByText(/从所有当前视图消失/)).toBeInTheDocument();
  });
});
