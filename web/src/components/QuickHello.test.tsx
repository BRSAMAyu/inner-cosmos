import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { UserProfileSettings } from "../api";
import { QuickHello } from "./QuickHello";

const profile: UserProfileSettings = {
  id: 91, username: "new-user", nickname: "New", role: "USER",
  auroraName: null, auroraTone: null, preferredInputType: null, socialReachabilityStatus: null,
  bio: null, reflectionDepth: null, allowMemoryRecall: null, quietHoursStart: null, quietHoursEnd: null,
  proactiveSensitivity: null, allowMultiMessage: null, focusModeEnabled: null, focusWindowsJson: null,
  currentEnvironmentLabel: null, weatherAwarenessEnabled: null, timeAwarenessEnabled: null, timezone: null,
};

afterEach(() => {
  cleanup();
  localStorage.removeItem("ic.quick-hello.91");
});

describe("QuickHello", () => {
  it("offers four compact choices, one optional line and a real skip", () => {
    render(<QuickHello profile={profile} onSave={vi.fn()} />);
    expect(screen.getAllByRole("group")).toHaveLength(4);
    expect(screen.getByPlaceholderText("例如：刚换了工作，想找回自己的节奏")).toHaveAttribute("maxlength", "120");
    fireEvent.click(screen.getByRole("button", { name: "暂时跳过" }));
    expect(screen.queryByRole("heading", { name: "先让 Aurora 认识此刻的你" })).not.toBeInTheDocument();
    expect(localStorage.getItem("ic.quick-hello.91")).toBe("done");
  });

  it("maps the quick choices and free line onto existing profile fields", async () => {
    const onSave = vi.fn().mockResolvedValue(true);
    const onBegin = vi.fn();
    render(<QuickHello profile={profile} onSave={onSave} onBegin={onBegin} />);
    fireEvent.click(screen.getByRole("button", { name: "直接" }));
    fireEvent.click(screen.getByRole("button", { name: "多关心" }));
    fireEvent.click(screen.getByRole("button", { name: "一起深挖" }));
    fireEvent.click(screen.getByRole("button", { name: "变化 / 选择" }));
    fireEvent.change(screen.getByPlaceholderText("例如：刚换了工作，想找回自己的节奏"),
      { target: { value: "  正在适应新的城市  " } });
    fireEvent.click(screen.getByRole("button", { name: "就这样开始" }));

    await vi.waitFor(() => expect(onSave).toHaveBeenCalledExactlyOnceWith({
      auroraTone: "朋友式直接", proactiveSensitivity: 5, reflectionDepth: 4,
      allowMemoryRecall: true, currentEnvironmentLabel: "变化与选择 · 正在适应新的城市",
    }));
    await vi.waitFor(() =>
      expect(screen.queryByRole("heading", { name: "先让 Aurora 认识此刻的你" })).not.toBeInTheDocument());
    expect(onBegin).toHaveBeenCalledOnce();
  });

  it("renders the complete journey in English", () => {
    render(<QuickHello profile={profile} locale="en-SG" onSave={vi.fn()} />);
    expect(screen.getByRole("heading", { name: "Let Aurora meet you as you are now" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Skip for now" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Begin like this" })).toBeVisible();
    expect(screen.getByPlaceholderText(/I just changed jobs/)).toBeVisible();
  });

  it("stays available with a gentle error when saving fails", async () => {
    render(<QuickHello profile={profile} onSave={vi.fn().mockResolvedValue(false)} />);
    fireEvent.click(screen.getByRole("button", { name: "就这样开始" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("暂时没能保存");
    expect(screen.getByRole("heading", { name: "先让 Aurora 认识此刻的你" })).toBeVisible();
    expect(localStorage.getItem("ic.quick-hello.91")).toBeNull();
  });

  it("does not appear for an already-personalised profile", () => {
    render(<QuickHello profile={{ ...profile, auroraTone: "温柔安静" }} onSave={vi.fn()} />);
    expect(screen.queryByRole("heading", { name: "先让 Aurora 认识此刻的你" })).not.toBeInTheDocument();
  });
});
