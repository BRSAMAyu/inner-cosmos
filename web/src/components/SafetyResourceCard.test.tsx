import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { SafetyResourceCard } from "./SafetyResourceCard";

afterEach(() => {
  cleanup();
});

const resources = [
  "如果你正处于紧急危险中，请立即拨打 110（报警），或联系身边可信赖的人。",
  "全国心理援助热线（希望 24）· 24 小时：400-161-9995。",
  "Inner Cosmos 不提供心理诊断，也不替代医生、咨询师或热线。"
];

describe("SafetyResourceCard", () => {
  it("renders nothing when there is no alert", () => {
    const { container } = render(
      <SafetyResourceCard alert={null} resources={resources} locale="zh-CN" onDismiss={vi.fn()} />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("renders a persistent, high-visibility alert with the server's safe message and the real backend crisis resources (zh-CN)", () => {
    render(
      <SafetyResourceCard
        alert={{ riskLevel: "HIGH", featureTarget: "AURORA_CHAT", safeMessage: "先看看这些资源" }}
        resources={resources}
        locale="zh-CN"
        onDismiss={vi.fn()}
      />
    );
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("先看看这些资源")).toBeInTheDocument();
    // Every real backend resource line is rendered verbatim -- no invented content.
    for (const line of resources) expect(screen.getByText(line)).toBeInTheDocument();
    // A dialable number gets a real tel: link, mirroring the legacy safety-harbor.html behavior.
    expect(screen.getByRole("link", { name: /110/ })).toHaveAttribute("href", "tel:110");
    expect(screen.getByRole("link", { name: /400-161-9995/ })).toHaveAttribute("href", "tel:4001619995");
  });

  it("renders the universal emergency-services fallback message even before resources have loaded", () => {
    render(
      <SafetyResourceCard
        alert={{ riskLevel: "HIGH", featureTarget: "AURORA_CHAT" }}
        resources={[]}
        locale="en-SG"
        onDismiss={vi.fn()}
      />
    );
    expect(screen.getByText(/if you are in immediate danger/i)).toBeInTheDocument();
  });

  it("stays visible until the user explicitly dismisses it", () => {
    const onDismiss = vi.fn();
    render(
      <SafetyResourceCard
        alert={{ riskLevel: "HIGH", featureTarget: "AURORA_CHAT" }}
        resources={resources}
        locale="zh-CN"
        onDismiss={onDismiss}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: /我看到了/ }));
    expect(onDismiss).toHaveBeenCalledOnce();
  });
});
