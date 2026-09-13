import { useState } from "react";
import type { Locale } from "../i18n";

/**
 * CP-26 锁屏通知脱敏。唤醒意图的本地通知（锁屏/横幅预览）默认只显示中性文案
 * 「Aurora 想起你」——锁屏是设备上最公开的展示面，任何来自用户记忆或约定内容的
 * 片段都不应出现在那里。隐私优先（fail-closed）：未设置、读取失败、私有模式抛错，
 * 一律按“脱敏开”处理；用户可显式关闭以换回完整预览。
 */
const STORAGE_KEY = "wake-lockscreen-desensitized";

export const LOCK_SCREEN_WAKE_COPY: Record<Locale, { title: string; body: string; label: string; hint: string }> = {
  "zh-CN": {
    title: "Aurora",
    body: "Aurora 想起你",
    label: "锁屏通知脱敏",
    hint: "开启后，锁屏与横幅预览只显示「Aurora 想起你」，不出现约定或记忆内容片段；关闭后预览显示完整提醒文案。"
  },
  "en-SG": {
    title: "Aurora",
    body: "Aurora is thinking of you",
    label: "Neutral lock-screen preview",
    hint: "When on, lock-screen and banner previews show only “Aurora is thinking of you” — never your agreement or memory fragments. Turn off to see the full reminder text."
  }
};

function safeStorage(): Storage | null {
  try {
    return typeof localStorage === "undefined" ? null : localStorage;
  } catch {
    return null;
  }
}

/** 默认 true（脱敏开）。存储不可用、键缺失或值非法时都回落到脱敏开。 */
export function readWakeLockScreenDesensitized(storage: Storage | null = safeStorage()): boolean {
  try {
    const raw = storage?.getItem(STORAGE_KEY);
    if (raw === null || raw === undefined) return true;
    return !(raw === "0" || raw.toLowerCase() === "false");
  } catch {
    return true;
  }
}

export function writeWakeLockScreenDesensitized(value: boolean, storage: Storage | null = safeStorage()): void {
  try {
    storage?.setItem(STORAGE_KEY, value ? "1" : "0");
  } catch {
    // 私有模式等写入失败时保持当前会话的内存值即可，下次会话仍默认脱敏开。
  }
}

/**
 * 计算真正下发给系统通知 API 的锁屏文案：脱敏开 → 中性标题/正文；
 * 脱敏关 → 原样透传调用方提供的标题与正文。
 */
export function lockScreenWakeNotice(input: { title: string; body: string }, locale: Locale = "zh-CN",
  desensitized: boolean = readWakeLockScreenDesensitized()): { title: string; body: string } {
  if (desensitized) {
    const copy = LOCK_SCREEN_WAKE_COPY[locale];
    return { title: copy.title, body: copy.body };
  }
  return { title: input.title, body: input.body };
}

/** 关闭脱敏后锁屏预览的示例文案（仅作开关里的示意，不含任何真实用户数据）。 */
const PREVIEW_SAMPLE: Record<Locale, string> = {
  "zh-CN": "示例：因为「继续聊聊白天的事」，Aurora 会在 明早 回来",
  "en-SG": "Sample: Aurora will return as agreed (tomorrow morning) to “continue what we left unfinished”"
};

/** “我的”空间里的开关：控制锁屏预览是否脱敏，默认开。 */
export function WakeLockScreenPrivacyToggle({ locale = "zh-CN" }: { locale?: Locale }) {
  const copy = LOCK_SCREEN_WAKE_COPY[locale];
  const [desensitized, setDesensitized] = useState(readWakeLockScreenDesensitized);
  const previewBody = desensitized ? copy.body : PREVIEW_SAMPLE[locale];
  return <label className="lockscreen-privacy-toggle" data-wake-lockscreen-desensitized={desensitized ? "on" : "off"}>
    <input type="checkbox" checked={desensitized} onChange={event => {
      const next = event.target.checked;
      writeWakeLockScreenDesensitized(next);
      setDesensitized(next);
    }} />
    <span>{copy.label}<small>{copy.hint}</small>
      <small className="lockscreen-preview" data-testid="lockscreen-preview-body">{previewBody}</small></span>
  </label>;
}
