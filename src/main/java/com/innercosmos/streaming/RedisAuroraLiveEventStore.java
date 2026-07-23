package com.innercosmos.streaming;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "inner-cosmos.aurora.stream.redis.enabled", havingValue = "true")
public class RedisAuroraLiveEventStore implements AuroraLiveEventStore {
    private static final Duration MAX_BLOCK = Duration.ofSeconds(1);
    /**
     * Gemini audit 2.7 (CONFIRMED/P1): XADD (with MAXLEN trim) and the TTL-setting EXPIRE used to
     * be two separate Redis round trips (redis.opsForStream().add(...) then redis.expire(...)) --
     * a crash/restart between them could leave a stream key with NO TTL at all (unbounded
     * retention). A single Lua script is atomic on the Redis server: every redis.call inside it
     * either all apply (the whole script ran) or the script never started applying any of them
     * (a pre-execution error, e.g. bad ARGV) -- there is no window in which the key can exist
     * with the new entry appended but no expiry set.
     */
    private static final DefaultRedisScript<String> ATOMIC_PUBLISH_AND_EXPIRE = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local maxlen = tonumber(ARGV[1])
            local ttl_seconds = tonumber(ARGV[2])
            local entry_id = redis.call('XADD', key, 'MAXLEN', '~', maxlen, '*',
                'userId', ARGV[3], 'turnId', ARGV[4], 'sequence', ARGV[5],
                'id', ARGV[6], 'name', ARGV[7], 'data', ARGV[8], 'terminal', ARGV[9])
            redis.call('EXPIRE', key, ttl_seconds)
            return entry_id
            """, String.class);

    private final StringRedisTemplate redis;
    private final String namespace;
    private final Duration retention;
    private final int maxLength;

    public RedisAuroraLiveEventStore(StringRedisTemplate redis,
                                     @Value("${inner-cosmos.aurora.stream.live-namespace:inner-cosmos:aurora:live:v1}") String namespace,
                                     @Value("${inner-cosmos.aurora.stream.retention:PT15M}") Duration retention,
                                     @Value("${inner-cosmos.aurora.stream.max-length:1024}") int maxLength) {
        this.redis = redis;
        this.namespace = namespace;
        this.retention = retention;
        this.maxLength = maxLength;
    }

    @Override
    public void publish(AuroraLiveEvent event) {
        String key = key(event.turnId());
        // Gemini audit 2.7: XADD (MAXLEN trim) and EXPIRE happen inside ONE atomic Lua script --
        // never two separate Redis commands with a crash window between them.
        redis.execute(ATOMIC_PUBLISH_AND_EXPIRE, List.of(key),
                Integer.toString(maxLength),
                Long.toString(Math.max(1, retention.toSeconds())),
                event.userId().toString(), event.turnId().toString(), Long.toString(event.sequence()),
                event.id(), event.name(), event.data(), Boolean.toString(event.terminal()));
    }

    @Override
    public List<AuroraLiveEvent> readAfter(Long userId, Long turnId, long afterSequence, Duration wait) {
        String key = key(turnId);
        List<MapRecord<String, Object, Object>> existing = redis.opsForStream().range(key, Range.unbounded());
        List<AuroraLiveEvent> found = decode(existing, userId, turnId, afterSequence);
        if (!found.isEmpty() || wait == null || wait.isZero() || wait.isNegative()) return found;
        String offset = existing == null || existing.isEmpty()
                ? "0-0" : existing.getLast().getId().getValue();
        List<MapRecord<String, Object, Object>> arrived = redis.opsForStream().read(
                StreamReadOptions.empty().count(maxLength).block(shorter(wait, MAX_BLOCK)),
                StreamOffset.create(key, ReadOffset.from(offset)));
        return decode(arrived, userId, turnId, afterSequence);
    }

    private List<AuroraLiveEvent> decode(List<MapRecord<String, Object, Object>> records,
                                         Long userId, Long turnId, long afterSequence) {
        if (records == null) return List.of();
        return records.stream().map(record -> decode(record.getValue()))
                .filter(event -> event != null && userId.equals(event.userId())
                        && turnId.equals(event.turnId()) && event.sequence() > afterSequence)
                .toList();
    }

    private AuroraLiveEvent decode(Map<Object, Object> body) {
        try {
            return new AuroraLiveEvent(
                    Long.valueOf(value(body, "userId")), Long.valueOf(value(body, "turnId")),
                    Long.parseLong(value(body, "sequence")), value(body, "id"), value(body, "name"),
                    value(body, "data"), Boolean.parseBoolean(value(body, "terminal")));
        } catch (Exception malformed) {
            return null;
        }
    }

    private String value(Map<Object, Object> body, String key) {
        Object direct = body.get(key);
        if (direct != null) return direct.toString();
        return body.entrySet().stream().filter(entry -> key.equals(entry.getKey().toString()))
                .map(entry -> entry.getValue().toString()).findFirst().orElseThrow();
    }

    private String key(Long turnId) {
        return namespace + ":turn:" + turnId;
    }

    private Duration shorter(Duration requested, Duration maximum) {
        return requested.compareTo(maximum) > 0 ? maximum : requested;
    }
}
