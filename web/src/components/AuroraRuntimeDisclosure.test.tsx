import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AuroraRuntimeDisclosure } from "./AuroraRuntimeDisclosure";

describe("AuroraRuntimeDisclosure", () => {
  it("keeps mock mode visibly distinguishable while technical diagnostics stay expandable", () => {
    render(<AuroraRuntimeDisclosure signal={{
      stage: "idle",
      runtime: "dual",
      responseSource: "DEMO_MODE",
      diagnostics: {
        provider: "mock",
        model: "aurora-demo",
        foregroundSource: "local-timeout",
        plannerStatus: "SCHEDULED",
        guidanceSource: "bootstrap",
        fallbackReason: "configured-mock",
        stageLatenciesMs: { speaker: 18, criticalPathTotal: 24 }
      }
    }} />);

    expect(screen.getByText("演示模式")).toBeVisible();
    expect(screen.queryByText("configured-mock")).not.toBeVisible();
    fireEvent.click(screen.getByLabelText("回应来源信息"));
    expect(screen.getByText("当前是可持续辨认的演示体验，内容不冒充真实模型调用。")).toBeVisible();
    expect(screen.getByRole("region", { name: "团队诊断" })).toBeVisible();
    expect(screen.getByText("configured-mock")).toBeVisible();
    expect(screen.getByText("speaker 18ms · criticalPathTotal 24ms")).toBeVisible();
  });

  it("uses natural English disclosure for a basic response", () => {
    render(<AuroraRuntimeDisclosure locale="en-SG" signal={{
      stage: "idle",
      runtime: "single",
      responseSource: "BASIC_RESPONSE",
      diagnostics: { provider: "minimax", model: "MiniMax-M2.5", fallbackReason: "speaker-fallback" }
    }} />);

    expect(screen.getByText("Using a basic response for now")).toBeVisible();
    fireEvent.click(screen.getByLabelText("Response source information"));
    expect(screen.getByText(/clearly switched to a basic response/)).toBeVisible();
    expect(screen.getByRole("region", { name: "Team diagnostics" })).toBeVisible();
  });

  it("does not add an empty disclosure before provenance arrives", () => {
    const { container } = render(<AuroraRuntimeDisclosure signal={{ stage: "idle", runtime: "single" }} />);
    expect(container).toBeEmptyDOMElement();
  });
});
