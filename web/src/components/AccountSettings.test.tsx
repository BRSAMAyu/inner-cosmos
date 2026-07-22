import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AccountSettings } from "./AccountSettings";
import type { UserProfileSettings } from "../api";

afterEach(cleanup);

const profile: UserProfileSettings = {
  id: 1, username: "demo", nickname: "demo", role: "USER",
  auroraName: null, auroraTone: "温柔安静", preferredInputType: null,
  socialReachabilityStatus: "PRIVATE", bio: null, reflectionDepth: 3,
  allowMemoryRecall: true, quietHoursStart: "22:00", quietHoursEnd: "07:00",
  proactiveSensitivity: 2, allowMultiMessage: true, focusModeEnabled: false,
  focusWindowsJson: null, currentEnvironmentLabel: null,
  weatherAwarenessEnabled: true, timeAwarenessEnabled: true, timezone: "Asia/Shanghai"
};

describe("AccountSettings", () => {
  it("triggers data export directly, without a confirmation form", () => {
    const onExportData = vi.fn();
    render(<AccountSettings busy={null} message={null} onChangePassword={() => undefined}
      onExportData={onExportData} onDeleteAccount={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "导出数据" }));
    expect(onExportData).toHaveBeenCalledOnce();
  });

  it("validates password length and confirmation match before calling onChangePassword", () => {
    const onChangePassword = vi.fn();
    render(<AccountSettings busy={null} message={null} onChangePassword={onChangePassword}
      onExportData={() => undefined} onDeleteAccount={() => undefined} />);
    fireEvent.click(screen.getByRole("button", { name: "修改密码" }));
    fireEvent.change(screen.getByPlaceholderText("当前密码"), { target: { value: "old-pass" } });
    fireEvent.change(screen.getByPlaceholderText("新密码（至少 8 位）"), { target: { value: "short" } });
    fireEvent.change(screen.getByPlaceholderText("再次输入新密码"), { target: { value: "short" } });
    fireEvent.click(screen.getByRole("button", { name: "确认修改" }));
    expect(screen.getByText("新密码至少 8 位")).toBeVisible();
    expect(onChangePassword).not.toHaveBeenCalled();

    fireEvent.change(screen.getByPlaceholderText("新密码（至少 8 位）"), { target: { value: "longenough1" } });
    fireEvent.change(screen.getByPlaceholderText("再次输入新密码"), { target: { value: "different1" } });
    fireEvent.click(screen.getByRole("button", { name: "确认修改" }));
    expect(screen.getByText("两次输入的新密码不一致")).toBeVisible();
    expect(onChangePassword).not.toHaveBeenCalled();

    fireEvent.change(screen.getByPlaceholderText("再次输入新密码"), { target: { value: "longenough1" } });
    fireEvent.click(screen.getByRole("button", { name: "确认修改" }));
    expect(onChangePassword).toHaveBeenCalledExactlyOnceWith("old-pass", "longenough1");
    expect(screen.queryByPlaceholderText("当前密码")).not.toBeInTheDocument();
  });

  it("requires a password before confirming account deletion, and shows the irreversibility warning", () => {
    const onDeleteAccount = vi.fn();
    render(<AccountSettings busy={null} message={null} onChangePassword={() => undefined}
      onExportData={() => undefined} onDeleteAccount={onDeleteAccount} />);
    fireEvent.click(screen.getByRole("button", { name: "删除账户" }));
    expect(screen.getByText(/此操作不可撤销/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
    expect(screen.getByText("请输入密码以确认")).toBeVisible();
    expect(onDeleteAccount).not.toHaveBeenCalled();

    fireEvent.change(screen.getByPlaceholderText("密码"), { target: { value: "my-real-password" } });
    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
    expect(onDeleteAccount).toHaveBeenCalledExactlyOnceWith("my-real-password");
  });

  it("disables the busy action and shows a status message", () => {
    render(<AccountSettings busy="export" message="数据已导出" onChangePassword={() => undefined}
      onExportData={() => undefined} onDeleteAccount={() => undefined} />);
    expect(screen.getByRole("button", { name: "导出数据" })).toBeDisabled();
    expect(screen.getByText("数据已导出")).toBeVisible();
  });

  it("seeds Aurora preferences from the loaded profile and saves a full patch on demand", () => {
    const onSaveProfile = vi.fn();
    render(<AccountSettings busy={null} message={null} onChangePassword={() => undefined}
      onExportData={() => undefined} onDeleteAccount={() => undefined}
      profile={profile} profileBusy={false} onSaveProfile={onSaveProfile} />);

    expect(screen.getByLabelText("对话风格")).toHaveValue("温柔安静");
    expect(screen.getByLabelText("反思深度")).toHaveValue("3");
    expect(screen.getByLabelText("允许记忆回溯")).toBeChecked();
    expect(screen.getByLabelText("允许多条消息")).toBeChecked();
    expect(screen.getByLabelText("主动关心频率")).toHaveValue("2");
    expect(screen.getByLabelText("谁可以找到你")).toHaveValue("PRIVATE");
    expect(screen.getByLabelText("安静时段开始")).toHaveValue("22:00");
    expect(screen.getByLabelText("安静时段结束")).toHaveValue("07:00");
    expect(screen.getByLabelText("专注模式")).not.toBeChecked();
    expect(screen.getByLabelText("感知天气")).toBeChecked();
    expect(screen.getByLabelText("感知时间")).toBeChecked();

    fireEvent.change(screen.getByLabelText("对话风格"), { target: { value: "理性清晰" } });
    fireEvent.click(screen.getByLabelText("专注模式"));
    fireEvent.click(screen.getByRole("button", { name: "保存偏好设置" }));

    expect(onSaveProfile).toHaveBeenCalledExactlyOnceWith({
      auroraTone: "理性清晰", reflectionDepth: 3, allowMemoryRecall: true, allowMultiMessage: true,
      proactiveSensitivity: 2, socialReachabilityStatus: "PRIVATE",
      quietHoursStart: "22:00", quietHoursEnd: "07:00",
      focusModeEnabled: true, weatherAwarenessEnabled: true, timeAwarenessEnabled: true
    });
  });

  it("does not render the preferences panel before the profile has loaded", () => {
    render(<AccountSettings busy={null} message={null} onChangePassword={() => undefined}
      onExportData={() => undefined} onDeleteAccount={() => undefined} />);
    expect(screen.queryByLabelText("对话风格")).not.toBeInTheDocument();
  });

  it("renders in English and validates in English when locale is en-SG", () => {
    const onChangePassword = vi.fn();
    render(<AccountSettings locale="en-SG" busy={null} message={null} onChangePassword={onChangePassword}
      onExportData={() => undefined} onDeleteAccount={() => undefined} />);
    expect(screen.getByRole("heading", { name: "Account & data" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Change password" }));
    fireEvent.change(screen.getByPlaceholderText("Current password"), { target: { value: "old-pass" } });
    fireEvent.change(screen.getByPlaceholderText("New password (at least 8 characters)"), { target: { value: "short" } });
    fireEvent.change(screen.getByPlaceholderText("Re-enter new password"), { target: { value: "short" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirm change" }));
    expect(screen.getByText("New password must be at least 8 characters")).toBeVisible();
    expect(onChangePassword).not.toHaveBeenCalled();
  });
});
