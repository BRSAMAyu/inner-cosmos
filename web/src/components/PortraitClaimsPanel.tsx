import { useEffect, useState } from "react";
import { isVersionConflictError, type PortraitClaimRow, type PortraitClaimsView, type UnderstandingClaim } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

type Props = {
  view: PortraitClaimsView | null;
  loading: boolean;
  loaded: boolean;
  busyClaimId: number | null;
  onLoad: () => void;
  /** CP-21: receives the full row so the caller pins its version (expectedVersion). */
  onSuppress: (claim: PortraitClaimRow, reason: string) => void | Promise<void>;
  onRestore: (claim: PortraitClaimRow) => void | Promise<void>;
  onDelete: (claim: PortraitClaimRow, reason: string) => void | Promise<void>;
  /** CP-23: loads one claim's full version chain (oldest→newest evolution). */
  onLoadHistory: (claimKey: string) => Promise<UnderstandingClaim[]>;
  /** CP-23↔CP-21: opens the dialog session a claim version was extracted from. */
  onOpenSourceSession: (sessionId: number) => void;
  locale?: Locale;
};

const COPY: Record<Locale, {
  aria: string; heading: string; explanationFallback: string;
  unknown: (count: number) => string; empty: string; suppressedHeading: string;
  suppressedEmpty: string; stateConfirmed: string; stateInferred: string;
  stateConflicting: string; stateSuppressed: string;
  userSourced: string; modelSourced: string; versionLabel: (v: string | null) => string;
  suppress: string; restore: string; remove: string; reasonPlaceholder: string;
  cancel: string; confirmDelete: string; deleteQuestion: string; reload: string;
  timeline: string; timelineLoading: string; timelineEmpty: string;
  statusActive: string; statusSuperseded: string; statusSuppressedLabel: string;
  statusDeleted: string; correctedByYou: string; openSourceSession: string;
  conflictTitle: string; conflictHint: string; conflictRefresh: string; conflictDismiss: string;
}> = {
  "zh-CN": {
    aria: "Aurora 对你的理解（可纠正）", heading: "Aurora 对你的理解（可纠正）",
    explanationFallback: "每项理解都标明来源与状态：已确认/推断/冲突。没有材料的维度显示为未知，不会用模板补齐。",
    unknown: count => `还有 ${count} 个维度 Aurora 尚未形成理解（如实显示为未知）`,
    empty: "Aurora 还没有形成任何关于你的理解。",
    suppressedHeading: "已搁置的理解", suppressedEmpty: "没有搁置中的理解。",
    stateConfirmed: "已确认", stateInferred: "推断", stateConflicting: "冲突", stateSuppressed: "已搁置",
    userSourced: "来自你的确认", modelSourced: "来自 Aurora 的观察",
    versionLabel: v => `第 ${v ?? "?"} 版`,
    suppress: "搁置", restore: "恢复", remove: "删除",
    reasonPlaceholder: "可选：为什么这不太是你",
    cancel: "先不", confirmDelete: "确认删除", deleteQuestion: "删除后这条理解从所有当前视图消失（保留审计）。确认？",
    reload: "刷新",
    timeline: "看它怎么变的", timelineLoading: "正在取回变化轨迹…", timelineEmpty: "还没有变化记录。",
    statusActive: "当前", statusSuperseded: "已被取代", statusSuppressedLabel: "被搁置",
    statusDeleted: "已删除", correctedByYou: "你纠正后的理解", openSourceSession: "查看来源对话",
    conflictTitle: "他人在你之前更新了这条内容",
    conflictHint: "你的这次操作没有生效；你本地填的内容（如果有）还留着，看完最新版本后再决定是否重来。",
    conflictRefresh: "查看最新", conflictDismiss: "知道了"
  },
  "en-SG": {
    aria: "What Aurora understands about you (correctable)", heading: "What Aurora understands about you (correctable)",
    explanationFallback: "Every understanding is labeled with source and state: confirmed / inferred / conflicting. Dimensions without material show as unknown — never template-filled.",
    unknown: count => `${count} more dimensions are honestly unknown so far`,
    empty: "Aurora hasn't formed any understanding of you yet.",
    suppressedHeading: "Parked understandings", suppressedEmpty: "Nothing parked.",
    stateConfirmed: "Confirmed", stateInferred: "Inferred", stateConflicting: "Conflicting", stateSuppressed: "Parked",
    userSourced: "From your confirmation", modelSourced: "From Aurora's observation",
    versionLabel: v => `v${v ?? "?"}`,
    suppress: "Park", restore: "Restore", remove: "Delete",
    reasonPlaceholder: "Optional: why this isn't quite you",
    cancel: "Not now", confirmDelete: "Confirm delete", deleteQuestion: "Deleting removes this understanding from every current surface (audit kept). Continue?",
    reload: "Reload",
    timeline: "See how it changed", timelineLoading: "Fetching the change trail…", timelineEmpty: "No change history yet.",
    statusActive: "Current", statusSuperseded: "Superseded", statusSuppressedLabel: "Parked",
    statusDeleted: "Deleted", correctedByYou: "Your corrected understanding",
    openSourceSession: "Open the source conversation",
    conflictTitle: "Someone updated this before you",
    conflictHint: "Your action did not land; anything you typed locally is kept — review the latest, then decide whether to redo it.",
    conflictRefresh: "See the latest", conflictDismiss: "Got it"
  }
};

function statusLabelOf(status: string, t: typeof COPY["zh-CN"]): string {
  return status === "SUPERSEDED" ? t.statusSuperseded
    : status === "SUPPRESSED" ? t.statusSuppressedLabel
    : status === "DELETED" ? t.statusDeleted
    : t.statusActive;
}

function readableValue(valueJson: string | null): string {
  if (!valueJson) return "";
  const raw = valueJson.trim();
  try {
    const parsed = JSON.parse(raw);
    if (typeof parsed === "string") return parsed;
    if (Array.isArray(parsed)) return parsed.map(String).join("、");
    if (parsed && typeof parsed === "object") {
      return parsed.value ?? parsed.summary ?? parsed.label ?? Object.values(parsed).join("、");
    }
    return String(parsed);
  } catch { return raw; }
}

/**
 * CP-23: the owner's control surface over Aurora's understandings. Every current claim shows
 * its honest state and source; the owner can park an inference they don't recognize (with an
 * optional reason), restore a parked one, or delete a claim outright — each action verbed in
 * the user's language, with delete behind an explicit confirmation.
 */
export function PortraitClaimsPanel({ view, loading, loaded, busyClaimId, onLoad,
  onSuppress, onRestore, onDelete, onLoadHistory, onOpenSourceSession, locale = "zh-CN" }: Props) {
  const t = COPY[locale];
  const [reasonFor, setReasonFor] = useState<number | null>(null);
  const [reason, setReason] = useState("");
  const [confirmingDelete, setConfirmingDelete] = useState<number | null>(null);
  // CP-21: the claim whose last action died on a version conflict (409 / code CONFLICT).
  // Nothing is silently overwritten: the banner says someone else updated it first and
  // offers a refresh; the locally typed draft is deliberately KEPT until the owner
  // decides, so re-doing the action against the latest state costs nothing.
  const [conflictFor, setConflictFor] = useState<number | null>(null);
  // CP-23 belief-change timeline: per-claim version chain, fetched lazily and cached.
  const [timelineFor, setTimelineFor] = useState<string | null>(null);
  const [timelineBusy, setTimelineBusy] = useState<string | null>(null);
  const [timelineByClaimKey, setTimelineByClaimKey] = useState<Record<string, UnderstandingClaim[]>>({});

  /** CP-21: runs an owner action; resolves true when it LANDED, false when it died on a
   * version conflict (banner shown, draft kept). The action itself is invoked SYNCHRONOUSLY
   * so sync handlers keep their original call timing. Non-conflict failures are swallowed
   * here on purpose — the parent already surfaced them through its status line. */
  const runAction = (claimId: number, action: () => void | Promise<void>): Promise<boolean> => {
    let outcome: void | Promise<void>;
    try {
      outcome = action();
    } catch (error) {
      if (isVersionConflictError(error)) setConflictFor(claimId);
      return Promise.resolve(false);
    }
    return Promise.resolve(outcome)
      .then(() => {
        setConflictFor(current => current === claimId ? null : current);
        return true;
      })
      .catch(error => {
        if (isVersionConflictError(error)) setConflictFor(claimId);
        return false;
      });
  };

  const openTimeline = (claimKey: string) => {
    if (timelineFor === claimKey) { setTimelineFor(null); return; }
    setTimelineFor(claimKey);
    if (timelineByClaimKey[claimKey] || timelineBusy === claimKey) return;
    setTimelineBusy(claimKey);
    onLoadHistory(claimKey)
      .then(rows => setTimelineByClaimKey(current => ({ ...current, [claimKey]: rows })))
      .catch(() => setTimelineByClaimKey(current => ({ ...current, [claimKey]: [] })))
      .finally(() => setTimelineBusy(null));
  };

  useEffect(() => {
    if (!loaded && !loading) onLoad();
  }, [loaded, loading, onLoad]);

  const stateLabel = (state: string) => state === "CONFIRMED" ? t.stateConfirmed
    : state === "CONFLICTING" ? t.stateConflicting
    : state === "SUPPRESSED" ? t.stateSuppressed : t.stateInferred;

  const row = (claim: PortraitClaimRow, suppressed: boolean) => (
    <li className={`portrait-claim-row state-${claim.state.toLowerCase()}`} key={claim.claimId}>
      <div className="portrait-claim-main">
        <span className={`portrait-claim-state ${claim.state.toLowerCase()}`}>{stateLabel(claim.state)}</span>
        <p className="portrait-claim-value">{readableValue(claim.value)
          || <span className="muted">{t.stateInferred}</span>}</p>
        <small className="muted">
          {(claim.authorityLevel === "USER_CORRECTION" || claim.authorityLevel === "USER_CONFIRMED"
            ? t.userSourced : t.modelSourced) + " · " + t.versionLabel(claim.version)}
        </small>
      </div>
      <div className="portrait-claim-actions">
        <button type="button" className="quiet" aria-expanded={timelineFor === claim.claimKey}
          onClick={() => openTimeline(claim.claimKey)}>{t.timeline}</button>
        {suppressed ? (
          <AsyncButton busy={busyClaimId === claim.claimId}
            onClick={() => void runAction(claim.claimId, () => onRestore(claim))}>{t.restore}</AsyncButton>
        ) : (
          <>
            <button type="button" onClick={() => {
              setReasonFor(reasonFor === claim.claimId ? null : claim.claimId);
              setReason("");
            }}>{t.suppress}</button>
            <button type="button" className="quiet"
              onClick={() => setConfirmingDelete(confirmingDelete === claim.claimId ? null : claim.claimId)}>
              {t.remove}</button>
          </>
        )}
      </div>
      {timelineFor === claim.claimKey && (timelineBusy === claim.claimKey
        ? <p className="muted">{t.timelineLoading}</p>
        : <ol className="portrait-claim-timeline" aria-label={t.timeline}>
            {!(timelineByClaimKey[claim.claimKey] ?? []).length
              ? <li className="muted">{t.timelineEmpty}</li>
              : timelineByClaimKey[claim.claimKey].slice().reverse().map(row => (
                <li key={row.id} className={`timeline-status-${row.status.toLowerCase()}`}>
                  <span className="timeline-version">{t.versionLabel(String(row.version))}</span>
                  <span className="timeline-value">{readableValue(row.valueJson)}</span>
                  <small className="muted">
                    {(row.authorityLevel === "USER_CORRECTION" || row.authorityLevel === "USER_CONFIRMED"
                      ? t.correctedByYou : t.modelSourced)
                      + " · " + statusLabelOf(row.status, t)
                      + (row.createdAt ? " · " + new Date(row.createdAt).toLocaleString(locale) : "")}
                  </small>
                  {typeof row.sourceId === "number" && row.sourceId > 0 && (
                    <button type="button" className="quiet"
                      onClick={() => onOpenSourceSession(row.sourceId!)}>{t.openSourceSession}</button>
                  )}
                </li>))}
          </ol>)}
      {reasonFor === claim.claimId && !suppressed && <div className="portrait-claim-reason">
        <input type="text" maxLength={120} value={reason} aria-label={t.reasonPlaceholder}
          onChange={event => setReason(event.target.value)} placeholder={t.reasonPlaceholder} />
        <div className="portrait-claim-reason-actions">
          <button type="button" onClick={() => setReasonFor(null)}>{t.cancel}</button>
          <AsyncButton busy={busyClaimId === claim.claimId} onClick={() => {
            // CP-21: the draft is only cleared when the action landed; on a version
            // conflict it survives so the owner can redo it after reviewing the latest.
            void runAction(claim.claimId, () => onSuppress(claim, reason.trim()))
              .then(landed => {
                if (landed) {
                  setReasonFor(null);
                  setReason("");
                }
              });
          }}>{t.suppress}</AsyncButton>
        </div>
      </div>}
      {confirmingDelete === claim.claimId && !suppressed && <div className="portrait-claim-confirm">
        <p className="muted">{t.deleteQuestion}</p>
        <div className="portrait-claim-reason-actions">
          <button type="button" onClick={() => setConfirmingDelete(null)}>{t.cancel}</button>
          <AsyncButton busy={busyClaimId === claim.claimId} onClick={() => {
            // CP-21: same landing rule as suppress — a conflicted delete keeps its
            // confirmation open instead of pretending it succeeded.
            void runAction(claim.claimId, () => onDelete(claim, reason.trim()))
              .then(landed => {
                if (landed) setConfirmingDelete(null);
              });
          }}>{t.confirmDelete}</AsyncButton>
        </div>
      </div>}
      {conflictFor === claim.claimId && <div className="portrait-claim-conflict" role="alert"
        data-testid="claim-conflict">
        <p>
          <strong>{t.conflictTitle}</strong>
          <span className="muted"> {t.conflictHint}</span>
        </p>
        <div className="portrait-claim-reason-actions">
          <button type="button" onClick={() => {
            setConflictFor(null);
            onLoad();
          }}>{t.conflictRefresh}</button>
          <button type="button" className="quiet" onClick={() => setConflictFor(null)}>{t.conflictDismiss}</button>
        </div>
      </div>}
    </li>
  );

  return <section className="portrait-claims-panel" aria-label={t.aria}>
    <span className="eyebrow">{locale === "en-SG" ? "CORRECTABLE PORTRAIT" : "可纠正画像"}</span>
    <h3>{t.heading}</h3>
    <p className="muted">{view?.explanation ?? t.explanationFallback}</p>
    {view && view.unknownDimensions > 0 && <p className="portrait-claims-unknown">{t.unknown(view.unknownDimensions)}</p>}
    {!loaded ? <p className="muted">…</p> : (
      <>
        <ul className="portrait-claims-list">
          {!(view?.claims ?? []).length ? <li className="muted">{t.empty}</li>
            : view!.claims.map(claim => row(claim, false))}
        </ul>
        <details className="portrait-claims-suppressed">
          <summary>{t.suppressedHeading}</summary>
          <ul className="portrait-claims-list">
            {!(view?.suppressed ?? []).length ? <li className="muted">{t.suppressedEmpty}</li>
              : view!.suppressed.map(claim => row(claim, true))}
          </ul>
        </details>
        <div className="portrait-claims-foot">
          <button type="button" onClick={onLoad} disabled={loading}>{t.reload}</button>
        </div>
      </>
    )}
  </section>;
}
