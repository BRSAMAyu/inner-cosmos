package com.innercosmos.ai.perception.dto;

import java.util.List;

/**
 * Weather context for the agent: current snapshot + 24h outlook.
 * <p>
 * {@code currentType} is one of: CLEAR / CLOUDY / RAINY / STORM / SNOW / FOG.
 * {@code worstTypeIn24h} is the most severe type that will appear inside the
 * 24h window — used by the agent to decide whether to nudge a "bring an umbrella"
 * kind of caring message.
 */
public record WeatherForecast(
        String currentType,
        double temperatureC,
        double windKph,
        String summary24h,
        boolean rainExpectedIn24h,
        String worstTypeIn24h,
        List<HourSlot> next24h
) {
    /**
     * One hour slot inside the 24h outlook. Hour 0 = the current hour.
     */
    public record HourSlot(int hour, String type, double tempC) {}

    /**
     * Compact Chinese label, e.g. "多云 · 当前 多云 21.5°C · 傍晚转小雨 · 24h内会下雨".
     */
    public String label() {
        return String.format("%s · 当前 %s %.1f°C · %s%s",
                currentType, typeChinese(currentType), temperatureC, summary24h,
                rainExpectedIn24h ? " · 24h内会下雨" : "");
    }

    public static String typeChinese(String t) {
        if (t == null) return "";
        return switch (t) {
            case "CLEAR" -> "晴";
            case "CLOUDY" -> "多云";
            case "RAINY" -> "雨";
            case "STORM" -> "雷暴";
            case "SNOW" -> "雪";
            case "FOG" -> "雾";
            default -> t;
        };
    }
}
