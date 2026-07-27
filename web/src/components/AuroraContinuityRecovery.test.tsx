import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuroraContinuityRecovery } from "./AuroraContinuityRecovery";

afterEach(cleanup);

describe("AuroraContinuityRecovery", () => {
  it("shows the durable replay as the active recovery step", () => {
    render(<AuroraContinuityRecovery
      signal={{ phase: "recovering", turnId: 42 }}
      locale="en-SG"
      onDismiss={() => undefined}
    />);

    expect(screen.getByTestId("continuity-recovery")).toHaveClass("recovering");
    expect(screen.getByText("Connection interruption detected")).toBeInTheDocument();
    expect(screen.getByText("Replaying the durable timeline").closest("li")).toHaveClass("active");
  });

  it("makes the successful no-loss outcome explicit and dismissible", () => {
    const dismiss = vi.fn();
    render(<AuroraContinuityRecovery
      signal={{ phase: "recovered", turnId: 42 }}
      locale="en-SG"
      onDismiss={dismiss}
    />);

    expect(screen.getByText("Recovery complete — no message or history was lost")).toBeInTheDocument();
    expect(screen.getByText("Messages and history intact").closest("li")).toHaveClass("done");
    fireEvent.click(screen.getByRole("button", { name: "Dismiss continuity status" }));
    expect(dismiss).toHaveBeenCalledOnce();
  });
});
