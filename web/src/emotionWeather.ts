import type { Locale } from "./i18n";

type EmotionWeatherPresentation = {
  icon: string;
  mark: string;
  label: Record<Locale, string>;
};

const EMOTION_WEATHER: Record<string, EmotionWeatherPresentation> = {
  CLEAR: { icon: "🌤️", mark: "◌", label: { "zh-CN": "清朗", "en-SG": "Clear" } },
  SUNNY: { icon: "☀️", mark: "☼", label: { "zh-CN": "明亮", "en-SG": "Bright" } },
  CLOUDY: { icon: "☁️", mark: "◒", label: { "zh-CN": "有云", "en-SG": "Clouded" } },
  RAINY: { icon: "🌧️", mark: "⌁", label: { "zh-CN": "有雨", "en-SG": "Rainy" } },
  FOGGY: { icon: "🌫️", mark: "≋", label: { "zh-CN": "有雾", "en-SG": "Foggy" } },
  STORM: { icon: "⛈️", mark: "≈", label: { "zh-CN": "风暴", "en-SG": "Stormy" } },
  // Keep accepting the historical frontend spelling while the persisted/backend contract uses STORM.
  STORMY: { icon: "⛈️", mark: "≈", label: { "zh-CN": "风暴", "en-SG": "Stormy" } },
  SNOWY: { icon: "❄️", mark: "❄", label: { "zh-CN": "有雪", "en-SG": "Snowy" } },
  WINDY: { icon: "🌬️", mark: "∿", label: { "zh-CN": "有风", "en-SG": "Windy" } }
};

export function emotionWeatherPresentation(type: string | null | undefined, locale: Locale) {
  const weather = type ? EMOTION_WEATHER[type.toUpperCase()] : undefined;
  return weather
    ? { icon: weather.icon, mark: weather.mark, label: weather.label[locale] }
    : { icon: "🌤️", mark: "·", label: locale === "en-SG" ? "Not named yet" : "尚未命名" };
}
