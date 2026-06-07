# M0: Context 感知升级 (Time + Weather + Geocoding) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade Aurora's context to include real LLM-meaningful time, weather (current + 24h forecast), and city-level location — replacing the current keyword-stub environment perception.

**Architecture:** Three thin service classes (TimeContextService, WeatherContextService, GeocodingService) feed into the existing `AgentContextAssembler` and `AuroraAgentServiceImpl`. Frontend stops sending raw lat/lon; backend resolves to city before injecting into prompt. No scheduler yet — that's M2.

**Tech Stack:** Spring Boot 3.3.6, MyBatis-Plus, Open-Meteo API (free, no key, CORS-enabled), Nominatim (reverse geocoding, CORS-enabled, with proper User-Agent).

---

## File Structure

**New files:**
- `src/main/java/com/innercosmos/ai/perception/TimeContextService.java`
- `src/main/java/com/innercosmos/ai/perception/WeatherContextService.java`
- `src/main/java/com/innercosmos/ai/perception/GeocodingService.java`
- `src/main/java/com/innercosmos/ai/perception/dto/WeatherForecast.java` (record)
- `src/main/java/com/innercosmos/ai/perception/dto/LocationInfo.java` (record)
- `src/test/java/com/innercosmos/ai/perception/WeatherContextServiceTest.java`
- `src/test/java/com/innercosmos/ai/perception/GeocodingServiceTest.java`
- `src/test/java/com/innercosmos/ai/perception/QuietWindowResolverTest.java` (foundation for M2)

**Modified:**
- `src/main/java/com/innercosmos/ai/context/AgentContextAssembler.java` — inject 3 new services; add `weather24hLabel`, `cityLabel`, `streetLabel` fields to `AgentContext`
- `src/main/java/com/innercosmos/service/impl/AuroraAgentServiceImpl.java` — use new context fields in prompt; remove old `clientWeather` keyword path
- `src/main/resources/static/js/time-system.js` — keep geolocation, stop posting raw lat/lon to chat; send `locationLabel` from localStorage
- `src/main/resources/static/js/weather-system.js` — keep weather fetch, also fetch `forecast_hours=24`, post 24h summary

---

## Tasks

### Task 1: GeocodingService — lat/lon → city/street (no LLM)

**Files:**
- Create: `src/main/java/com/innercosmos/ai/perception/GeocodingService.java`
- Create: `src/main/java/com/innercosmos/ai/perception/dto/LocationInfo.java`
- Create: `src/test/java/com/innercosmos/ai/perception/GeocodingServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class GeocodingServiceTest {
  @Autowired GeocodingService service;
  @Test void resolvesBeijingToCity() {
    LocationInfo loc = service.resolve(39.9042, 116.4074).block();
    assertThat(loc.city()).contains("北京");
  }
  @Test void fallsBackOnApiError() {
    // Point to open ocean; should still return some LocationInfo
    LocationInfo loc = service.resolve(0.0, 0.0).block();
    assertThat(loc).isNotNull();
    assertThat(loc.city()).isNotBlank();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd "C:/inner cosmos" && ./.tools/apache-maven-3.9.9/bin/mvn.cmd test -Dtest=GeocodingServiceTest -q`
Expected: compilation error (no such class)

- [ ] **Step 3: Implement LocationInfo record**

```java
package com.innercosmos.ai.perception.dto;
public record LocationInfo(String city, String street, String country, Double lat, Double lon) {
  public String label() { return street != null && !street.isBlank() ? city + " · " + street : city; }
  public String cityOnly() { return city == null ? "" : city; }
}
```

- [ ] **Step 4: Implement GeocodingService**

```java
package com.innercosmos.ai.perception;
@Service
public class GeocodingService {
  private final WebClient web = WebClient.builder()
      .defaultHeader("User-Agent", "InnerCosmos/1.0 (teacher-demo)")
      .defaultHeader("Accept-Language", "zh-CN")
      .build();
  private final Map<String, LocationInfo> cache = new ConcurrentHashMap<>();
  private static final Duration TTL = Duration.ofHours(24);

  public Mono<LocationInfo> resolve(double lat, double lon) {
    String key = String.format("%.2f,%.2f", lat, lon);
    LocationInfo cached = cache.get(key);
    if (cached != null) return Mono.just(cached);
    String url = "https://nominatim.openstreetmap.org/reverse?lat=" + lat
        + "&lon=" + lon + "&format=json&accept-language=zh-CN&zoom=18";
    return web.get().uri(url).retrieve().bodyToMono(JsonNode.class)
        .map(this::toLocationInfo)
        .doOnNext(li -> cache.put(key, li))
        .onErrorResume(e -> Mono.just(new LocationInfo("未知", null, null, lat, lon)));
  }

  private LocationInfo toLocationInfo(JsonNode n) {
    JsonNode a = n.path("address");
    String city = firstNonBlank(a, "city", "town", "village", "county", "state");
    String street = firstNonBlank(a, "road", "pedestrian", "suburb", "neighbourhood");
    String country = a.path("country").asText(null);
    return new LocationInfo(city, street, country,
        n.path("lat").asDouble(), n.path("lon").asDouble());
  }
  private String firstNonBlank(JsonNode a, String... keys) {
    for (String k : keys) { String v = a.path(k).asText(null); if (v != null && !v.isBlank()) return v; }
    return null;
  }
}
```

- [ ] **Step 5: Run test, verify pass**

Run: `cd "C:/inner cosmos" && ./.tools/apache-maven-3.9.9/bin/mvn.cmd test -Dtest=GeocodingServiceTest -q`
Expected: PASS (Beijing resolves; ocean point returns "未知")

- [ ] **Step 6: Commit**

```bash
cd "C:/inner cosmos"
git add src/main/java/com/innercosmos/ai/perception/
git -c commit.gpgsign=false commit -m "feat(M0): GeocodingService resolves lat/lon to city + street via Nominatim"
```

---

### Task 2: WeatherContextService — current + 24h forecast

**Files:**
- Create: `src/main/java/com/innercosmos/ai/perception/WeatherContextService.java`
- Create: `src/main/java/com/innercosmos/ai/perception/dto/WeatherForecast.java`
- Create: `src/test/java/com/innercosmos/ai/perception/WeatherContextServiceTest.java`

- [ ] **Step 1: Write failing test**

```java
@SpringBootTest
class WeatherContextServiceTest {
  @Autowired WeatherContextService svc;
  @Test void fetchesCurrentAnd24hForBeijing() {
    WeatherForecast w = svc.fetch(39.9042, 116.4074).block();
    assertThat(w).isNotNull();
    assertThat(w.temperatureC()).isBetween(-50.0, 60.0);
    assertThat(w.summary24h()).isNotBlank();   // e.g. "明天 14-22°C 阵雨"
    assertThat(w.rainExpectedIn24h()).isIn(true, false);
  }
}
```

- [ ] **Step 2: Verify fail** (compile error)

- [ ] **Step 3: Implement WeatherForecast record**

```java
package com.innercosmos.ai.perception.dto;
public record WeatherForecast(
    String currentType,       // CLEAR/CLOUDY/RAINY/STORM/SNOW/FOG
    double temperatureC,
    double windKph,
    String summary24h,        // Chinese 1-line summary
    boolean rainExpectedIn24h,
    String worstTypeIn24h,
    List<HourSlot> next24h
) {
  public record HourSlot(int hour, String type, double tempC) {}
  public String label() {
    return String.format("%s · 当前 %s %.1f°C · %s%s",
        currentType, typeChinese(currentType), temperatureC, summary24h,
        rainExpectedIn24h ? " · 24h内会下雨" : "");
  }
  public static String typeChinese(String t) { return switch (t) {
    case "CLEAR" -> "晴"; case "CLOUDY" -> "多云"; case "RAINY" -> "雨";
    case "STORM" -> "雷暴"; case "SNOW" -> "雪"; case "FOG" -> "雾";
    default -> t; };
  }
}
```

- [ ] **Step 4: Implement WeatherContextService**

```java
package com.innercosmos.ai.perception;
@Service
public class WeatherContextService {
  private final WebClient web = WebClient.create();
  private final Map<String, CachedForecast> cache = new ConcurrentHashMap<>();
  private static final Duration TTL = Duration.ofMinutes(30);

  public Mono<WeatherForecast> fetch(double lat, double lon) {
    String key = String.format("%.2f,%.2f", lat, lon);
    CachedForecast c = cache.get(key);
    if (c != null && c.at.isAfter(Instant.now().minus(TTL))) return Mono.just(c.f);
    String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
        + "&longitude=" + lon
        + "&current=temperature_2m,weather_code,wind_speed_10m"
        + "&hourly=weather_code,temperature_2m&forecast_days=2&timezone=auto";
    return web.get().uri(url).retrieve().bodyToMono(JsonNode.class)
        .map(n -> toForecast(n, lat, lon))
        .doOnNext(f -> cache.put(key, new CachedForecast(f, Instant.now())))
        .onErrorResume(e -> Mono.just(fallback()));
  }
  private WeatherForecast fallback() {
    return new WeatherForecast("UNKNOWN", 18.0, 0.0, "天气未知", false, "UNKNOWN", List.of());
  }
  private WeatherForecast toForecast(JsonNode n, double lat, double lon) {
    JsonNode cur = n.path("current");
    String curType = wmoToType(cur.path("weather_code").asInt());
    double temp = cur.path("temperature_2m").asDouble();
    double wind = cur.path("wind_speed_10m").asDouble();
    JsonNode hours = n.path("hourly");
    // Take next 24 entries starting from "now"
    Instant nowHour = Instant.now().truncatedTo(ChronoUnit.HOURS);
    List<WeatherForecast.HourSlot> next24 = new ArrayList<>();
    boolean rain = false; String worst = curType;
    JsonNode times = hours.path("time"); JsonNode codes = hours.path("weather_code");
    JsonNode temps = hours.path("temperature_2m");
    for (int i = 0; i < Math.min(24, times.size()); i++) {
      String t = times.get(i).asText();
      if (LocalDateTime.parse(t).toInstant(ZoneOffset.UTC).isBefore(nowHour)) continue;
      String type = wmoToType(codes.get(i).asInt());
      double tC = temps.get(i).asDouble();
      next24.add(new WeatherForecast.HourSlot(i, type, tC));
      if (type.equals("RAINY") || type.equals("STORM") || type.equals("SNOW")) rain = true;
      if (severity(type) > severity(worst)) worst = type;
    }
    String summary = buildSummary(curType, temp, rain, worst, next24);
    return new WeatherForecast(curType, temp, wind, summary, rain, worst, next24);
  }
  private static int severity(String t) { return switch (t) {
    case "STORM" -> 4; case "RAINY" -> 3; case "SNOW" -> 3;
    case "FOG" -> 2; case "CLOUDY" -> 1; default -> 0; }; }
  private static String buildSummary(String cur, double t, boolean rain, String worst, List<WeatherForecast.HourSlot> h) {
    StringBuilder sb = new StringBuilder();
    sb.append("当前 ").append(WeatherForecast.typeChinese(cur)).append(" ").append(String.format("%.0f", t)).append("°C");
    if (rain) sb.append(" · 24h 内有降水");
    if (!worst.equals(cur)) sb.append(" · 最差 ").append(WeatherForecast.typeChinese(worst));
    return sb.toString();
  }
  private static String wmoToType(int code) {
    if (code == 0) return "CLEAR";
    if (code <= 3) return "CLOUDY";
    if (code == 45 || code == 48) return "FOG";
    if (code >= 51 && code <= 67) return "RAINY";
    if (code >= 71 && code <= 77) return "SNOW";
    if (code >= 80 && code <= 86) return "RAINY";
    if (code >= 95) return "STORM";
    return "CLOUDY";
  }
  private record CachedForecast(WeatherForecast f, Instant at) {}
}
```

- [ ] **Step 5: Run test, verify pass**

- [ ] **Step 6: Commit**

```bash
cd "C:/inner cosmos"
git add src/main/java/com/innercosmos/ai/perception/WeatherContextService.java src/main/java/com/innercosmos/ai/perception/dto/WeatherForecast.java src/test/java/com/innercosmos/ai/perception/WeatherContextServiceTest.java
git -c commit.gpgsign=false commit -m "feat(M0): WeatherContextService fetches current + 24h forecast from Open-Meteo"
```

---

### Task 3: TimeContextService — now + todo context

**Files:**
- Create: `src/main/java/com/innercosmos/ai/perception/TimeContextService.java`

- [ ] **Step 1: Implement directly (no separate test, covered by integration)**

```java
package com.innercosmos.ai.perception;
@Service
public class TimeContextService {
  public record TimeContext(String label, String dateLabel, boolean isSleep, boolean isFocus, String nearestTodo) {}

  public TimeContext now() {
    ZonedDateTime n = ZonedDateTime.now(ZoneId.systemDefault());
    String label = timeLabel(n.getHour());
    return new TimeContext(label, n.format(DateTimeFormatter.ofPattern("M月d日 EEE HH:mm")),
        isInferredSleep(n), false, null);
  }
  public TimeContext now(boolean focusActive, String nearestTodo) {
    TimeContext t = now();
    return new TimeContext(t.label(), t.dateLabel(), t.isSleep(), focusActive, nearestTodo);
  }
  public static String timeLabel(int h) {
    if (h >= 5 && h < 7) return "清晨";
    if (h >= 7 && h < 9) return "早晨";
    if (h >= 9 && h < 12) return "上午";
    if (h >= 12 && h < 14) return "中午";
    if (h >= 14 && h < 18) return "下午";
    if (h >= 18 && h < 20) return "傍晚";
    if (h >= 20 && h < 23) return "晚上";
    return "深夜";
  }
  public static boolean isInferredSleep(ZonedDateTime n) {
    int h = n.getHour();
    return h >= 23 || h < 7;
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/innercosmos/ai/perception/TimeContextService.java
git -c commit.gpgsign=false commit -m "feat(M0): TimeContextService provides time label, date, sleep inference"
```

---

### Task 4: Wire services into AgentContextAssembler

**Files:**
- Modify: `src/main/java/com/innercosmos/ai/context/AgentContextAssembler.java`
- Modify: `src/main/java/com/innercosmos/ai/context/AgentContext.java` (add fields: `cityLabel`, `weather24hLabel`, `rainExpectedIn24h`, `timeLabel`)

- [ ] **Step 1: Add new fields to AgentContext**

Add to the record/class: `String timeLabel; String cityLabel; String weather24hLabel; boolean rainExpectedIn24h;`

- [ ] **Step 2: Wire in `assemble()`**

```java
// at top of assemble():
TimeContextService.TimeContext t = timeContextService.now();
String city = geocodingService.resolve(currentLat, currentLon)
    .map(GeocodingService.LocationInfo::cityOnly).block(Duration.ofSeconds(2));
WeatherForecast w = weatherContextService.fetch(currentLat, currentLon).block(Duration.ofSeconds(2));
ctx.timeLabel = t.label();
ctx.cityLabel = city;
ctx.weather24hLabel = w.label();
ctx.rainExpectedIn24h = w.rainExpectedIn24h();
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/innercosmos/ai/context/AgentContext.java src/main/java/com/innercosmos/ai/context/AgentContextAssembler.java
git -c commit.gpgsign=false commit -m "feat(M0): AgentContextAssembler injects time, city, weather24h"
```

---

### Task 5: Frontend: stop sending raw lat/lon, send locationLabel

**Files:**
- Modify: `src/main/resources/static/js/time-system.js` — already reverse-geocodes; expose `window.ICLocation.getCityLabel()`
- Modify: `src/main/resources/static/js/aurora-chat.html` (the page, not the JS) — change payload to send `locationLabel: ICLocation.getCityLabel()` instead of lat/lon

- [ ] **Step 1: In `time-system.js`, expose global**

```js
// add at end of resolveLocationLabel():
window.ICLocation = window.ICLocation || {};
window.ICLocation.getCityLabel = () => localStorage.getItem('ic_location_label') || '未知';
```

- [ ] **Step 2: In `aurora-chat.html` find `auroraGreeting`/`auroraMessage` payload, replace `lat/lon` with `locationLabel`**

```js
locationLabel: ICLocation.getCityLabel()
```

- [ ] **Step 3: Copy to target/classes and restart server**

```bash
cp "C:/inner cosmos/src/main/resources/static/js/time-system.js" "C:/inner cosmos/target/classes/static/js/time-system.js"
# restart server via run-dev.ps1 or the mvn background task
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/js/time-system.js src/main/resources/static/pages/aurora-chat.html
git -c commit.gpgsign=false commit -m "feat(M0): frontend sends city label instead of raw lat/lon"
```

---

### Task 6: Verification (end-to-end)

- [ ] **Step 1: Send Aurora chat, verify prompt includes "上海" or actual city, current temp, 24h forecast**

Run: `curl -b /tmp/cookies.txt -X POST -H "Content-Type: application/json" -d '{"message":"今天天气怎样?"}' http://localhost:8080/api/aurora/message-rich`
Expected: response mentions current temp, tomorrow's rain if any

- [ ] **Step 2: Health check confirms**

```bash
curl -b /tmp/cookies.txt http://localhost:8080/api/ai/health
```

- [ ] **Step 3: Browser test (Playwright)**

Navigate to `http://localhost:8080/pages/aurora-chat.html`, ask about weather, verify response.

---

## Acceptance Criteria

- Aurora says real city name (not "未知"), current temperature, 24h rain warning if applicable
- Backend logs show no `weather-system` CORS errors
- Existing chat still works (no regressions in mode switching, multi-bubble, etc.)

## Dependencies

- None (M0 is the foundation)

## Risks

- Open-Meteo rate limit: 10k req/day, cached 30 min → low risk for single user demo
- Nominatim rate limit: 1 req/sec; cached 24h, only re-fires on lat/lon change → safe
- Latency: 2s blocking in assemble() — if geocoding hangs, user feels it. Use `block(Duration.ofSeconds(2))` and graceful degradation (fall back to "未知")
