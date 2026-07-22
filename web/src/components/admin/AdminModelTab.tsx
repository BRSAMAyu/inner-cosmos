import type { AiHealth, AdminModelConfigRow } from "../../api";
import type { Locale } from "../../i18n";

// Port of admin.html's "模型" tab (loadModelConfig(): AI health card + model config rows).
const COPY: Record<Locale, {
  aria: string; healthTitle: string; llmLine: (provider: string, model: string) => string;
  keyConfigured: string; keyMissing: string; fallbackAllowed: string; fallbackOff: string;
  asrLine: (provider: string, model: string) => string; lastCall: string; lastNone: string;
  lastSuccess: string; lastFailure: string; emptyConfig: string;
}> = {
  "zh-CN": {
    aria: "模型配置", healthTitle: "真实 AI 状态", llmLine: (p, m) => `LLM: ${p} / ${m}`,
    keyConfigured: "已配置 key", keyMissing: "未配置 key", fallbackAllowed: "允许 fallback", fallbackOff: "fallback 关闭",
    asrLine: (p, m) => `ASR: ${p} / ${m}`, lastCall: "最近调用", lastNone: "暂无",
    lastSuccess: "成功", lastFailure: "失败", emptyConfig: "没有其他模型配置项"
  },
  "en-SG": {
    aria: "Model configuration", healthTitle: "Live AI status", llmLine: (p, m) => `LLM: ${p} / ${m}`,
    keyConfigured: "key configured", keyMissing: "key missing", fallbackAllowed: "fallback allowed", fallbackOff: "fallback off",
    asrLine: (p, m) => `ASR: ${p} / ${m}`, lastCall: "Last call", lastNone: "None yet",
    lastSuccess: "Success", lastFailure: "Failed", emptyConfig: "No other model config entries"
  }
};

export function AdminModelTab({ health, configs, locale = "zh-CN" }: {
  health: AiHealth | null; configs: AdminModelConfigRow[]; locale?: Locale;
}) {
  const t = COPY[locale];
  return <div className="admin-grid" aria-label={t.aria}>
    <article className="admin-card">
      <strong>{t.healthTitle}</strong>
      {health && <>
        <p className="admin-muted">{t.llmLine(health.provider || "-", health.model || "-")} &middot; {health.apiKeyConfigured ? t.keyConfigured : t.keyMissing} &middot; {health.fallbackAllowed ? t.fallbackAllowed : t.fallbackOff}</p>
        <p className="admin-muted">{t.asrLine(health.asrProvider || "-", health.asrModel || "-")} &middot; {health.asrKeyConfigured ? t.keyConfigured : t.keyMissing}</p>
        <p className="admin-muted">{t.lastCall}: {health.lastSuccess === null ? t.lastNone : (health.lastSuccess ? t.lastSuccess : t.lastFailure)}{health.lastError ? ` · ${health.lastError}` : ""}</p>
      </>}
    </article>
    {configs.length === 0 ? <div className="admin-empty">{t.emptyConfig}</div> : configs.map(c => <article className="admin-card" key={c.id}>
      <strong>{c.configKey}</strong>
      <p className="admin-muted">{c.description || c.configValue}</p>
    </article>)}
  </div>;
}
