import type { Locale } from "../i18n";

// Compact app-wide language control. It deliberately does not reuse AppearanceToggle:
// that component is now a rich time studio and made this two-option preference needlessly tall.
const OPTIONS: Array<[Locale, string]> = [["zh-CN", "中文"], ["en-SG", "English"]];

export function LocaleToggle({ locale, onChange }: { locale: Locale; onChange: (locale: Locale) => void }) {
  return (
    <label className="locale-compact">
      <span className="appearance-label">{locale === "en-SG" ? "Language" : "语言"}</span>
      <select aria-label="界面语言 / Language" value={locale}
        onChange={event => onChange(event.target.value as Locale)}>
        {OPTIONS.map(([value, label]) => (
          <option key={value} value={value} lang={value === "en-SG" ? "en" : "zh"}>{label}</option>
        ))}
      </select>
    </label>
  );
}
