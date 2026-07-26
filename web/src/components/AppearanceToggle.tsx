import { useEffect, useState } from "react";
import {
  TIME_OF_DAY_HOURS,
  TIME_OF_DAY_ORDER,
  applyAdaptiveTheme,
  getColorScheme,
  getPreviewHour,
  setColorScheme,
  setPreviewHour,
  timeOfDayForHour,
  type ColorScheme,
  type TimeOfDay,
} from "../theme";
import type { Locale } from "../i18n";

const OPTIONS: Array<ColorScheme | null> = [null, "day", "night"];

type Copy = {
  aria: string;
  label: string;
  option: Record<string, string>;
  timeLabel: string;
  demoLabel: string;
  realtime: string;
  restore: string;
  helper: string;
  period: Record<TimeOfDay, string>;
};

const COPY: Record<Locale, Copy> = {
  "zh-CN": {
    aria: "外观主题",
    label: "光线",
    option: { follow: "跟随时间", day: "白昼", night: "夜色" },
    timeLabel: "环境时间",
    demoLabel: "演示时间",
    realtime: "实时",
    restore: "恢复实时",
    helper: "拖动时间，现场预览七种光线；选择“跟随时间”时，明暗也会一起变化。",
    period: { dawn: "黎明", morning: "早晨", noon: "正午", evening: "午后", dusk: "黄昏", night: "夜晚", "deep-night": "深夜" },
  },
  "en-SG": {
    aria: "Appearance",
    label: "Light",
    option: { follow: "Follow time", day: "Day", night: "Night" },
    timeLabel: "Ambient time",
    demoLabel: "Demo time",
    realtime: "Live",
    restore: "Return to live time",
    helper: "Move through the day to preview all seven atmospheres. Follow time changes the light mode too.",
    period: { dawn: "Dawn", morning: "Morning", noon: "Noon", evening: "Afternoon", dusk: "Dusk", night: "Night", "deep-night": "Deep night" },
  },
};

const padHour = (hour: number) => `${String(hour).padStart(2, "0")}:00`;

export function AppearanceToggle({ locale = "zh-CN" }: { locale?: Locale }) {
  const t = COPY[locale];
  const [scheme, setScheme] = useState<ColorScheme | null>(() => getColorScheme());
  const [previewHour, setPreviewHourState] = useState<number | null>(() => getPreviewHour());
  const [liveHour, setLiveHour] = useState(() => new Date().getHours());
  const shownHour = previewHour ?? liveHour;
  const period = timeOfDayForHour(shownHour);

  useEffect(() => {
    const id = window.setInterval(() => setLiveHour(new Date().getHours()), 60_000);
    return () => window.clearInterval(id);
  }, []);

  const chooseScheme = (value: ColorScheme | null) => {
    setColorScheme(value);
    setScheme(value);
    applyAdaptiveTheme();
  };

  const preview = (hour: number) => {
    setPreviewHour(hour);
    setPreviewHourState(hour);
    applyAdaptiveTheme();
  };

  const restoreLive = () => {
    setPreviewHour(null);
    setPreviewHourState(null);
    setLiveHour(new Date().getHours());
    applyAdaptiveTheme();
  };

  return (
    <section className="appearance-toggle" role="group" aria-label={t.aria}>
      <div className="appearance-heading">
        <div>
          <span className="appearance-label">{t.label}</span>
          <strong>{t.period[period]}</strong>
        </div>
        <span className={`appearance-time-status ${previewHour === null ? "live" : ""}`}>
          {previewHour === null ? t.realtime : t.demoLabel} · {padHour(shownHour)}
        </span>
      </div>

      <div className="appearance-options" role="group" aria-label={t.label}>
        {OPTIONS.map(value => (
          <button
            type="button"
            key={value ?? "follow"}
            aria-pressed={scheme === value}
            className={scheme === value ? "active" : ""}
            onClick={() => chooseScheme(value)}
          >
            {t.option[value ?? "follow"]}
          </button>
        ))}
      </div>

      <div className="time-preview">
        <label htmlFor="ambient-time-slider">
          <span>{t.timeLabel}</span>
          <strong>{t.period[period]} · {padHour(shownHour)}</strong>
        </label>
        <input
          id="ambient-time-slider"
          type="range"
          min="0"
          max="23"
          step="1"
          value={shownHour}
          aria-label={t.demoLabel}
          aria-valuetext={`${t.period[period]}, ${padHour(shownHour)}`}
          onChange={event => preview(Number(event.currentTarget.value))}
        />
        <div className="time-presets" role="group" aria-label={t.demoLabel}>
          {TIME_OF_DAY_ORDER.map(item => (
            <button
              type="button"
              key={item}
              className={period === item ? "active" : ""}
              aria-pressed={period === item}
              onClick={() => preview(TIME_OF_DAY_HOURS[item])}
            >
              <span aria-hidden="true" />
              {t.period[item]}
            </button>
          ))}
        </div>
      </div>

      <div className="appearance-foot">
        <small>{t.helper}</small>
        <button type="button" className="restore-live" disabled={previewHour === null} onClick={restoreLive}>
          {t.restore}
        </button>
      </div>
    </section>
  );
}
