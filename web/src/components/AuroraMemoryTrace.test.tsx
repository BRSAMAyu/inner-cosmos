import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuroraMemoryTrace } from "./AuroraMemoryTrace";

afterEach(cleanup);

const memories = [
  { id: 7, title: "星期三的固定河岸路线", summary: "散步恢复节律", status: "ACTIVE",
    versionNo: 2, consentScope: "AURORA_PRIVATE", memoryLayer: "EPISODIC", confidence: .86 }
];

describe("AuroraMemoryTrace", () => {
  it("stays absent when the turn did not use memory", () => {
    render(<AuroraMemoryTrace trace={null} memories={memories}
      onOpenMemory={vi.fn()} onDismiss={vi.fn()} />);
    expect(screen.queryByRole("complementary")).not.toBeInTheDocument();
  });

  it("names the exact retrieved memory and opens its provenance", () => {
    const onOpenMemory = vi.fn();
    render(<AuroraMemoryTrace trace={{ referencedMemoryIds: [7], detectedTheme: "恢复" }}
      memories={memories} onOpenMemory={onOpenMemory} onDismiss={vi.fn()} />);

    expect(screen.getByRole("complementary", { name: "Aurora 本轮使用的记忆" })).toBeVisible();
    expect(screen.getByText("星期三的固定河岸路线")).toBeVisible();
    expect(screen.getByText("这一轮的主题：恢复")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: /星期三的固定河岸路线/ }));
    expect(onOpenMemory).toHaveBeenCalledExactlyOnceWith(7);
  });

  it("is honest when long-term context was used without a resolvable star id", () => {
    render(<AuroraMemoryTrace trace={{ referencedMemoryIds: [] }}
      memories={memories} onOpenMemory={vi.fn()} onDismiss={vi.fn()} />);
    expect(screen.getByText(/没有把依据锁定到单独一颗星/)).toBeVisible();
  });
});
