import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { SafetyHarborPage } from "./SafetyHarborPage";

afterEach(cleanup);

const resources = ["如果你正处于紧急危险中，请立即拨打 110（报警），或联系身边可信赖的人。"];

describe("SafetyHarborPage", () => {
  it("renders the breathing exercise, 5-4-3-2-1 grounding steps, self-care suggestions and resources (zh-CN)", () => {
    render(<SafetyHarborPage resources={resources} locale="zh-CN" onBack={vi.fn()} onTalkToAurora={vi.fn()} />);
    expect(screen.getByRole("heading", { name: "安全避风港" })).toBeInTheDocument();
    // 5-4-3-2-1 grounding: all five senses must be present.
    expect(screen.getByText("你能看到的东西")).toBeInTheDocument();
    expect(screen.getByText("你能摸到的东西")).toBeInTheDocument();
    expect(screen.getByText("你能听到的声音")).toBeInTheDocument();
    expect(screen.getByText("你能闻到的气味")).toBeInTheDocument();
    expect(screen.getByText("你能尝到的味道")).toBeInTheDocument();
    // Self-care suggestions.
    expect(screen.getByText("喝一杯温水")).toBeInTheDocument();
    // Real backend crisis resources are rendered here too, not just in the alert card.
    expect(screen.getByText(/如果你正处于紧急危险中/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /110/ })).toHaveAttribute("href", "tel:110");
  });

  it("renders in English for en-SG", () => {
    render(<SafetyHarborPage resources={[]} locale="en-SG" onBack={vi.fn()} onTalkToAurora={vi.fn()} />);
    expect(screen.getByRole("heading", { name: "Safety Harbor" })).toBeInTheDocument();
    expect(screen.getByText("Something you can see")).toBeInTheDocument();
  });

  it("wires the back and talk-to-Aurora actions", () => {
    const onBack = vi.fn();
    const onTalkToAurora = vi.fn();
    render(<SafetyHarborPage resources={resources} locale="zh-CN" onBack={onBack} onTalkToAurora={onTalkToAurora} />);
    fireEvent.click(screen.getByRole("button", { name: "和 Aurora 聊聊" }));
    expect(onTalkToAurora).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole("button", { name: "返回核心" }));
    expect(onBack).toHaveBeenCalledOnce();
  });
});
