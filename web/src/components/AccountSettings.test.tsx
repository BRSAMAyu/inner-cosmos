import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
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

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(res => { resolve = res; });
  return { promise, resolve };
}

describe("AccountSettings", () => {
  it("triggers data export directly, without a confirmation form", () => {
    const onExportData = vi.fn();
    render(<AccountSettings busy={null} message={null} onChangePassword={() => Promise.resolve(null)}
      onExportData={onExportData} onDeleteAccount={() => Promise.resolve(null)} />);
    fireEvent.click(screen.getByRole("button", { name: "导出数据" }));
    expect(onExportData).toHaveBeenCalledOnce();
  });

  it("validates password length and confirmation match before calling onChangePassword", () => {
    const onChangePassword = vi.fn();
    render(<AccountSettings busy={null} message={null} onChangePassword={onChangePassword}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)} />);
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
  });

  // Gemini audit 4.10 (CONFIRMED/P1): the form used to call onChangePassword and then close/clear
  // ITSELF synchronously, before the async result was known -- a slow or failing request could
  // "appear to have succeeded" (form already closed) or leave the user unable to retry with their
  // original input. The form must now stay open (and disabled) until the promise resolves, and
  // must only close/clear on a CONFIRMED success.
  it("keeps the password form open and disabled while the change is in flight, and only closes/clears on confirmed success", async () => {
    const pending = deferred<string | null>();
    const onChangePassword = vi.fn().mockReturnValue(pending.promise);
    render(<AccountSettings busy={null} message={null} onChangePassword={onChangePassword}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)} />);
    fireEvent.click(screen.getByRole("button", { name: "修改密码" }));
    fireEvent.change(screen.getByPlaceholderText("当前密码"), { target: { value: "old-pass" } });
    fireEvent.change(screen.getByPlaceholderText("新密码（至少 8 位）"), { target: { value: "longenough1" } });
    fireEvent.change(screen.getByPlaceholderText("再次输入新密码"), { target: { value: "longenough1" } });
    fireEvent.click(screen.getByRole("button", { name: "确认修改" }));
    expect(onChangePassword).toHaveBeenCalledExactlyOnceWith("old-pass", "longenough1");

    // Still in flight: the form must NOT have closed yet, and the confirm button must be disabled
    // (guards against a double-submit while the first request is still outstanding).
    expect(screen.getByPlaceholderText("当前密码")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("当前密码")).toHaveValue("old-pass");
    expect(screen.getByRole("button", { name: "确认修改" })).toBeDisabled();

    // A second click while still in flight must not call onChangePassword again.
    fireEvent.click(screen.getByRole("button", { name: "确认修改" }));
    expect(onChangePassword).toHaveBeenCalledTimes(1);

    await act(async () => { pending.resolve(null); await pending.promise; });

    // Confirmed success -- NOW the form closes and clears.
    expect(screen.queryByPlaceholderText("当前密码")).not.toBeInTheDocument();
  });

  it("keeps the user's input and shows an inline focused error when the password change fails, instead of closing the form", async () => {
    const pending = deferred<string | null>();
    const onChangePassword = vi.fn().mockReturnValue(pending.promise);
    render(<AccountSettings busy={null} message={null} onChangePassword={onChangePassword}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)} />);
    fireEvent.click(screen.getByRole("button", { name: "修改密码" }));
    fireEvent.change(screen.getByPlaceholderText("当前密码"), { target: { value: "wrong-old-pass" } });
    fireEvent.change(screen.getByPlaceholderText("新密码（至少 8 位）"), { target: { value: "longenough1" } });
    fireEvent.change(screen.getByPlaceholderText("再次输入新密码"), { target: { value: "longenough1" } });
    fireEvent.click(screen.getByRole("button", { name: "确认修改" }));

    await act(async () => { pending.resolve("当前密码不正确"); await pending.promise; });

    // The form is STILL open with the user's original input intact -- nothing was silently wiped.
    expect(screen.getByPlaceholderText("当前密码")).toHaveValue("wrong-old-pass");
    expect(screen.getByPlaceholderText("新密码（至少 8 位）")).toHaveValue("longenough1");
    // An inline, focused error tied to the form -- not just a generic top-of-page banner.
    expect(screen.getByRole("alert")).toHaveTextContent("当前密码不正确");
    expect(screen.getByRole("button", { name: "确认修改" })).not.toBeDisabled();
  });

  it("requires a password before confirming account deletion, and shows the irreversibility warning", () => {
    const onDeleteAccount = vi.fn();
    render(<AccountSettings busy={null} message={null} onChangePassword={() => Promise.resolve(null)}
      onExportData={() => undefined} onDeleteAccount={onDeleteAccount} />);
    fireEvent.click(screen.getByRole("button", { name: "删除账户" }));
    expect(screen.getByText(/此操作不可撤销/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
    expect(screen.getByText("请输入密码以确认")).toBeVisible();
    expect(onDeleteAccount).not.toHaveBeenCalled();
  });

  // Gemini audit 4.10: the same async-safety contract applies to the destructive delete-account
  // confirmation dialog -- it must not close itself before the deletion is confirmed to have
  // actually happened.
  it("keeps the delete-confirmation dialog open and disabled while deletion is in flight, and only closes it after confirmed success", async () => {
    const pending = deferred<string | null>();
    const onDeleteAccount = vi.fn().mockReturnValue(pending.promise);
    render(<AccountSettings busy={null} message={null} onChangePassword={() => Promise.resolve(null)}
      onExportData={() => undefined} onDeleteAccount={onDeleteAccount} />);
    fireEvent.click(screen.getByRole("button", { name: "删除账户" }));
    fireEvent.change(screen.getByPlaceholderText("密码"), { target: { value: "my-real-password" } });
    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
    expect(onDeleteAccount).toHaveBeenCalledExactlyOnceWith("my-real-password");

    expect(screen.getByPlaceholderText("密码")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "确认删除" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
    expect(onDeleteAccount).toHaveBeenCalledTimes(1); // no double-submit while in flight

    await act(async () => { pending.resolve(null); await pending.promise; });
    expect(screen.queryByPlaceholderText("密码")).not.toBeInTheDocument();
  });

  it("keeps the delete dialog open with the password intact and shows an inline error when deletion fails", async () => {
    const pending = deferred<string | null>();
    const onDeleteAccount = vi.fn().mockReturnValue(pending.promise);
    render(<AccountSettings busy={null} message={null} onChangePassword={() => Promise.resolve(null)}
      onExportData={() => undefined} onDeleteAccount={onDeleteAccount} />);
    fireEvent.click(screen.getByRole("button", { name: "删除账户" }));
    fireEvent.change(screen.getByPlaceholderText("密码"), { target: { value: "wrong-password" } });
    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));

    await act(async () => { pending.resolve("密码不正确"); await pending.promise; });

    expect(screen.getByPlaceholderText("密码")).toHaveValue("wrong-password");
    expect(screen.getByRole("alert")).toHaveTextContent("密码不正确");
    expect(screen.getByRole("button", { name: "确认删除" })).not.toBeDisabled();
  });

  it("disables the busy action and shows a status message", () => {
    render(<AccountSettings busy="export" message="数据已导出" onChangePassword={() => Promise.resolve(null)}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)} />);
    expect(screen.getByRole("button", { name: "导出数据" })).toBeDisabled();
    expect(screen.getByText("数据已导出")).toBeVisible();
  });

  it("seeds Aurora preferences from the loaded profile and saves a full patch on demand", () => {
    const onSaveProfile = vi.fn();
    render(<AccountSettings busy={null} message={null} onChangePassword={() => Promise.resolve(null)}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)}
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
    render(<AccountSettings busy={null} message={null} onChangePassword={() => Promise.resolve(null)}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)} />);
    expect(screen.queryByLabelText("对话风格")).not.toBeInTheDocument();
  });

  it("renders in English and validates in English when locale is en-SG", () => {
    const onChangePassword = vi.fn();
    render(<AccountSettings locale="en-SG" busy={null} message={null} onChangePassword={onChangePassword}
      onExportData={() => undefined} onDeleteAccount={() => Promise.resolve(null)} />);
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
