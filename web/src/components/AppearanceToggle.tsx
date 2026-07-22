import { useState } from "react";
import { applyColorScheme, getColorScheme, setColorScheme, type ColorScheme } from "../theme";
import type { Locale } from "../i18n";

// 外观明暗切换（§1.1 白昼莫兰迪 / 用户可锁定主题）。与七时段正交：
// 跟随时间(null)=当前暖夜暗色系；白昼=莫兰迪浅色；夜色=强制暖夜暗色。
// 直接改 <html data-theme>，CSS 即时响应，无需 React 重渲染整树。
const OPTIONS: Array<ColorScheme | null> = [null, "day", "night"];

const COPY: Record<Locale, { aria: string; label: string; option: Record<string, string> }> = {
  "zh-CN": { aria: "外观主题", label: "外观", option: { follow: "跟随时间", day: "白昼", night: "夜色" } },
  "en-SG": { aria: "Appearance", label: "Appearance", option: { follow: "Follow time", day: "Day", night: "Night" } }
};

export function AppearanceToggle({ locale = "zh-CN" }: { locale?: Locale }) {
  const t = COPY[locale];
  const [scheme, setScheme] = useState<ColorScheme | null>(() => {
    try {
      return getColorScheme();
    } catch {
      return null;
    }
  });
  const choose = (value: ColorScheme | null) => {
    setColorScheme(value);
    applyColorScheme();
    setScheme(value);
  };
  return (
    <div className="appearance-toggle" role="group" aria-label={t.aria}>
      <span className="appearance-label">{t.label}</span>
      <div className="appearance-options">
        {OPTIONS.map(value => (
          <button
            type="button"
            key={value ?? "follow"}
            aria-pressed={scheme === value}
            className={scheme === value ? "active" : ""}
            onClick={() => choose(value)}
          >
            {t.option[value ?? "follow"]}
          </button>
        ))}
      </div>
    </div>
  );
}
