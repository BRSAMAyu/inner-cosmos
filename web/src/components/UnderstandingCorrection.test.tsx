import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { UserCorrection } from "../api";
import { UnderstandingCorrection } from "./UnderstandingCorrection";

afterEach(cleanup);

const correction = (over: Partial<UserCorrection> = {}): UserCorrection => ({
  id: 1, targetType: "AURORA_UNDERSTANDING", fieldName: "self_understanding",
  oldValue: "你更喜欢独处", newValue: "我只是需要先恢复精力", reason: "用户主动校准",
  status: "CONFIRMED", createdAt: "2026-07-16T09:30:00", ...over
});

describe("UnderstandingCorrection", () => {
  it("previews and lets a general correction proceed without a specific target", () => {
    const onPreview = vi.fn();
    render(<UnderstandingCorrection claims={[]} oldValue="" newValue="我是在谨慎选择下一步" impact={null} busy={false}
      target={null} onOldValue={() => undefined} onNewValue={() => undefined} onPreview={onPreview}
      onCancelPreview={() => undefined} onConfirm={() => undefined} onClearTarget={() => undefined} />);
    expect(screen.getByLabelText("Aurora 原先怎样理解（可选）")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "预览会改变什么" }));
    expect(onPreview).toHaveBeenCalledOnce();
  });

  it("shows the targeted memory banner, hides the old-value field, and lets the user clear the target", () => {
    const onClearTarget = vi.fn();
    render(<UnderstandingCorrection claims={[]} oldValue="" newValue="我更需要被理解" impact={null} busy={false}
      target={{ id: 7, label: "我总是在逃避" }} onOldValue={() => undefined} onNewValue={() => undefined}
      onPreview={() => undefined} onCancelPreview={() => undefined} onConfirm={() => undefined} onClearTarget={onClearTarget} />);
    expect(screen.getByText("我总是在逃避")).toBeVisible();
    expect(screen.queryByLabelText("Aurora 原先怎样理解（可选）")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "改为整体理解" }));
    expect(onClearTarget).toHaveBeenCalledOnce();
  });

  it("shows impact items in preview and lets the user confirm", () => {
    const onConfirm = vi.fn();
    render(<UnderstandingCorrection claims={[]} oldValue="" newValue="我更需要被理解" busy={false} target={null}
      impact={{ claimKey: "k", newValue: "我更需要被理解", affectedMemoryCount: 1, authorizedCapsuleContextCount: 1, confirmationRequired: true,
        impacts: [{ kind: "STARFIELD", targetId: null, label: "记忆星空", action: "不再把已替代记忆展示为当前事实" }] }}
      onOldValue={() => undefined} onNewValue={() => undefined} onPreview={() => undefined}
      onCancelPreview={() => undefined} onConfirm={onConfirm} onClearTarget={() => undefined} />);
    expect(screen.getByText("记忆星空")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "确认，这是更准确的我" }));
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it("lists past corrections with their before→after change and reason", () => {
    render(<UnderstandingCorrection claims={[]} oldValue="" newValue="" impact={null} busy={false} target={null}
      corrections={[correction({ id: 42, oldValue: "你更喜欢独处", newValue: "我只是需要先恢复精力", reason: "用户主动校准" })]}
      onOldValue={() => undefined} onNewValue={() => undefined} onPreview={() => undefined}
      onCancelPreview={() => undefined} onConfirm={() => undefined} onClearTarget={() => undefined} onRetire={() => undefined} />);
    expect(screen.getByText("你更喜欢独处")).toBeVisible();
    expect(screen.getByText("我只是需要先恢复精力")).toBeVisible();
    expect(screen.getByText(/用户主动校准/)).toBeVisible();
  });

  it("retiring a correction requires an inline confirmation before calling onRetire with its id", () => {
    const onRetire = vi.fn();
    render(<UnderstandingCorrection claims={[]} oldValue="" newValue="" impact={null} busy={false} target={null}
      corrections={[correction({ id: 42 })]} retiringId={null}
      onOldValue={() => undefined} onNewValue={() => undefined} onPreview={() => undefined}
      onCancelPreview={() => undefined} onConfirm={() => undefined} onClearTarget={() => undefined} onRetire={onRetire} />);
    fireEvent.click(screen.getByRole("button", { name: "让这条退休" }));
    expect(onRetire).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "确认退休" }));
    expect(onRetire).toHaveBeenCalledExactlyOnceWith(42);
  });

  it("shows a retiring state and disables the confirm while that correction is being retired", () => {
    render(<UnderstandingCorrection claims={[]} oldValue="" newValue="" impact={null} busy={false} target={null}
      corrections={[correction({ id: 42 })]} retiringId={42}
      onOldValue={() => undefined} onNewValue={() => undefined} onPreview={() => undefined}
      onCancelPreview={() => undefined} onConfirm={() => undefined} onClearTarget={() => undefined} onRetire={() => undefined} />);
    expect(screen.getByRole("button", { name: "退休中…" })).toBeDisabled();
  });

  it("disables the confirm-retire button once the inline confirmation is open and that correction is busy", () => {
    // Characterizes the actual async trigger (btn-retire-confirm, calling onRetire) rather than
    // the "让这条退休" opener button above (which only ever shows retiringId's ternary because it
    // renders before the inline confirmation is opened, and confirmingId is local state).
    const { rerender } = render(<UnderstandingCorrection claims={[]} oldValue="" newValue="" impact={null} busy={false} target={null}
      corrections={[correction({ id: 42 })]} retiringId={null}
      onOldValue={() => undefined} onNewValue={() => undefined} onPreview={() => undefined}
      onCancelPreview={() => undefined} onConfirm={() => undefined} onClearTarget={() => undefined} onRetire={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "让这条退休" }));
    rerender(<UnderstandingCorrection claims={[]} oldValue="" newValue="" impact={null} busy={false} target={null}
      corrections={[correction({ id: 42 })]} retiringId={42}
      onOldValue={() => undefined} onNewValue={() => undefined} onPreview={() => undefined}
      onCancelPreview={() => undefined} onConfirm={() => undefined} onClearTarget={() => undefined} onRetire={() => undefined} />);
    // AsyncButton (web/src/loading.tsx) keeps the original label for the first second of a busy
    // state (the spec's "don't flash before 1s" rule), so a synchronous render/assert checks
    // disabled on the original label -- matching every other AsyncButton-adopting component's tests.
    expect(screen.getByRole("button", { name: "确认退休" })).toBeDisabled();
  });

  it("does not render the history section when there are no past corrections", () => {
    render(<UnderstandingCorrection claims={[]} oldValue="" newValue="" impact={null} busy={false} target={null}
      corrections={[]}
      onOldValue={() => undefined} onNewValue={() => undefined} onPreview={() => undefined}
      onCancelPreview={() => undefined} onConfirm={() => undefined} onClearTarget={() => undefined} onRetire={() => undefined} />);
    expect(screen.queryByRole("button", { name: "让这条退休" })).not.toBeInTheDocument();
  });

  it("renders the correction surface and history in English when locale is en-SG", () => {
    render(<UnderstandingCorrection locale="en-SG" claims={[]} oldValue="" newValue="I just need to recharge first"
      impact={null} busy={false} target={null} corrections={[correction()]} onOldValue={() => undefined}
      onNewValue={() => undefined} onPreview={() => undefined} onCancelPreview={() => undefined}
      onConfirm={() => undefined} onClearTarget={() => undefined} onRetire={() => undefined} />);
    expect(screen.getByRole("heading", { name: "If this isn't quite you" })).toBeVisible();
    expect(screen.getByLabelText("How Aurora understood it before (optional)")).toBeVisible();
    expect(screen.getByRole("button", { name: "Preview what changes" })).toBeEnabled();
    expect(screen.getByRole("heading", { name: "Corrections you've confirmed" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Retire this one" })).toBeVisible();
  });
});
