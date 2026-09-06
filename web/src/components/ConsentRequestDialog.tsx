import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

// CP-07 J01 progressive consent: shown exactly when the backend refuses an action with
// CONSENT_REQUIRED (initially: sending content to a real model provider). The refusal copy
// comes from the consent registry so the dialog never promises more than the contract.
// Declining is a real choice — the draft stays and local-only features keep working.

const COPY: Record<Locale, {
  title: string; grantAndContinue: string; notNow: string; busy: string;
  whatStillWorks: string;
}> = {
  "zh-CN": {
    title: "需要你的一项同意",
    grantAndContinue: "同意并继续",
    notNow: "暂不",
    busy: "正在记录你的选择…",
    whatStillWorks: "未同意前：你的对话内容不会发送到任何外部模型；本地功能（浏览记忆、数据导出与删除等）不受影响。"
  },
  "en-SG": {
    title: "One consent needed",
    grantAndContinue: "Grant and continue",
    notNow: "Not now",
    busy: "Recording your choice…",
    whatStillWorks: "Until you agree: your conversation content is not sent to any external model; local features (browsing memories, data export and deletion) keep working."
  }
};

export function ConsentRequestDialog({ open, purposeCode, description, withdrawalEffect,
  busy, onGrant, onDismiss, locale = "zh-CN" }: {
  open: boolean;
  purposeCode: string;
  description: string;
  withdrawalEffect: string;
  busy: boolean;
  onGrant: () => void;
  onDismiss: () => void;
  locale?: Locale;
}) {
  if (!open) return null;
  const copy = COPY[locale];
  return (
    <div className="consent-request-backdrop" role="dialog" aria-modal="true"
         aria-labelledby="consent-request-title" data-purpose={purposeCode}>
      <div className="consent-request">
        <h4 id="consent-request-title">{copy.title}</h4>
        <p className="consent-purpose"><strong>{purposeCode}</strong></p>
        <p className="consent-description">{description}</p>
        <p className="consent-withdrawal">{withdrawalEffect}</p>
        <p className="consent-still-works">{copy.whatStillWorks}</p>
        <div className="consent-actions">
          <AsyncButton busy={busy} onClick={onGrant}>{copy.grantAndContinue}</AsyncButton>
          <button type="button" className="secondary" onClick={onDismiss} disabled={busy}>
            {copy.notNow}
          </button>
        </div>
        {busy && <small>{copy.busy}</small>}
      </div>
    </div>
  );
}
