import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ConsentRequestDialog } from "./ConsentRequestDialog";

afterEach(cleanup);

const base = {
  purposeCode: "AI_PROVIDER_EGRESS",
  description: "将你的对话内容发送到所选的境内大模型服务以生成回应",
  withdrawalEffect: "拒绝后 AI 回应功能不可用；本地功能与数据权利不受影响",
  busy: false,
  onGrant: () => undefined,
  onDismiss: () => undefined
};

describe("ConsentRequestDialog (J01 progressive consent)", () => {
  it("renders nothing when closed", () => {
    const { container } = render(<ConsentRequestDialog {...base} open={false} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("states the purpose, the withdrawal effect and what still works without consent", () => {
    render(<ConsentRequestDialog {...base} open />);
    expect(screen.getByRole("dialog")).toBeVisible();
    expect(screen.getByText("AI_PROVIDER_EGRESS")).toBeVisible();
    expect(screen.getByText(/境内大模型/)).toBeVisible();
    expect(screen.getByText(/不会发送到任何外部模型/)).toBeVisible();
  });

  it("forwards grant and dismiss when idle", () => {
    const onGrant = vi.fn();
    const onDismiss = vi.fn();
    render(<ConsentRequestDialog {...base} open onGrant={onGrant} onDismiss={onDismiss} />);
    fireEvent.click(screen.getByRole("button", { name: /同意并继续/ }));
    expect(onGrant).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole("button", { name: "暂不" }));
    expect(onDismiss).toHaveBeenCalledOnce();
  });

  it("blocks dismissal while the decision is being recorded", () => {
    render(<ConsentRequestDialog {...base} open busy />);
    expect(screen.getByRole("button", { name: "暂不" })).toBeDisabled();
  });

  it("renders English copy for en-SG", () => {
    render(<ConsentRequestDialog {...base} open locale="en-SG" />);
    expect(screen.getByRole("heading", { name: "One consent needed" })).toBeVisible();
    expect(screen.getByText(/not sent to any external model/)).toBeVisible();
  });
});
