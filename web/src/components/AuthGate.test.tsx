import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthGate } from "./AuthGate";
import { api } from "../api";

vi.mock("../api", () => ({
  api: { login: vi.fn(), register: vi.fn() }
}));
vi.mock("../mobile-auth", () => ({
  mobileOidc: { beginLogin: vi.fn() }
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("AuthGate -- web session mode (native=false)", () => {
  it("characterizes the pre-existing behavior: defaults to a login-only form with no register fields", () => {
    render(<AuthGate native={false} onSuccess={vi.fn()} />);
    expect(screen.getByRole("button", { name: "登录" })).toBeInTheDocument();
    expect(screen.getByLabelText("用户名")).toBeInTheDocument();
    expect(screen.getByLabelText("密码")).toBeInTheDocument();
    // No register-only fields present in the default (login) mode.
    expect(screen.queryByLabelText(/昵称/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText("确认密码")).not.toBeInTheDocument();
  });

  it("characterizes the pre-existing behavior: submitting login calls api.login then onSuccess", async () => {
    vi.mocked(api.login).mockResolvedValue(undefined);
    const onSuccess = vi.fn().mockResolvedValue(undefined);
    render(<AuthGate native={false} onSuccess={onSuccess} />);
    fireEvent.change(screen.getByLabelText("用户名"), { target: { value: "mira" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "correcthorse" } });
    fireEvent.click(screen.getByRole("button", { name: "登录" }));
    await vi.waitFor(() => expect(onSuccess).toHaveBeenCalledOnce());
    expect(api.login).toHaveBeenCalledExactlyOnceWith("mira", "correcthorse");
  });

  it("characterizes the pre-existing behavior: a failed login shows an inline alert and does not call onSuccess", async () => {
    vi.mocked(api.login).mockRejectedValue(new Error("用户名或密码不正确"));
    const onSuccess = vi.fn();
    render(<AuthGate native={false} onSuccess={onSuccess} />);
    fireEvent.change(screen.getByLabelText("用户名"), { target: { value: "mira" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "wrongpass" } });
    fireEvent.click(screen.getByRole("button", { name: "登录" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("用户名或密码不正确");
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it("offers a mode toggle so the same screen can switch to registration", () => {
    render(<AuthGate native={false} onSuccess={vi.fn()} />);
    expect(screen.getByRole("tablist", { name: "登录或注册" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "登录" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: "注册" })).toHaveAttribute("aria-selected", "false");
  });

  it("switching to register mode reveals nickname (optional) and password-confirmation fields", () => {
    render(<AuthGate native={false} onSuccess={vi.fn()} />);
    fireEvent.click(screen.getByRole("tab", { name: "注册" }));
    expect(screen.getByRole("tab", { name: "注册" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByLabelText(/昵称/)).toBeInTheDocument();
    expect(screen.getByLabelText("确认密码")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "创建账号" })).toBeInTheDocument();
  });

  it("switching back to login hides the register-only fields again", () => {
    render(<AuthGate native={false} onSuccess={vi.fn()} />);
    fireEvent.click(screen.getByRole("tab", { name: "注册" }));
    fireEvent.click(screen.getByRole("tab", { name: "登录" }));
    expect(screen.queryByLabelText(/昵称/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText("确认密码")).not.toBeInTheDocument();
  });

  it("shows an inline mismatch error and never calls the API when the two passwords differ", () => {
    render(<AuthGate native={false} onSuccess={vi.fn()} />);
    fireEvent.click(screen.getByRole("tab", { name: "注册" }));
    fireEvent.change(screen.getByLabelText("用户名"), { target: { value: "newperson" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "longenoughpass" } });
    fireEvent.change(screen.getByLabelText("确认密码"), { target: { value: "differentpass" } });
    fireEvent.click(screen.getByRole("button", { name: "创建账号" }));
    expect(screen.getByRole("alert")).toHaveTextContent("两次输入的密码不一致");
    expect(api.register).not.toHaveBeenCalled();
  });

  it("shows an inline error and never calls the API when the password is shorter than 8 characters", () => {
    render(<AuthGate native={false} onSuccess={vi.fn()} />);
    fireEvent.click(screen.getByRole("tab", { name: "注册" }));
    fireEvent.change(screen.getByLabelText("用户名"), { target: { value: "newperson" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "short1" } });
    fireEvent.change(screen.getByLabelText("确认密码"), { target: { value: "short1" } });
    fireEvent.click(screen.getByRole("button", { name: "创建账号" }));
    expect(screen.getByRole("alert")).toHaveTextContent("密码至少 8 位");
    expect(api.register).not.toHaveBeenCalled();
  });

  it("calls api.register with the exact backend contract (username, nickname falling back to username, password) then onSuccess", async () => {
    vi.mocked(api.register).mockResolvedValue(undefined);
    const onSuccess = vi.fn().mockResolvedValue(undefined);
    render(<AuthGate native={false} onSuccess={onSuccess} />);
    fireEvent.click(screen.getByRole("tab", { name: "注册" }));
    fireEvent.change(screen.getByLabelText("用户名"), { target: { value: "newperson" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "longenoughpass" } });
    fireEvent.change(screen.getByLabelText("确认密码"), { target: { value: "longenoughpass" } });
    fireEvent.click(screen.getByRole("button", { name: "创建账号" }));
    await vi.waitFor(() => expect(onSuccess).toHaveBeenCalledOnce());
    expect(api.register).toHaveBeenCalledExactlyOnceWith("newperson", "", "longenoughpass");
  });

  it("passes a filled-in nickname through untouched", async () => {
    vi.mocked(api.register).mockResolvedValue(undefined);
    const onSuccess = vi.fn().mockResolvedValue(undefined);
    render(<AuthGate native={false} onSuccess={onSuccess} />);
    fireEvent.click(screen.getByRole("tab", { name: "注册" }));
    fireEvent.change(screen.getByLabelText("用户名"), { target: { value: "newperson" } });
    fireEvent.change(screen.getByLabelText(/昵称/), { target: { value: "阿新" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "longenoughpass" } });
    fireEvent.change(screen.getByLabelText("确认密码"), { target: { value: "longenoughpass" } });
    fireEvent.click(screen.getByRole("button", { name: "创建账号" }));
    await vi.waitFor(() => expect(onSuccess).toHaveBeenCalledOnce());
    expect(api.register).toHaveBeenCalledExactlyOnceWith("newperson", "阿新", "longenoughpass");
  });

  it("renders login, the mode toggle and register validation in English when locale is en-SG", () => {
    render(<AuthGate native={false} onSuccess={vi.fn()} locale="en-SG" />);
    expect(screen.getByRole("heading", { name: "Back to your inner cosmos" })).toBeVisible();
    expect(screen.getByLabelText("Username")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Sign up" }));
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "newperson" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "short1" } });
    fireEvent.change(screen.getByLabelText("Confirm password"), { target: { value: "short1" } });
    fireEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Password must be at least 8 characters.");
    expect(api.register).not.toHaveBeenCalled();
  });

  it("a failed register shows an inline alert and does not call onSuccess", async () => {
    vi.mocked(api.register).mockRejectedValue(new Error("注册失败，请换个用户名试试。"));
    const onSuccess = vi.fn();
    render(<AuthGate native={false} onSuccess={onSuccess} />);
    fireEvent.click(screen.getByRole("tab", { name: "注册" }));
    fireEvent.change(screen.getByLabelText("用户名"), { target: { value: "taken" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "longenoughpass" } });
    fireEvent.change(screen.getByLabelText("确认密码"), { target: { value: "longenoughpass" } });
    fireEvent.click(screen.getByRole("button", { name: "创建账号" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("注册失败");
    expect(onSuccess).not.toHaveBeenCalled();
  });
});

describe("AuthGate -- native (Capacitor OIDC) mode is untouched", () => {
  it("renders only the OIDC entry point, with no mode toggle and no register/login form fields", () => {
    render(<AuthGate native={true} onSuccess={vi.fn()} />);
    expect(screen.getByRole("button", { name: "使用身份提供方继续" })).toBeInTheDocument();
    expect(screen.queryByRole("tablist", { name: "登录或注册" })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("用户名")).not.toBeInTheDocument();
  });
});
