import { Component, type ErrorInfo, type ReactNode } from "react";
import type { Locale } from "../i18n";

// Gemini audit 4.7 (CONFIRMED/P1): `main.tsx` had no ErrorBoundary at all -- a render-time throw
// anywhere in the tree unmounted the entire app with nothing but a blank page and no recovery path.
// Two variants are used:
//   - "fatal": the single top-level boundary wrapping <AuroraApp/> in main.tsx. Offers Retry AND a
//     full page Reload.
//   - "space": one per product-shell space (aurora/cosmos/resonance/letters/me). A crash inside one
//     space's render must not take the rest of the shell down with it -- each space gets its own
//     boundary so the other four (still mounted as always-present siblings, see AuroraApp.tsx's
//     `hidden={productSpace !== "..."}` pattern) keep working. Offers Retry only (a full reload
//     would be a disproportionate reaction to one section's bug and would cost the user their place
//     in the other four spaces).
//
// Draft-preservation note: this boundary itself holds no application state to preserve -- any
// already-created server-side draft (e.g. a slow-letter DRAFT row, see composeAndSend.ts) survives
// a Retry or Reload untouched because it lives in the backend and is refetched on remount/bootstrap.
// Only same-render, not-yet-submitted local text state inside the SPECIFIC crashed subtree would be
// lost, same as it would be for any unhandled exception -- there is no separate volatile draft cache
// to persist here.

export type ErrorBoundaryVariant = "fatal" | "space";

type Props = {
  children: ReactNode;
  locale?: Locale;
  variant?: ErrorBoundaryVariant;
  /** Fired once per catch with an already-scrubbed payload -- never the raw Error or component
   *  stack, both of which can embed user-entered text (e.g. a thrown validation error quoting the
   *  user's own input). Callers must not attempt to recover the original message from this. */
  onError?: (scrubbed: { name: string; digest: string }) => void;
  /** Overrides the default localized heading, e.g. a specific space's own name. */
  label?: string;
};

type State = { hasError: boolean };

const COPY: Record<Locale, { fatalTitle: string; spaceTitle: string; retry: string; reload: string; detail: string }> = {
  "zh-CN": {
    fatalTitle: "这个页面遇到了没有处理好的错误",
    spaceTitle: "这个板块暂时出了问题",
    retry: "重试",
    reload: "重新加载整个页面",
    detail: "其他板块仍然可以正常使用；你的账号没有退出，之前已经寄出或保存的内容不会丢失。"
  },
  "en-SG": {
    fatalTitle: "Something went wrong on this page",
    spaceTitle: "This section ran into a problem",
    retry: "Retry",
    reload: "Reload the whole page",
    detail: "The rest of the app still works; you're still signed in, and anything already sent or saved is safe."
  }
};

// Strips anything that could carry user-entered text before it ever leaves this boundary: the
// error's own `message` (a thrown error can legitimately quote user input, e.g. a validation
// failure) and the full component stack are both dropped. Only the error's constructor name and a
// short, non-reversible slice of the FIRST stack frame (source file:line, not a message) are kept,
// purely to help distinguish one crash site from another in aggregate telemetry.
function scrubError(error: Error): { name: string; digest: string } {
  const firstFrame = (error.stack ?? "").split("\n")[1]?.trim().slice(0, 160) ?? "";
  return { name: error.name || "Error", digest: firstFrame };
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, _info: ErrorInfo): void {
    void _info; // deliberately unused -- componentStack can itself embed rendered prop values.
    this.props.onError?.(scrubError(error));
  }

  private handleRetry = (): void => this.setState({ hasError: false });
  private handleReload = (): void => window.location.reload();

  render(): ReactNode {
    if (!this.state.hasError) return this.props.children;
    const variant = this.props.variant ?? "space";
    const t = COPY[this.props.locale ?? "zh-CN"];
    return (
      <div className={`error-boundary error-boundary-${variant}`} role="alert">
        <p className="error-boundary-title">{this.props.label ?? (variant === "fatal" ? t.fatalTitle : t.spaceTitle)}</p>
        <p className="error-boundary-detail">{t.detail}</p>
        <div className="error-boundary-actions">
          <button type="button" data-testid="error-boundary-retry" onClick={this.handleRetry}>{t.retry}</button>
          {variant === "fatal" && (
            <button type="button" data-testid="error-boundary-reload" onClick={this.handleReload}>{t.reload}</button>
          )}
        </div>
      </div>
    );
  }
}
