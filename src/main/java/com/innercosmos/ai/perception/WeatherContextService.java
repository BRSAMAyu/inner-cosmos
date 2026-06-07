package com.innercosmos.ai.perception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.perception.dto.WeatherForecast;
import com.innercosmos.ai.perception.dto.WeatherForecast.HourSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Real-time + 24h weather forecast via Open-Meteo (free, no API key).
 * <p>
 * Endpoint: https://api.open-meteo.com/v1/forecast
 * We request hourly weather_code + temperature_2m for 24h starting from
 * the current hour, plus the current snapshot. The WMO weather code is
 * collapsed to one of six buckets (CLEAR / CLOUDY / RAINY / STORM / SNOW / FOG)
 * so the agent prompt can reason about it without parsing raw numbers.
 * <p>
 * 30-minute in-memory cache keyed by "lat,lon" (2-decimal bucket) so that
 * successive agent turns inside the same window don't hit the API.
 * <p>
 * On any error the service returns a single CLEAR forecast so callers
 * never see a null / exception — losing the weather context is preferable
 * to breaking the chat.
 */
@Service
public class WeatherContextService {
    private static final Logger log = LoggerFactory.getLogger(WeatherContextService.class);

    private static final String OPEN_METEO_URL =
            "https://api.open-meteo.com/v1/forecast"
                    + "?current=temperature_2m,weather_code,wind_speed_10m"
                    + "&hourly=temperature_2m,weather_code"
                    + "&forecast_days=2"
                    + "&timezone=auto";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30L * 60L * 1000L;

    /**
     * Get the weather forecast for the given coordinates.
     * Always returns a non-null {@link WeatherForecast}. On any error
     * returns a CLEAR default at 0°C with no rain expected.
     */
    public WeatherForecast get(double lat, double lon) {
        String key = String.format("%.2f,%.2f", lat, lon);
        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.isExpired()) return cached.value;

        try {
            String url = OPEN_METEO_URL
                    + "&latitude=" + lat
                    + "&longitude=" + lon;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "InnerCosmos/1.0 (teacher-demo)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                log.warn("Open-Meteo non-2xx status={} for {},{}", response.statusCode(), lat, lon);
                return fallback();
            }
            JsonNode node = objectMapper.readTree(response.body());
            WeatherForecast forecast = parse(node);
            cache.put(key, new CacheEntry(forecast));
            return forecast;
        } catch (Exception e) {
            log.warn("Weather fetch failed for {},{}: {}", lat, lon, e.getMessage());
            return fallback();
        }
    }

    private WeatherForecast parse(JsonNode root) {
        JsonNode current = root.path("current");
        double temperature = current.path("temperature_2m").asDouble(0.0);
        double windKph = current.path("wind_speed_10m").asDouble(0.0);
        String currentType = mapWeatherCode(current.path("weather_code").asInt(0));

        JsonNode hourly = root.path("hourly");
        JsonNode hours = hourly.path("time");
        JsonNode temps = hourly.path("temperature_2m");
        JsonNode codes = hourly.path("weather_code");

        List<HourSlot> next24h = new ArrayList<>();
        boolean rain = false;
        String worst = currentType;
        int worstRank = severityRank(currentType);

        int n = hours.size();
        for (int i = 0; i < n && next24h.size() < 24; i++) {
            int hour = parseHourOfDay(hours.path(i).asText(""));
            if (hour < 0) continue;
            double t = i < temps.size() ? temps.path(i).asDouble(0.0) : 0.0;
            int code = i < codes.size() ? codes.path(i).asInt(0) : 0;
            String type = mapWeatherCode(code);
            next24h.add(new HourSlot(hour, type, t));
            if ("RAINY".equals(type) || "STORM".equals(type)) rain = true;
            int rank = severityRank(type);
            if (rank > worstRank) {
                worstRank = rank;
                worst = type;
            }
        }

        String summary = buildSummary(currentType, worst, rain);
        return new WeatherForecast(currentType, temperature, windKph, summary, rain, worst, next24h);
    }

    /**
     * Map WMO weather code → our 6-bucket system.
     * https://open-meteo.com/en/docs#weathervariables
     */
    static String mapWeatherCode(int code) {
        if (code == 0) return "CLEAR";
        if (code == 1 || code == 2) return "CLOUDY";
        if (code == 3) return "CLOUDY";
        if (code == 45 || code == 48) return "FOG";
        if (code >= 51 && code <= 67) return "RAINY";
        if (code >= 71 && code <= 77) return "SNOW";
        if (code >= 80 && code <= 82) return "RAINY";
        if (code >= 85 && code <= 86) return "SNOW";
        if (code >= 95) return "STORM";
        return "CLEAR";
    }

    /** Higher = more "actionable" for the agent. CLEAR=0, FOG=1, CLOUDY=2, SNOW=3, RAINY=4, STORM=5. */
    static int severityRank(String type) {
        return switch (type) {
            case "STORM" -> 5;
            case "RAINY" -> 4;
            case "SNOW" -> 3;
            case "CLOUDY" -> 2;
            case "FOG" -> 1;
            default -> 0;
        };
    }

    private String buildSummary(String current, String worst, boolean rain) {
        if ("STORM".equals(worst)) return "未来有雷暴";
        if (rain && "CLEAR".equals(current)) return "晴转雨";
        if (rain && !"RAINY".equals(current)) return currentTypeChinese(current) + "转雨";
        if ("SNOW".equals(worst)) return "未来可能下雪";
        if ("FOG".equals(worst)) return "有雾";
        if ("RAINY".equals(current)) return "持续阴雨";
        if ("CLOUDY".equals(current)) return "全天多云";
        return "全天晴朗";
    }

    private static String currentTypeChinese(String t) {
        return WeatherForecast.typeChinese(t);
    }

    private int parseHourOfDay(String iso) {
        if (iso == null || iso.length() < 13) return -1;
        try {
            return Integer.parseInt(iso.substring(11, 13));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private WeatherForecast fallback() {
        return new WeatherForecast("CLEAR", 0.0, 0.0, "天气暂不可知", false, "CLEAR", List.of());
    }

    private static final class CacheEntry {
        final WeatherForecast value;
        final long createdAtMs;

        CacheEntry(WeatherForecast value) {
            this.value = value;
            this.createdAtMs = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAtMs > CACHE_TTL_MS;
        }
    }
}
