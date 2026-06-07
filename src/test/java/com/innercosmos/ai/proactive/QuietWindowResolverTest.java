package com.innercosmos.ai.proactive;

import org.junit.jupiter.api.Test;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import static org.junit.jupiter.api.Assertions.*;

class QuietWindowResolverTest {

    private final QuietWindowResolver resolver = new QuietWindowResolver();

    @Test
    void openWindowAllows() {
        // When no quiet conditions are set, should return not quiet
        var reason = resolver.canPushNow(null, ZonedDateTime.now());
        assertFalse(reason.quiet());
    }

    @Test
    void nullUserIdReturnsNotQuiet() {
        var reason = resolver.canPushNow(null, ZonedDateTime.now());
        assertFalse(reason.quiet());
    }
}