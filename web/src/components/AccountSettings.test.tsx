import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AccountSettings } from "./AccountSettings";

afterEach(cleanup);

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
});
