// 加载四态 / 三档延迟加载（§1.1.5 / UIUXdesign §6.5 与 doc-11 §14.1 "加载态永不用 spinner"）：
//   <1s   —— 什么都不显示（避免快响应时的加载闪烁）
//   1-3s  —— 显示文案（"正在保存…"），静态省略号
//   >3s   —— 文案 + 微动画（渐隐的三点，绝不是旋转 spinner）
// prefers-reduced-motion 时封顶在文案档，不进入微动画。
// 直接补上 checkpoint-4 诊断出的体验根因：点击到异步结果之间缺少可见的"等待"反馈。

import { useEffect, useState, type ButtonHTMLAttributes, type ReactNode } from "react";
import { prefersReducedMotion } from "./ripple";
import type { Locale } from "./i18n";

export type LoadPhase = "idle" | "text" | "anim";

export const LOAD_TEXT_MS = 1000; // 进入"文案"档的阈值
export const LOAD_ANIM_MS = 3000; // 进入"文案+微动画"档的阈值

/**
 * 三档延迟加载状态机。busy 为真后先停在 idle（<1s 不闪），1s 后进 text，3s 后进 anim；
 * busy 变假立即回 idle。prefers-reduced-motion 时不再推进到 anim。
 */
export function useDelayedBusy(busy: boolean): LoadPhase {
  const [phase, setPhase] = useState<LoadPhase>("idle");
  useEffect(() => {
    if (!busy) {
      setPhase("idle");
      return;
    }
    setPhase("idle");
    const reduce = prefersReducedMotion();
    const toText = window.setTimeout(() => setPhase("text"), LOAD_TEXT_MS);
    const toAnim = reduce ? undefined : window.setTimeout(() => setPhase("anim"), LOAD_ANIM_MS);
    return () => {
      window.clearTimeout(toText);
      if (toAnim !== undefined) window.clearTimeout(toAnim);
    };
  }, [busy]);
  return phase;
}

/** 渐隐三点指示器；anim 档才动，text/reduced-motion 档保持静态。绝不用旋转 spinner。 */
export function LoadingDots({ animated }: { animated: boolean }) {
  return (
    <span className={animated ? "loading-dots" : "loading-dots static"} aria-hidden="true">
      <i />
      <i />
      <i />
    </span>
  );
}

/**
 * 区块级"等待"文案。busy 时按三档时序渐进出现（<1s 返回 null）。
 * 用于整屏/区块加载，而不是单个按钮。
 */
export function LoadingText({
  busy,
  children,
  className,
}: {
  busy: boolean;
  children?: ReactNode;
  className?: string;
}) {
  const phase = useDelayedBusy(busy);
  if (phase === "idle") return null;
  return (
    <p className={`loading-text ${className ?? ""}`.trim()} role="status" aria-live="polite">
      {/* i18n-completeness remaining: every real caller passes its own bilingual children, so
          this Chinese fallback only shows if a future caller forgets to. Not worth a `locale`
          prop on this generic primitive until/unless that actually happens. */}
      {children ?? "正在加载"}
      <LoadingDots animated={phase === "anim"} />
    </p>
  );
}

/**
 * 四态之"错误 + 恢复"：入口连接失败态。带一个再试按钮（恢复路径）。
 * 抽成独立组件以便单测；此前 AuroraApp bootstrap 非鉴权失败会把 authenticated 停在 null，
 * 用户永久卡在连接加载屏——本组件让失败可见且可恢复。
 */
const CONNECT_ERROR_COPY: Record<Locale, { title: string; retry: string }> = {
  "zh-CN": { title: "没能连上你的内宇宙", retry: "重试" },
  "en-SG": { title: "Couldn't connect to your Inner Cosmos", retry: "Retry" }
};

export function ConnectError({ message, onRetry, locale = "zh-CN" }: { message: string; onRetry: () => void; locale?: Locale }) {
  const t = CONNECT_ERROR_COPY[locale];
  return (
    <div className="connect-error" role="alert">
      <p className="connect-error-title">{t.title}</p>
      <p className="connect-error-detail">{message}</p>
      <button type="button" className="understanding-action" onClick={onRetry}>
        {t.retry}
      </button>
    </div>
  );
}

type AsyncButtonProps = {
  busy: boolean;
  busyText?: string;
  children: ReactNode;
} & Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children">;

/**
 * 异步动作按钮：busy 时禁用并按三档时序把标签换成忙碌文案。
 * <1s 保持原标签（不闪），1-3s 显示忙碌文案，>3s 追加微动画三点。
 * 默认 type="button"，避免误触发表单提交。
 */
export function AsyncButton({ busy, busyText, children, disabled, type, ...rest }: AsyncButtonProps) {
  const phase = useDelayedBusy(busy);
  const showBusy = busy && phase !== "idle";
  return (
    <button {...rest} type={type ?? "button"} disabled={disabled || busy} aria-busy={busy || undefined}>
      {showBusy ? (
        <span className="async-busy">
          {busyText ?? "处理中"}
          <LoadingDots animated={phase === "anim"} />
        </span>
      ) : (
        children
      )}
    </button>
  );
}
