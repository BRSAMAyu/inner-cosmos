import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { UnderstandingCorrection } from "./UnderstandingCorrection";

afterEach(cleanup);

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
});
