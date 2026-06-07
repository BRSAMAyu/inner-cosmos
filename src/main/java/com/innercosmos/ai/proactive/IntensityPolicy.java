package com.innercosmos.ai.proactive;

import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Map;

/**
 * Intensity policy table for the proactive engine.
 * Defines max pushes per day and minimum gap between pushes for each intensity level.
 */
@Component
public class IntensityPolicy {

    public record Policy(int maxPerDay, Duration minGap) {}

    private static final Map<String, Policy> TABLE = Map.of(
        "OFF",       new Policy(0, Duration.ZERO),
        "WHISPER",   new Policy(0, Duration.ZERO),
        "LIGHT",     new Policy(1, Duration.ofHours(8)),
        "ACTIVE",    new Policy(3, Duration.ofHours(3)),
        "COMPANION", new Policy(6, Duration.ofMinutes(90)),
        "ALIVE",     new Policy(Integer.MAX_VALUE, Duration.ofMinutes(15))
    );

    public Policy get(String intensity) {
        return TABLE.getOrDefault(intensity, TABLE.get("LIGHT"));
    }

    public boolean isAlive(String intensity) {
        return "ALIVE".equalsIgnoreCase(intensity);
    }
}