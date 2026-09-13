import { useState } from "react";
import { api, type DialogContinuity } from "../api";
import type { SkillLocale } from "./PsychologySkillStudio";

type Props = {
  continuity: DialogContinuity | null;
  locale: SkillLocale;
  onDismiss: () => void;
};

const COPY = {
  "zh-CN": {
    eyebrow: "来自上一次对话",
    carryLabel: "Aurora 带来的整理",
    firstEyebrow: "第一次对话",
    freshHint: "没有历史包袱，你想说的那件事，慢慢来。",
    continueHint: "想继续，也可以从新的开始——由你决定。",
    provenancePrefix: "来源：",
    close: "收起开场上下文",
    withdraw: "不再显示开场回顾",
    withdrawBusy: "正在关闭…",
    withdrawFailed: "暂时没能关闭，稍后再试。"
  },
  "en-SG": {
    eyebrow: "From your last conversation",
    carryLabel: "What Aurora carried over",
    firstEyebrow: "First conversation",
    freshHint: "No history to catch up on — take your time with what matters.",
    continueHint: "Continue, or start fresh — your choice.",
    provenancePrefix: "Source: ",
    close: "Dismiss opening context",
    withdraw: "Don't show the opening recap again",
    withdrawBusy: "Turning off…",
    withdrawFailed: "Could not turn it off just now — try again shortly."
  }
} as const;

/**
 * CP-18: the honest opening card of a fresh conversation. A returning user sees exactly what
 * the previous real conversation left behind — each carry note labeled with its provenance —
 * plus the choice to continue or start new. A brand-new user sees an explicit first-
 * conversation state and never a fabricated "last time". Rendered only while the new
 * conversation has no messages yet; dismissed when the user speaks.
 *
 * CP-18 §2-13 — the owner's visibility withdrawal: when the backend says openingVisible
 * false, NOTHING renders here. That is a display choice only; the backend keeps recording
 * continuity facts honestly. The card itself carries the withdrawal switch, so the user
 * never has to hunt for a settings page to stop seeing it.
 */
export function AuroraOpeningContinuity({ continuity, locale, onDismiss }: Props) {
  const [withdrawBusy, setWithdrawBusy] = useState(false);
  const [withdrawFailed, setWithdrawFailed] = useState(false);
  if (!continuity) return null;
  // §2-13: withdrawn marker from the supply endpoint — no card, no fabricated "first
  // conversation" either; the marker itself says this was a choice, not an empty history.
  if (continuity.openingVisible === false) return null;
  const t = COPY[locale];
  const carry = continuity.carryForward ?? [];
  const returning = continuity.hasPrior && carry.length > 0;

  const withdraw = () => {
    if (withdrawBusy) return;
    setWithdrawBusy(true);
    setWithdrawFailed(false);
    api.setContinuityVisibility(false)
      .then(() => onDismiss())
      .catch(() => setWithdrawFailed(true))
      .finally(() => setWithdrawBusy(false));
  };

  return (
    <aside className={`opening-continuity ${returning ? "returning" : "fresh"}`}
      data-testid="opening-continuity">
      <header>
        <span className="opening-continuity-mark" aria-hidden="true">{returning ? "✦" : "○"}</span>
        <div>
          <small>{returning ? t.eyebrow : t.firstEyebrow}</small>
          <strong>{continuity.openingLine}</strong>
        </div>
        <button type="button" onClick={onDismiss} aria-label={t.close}>×</button>
      </header>
      {returning ? (
        <>
          <ul aria-label={t.carryLabel}>
            {carry.map((note, index) => (
              <li key={`${note.kind}-${index}`} data-kind={note.kind}>
                <span className="carry-text">{note.text}</span>
                {note.provenance && (
                  <small className="carry-provenance">{t.provenancePrefix}{note.provenance}</small>
                )}
              </li>
            ))}
          </ul>
          <p className="opening-choice">{t.continueHint}</p>
        </>
      ) : (
        <p className="opening-choice">{t.freshHint}</p>
      )}
      <footer className="opening-continuity-withdraw">
        <button type="button" className="quiet" onClick={withdraw} disabled={withdrawBusy}>
          {withdrawBusy ? t.withdrawBusy : t.withdraw}
        </button>
        {withdrawFailed && <small role="alert">{t.withdrawFailed}</small>}
      </footer>
    </aside>
  );
}
