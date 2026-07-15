import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuroraConversation } from "./AuroraConversation";

afterEach(cleanup);

describe("AuroraConversation", () => {
  it("preserves multi-message and partial interruption semantics", () => {
    render(<AuroraConversation messages={[
      { key: "u1", speaker: "USER", text: "先听我说" },
      { key: "a1", speaker: "AURORA", text: "我在这里", partial: true }
    ]} activeTurnId={7} draft="新的补充" sessionReady onDraftChange={() => undefined}
      onSubmit={event => event.preventDefault()} onStop={() => undefined} />);
    expect(screen.getByText("先听我说")).toBeVisible();
    expect(screen.getByText("我在这里")).toBeVisible();
    expect(screen.getByText("停在这里")).toBeVisible();
    expect(screen.getByRole("button", { name: "打断并发送" })).toBeEnabled();
  });

  it("keeps stop and draft changes under caller control", () => {
    const onStop = vi.fn();
    const onDraftChange = vi.fn();
    render(<AuroraConversation messages={[]} activeTurnId={9} draft="" sessionReady
      onDraftChange={onDraftChange} onSubmit={event => event.preventDefault()} onStop={onStop} />);
    fireEvent.change(screen.getByLabelText("写给 Aurora"), { target: { value: "等等" } });
    fireEvent.click(screen.getByRole("button", { name: "停止回应" }));
    expect(onDraftChange).toHaveBeenCalledWith("等等");
    expect(onStop).toHaveBeenCalledOnce();
  });
});
