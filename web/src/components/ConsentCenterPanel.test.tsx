import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ConsentCenterPanel } from "./ConsentCenterPanel";
import type { ConsentView } from "../api";

afterEach(cleanup);

const view = (overrides: Partial<ConsentView> = {}): ConsentView => ({
  purposeCode: "AI_PROVIDER_EGRESS",
  group: "OPTIONAL_ASK",
  granted: false,
  userSettable: true,
  description: "将你的对话内容发送到所选的境内大模型服务以生成回应",
  withdrawalEffect: "拒绝后 AI 回应功能不可用；本地功能与数据权利不受影响",
  version: "PV-2026-09",
  source: "DEFAULT",
  ...overrides
});

describe("ConsentCenterPanel", () => {
  it("invites the user to load consents and calls onLoad", () => {
    const onLoad = vi.fn();
    render(<ConsentCenterPanel views={[]} loading={false} loaded={false} onLoad={onLoad}
      onDecide={() => undefined} busyPurpose={null} />);
    fireEvent.click(screen.getByRole("button", { name: "查看我的同意项" }));
    expect(onLoad).toHaveBeenCalledOnce();
  });

  it("groups purposes in the registry order and shows the required note read-only", () => {
    render(<ConsentCenterPanel loaded loading={false} onLoad={() => undefined}
      onDecide={() => undefined} busyPurpose={null} views={[
        view({ purposeCode: "VOICE_PROCESSING", group: "SENSITIVE" }),
        view({ purposeCode: "CORE_SERVICE", group: "REQUIRED", granted: true, userSettable: true }),
        view({ purposeCode: "AI_PROVIDER_EGRESS", group: "OPTIONAL_ASK" }),
        view({ purposeCode: "CAPSULE_COMPILE", group: "MANAGED_ELSEWHERE", granted: true, userSettable: false })
      ]} />);
    const groups = screen.getAllByRole("heading", { level: 4 }).map(node => node.textContent);
    expect(groups).toEqual(["服务必需", "按需征求", "敏感信息（单独同意）", "由对应功能逐项管理"]);
    expect(screen.getByText(/核心功能必需/)).toBeVisible();
    expect(screen.getByText(/在对应功能内逐项授权与撤回/)).toBeVisible();
  });

  it("shows grant/revoke per settable purpose and forwards the decision", () => {
    const onDecide = vi.fn();
    const { rerender } = render(<ConsentCenterPanel loaded loading={false}
      onLoad={() => undefined} onDecide={onDecide} busyPurpose={null} views={[
        view({ purposeCode: "AI_PROVIDER_EGRESS", granted: false })
      ]} />);
    fireEvent.click(screen.getByRole("button", { name: "同意: AI_PROVIDER_EGRESS" }));
    expect(onDecide).toHaveBeenCalledWith("AI_PROVIDER_EGRESS", true);

    rerender(<ConsentCenterPanel loaded loading={false} onLoad={() => undefined}
      onDecide={onDecide} busyPurpose={null} views={[
        view({ purposeCode: "AI_PROVIDER_EGRESS", granted: true })
      ]} />);
    expect(screen.getByText("已同意")).toBeVisible();
    expect(screen.getByText(/拒绝后 AI 回应功能不可用/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "撤回同意: AI_PROVIDER_EGRESS" }));
    expect(onDecide).toHaveBeenCalledWith("AI_PROVIDER_EGRESS", false);
  });

  it("renders no action for managed (not user-settable) purposes", () => {
    render(<ConsentCenterPanel loaded loading={false} onLoad={() => undefined}
      onDecide={() => undefined} busyPurpose={null} views={[
        view({ purposeCode: "CAPSULE_COMPILE", group: "MANAGED_ELSEWHERE", userSettable: false, granted: true })
      ]} />);
    expect(screen.queryByRole("button", { name: /撤回同意/ })).not.toBeInTheDocument();
  });

  it("disables sibling rows while one decision is in flight", () => {
    render(<ConsentCenterPanel loaded loading={false} onLoad={() => undefined}
      onDecide={() => undefined} busyPurpose="PROACTIVE_CARE" views={[
        view({ purposeCode: "PROACTIVE_CARE", group: "OPTIONAL" }),
        view({ purposeCode: "AI_PROVIDER_EGRESS", granted: true })
      ]} />);
    expect(screen.getByRole("button", { name: "撤回同意: AI_PROVIDER_EGRESS" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "同意: PROACTIVE_CARE" })).toBeDisabled();
  });

  it("renders English (en-SG) copy when that locale is selected", () => {
    render(<ConsentCenterPanel loaded loading={false} locale="en-SG"
      onLoad={() => undefined} onDecide={() => undefined} busyPurpose={null} views={[
        view({ purposeCode: "AI_PROVIDER_EGRESS" })
      ]} />);
    expect(screen.getByRole("heading", { name: "What I have agreed to" })).toBeVisible();
    expect(screen.getByText("Not granted")).toBeVisible();
    expect(screen.getByRole("button", { name: "Grant: AI_PROVIDER_EGRESS" })).toBeVisible();
  });

  // CP-07 versioned re-consent: ConsentView.source === "RE_CONSENT_REQUIRED" marks a recorded
  // decision whose version is no longer the registry's current one (stale rows never authorize).
  it("shows the re-confirm badge and version note only on RE_CONSENT_REQUIRED rows", () => {
    render(<ConsentCenterPanel loaded loading={false}
      onLoad={() => undefined} onDecide={() => undefined} busyPurpose={null} views={[
        view({ purposeCode: "AI_PROVIDER_EGRESS", source: "RE_CONSENT_REQUIRED", granted: true }),
        view({ purposeCode: "PROACTIVE_CARE", group: "OPTIONAL", source: "CONSENT_CENTER", granted: true }),
        view({ purposeCode: "ANALYTICS", group: "OPTIONAL", source: "DEFAULT" })
      ]} />);
    // Exactly one badge, on the stale row, naming the current version it would record into.
    expect(screen.getAllByText("条款已更新，需要重新确认")).toHaveLength(1);
    expect(screen.getByText(/已不再作为当前依据；重新确认后以版本 PV-2026-09 为准/)).toBeVisible();
    // The other sources stay badge-free.
    expect(screen.getByText("AI_PROVIDER_EGRESS").closest("li")).toHaveAttribute("data-re-consent", "true");
    expect(screen.getByText("PROACTIVE_CARE").closest("li")).not.toHaveAttribute("data-re-consent");
    expect(screen.getByText("ANALYTICS").closest("li")).not.toHaveAttribute("data-re-consent");
  });

  it("carries the re-confirm copy into en-SG", () => {
    render(<ConsentCenterPanel loaded loading={false} locale="en-SG"
      onLoad={() => undefined} onDecide={() => undefined} busyPurpose={null} views={[
        view({ source: "RE_CONSENT_REQUIRED" })
      ]} />);
    expect(screen.getByText("Terms updated · re-confirm")).toBeVisible();
    expect(screen.getByText(/confirming again records it under version PV-2026-09/i)).toBeVisible();
  });
});
