import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import {
  LOCK_SCREEN_WAKE_COPY,
  WakeLockScreenPrivacyToggle,
  lockScreenWakeNotice,
  readWakeLockScreenDesensitized,
  writeWakeLockScreenDesensitized
} from "../WakeLockScreenPrivacy";

const memoryFragment = "因为“和妈妈的争吵还没聊完”，Aurora 会在 明早 回来";

function storageStub(initial: Record<string, string> = {}): Storage {
  const map = new Map(Object.entries(initial));
  return {
    get length() { return map.size; },
    clear: () => map.clear(),
    getItem: key => map.get(key) ?? null,
    key: index => Array.from(map.keys())[index] ?? null,
    removeItem: key => { map.delete(key); },
    setItem: (key, value) => { map.set(key, value); }
  };
}

afterEach(() => {
  cleanup();
  localStorage.clear();
});

describe("lockScreenWakeNotice（CP-26 锁屏通知脱敏）", () => {
  it("默认脱敏开：锁屏预览只显示中性文案，不包含任何记忆/约定内容片段", () => {
    const notice = lockScreenWakeNotice({ title: "Aurora", body: memoryFragment }, "zh-CN");
    expect(notice.title).toBe("Aurora");
    expect(notice.body).toBe(LOCK_SCREEN_WAKE_COPY["zh-CN"].body);
    // 负测：中性文案绝不携带用户内容片段
    expect(notice.body).not.toContain("妈妈");
    expect(notice.body).not.toContain("争吵");
    expect(notifyBodyCarriesFragment(notice.body)).toBe(false);
  });

  it("未设置存储键时读取结果为脱敏开（隐私优先 fail-closed）", () => {
    expect(readWakeLockScreenDesensitized(storageStub())).toBe(true);
    expect(readWakeLockScreenDesensitized(null)).toBe(true);
  });

  it("存储读取抛错时仍按脱敏开处理", () => {
    const throwing: Storage = {
      ...storageStub(),
      getItem: () => { throw new DOMException("denied", "SecurityError"); }
    };
    expect(readWakeLockScreenDesensitized(throwing)).toBe(true);
  });

  it("显式关闭脱敏后原样透传完整提醒文案", () => {
    const storage = storageStub({ "wake-lockscreen-desensitized": "0" });
    expect(readWakeLockScreenDesensitized(storage)).toBe(false);
    const notice = lockScreenWakeNotice({ title: "Aurora", body: memoryFragment }, "zh-CN",
      readWakeLockScreenDesensitized(storage));
    expect(notice.body).toBe(memoryFragment);
  });

  it("非法存储值回落为脱敏开而不是猜成关闭", () => {
    expect(readWakeLockScreenDesensitized(storageStub({ "wake-lockscreen-desensitized": "yes?" }))).toBe(true);
  });

  it("en-SG locale 使用英文中性文案", () => {
    const notice = lockScreenWakeNotice({ title: "Aurora", body: memoryFragment }, "en-SG");
    expect(notice.body).toBe(LOCK_SCREEN_WAKE_COPY["en-SG"].body);
    expect(notice.body).not.toMatch(/[\u4e00-\u9fff]/);
  });
});

describe("WakeLockScreenPrivacyToggle（脱敏开关组件）", () => {
  it("默认渲染为开启状态，预览即中性文案", () => {
    render(<WakeLockScreenPrivacyToggle />);
    const toggle = screen.getByRole("checkbox", { name: /锁屏通知脱敏/ });
    expect(toggle).toBeChecked();
    expect(screen.getByTestId("lockscreen-preview-body")).toHaveTextContent("Aurora 想起你");
  });

  it("取消勾选会把“脱敏关”写入本地存储并切换为完整文案示例，重开则恢复为开", () => {
    render(<WakeLockScreenPrivacyToggle />);
    const toggle = screen.getByRole("checkbox", { name: /锁屏通知脱敏/ });
    fireEvent.click(toggle);
    expect(toggle).not.toBeChecked();
    expect(localStorage.getItem("wake-lockscreen-desensitized")).toBe("0");
    expect(readWakeLockScreenDesensitized()).toBe(false);
    // 脱敏关时预览展示“完整文案”示例（示意文本，非用户数据）
    expect(screen.getByTestId("lockscreen-preview-body")).toHaveTextContent("示例：因为「继续聊聊白天的事」");

    fireEvent.click(toggle);
    expect(localStorage.getItem("wake-lockscreen-desensitized")).toBe("1");
    expect(readWakeLockScreenDesensitized()).toBe(true);
    expect(screen.getByTestId("lockscreen-preview-body")).toHaveTextContent("Aurora 想起你");
  });

  it("写入失败（私有模式）时开关仍可用且读取保持默认开", () => {
    const consoleWarn = vi.spyOn(console, "error").mockImplementation(() => undefined);
    const original = window.localStorage.setItem;
    window.localStorage.setItem = () => { throw new DOMException("Private mode", "QuotaExceededError"); };
    try {
      render(<WakeLockScreenPrivacyToggle />);
      fireEvent.click(screen.getByRole("checkbox", { name: /锁屏通知脱敏/ }));
      // 存储写入失败，但下一次会话仍默认脱敏开（fail-closed）
      expect(readWakeLockScreenDesensitized()).toBe(true);
    } finally {
      window.localStorage.setItem = original;
      consoleWarn.mockRestore();
    }
  });

  it("英文 locale 渲染英文说明与英文预览", () => {
    render(<WakeLockScreenPrivacyToggle locale="en-SG" />);
    expect(screen.getByRole("checkbox", { name: /Neutral lock-screen preview/ })).toBeChecked();
    expect(screen.getByTestId("lockscreen-preview-body")).toHaveTextContent(LOCK_SCREEN_WAKE_COPY["en-SG"].body);
  });
});

/** 模拟锁屏上真正可见的正文是否泄露了用户内容片段。 */
function notifyBodyCarriesFragment(body: string): boolean {
  return ["妈妈", "争吵", "Aurora 会在"].some(fragment => body.includes(fragment));
}
