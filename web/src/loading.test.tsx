import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, render, screen } from "@testing-library/react";
import { AsyncButton, ConnectError, LoadingText } from "./loading";

// 默认：不减少动效（matchMedia reduce=false）。个别用例覆盖。
function mockReducedMotion(reduce: boolean) {
  vi.stubGlobal("matchMedia", (query: string) => ({
    matches: reduce && query.includes("reduce"),
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
    onchange: null,
  }));
}

describe("加载四态 三档时序", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockReducedMotion(false);
  });
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("AsyncButton：<1s 保持原标签（不闪），1-3s 显示忙碌文案，>3s 追加微动画三点", () => {
    render(<AsyncButton busy busyText="正在保存">保存</AsyncButton>);
    // <1s：仍是原标签，但已禁用 + aria-busy
    const button = screen.getByRole("button");
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("aria-busy", "true");
    expect(button.textContent).toBe("保存");

    // 1s 后：忙碌文案，静态点（非动画）
    act(() => vi.advanceTimersByTime(1000));
    expect(button.textContent).toContain("正在保存");
    expect(button.querySelector(".loading-dots.static")).not.toBeNull();
    expect(button.querySelector(".loading-dots:not(.static)")).toBeNull();

    // 3s 后：进入微动画档（去掉 static）
    act(() => vi.advanceTimersByTime(2000));
    expect(button.querySelector(".loading-dots.static")).toBeNull();
    expect(button.querySelector(".loading-dots")).not.toBeNull();
  });

  it("AsyncButton：busy 变假立即回到原标签且可用", () => {
    const { rerender } = render(<AsyncButton busy busyText="正在保存">保存</AsyncButton>);
    act(() => vi.advanceTimersByTime(1500));
    expect(screen.getByRole("button").textContent).toContain("正在保存");
    rerender(<AsyncButton busy={false} busyText="正在保存">保存</AsyncButton>);
    const button = screen.getByRole("button");
    expect(button).not.toBeDisabled();
    expect(button.textContent).toBe("保存");
  });

  it("prefers-reduced-motion：封顶在文案档，永不进入微动画", () => {
    mockReducedMotion(true);
    render(<AsyncButton busy busyText="正在保存">保存</AsyncButton>);
    act(() => vi.advanceTimersByTime(5000));
    const button = screen.getByRole("button");
    expect(button.textContent).toContain("正在保存");
    // 5s 后仍是静态点，没有动画点
    expect(button.querySelector(".loading-dots.static")).not.toBeNull();
    expect(button.querySelector(".loading-dots:not(.static)")).toBeNull();
  });

  it("AsyncButton：默认 type=button，不触发表单提交", () => {
    const onSubmit = vi.fn();
    render(<form onSubmit={onSubmit}><AsyncButton busy={false}>点我</AsyncButton></form>);
    screen.getByRole("button").click();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("LoadingText：<1s 什么都不渲染，1s 后出现文案", () => {
    const { container } = render(<LoadingText busy>正在加载</LoadingText>);
    expect(container.querySelector(".loading-text")).toBeNull();
    act(() => vi.advanceTimersByTime(1000));
    expect(container.querySelector(".loading-text")).not.toBeNull();
    expect(screen.getByRole("status").textContent).toContain("正在加载");
  });

  it("LoadingText：busy 为假时不渲染", () => {
    const { container } = render(<LoadingText busy={false}>正在加载</LoadingText>);
    act(() => vi.advanceTimersByTime(5000));
    expect(container.querySelector(".loading-text")).toBeNull();
  });

  it("ConnectError：显示错误详情，重试按钮回调 onRetry（恢复路径）", () => {
    const onRetry = vi.fn();
    render(<ConnectError message="暂时无法连接你的内宇宙" onRetry={onRetry} />);
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("暂时无法连接你的内宇宙")).toBeVisible();
    screen.getByRole("button", { name: "重试" }).click();
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it("ConnectError renders in English when locale is en-SG", () => {
    render(<ConnectError locale="en-SG" message="Could not reach the backend" onRetry={() => undefined} />);
    expect(screen.getByText("Couldn't connect to your Inner Cosmos")).toBeVisible();
    expect(screen.getByRole("button", { name: "Retry" })).toBeVisible();
  });
});
