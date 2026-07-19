import type { Locale } from "../i18n";

// B4: the app-wide language switcher. Detection (i18n.loadLocale) sets the initial locale; this lets
// the user override and persist it (AuroraApp wires onChange to saveLocale). Reuses the
// .appearance-toggle styling so it sits naturally next to the theme toggle in the Me space.
const OPTIONS: Array<[Locale, string]> = [["zh-CN", "中文"], ["en-SG", "English"]];

export function LocaleToggle({ locale, onChange }: { locale: Locale; onChange: (locale: Locale) => void }) {
  return (
    <div className="appearance-toggle" role="group" aria-label="界面语言 / Language">
      <span className="appearance-label">{locale === "en-SG" ? "Language" : "语言"}</span>
      <div className="appearance-options">
        {OPTIONS.map(([value, label]) => (
          <button
            type="button"
            key={value}
            lang={value === "en-SG" ? "en" : "zh"}
            aria-pressed={locale === value}
            className={locale === value ? "active" : ""}
            onClick={() => onChange(value)}
          >
            {label}
          </button>
        ))}
      </div>
    </div>
  );
}
