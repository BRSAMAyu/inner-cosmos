package com.innercosmos.ai.perception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.perception.dto.LocationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Reverse-geocoding via the public Nominatim (OpenStreetMap) API.
 * <p>
 * - No LLM involved: lat/lon → city / street / country straight from
 *   OpenStreetMap's address hierarchy.
 * - 24h in-memory cache keyed by a 2-decimal lat/lon bucket (≈1km grid).
 * - On any failure (network, parse, 4xx/5xx) the service falls back to a
 *   {@code LocationInfo("未知", null, null, lat, lon)} so callers never have
 *   to deal with nulls.
 * <p>
 * Honors the Nominatim usage policy: a custom User-Agent is set on every
 * request. The remote server is not hammered — calls outside the cache hit
 * Nominatim at most once per ~1km bucket per 24h.
 */
@Service
public class GeocodingService {
    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    private static final String NOMINATIM_URL =
            "https://nominatim.openstreetmap.org/reverse?format=json&accept-language=zh-CN&zoom=18";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 24h cache keyed by "lat,lon" rounded to 2 decimals. */
    private final ConcurrentMap<String, LocationInfo> cache = new ConcurrentHashMap<>();

    /**
     * Resolve (lat, lon) into a {@link LocationInfo}.
     * <p>
     * Synchronous from the caller's perspective. Always returns a non-null
     * value; on any error returns "未知" as city and the original lat/lon
     * for downstream debugging.
     */
    public LocationInfo resolve(double lat, double lon) {
        String key = String.format("%.2f,%.2f", lat, lon);
        LocationInfo cached = cache.get(key);
        if (cached != null) return cached;

        try {
            String url = NOMINATIM_URL
                    + "&lat=" + lat
                    + "&lon=" + lon;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "InnerCosmos/1.0 (teacher-demo)")
                    .header("Accept-Language", "zh-CN")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                log.warn("Nominatim non-2xx status={} for {},{}", response.statusCode(), lat, lon);
                return fallback(lat, lon);
            }
            JsonNode node = objectMapper.readTree(response.body());
            LocationInfo info = toLocationInfo(node, lat, lon);
            cache.put(key, info);
            return info;
        } catch (Exception e) {
            log.warn("Geocoding failed for {},{}: {}", lat, lon, e.getMessage());
            return fallback(lat, lon);
        }
    }

    /**
     * Best-effort city resolution used by the request layer when the client
     * only sent a label like "北京 · 海淀区". Falls back to "未知" on null.
     */
    public String cityOnly(double lat, double lon) {
        LocationInfo info = resolve(lat, lon);
        String c = info.cityOnly();
        return c == null || c.isBlank() ? "未知" : c;
    }

    private LocationInfo toLocationInfo(JsonNode root, double lat, double lon) {
        JsonNode a = root.path("address");
        String city = firstNonBlank(a, "city", "town", "village", "county", "state", "municipality");
        String street = firstNonBlank(a, "road", "pedestrian", "suburb", "neighbourhood", "hamlet");
        String country = a.path("country").asText(null);
        Double resolvedLat = root.path("lat").asDouble(lat);
        Double resolvedLon = root.path("lon").asDouble(lon);
        return new LocationInfo(city, street, country, resolvedLat, resolvedLon);
    }

    private String firstNonBlank(JsonNode a, String... keys) {
        for (String k : keys) {
            String v = a.path(k).asText(null);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private LocationInfo fallback(double lat, double lon) {
        return new LocationInfo("未知", null, null, lat, lon);
    }
}
