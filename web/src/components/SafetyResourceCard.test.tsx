import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { SafetyResourceCard } from "./SafetyResourceCard";

afterEach(() => {
  cleanup();
});

const resource = (id: string, label: string, phone: string | null, region: "CN" | "SG" | "GLOBAL" = "CN") => ({
  id, label, phone, region, authorityUrl: "https://example.gov/authority", verifiedAt: "2026-07-27",
  audience: "ALL", hours: "24/7", channel: phone ? "PHONE" as const : "NOTICE" as const,
  category: phone ? "EMERGENCY" as const : "PRODUCT_BOUNDARY" as const
});
const resources = [
  resource("cn-police", "如果你正处于紧急危险中，请立即拨打 110（报警），或联系身边可信赖的人。", "110"),
  resource("cn-help", "全国统一心理援助热线：12356。", "12356"),
  resource("cn-boundary", "Inner Cosmos 不提供心理诊断，也不替代医生、咨询师或热线。", null)
];

describe("SafetyResourceCard", () => {
  it("renders nothing when there is no alert", () => {
    const { container } = render(
      <SafetyResourceCard alert={null} resources={resources} locale="zh-CN" onDismiss={vi.fn()} />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("does not render an interruptive resource wall for a non-HIGH decision", () => {
    const { container } = render(
      <SafetyResourceCard
        alert={{ riskLevel: "MEDIUM", featureTarget: "AURORA_CHAT", safeMessage: "慢一点说" }}
        resources={resources}
        locale="zh-CN"
        onDismiss={vi.fn()}
      />
    );
    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
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
    for (const item of resources) expect(screen.getByText(item.label)).toBeInTheDocument();
    // A dialable number gets a real tel: link, mirroring the legacy safety-harbor.html behavior.
    expect(screen.getByRole("link", { name: /110/ })).toHaveAttribute("href", "tel:110");
    expect(screen.getByRole("link", { name: /12356/ })).toHaveAttribute("href", "tel:12356");
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

  it("renders verified Singapore English resources without injecting Chinese resource copy", () => {
    const singaporeResources = [
      resource("sg-police", "If you are in immediate danger, call Singapore Police at 999.", "999", "SG"),
      resource("sg-ambulance", "For emergency ambulance or fire services, call 995.", "995", "SG"),
      resource("sg-sos", "Samaritans of Singapore (SOS) · 24-hour hotline: 1767.", "1767", "SG")
    ];
    render(
      <SafetyResourceCard
        alert={{ riskLevel: "HIGH", featureTarget: "safety-harbor", safeMessage: "Your safety comes first." }}
        resources={singaporeResources}
        locale="en-SG"
        onDismiss={vi.fn()}
      />
    );
    expect(screen.getByText("Your safety comes first.")).toBeVisible();
    expect(screen.getByRole("link", { name: /999/ })).toHaveAttribute("href", "tel:999");
    expect(screen.getByRole("link", { name: /995/ })).toHaveAttribute("href", "tel:995");
    expect(screen.getByRole("link", { name: /1767/ })).toHaveAttribute("href", "tel:1767");
    expect(screen.queryByText(/心理援助/)).not.toBeInTheDocument();
  });

  it("renders GENTLE_CHECK_IN as a polite collapsed support offer without an alert wall", () => {
    render(
      <SafetyResourceCard
        alert={{
          riskLevel: "MEDIUM", safetyState: "GENTLE_CHECK_IN",
          featureTarget: "safety-harbor", safeMessage: "Can I gently check: are you safe right now?"
        }}
        resources={[
          resource("sg-sos", "Samaritans of Singapore (SOS) · 24-hour hotline: 1767.", "1767", "SG")
        ]}
        locale="en-SG"
        onDismiss={vi.fn()}
      />
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("A gentle safety check-in");
    expect(screen.getByText(/Samaritans of Singapore/)).not.toBeVisible();
    fireEvent.click(screen.getByText("Expand local support when you want it"));
    expect(screen.getByRole("link", { name: /1767/ })).toHaveAttribute("href", "tel:1767");
  });

  it("moves keyboard focus to a HIGH card when it becomes urgent", () => {
    render(
      <SafetyResourceCard
        alert={{ riskLevel: "HIGH", safetyState: "HIGH_CONFIRMED", featureTarget: "safety-harbor" }}
        resources={resources}
        locale="zh-CN"
        onDismiss={vi.fn()}
      />
    );
    expect(screen.getByRole("alert")).toHaveFocus();
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
