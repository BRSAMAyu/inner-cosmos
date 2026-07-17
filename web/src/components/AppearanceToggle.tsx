import { useState } from "react";
import { applyColorScheme, getColorScheme, setColorScheme, type ColorScheme } from "../theme";

// 外观明暗切换（§1.1 白昼莫兰迪 / 用户可锁定主题）。与七时段正交：
// 跟随时间(null)=当前暖夜暗色系；白昼=莫兰迪浅色；夜色=强制暖夜暗色。
// 直接改 <html data-theme>，CSS 即时响应，无需 React 重渲染整树。
const OPTIONS: Array<[ColorScheme | null, string]> = [
  [null, "跟随时间"],
  ["day", "白昼"],
  ["night", "夜色"],
];

export function AppearanceToggle() {
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
    <div className="appearance-toggle" role="group" aria-label="外观主题">
      <span className="appearance-label">外观</span>
      <div className="appearance-options">
        {OPTIONS.map(([value, label]) => (
          <button
            type="button"
            key={label}
            aria-pressed={scheme === value}
            className={scheme === value ? "active" : ""}
            onClick={() => choose(value)}
          >
            {label}
          </button>
        ))}
      </div>
    </div>
  );
}
